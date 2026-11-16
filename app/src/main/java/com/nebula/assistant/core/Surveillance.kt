package com.nebula.assistant.core

import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/** Ultimo fotogramma della fotocamera: lo aggiorna l'anteprima, lo leggono visione e sorveglianza. */
object CameraHub {
    @Volatile var latest: Bitmap? = null
    @Volatile var latestAt: Long = 0L
}

/**
 * Sorveglianza: confronta ogni ~1,5 secondi il volto inquadrato con quelli registrati; se è diverso scatta l'allarme.
 * Funziona solo con l'app aperta e la fotocamera attiva. Non è un sistema di sicurezza professionale.
 */
class Surveillance(private val a: Assistant) {
    val engine: FaceEngine? = FaceEngineFactory.create(a.ctx)
    private val faceStore = FaceStore(a.ctx)
    val status = MutableStateFlow("Sorveglianza spenta.")
    val enrollStatus = MutableStateFlow(enrollText())
    val active = MutableStateFlow(false)
    private var job: Job? = null

    fun count(): Int = faceStore.load().size

    private fun enrollText(): String {
        val n = count()
        return when (n) {
            0 -> "Nessun volto registrato."
            1 -> "1 campione di volto registrato."
            else -> "$n campioni di volto registrati."
        }
    }

    suspend fun enroll() {
        val eng = engine
        if (eng == null) {
            enrollStatus.value = "Questa versione non include il riconoscimento dei volti (vedi il README: opzione «con_volti»)."
            return
        }
        enrollStatus.value = "Rilevo il volto…"
        try {
            eng.prepare { enrollStatus.value = it }
            val bmp = CameraHub.latest
            if (bmp == null) {
                enrollStatus.value = "Nessun fotogramma dalla fotocamera: apri la fotocamera e inquadra il viso."
                return
            }
            val emb = eng.embedding(bmp)
            if (emb == null) {
                enrollStatus.value = "Nessun volto rilevato: avvicinati e assicurati di avere luce sul viso, poi riprova."
                return
            }
            faceStore.save(faceStore.load() + listOf(emb))
            enrollStatus.value = enrollText()
        } catch (e: Exception) {
            enrollStatus.value = "Errore: " + (e.message ?: "").take(120)
        }
    }

    fun clear() {
        faceStore.save(emptyList())
        enrollStatus.value = enrollText()
    }

    fun start() {
        val eng = engine
        if (eng == null) {
            status.value = "Questa versione non include il riconoscimento dei volti."
            return
        }
        if (count() == 0) {
            status.value = "Registra almeno un volto prima di attivare la sorveglianza."
            return
        }
        if (job != null) return
        active.value = true
        status.value = "Carico il modello…"
        job = a.scope.launch(Dispatchers.Default) {
            try {
                eng.prepare { status.value = it }
            } catch (e: Exception) {
                status.value = "Errore: " + (e.message ?: "").take(120)
                active.value = false
                job = null
                return@launch
            }
            status.value = "Sorveglianza attiva: nessuna persona rilevata."
            while (isActive) {
                delay(1500)
                try {
                    val bmp = CameraHub.latest ?: continue
                    if (System.currentTimeMillis() - CameraHub.latestAt > 5000) continue
                    val emb = eng.embedding(bmp)
                    if (emb == null) {
                        if (!a.alarmActive.value) status.value = "Sorveglianza attiva: nessuna persona rilevata."
                        continue
                    }
                    val best = faceStore.load().maxOfOrNull { cosine(it, emb) } ?: -1f
                    if (best >= FACE_THRESHOLD) {
                        a.stopAlarm()
                        status.value = "Volto riconosciuto ✓"
                    } else {
                        status.value = "Volto NON riconosciuto!"
                        a.triggerAlarm()
                    }
                } catch (e: Exception) {
                    // un fotogramma saltato non è un problema
                }
            }
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        active.value = false
        a.stopAlarm()
        status.value = "Sorveglianza spenta."
    }
}
