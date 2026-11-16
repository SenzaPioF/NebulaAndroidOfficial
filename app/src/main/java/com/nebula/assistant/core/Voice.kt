package com.nebula.assistant.core

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.delay
import kotlinx.coroutines.withTimeoutOrNull
import java.util.Locale
import java.util.concurrent.ConcurrentHashMap

/** Sintesi vocale di Android: voce, velocità e tono scelti nelle impostazioni. */
class Tts(ctx: Context, private val store: ConfigStore) {
    private var tts: TextToSpeech? = null
    @Volatile private var ready = false
    private val waiting = ConcurrentHashMap<String, CompletableDeferred<Unit>>()
    private var counter = 0

    init {
        tts = TextToSpeech(ctx.applicationContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {}
                    override fun onDone(utteranceId: String?) {
                        if (utteranceId != null) waiting.remove(utteranceId)?.complete(Unit)
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        if (utteranceId != null) waiting.remove(utteranceId)?.complete(Unit)
                    }

                    override fun onError(utteranceId: String?, errorCode: Int) {
                        if (utteranceId != null) waiting.remove(utteranceId)?.complete(Unit)
                    }
                })
            }
        }
    }

    private fun chunks(text: String): List<String> {
        val out = ArrayList<String>()
        var rest = text.trim()
        while (rest.length > 3500) {
            var cut = rest.lastIndexOf(". ", 3500)
            if (cut < 500) cut = 3500
            out.add(rest.substring(0, cut + 1))
            rest = rest.substring(cut + 1).trim()
        }
        if (rest.isNotEmpty()) out.add(rest)
        return out
    }

    fun voices(): List<Pair<String, String>> {
        val t = tts ?: return emptyList()
        return try {
            val lang = Locale.forLanguageTag(store.value.lang).language
            t.voices.orEmpty().sortedWith(compareBy({ it.locale.language != lang }, { it.name }))
                .map { Pair(it.name, "${it.name} (${it.locale.toLanguageTag()})") }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private suspend fun waitReady(): Boolean {
        var n = 0
        while (!ready && n < 50) {
            delay(100)
            n++
        }
        return ready
    }

    /** Legge il testo e ritorna quando ha finito. */
    suspend fun speak(text: String) {
        if (text.isBlank() || !waitReady()) return
        val t = tts ?: return
        val c = store.value
        try {
            t.language = Locale.forLanguageTag(c.lang)
            t.setSpeechRate(c.ttsRate.coerceIn(0.4f, 2.5f))
            t.setPitch(c.ttsPitch.coerceIn(0.5f, 2.0f))
            if (c.ttsVoice.isNotBlank()) {
                val v = t.voices.orEmpty().firstOrNull { it.name == c.ttsVoice }
                if (v != null) t.voice = v
            }
        } catch (e: Exception) {
        }
        for (part in chunks(text)) {
            val id = "u${counter++}"
            val d = CompletableDeferred<Unit>()
            waiting[id] = d
            t.speak(part, TextToSpeech.QUEUE_ADD, null, id)
            withTimeoutOrNull(120_000) { d.await() }
            waiting.remove(id)
        }
    }

    fun stop() {
        try {
            tts?.stop()
        } catch (e: Exception) {
        }
        for ((_, d) in waiting) d.complete(Unit)
        waiting.clear()
    }

    fun shutdown() {
        stop()
        try {
            tts?.shutdown()
        } catch (e: Exception) {
        }
    }
}

/** Riconoscimento vocale di Android (gratuito, usa l'app Google). Continuo per l'ascolto sempre attivo. */
class Listener(
    private val ctx: Context,
    private val store: ConfigStore,
    private val onText: (String) -> Unit,
    private val onListening: (Boolean) -> Unit,
    private val fail: (String) -> Unit,
) {
    private val main = Handler(Looper.getMainLooper())
    private var rec: SpeechRecognizer? = null
    @Volatile var running = false
        private set
    @Volatile private var paused = false
    private var continuous = true
    private var errorsInRow = 0

    fun start(continuous: Boolean) {
        this.continuous = continuous
        if (running) return
        running = true
        paused = false
        errorsInRow = 0
        main.post { startInternal() }
    }

    fun stop() {
        running = false
        main.post {
            destroy()
            onListening(false)
        }
    }

    fun pause() {
        paused = true
        main.post { try { rec?.cancel() } catch (e: Exception) { } }
    }

    fun resume() {
        paused = false
        if (running) main.postDelayed({ listenOnce() }, 400)
    }

    private fun destroy() {
        try {
            rec?.destroy()
        } catch (e: Exception) {
        }
        rec = null
    }

    private fun startInternal() {
        if (!running) return
        if (!SpeechRecognizer.isRecognitionAvailable(ctx)) {
            running = false
            onListening(false)
            fail("Il riconoscimento vocale non è disponibile su questo telefono (serve l'app Google). Puoi scrivere i messaggi.")
            return
        }
        destroy()
        val r = SpeechRecognizer.createSpeechRecognizer(ctx)
        rec = r
        r.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {}
            override fun onBeginningOfSpeech() {}
            override fun onRmsChanged(rmsdB: Float) {}
            override fun onBufferReceived(buffer: ByteArray?) {}
            override fun onEndOfSpeech() {}
            override fun onPartialResults(partialResults: Bundle?) {}
            override fun onEvent(eventType: Int, params: Bundle?) {}

            override fun onResults(results: Bundle?) {
                errorsInRow = 0
                val text = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull() ?: ""
                if (!continuous) {
                    running = false
                    onListening(false)
                }
                if (text.isNotBlank()) onText(text)
                if (running) main.postDelayed({ listenOnce() }, 250)
            }

            override fun onError(error: Int) {
                if (!running) return
                when (error) {
                    SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                        if (continuous) main.postDelayed({ listenOnce() }, 300)
                        else {
                            running = false
                            onListening(false)
                        }
                    }
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> main.postDelayed({ startInternal() }, 800)
                    SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                        running = false
                        onListening(false)
                        fail("Il permesso del microfono è negato: attivalo nelle impostazioni di Android.")
                    }
                    else -> {
                        errorsInRow++
                        if (errorsInRow > 5) {
                            running = false
                            onListening(false)
                            fail("Il riconoscimento vocale non risponde (errore $error). Controlla la connessione.")
                        } else {
                            main.postDelayed({ startInternal() }, 1000)
                        }
                    }
                }
            }
        })
        listenOnce()
    }

    private fun listenOnce() {
        if (!running || paused) return
        val r = rec ?: return
        val i = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
        i.putExtra(RecognizerIntent.EXTRA_LANGUAGE, store.value.lang)
        i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
        i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, ctx.packageName)
        try {
            r.startListening(i)
            onListening(true)
        } catch (e: Exception) {
            main.postDelayed({ startInternal() }, 1000)
        }
    }
}
