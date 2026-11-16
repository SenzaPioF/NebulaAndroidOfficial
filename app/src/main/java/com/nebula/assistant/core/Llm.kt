package com.nebula.assistant.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject

class LlmException(message: String) : Exception(message)

/** vision: null = non supportata; "=" = stesso modello della conversazione; altrimenti il nome del modello. */
data class Preset(
    val id: String,
    val label: String,
    val base: String,
    val model: String,
    val vision: String?,
    val keyUrl: String,
    val needsKey: Boolean,
) {
    val shortName: String get() = label.substringBefore(" (")
}

val PRESETS: List<Preset> = listOf(
    Preset("groq", "Groq (gratis)", "https://api.groq.com/openai/v1", "openai/gpt-oss-20b", "qwen/qwen3.6-27b", "https://console.groq.com/keys", true),
    Preset("gemini", "Google Gemini (gratis)", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash", "=", "https://aistudio.google.com/apikey", true),
    Preset("openrouter", "OpenRouter (modelli :free)", "https://openrouter.ai/api/v1", "meta-llama/llama-3.3-70b-instruct:free", null, "https://openrouter.ai/keys", true),
    Preset("mistral", "Mistral (piano gratuito)", "https://api.mistral.ai/v1", "mistral-small-latest", "=", "https://console.mistral.ai/api-keys", true),
    Preset("cerebras", "Cerebras (gratis)", "https://api.cerebras.ai/v1", "gpt-oss-120b", null, "https://cloud.cerebras.ai", true),
    Preset("sambanova", "SambaNova (gratis)", "https://api.sambanova.ai/v1", "Meta-Llama-3.3-70B-Instruct", null, "https://cloud.sambanova.ai", true),
    Preset("pollinations", "Pollinations (pubblico, senza chiave, sperimentale)", "https://text.pollinations.ai/openai", "openai", null, "", false),
    Preset("ollama", "Ollama (su un PC della tua rete, gratis)", "http://192.168.1.10:11434/v1", "llama3.1", null, "https://ollama.com/download", false),
    Preset("custom", "Personalizzato (compatibile OpenAI)", "", "", null, "", false),
)

private val MODEL_PREFS: Map<String, List<String>> = mapOf(
    "groq" to listOf("gpt-oss-20b", "gpt-oss-120b", "llama-3.3-70b", "qwen", "llama"),
    "gemini" to listOf("gemini-2.5-flash", "gemini-flash", "gemini-2.0-flash", "flash", "gemini"),
    "openrouter" to listOf("llama-3.3-70b-instruct:free", "gpt-oss", "qwen", "mistral", "gemma", ":free"),
    "mistral" to listOf("mistral-small-latest", "mistral-medium", "mistral-large", "small"),
    "cerebras" to listOf("gpt-oss-120b", "llama", "qwen"),
    "sambanova" to listOf("Llama-3.3-70B", "Llama", "DeepSeek", "Qwen"),
    "ollama" to listOf("llama3.1", "llama3", "qwen2.5", "mistral", "gemma"),
)

private val JUNK_MODELS = listOf(
    "embedding", "embed", "whisper", "tts", "guard", "imagen", "veo", "aqa", "moderation",
    "transcribe", "rerank", "dall-e", "image",
)

private val UUID_RX = Regex("^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$")

/** Riconosce il servizio dal formato della chiave. Non invia mai la chiave a servizi a cui non appartiene. */
fun detectProvider(key: String): String? {
    val k = key.trim()
    return when {
        k.startsWith("gsk_") -> "groq"
        k.startsWith("AIza") -> "gemini"
        k.startsWith("sk-or-") -> "openrouter"
        k.startsWith("csk-") -> "cerebras"
        UUID_RX.matches(k) -> "sambanova"
        Regex("^[A-Za-z0-9]{32}$").matches(k) -> "mistral"
        else -> null
    }
}

fun pickModel(provider: String, models: List<String>): String {
    for (pref in MODEL_PREFS[provider] ?: emptyList()) {
        for (m in models) if (m.lowercase().contains(pref.lowercase())) return m
    }
    return models.firstOrNull() ?: ""
}

class Router(private val store: ConfigStore) {
    private val paused = HashMap<Int, Long>()
    private val autoModels = HashMap<Int, String>()
    val status = MutableStateFlow<Map<Int, String>>(emptyMap())

    fun slots(): List<Slot> = normalizeSlots(store.value.slots)

    fun preset(slot: Slot): Preset = PRESETS.firstOrNull { it.id == slot.provider } ?: PRESETS.last()

    fun baseOf(slot: Slot): String {
        val p = preset(slot)
        val custom = (slot.provider == "custom" || slot.provider == "ollama") && slot.base.isNotBlank()
        return (if (custom) slot.base else p.base).trim().trimEnd('/')
    }

    fun modelOf(slot: Slot, kind: String, idx: Int? = null): String {
        val p = preset(slot)
        val chat = slot.model.trim().ifEmpty { (if (idx != null) autoModels[idx] else null) ?: p.model }
        if (kind == "vision") {
            if (slot.visionModel.isNotBlank()) return slot.visionModel.trim()
            val v = p.vision ?: return ""
            return if (v == "=") chat else v
        }
        return chat
    }

    fun usable(slot: Slot, kind: String = "chat"): Boolean {
        val p = preset(slot)
        if (baseOf(slot).isEmpty()) return false
        if (p.needsKey && slot.key.isBlank()) return false
        if (kind == "vision" && modelOf(slot, "vision").isEmpty()) return false
        return modelOf(slot, "chat").isNotEmpty() || kind == "vision"
    }

    fun configured(kind: String = "chat"): Boolean = slots().any { usable(it, kind) }

    fun label(idx: Int): String = "Chiave ${idx + 1} (${preset(slots()[idx]).shortName})"

    fun candidates(kind: String): List<Int> {
        val c = store.value
        val route = if (kind == "vision") c.routeVision else c.routeChat
        val sl = slots()
        if (route == "1" || route == "2" || route == "3") {
            val i = route.toInt() - 1
            return if (usable(sl[i], kind)) listOf(i) else emptyList()
        }
        return sl.indices.filter { usable(sl[it], kind) }
    }

    private fun setStatus(idx: Int, text: String) {
        status.value = status.value + (idx to text)
    }

    fun statusOf(idx: Int): String = status.value[idx] ?: ""

    private fun pause(idx: Int, seconds: Int, why: String) {
        paused[idx] = System.currentTimeMillis() + seconds * 1000L
        setStatus(idx, "in pausa ${seconds}s: $why")
    }

    private fun headersFor(slot: Slot): Map<String, String> {
        val h = HashMap<String, String>()
        h["X-Title"] = "Nebula"
        if (slot.key.isNotBlank()) h["Authorization"] = "Bearer " + slot.key.trim()
        return h
    }

    /** Invia la richiesta al primo slot disponibile e restituisce (risposta, indice dello slot). */
    suspend fun request(kind: String, payload: JSONObject, timeoutSec: Long = 60, only: Int? = null): Pair<JSONObject, Int> =
        withContext(Dispatchers.IO) {
            val cands = if (only != null) listOf(only) else candidates(kind)
            if (cands.isEmpty()) {
                throw LlmException(
                    if (kind == "vision")
                        "Nessuna chiave può analizzare immagini: usa Groq, Gemini o Mistral, oppure indica un «modello per le immagini» tra le opzioni avanzate."
                    else "Nessuna chiave API configurata: aggiungila nelle impostazioni, sezione Chiavi API."
                )
            }
            val slots = slots()
            val errors = ArrayList<String>()
            val waiting = ArrayList<Int>()
            val healed = HashSet<Int>()
            for (idx in cands) {
                val slot = slots[idx]
                val now = System.currentTimeMillis()
                val until = paused[idx] ?: 0L
                if (only == null && until > now) {
                    waiting.add(((until - now) / 1000).toInt())
                    errors.add("${label(idx)}: in pausa per il limite gratuito")
                    continue
                }
                val body = JSONObject(payload.toString())
                body.put("model", modelOf(slot, kind, idx))
                val res = try {
                    Http.postJson(baseOf(slot) + "/chat/completions", body.toString(), headersFor(slot), timeoutSec)
                } catch (e: Exception) {
                    pause(idx, 15, "nessuna risposta dal servizio")
                    errors.add("${label(idx)}: nessuna risposta")
                    continue
                }
                if (res.ok) {
                    try {
                        val data = res.json()
                        paused.remove(idx)
                        setStatus(idx, "ok")
                        return@withContext Pair(data, idx)
                    } catch (e: Exception) {
                        errors.add("${label(idx)}: risposta non valida")
                        continue
                    }
                }
                var detail = ""
                try {
                    val j = JSONObject(res.body)
                    val err = j.opt("error")
                    detail = if (err is JSONObject) err.optString("message") else (err?.toString() ?: "")
                } catch (e: Exception) {
                    detail = res.body.take(120)
                }
                detail = detail.replace(Regex("\\s+"), " ").take(160)
                if ((res.code == 400 || res.code == 404) && detail.lowercase().contains("model") &&
                    slot.model.isBlank() && kind != "vision" && idx !in healed
                ) {
                    healed.add(idx) // il modello predefinito non esiste più: ne cerca uno valido
                    try {
                        val nm = pickModel(slot.provider, listModelsInternal(slot))
                        if (nm.isNotEmpty() && nm != body.optString("model")) {
                            autoModels[idx] = nm
                            val retry = JSONObject(payload.toString())
                            retry.put("model", nm)
                            val r2 = Http.postJson(baseOf(slot) + "/chat/completions", retry.toString(), headersFor(slot), timeoutSec)
                            if (r2.ok) {
                                setStatus(idx, "ok, uso il modello $nm")
                                return@withContext Pair(r2.json(), idx)
                            }
                        }
                    } catch (e: Exception) {
                    }
                }
                if (res.code == 429 || res.code >= 500) {
                    val wait = (res.headers["retry-after"]?.toDoubleOrNull() ?: if (res.code == 429) 60.0 else 20.0)
                    pause(idx, wait.coerceIn(5.0, 600.0).toInt(),
                        if (res.code == 429) "limite raggiunto" else "servizio in errore (${res.code})")
                } else if (res.code == 401 || res.code == 403) {
                    pause(idx, 300, "chiave non valida o non autorizzata")
                } else {
                    setStatus(idx, "errore ${res.code}: $detail")
                }
                errors.add("${label(idx)}: HTTP ${res.code} $detail".trim())
            }
            if (waiting.isNotEmpty() && waiting.size == cands.size) {
                throw LlmException("Tutte le chiavi hanno raggiunto il limite gratuito: riprova tra circa ${waiting.minOrNull()} secondi.")
            }
            throw LlmException("Nessun servizio ha risposto. " + errors.joinToString(" | "))
        }

    private fun listModelsInternal(slot: Slot): List<String> {
        if (baseOf(slot).isEmpty()) throw LlmException("Indirizzo del servizio mancante.")
        val r = Http.get(baseOf(slot) + "/models", headersFor(slot), 20)
        if (!r.ok) throw LlmException("HTTP ${r.code}: controlla la chiave.")
        val j = r.json()
        val arr = j.optJSONArray("data") ?: j.optJSONArray("models") ?: return emptyList()
        val out = LinkedHashSet<String>()
        for (i in 0 until arr.length()) {
            val m = arr.optJSONObject(i) ?: continue
            val id = (m.optString("id").ifEmpty { m.optString("name") }).replace("models/", "")
            if (id.isEmpty() || JUNK_MODELS.any { id.lowercase().contains(it) }) continue
            if (slot.provider == "openrouter") {
                val pr = m.optJSONObject("pricing")
                val free = id.endsWith(":free") || (pr != null && pr.optString("prompt") == "0" && pr.optString("completion") == "0")
                if (!free) continue
            }
            out.add(id)
        }
        return out.sorted()
    }

    suspend fun listModels(idx: Int): List<String> = withContext(Dispatchers.IO) { listModelsInternal(slots()[idx]) }

    suspend fun test(idx: Int): String {
        val p = JSONObject()
        p.put("max_tokens", 30)
        p.put("messages", org.json.JSONArray().put(JSONObject().put("role", "user").put("content", "Rispondi solo con la parola: ok")))
        val (data, _) = request("chat", p, 40, idx)
        val text = data.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content") ?: ""
        return text.replace(Regex("\\s+"), " ").trim().take(60).ifEmpty { "(risposta vuota, ma la chiave funziona)" }
    }

    private fun updateSlot(idx: Int, f: (Slot) -> Slot) {
        store.update { c -> c.copy(slots = normalizeSlots(c.slots).mapIndexed { i, s -> if (i == idx) f(s) else s }) }
    }

    /** Collegamento automatico: riconosce il servizio, sceglie il modello e prova la chiave. */
    suspend fun setup(idx: Int): String {
        var slot = slots()[idx]
        val found = detectProvider(slot.key)
        if (found != null && found != slot.provider) {
            updateSlot(idx) { it.copy(provider = found, model = "") }
            autoModels.remove(idx)
            slot = slots()[idx]
        }
        paused.remove(idx)
        val models = try {
            listModels(idx)
        } catch (e: LlmException) {
            val m = e.message ?: ""
            if (m.contains("401") || m.contains("403")) throw LlmException("la chiave non è valida per questo servizio")
            emptyList()
        }
        if (models.isNotEmpty() && slot.model.isBlank()) autoModels[idx] = pickModel(slot.provider, models)
        test(idx)
        val now = slots()[idx]
        return "${preset(now).shortName} collegato, modello ${modelOf(now, "chat", idx)}."
    }

    fun clearAuto(idx: Int) {
        autoModels.remove(idx)
        paused.remove(idx)
    }

    fun resetStatus(idx: Int) {
        status.value = status.value - idx
    }
}

/** Alcuni servizi restituiscono chiamate agli strumenti senza id: lo aggiunge. */
fun normalizeToolCalls(calls: org.json.JSONArray?): org.json.JSONArray {
    val out = org.json.JSONArray()
    if (calls == null) return out
    for (i in 0 until calls.length()) {
        val tc = calls.optJSONObject(i) ?: continue
        if (tc.optString("id").isEmpty()) tc.put("id", "call_${System.currentTimeMillis() % 1000000000}_$i")
        if (!tc.has("type")) tc.put("type", "function")
        out.put(tc)
    }
    return out
}
