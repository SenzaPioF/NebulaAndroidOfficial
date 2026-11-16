package com.nebula.assistant.core

import android.content.Context
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import org.json.JSONArray
import org.json.JSONObject

const val DEFAULT_PERSONALITY =
    "Sei Nebula, l'assistente vocale personale dell'utente. Rispondi sempre in italiano, in modo colloquiale, " +
        "con un tocco di arguzia british-butler ma senza esagerare. Sii conciso: 1-3 frasi, salvo quando " +
        "l'utente chiede esplicitamente più dettagli."

val DEFAULT_GOOGLE_FEATURES: Set<String> = setOf("gmail_read", "drive_read", "calendar_read", "classroom")

data class Slot(
    val provider: String = "groq",
    val key: String = "",
    val model: String = "",
    val visionModel: String = "",
    val base: String = "",
)

data class Contact(val name: String, val number: String)

data class Place(val lat: Double, val lon: Double, val label: String)

fun defaultSlots(): List<Slot> = listOf(Slot("groq"), Slot("gemini"), Slot("openrouter"))

fun normalizeSlots(raw: List<Slot>): List<Slot> {
    val base = defaultSlots()
    return List(3) { i -> if (i < raw.size) raw[i] else base[i] }
}

data class Config(
    val aiName: String = "Nebula",
    val personality: String = DEFAULT_PERSONALITY,
    val lang: String = "it-IT",
    val alwaysOn: Boolean = false,
    val wakeRequired: Boolean = true,
    val wakeWord: String = "nebula",
    val saveKeys: Boolean = true,
    val savedCalls: Int = 0,
    val slots: List<Slot> = defaultSlots(),
    val routeChat: String = "auto",
    val routeVision: String = "auto",
    val theme: String = "default",
    val lightUi: Boolean = false,
    val ttsVoice: String = "",
    val ttsRate: Float = 1.0f,
    val ttsPitch: Float = 1.0f,
    val contacts: List<Contact> = emptyList(),
    val places: Map<String, Place> = emptyMap(),
    val myPlace: Place? = null,
    val locSource: String = "auto",
    val navSim: Boolean = false,
    val navSimSpeed: Int = 6,
    val navStart: String = "",
    val countryCode: String = "39",
    val googleFeatures: Set<String> = DEFAULT_GOOGLE_FEATURES,
    val googleEmail: String = "",
    val googleConnected: Boolean = false,
    val googleGranted: Set<String> = emptySet(),
)

private fun placeToJson(p: Place): JSONObject = JSONObject().put("lat", p.lat).put("lon", p.lon).put("label", p.label)

private fun placeFromJson(o: JSONObject): Place = Place(o.dbl("lat", 0.0), o.dbl("lon", 0.0), o.str("label"))

fun Config.toJson(): JSONObject {
    val o = JSONObject()
    o.put("aiName", aiName)
    o.put("personality", personality)
    o.put("lang", lang)
    o.put("alwaysOn", alwaysOn)
    o.put("wakeRequired", wakeRequired)
    o.put("wakeWord", wakeWord)
    o.put("saveKeys", saveKeys)
    o.put("savedCalls", savedCalls)
    val sl = JSONArray()
    for (s in slots) {
        sl.put(
            JSONObject().put("provider", s.provider).put("key", s.key).put("model", s.model)
                .put("visionModel", s.visionModel).put("base", s.base)
        )
    }
    o.put("slots", sl)
    o.put("routeChat", routeChat)
    o.put("routeVision", routeVision)
    o.put("theme", theme)
    o.put("lightUi", lightUi)
    o.put("ttsVoice", ttsVoice)
    o.put("ttsRate", ttsRate.toDouble())
    o.put("ttsPitch", ttsPitch.toDouble())
    val cs = JSONArray()
    for (c in contacts) cs.put(JSONObject().put("name", c.name).put("number", c.number))
    o.put("contacts", cs)
    val ps = JSONObject()
    for ((k, v) in places) ps.put(k, placeToJson(v))
    o.put("places", ps)
    if (myPlace != null) o.put("myPlace", placeToJson(myPlace))
    o.put("locSource", locSource)
    o.put("navSim", navSim)
    o.put("navSimSpeed", navSimSpeed)
    o.put("navStart", navStart)
    o.put("countryCode", countryCode)
    val gf = JSONArray()
    for (f in googleFeatures) gf.put(f)
    o.put("googleFeatures", gf)
    o.put("googleEmail", googleEmail)
    o.put("googleConnected", googleConnected)
    val gg = JSONArray()
    for (f in googleGranted) gg.put(f)
    o.put("googleGranted", gg)
    return o
}

fun configFromJson(o: JSONObject): Config {
    val d = Config()
    val slots = ArrayList<Slot>()
    val sa = o.optJSONArray("slots")
    if (sa != null) {
        for (i in 0 until sa.length()) {
            val s = sa.optJSONObject(i) ?: continue
            slots.add(Slot(s.str("provider", "groq"), s.str("key"), s.str("model"), s.str("visionModel"), s.str("base")))
        }
    }
    val contacts = ArrayList<Contact>()
    val ca = o.optJSONArray("contacts")
    if (ca != null) {
        for (i in 0 until ca.length()) {
            val c = ca.optJSONObject(i) ?: continue
            contacts.add(Contact(c.str("name"), c.str("number")))
        }
    }
    val places = LinkedHashMap<String, Place>()
    val po = o.optJSONObject("places")
    if (po != null) {
        val it = po.keys()
        while (it.hasNext()) {
            val k = it.next()
            val v = po.optJSONObject(k) ?: continue
            places[k] = placeFromJson(v)
        }
    }
    val gf = HashSet<String>()
    val ga = o.optJSONArray("googleFeatures")
    if (ga != null) for (i in 0 until ga.length()) gf.add(ga.optString(i))
    val gg = HashSet<String>()
    val gga = o.optJSONArray("googleGranted")
    if (gga != null) for (i in 0 until gga.length()) gg.add(gga.optString(i))
    return Config(
        aiName = o.str("aiName", d.aiName),
        personality = o.str("personality", d.personality),
        lang = o.str("lang", d.lang),
        alwaysOn = o.bool("alwaysOn", d.alwaysOn),
        wakeRequired = o.bool("wakeRequired", d.wakeRequired),
        wakeWord = o.str("wakeWord", d.wakeWord),
        saveKeys = o.bool("saveKeys", d.saveKeys),
        savedCalls = o.int("savedCalls", d.savedCalls),
        slots = if (slots.isEmpty()) d.slots else normalizeSlots(slots),
        routeChat = o.str("routeChat", d.routeChat),
        routeVision = o.str("routeVision", d.routeVision),
        theme = o.str("theme", d.theme),
        lightUi = o.bool("lightUi", d.lightUi),
        ttsVoice = o.str("ttsVoice", d.ttsVoice),
        ttsRate = o.dbl("ttsRate", d.ttsRate.toDouble()).toFloat(),
        ttsPitch = o.dbl("ttsPitch", d.ttsPitch.toDouble()).toFloat(),
        contacts = contacts,
        places = places,
        myPlace = o.optJSONObject("myPlace")?.let { placeFromJson(it) },
        locSource = o.str("locSource", d.locSource),
        navSim = o.bool("navSim", d.navSim),
        navSimSpeed = o.int("navSimSpeed", d.navSimSpeed),
        navStart = o.str("navStart", d.navStart),
        countryCode = o.str("countryCode", d.countryCode),
        googleFeatures = if (ga == null) d.googleFeatures else gf,
        googleEmail = o.str("googleEmail", d.googleEmail),
        googleConnected = o.bool("googleConnected", d.googleConnected),
        googleGranted = gg,
    )
}

class ConfigStore(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("nebula", Context.MODE_PRIVATE)
    private val _flow = MutableStateFlow(load())
    val flow: StateFlow<Config> = _flow
    val value: Config get() = _flow.value

    private fun load(): Config {
        return try {
            val s = prefs.getString("cfg", null) ?: return Config()
            configFromJson(JSONObject(s))
        } catch (e: Exception) {
            Config()
        }
    }

    @Synchronized
    fun update(f: (Config) -> Config) {
        _flow.value = f(_flow.value)
        prefs.edit().putString("cfg", _flow.value.toJson().toString()).apply()
    }
}
