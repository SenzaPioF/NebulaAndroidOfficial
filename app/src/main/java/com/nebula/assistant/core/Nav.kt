package com.nebula.assistant.core

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Bundle
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.math.abs
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/*
 * Navigatore senza chiavi: OpenStreetMap (Nominatim) cerca il luogo, OSRM traccia il percorso; poi ogni 2 secondi
 * si confronta la posizione con il tracciato e si danno le indicazioni a voce. Nessuna IA durante il tragitto.
 */

class NavException(message: String) : Exception(message)

data class LatLon(val lat: Double, val lon: Double)

class Step(
    val type: String,
    val modifier: String,
    val name: String,
    val distance: Double,
    val loc: LatLon,
    val exit: Int,
    var along: Double,
)

class Route(
    val coords: List<LatLon>,
    val cum: DoubleArray,
    val steps: List<Step>,
    val distance: Double,
    val duration: Double,
    val mode: String,
    val origin: LatLon,
    val destPt: LatLon,
    val destLabel: String,
)

data class NavState(
    val active: Boolean = false,
    val pos: LatLon? = null,
    val remainingM: Double = 0.0,
    val etaS: Double = 0.0,
    val distToNext: Double? = null,
    val nextText: String = "",
    val offRoute: Boolean = false,
    val arrived: Boolean = false,
    val noPosition: Boolean = false,
    val error: String = "",
)

data class GeoHit(val label: String, val lat: Double, val lon: Double)

data class GeoInfo(val label: String, val road: String, val city: String, val region: String, val country: String)

// --------------------------------------------------------------------------------------
// Geometria
// --------------------------------------------------------------------------------------

private const val R_EARTH = 6371000.0

fun haversine(a: LatLon, b: LatLon): Double {
    val p1 = Math.toRadians(a.lat)
    val p2 = Math.toRadians(b.lat)
    val dp = p2 - p1
    val dl = Math.toRadians(b.lon - a.lon)
    val h = sin(dp / 2) * sin(dp / 2) + cos(p1) * cos(p2) * sin(dl / 2) * sin(dl / 2)
    return 2 * R_EARTH * asin(sqrt(h))
}

private fun toXY(p: LatLon, lat0: Double): DoubleArray =
    doubleArrayOf(Math.toRadians(p.lon) * cos(Math.toRadians(lat0)) * R_EARTH, Math.toRadians(p.lat) * R_EARTH)

/** Distanza di p dal segmento ab (metri) e posizione t in [0,1] della proiezione. */
fun projectOnSegment(p: LatLon, a: LatLon, b: LatLon): Pair<Double, Double> {
    val lat0 = (a.lat + b.lat) / 2
    val pp = toXY(p, lat0)
    val aa = toXY(a, lat0)
    val bb = toXY(b, lat0)
    val dx = bb[0] - aa[0]
    val dy = bb[1] - aa[1]
    val seg2 = dx * dx + dy * dy
    val t = if (seg2 == 0.0) 0.0 else max(0.0, min(1.0, ((pp[0] - aa[0]) * dx + (pp[1] - aa[1]) * dy) / seg2))
    return Pair(hypot(pp[0] - (aa[0] + t * dx), pp[1] - (aa[1] + t * dy)), t)
}

fun buildCum(coords: List<LatLon>): DoubleArray {
    val cum = DoubleArray(coords.size)
    for (i in 1 until coords.size) cum[i] = cum[i - 1] + haversine(coords[i - 1], coords[i])
    return cum
}

/** (distanza dal tracciato in metri, metri percorsi lungo il tracciato, indice del segmento). */
fun nearestOnRoute(pos: LatLon, coords: List<LatLon>, cum: DoubleArray, hint: Int = 0, window: Int = 80): Triple<Double, Double, Int> {
    val n = coords.size - 1
    if (n < 1) return Triple(0.0, 0.0, 0)
    fun scan(lo: Int, hi: Int): Triple<Double, Double, Int> {
        var best = Triple(1e18, 0.0, lo)
        for (i in lo until hi) {
            val (d, t) = projectOnSegment(pos, coords[i], coords[i + 1])
            if (d < best.first) best = Triple(d, cum[i] + t * (cum[i + 1] - cum[i]), i)
        }
        return best
    }
    var best = scan(max(0, hint - 5), min(n, hint + window))
    if (best.first > 150) best = scan(0, n)
    return best
}

// --------------------------------------------------------------------------------------
// Testi delle indicazioni (italiano, tutto in locale)
// --------------------------------------------------------------------------------------

private val MOD_IT = mapOf(
    "left" to "a sinistra", "right" to "a destra", "slight left" to "leggermente a sinistra",
    "slight right" to "leggermente a destra", "sharp left" to "stretta a sinistra",
    "sharp right" to "stretta a destra", "straight" to "dritto", "uturn" to "invertendo la marcia",
)
private val ORDINALS = listOf("prima", "seconda", "terza", "quarta", "quinta", "sesta", "settima", "ottava")

fun fmtDist(m: Double): String {
    val v = max(0.0, m)
    if (v < 1000) {
        val step = if (v < 100) 5 else if (v < 300) 10 else 50
        val r = ((v / step).roundToInt() * step)
        return "${if (r == 0) step else r} metri"
    }
    return String.format(java.util.Locale.ITALY, "%.1f chilometri", v / 1000)
}

fun fmtEta(seconds: Double): String {
    val mins = (max(0.0, seconds) / 60).roundToInt()
    if (mins < 1) return "meno di un minuto"
    if (mins < 60) return "$mins minut${if (mins == 1) "o" else "i"}"
    val h = mins / 60
    val mm = mins % 60
    return "$h or${if (h == 1) "a" else "e"}" + if (mm > 0) " e $mm minuti" else ""
}

fun maneuverText(s: Step): String {
    val t = s.type
    val m = s.modifier
    val on = if (s.name.isNotEmpty()) " in ${s.name}" else ""
    return when (t) {
        "depart" -> "Parti" + if (s.name.isNotEmpty()) " su ${s.name}" else ""
        "arrive" -> "Sei arrivato a destinazione." + when (m) {
            "left" -> " La destinazione è a sinistra."
            "right" -> " La destinazione è a destra."
            else -> ""
        }
        "turn", "end of road" -> if (m == "straight") "Prosegui dritto$on" else "Svolta ${MOD_IT[m] ?: "a destra"}$on"
        "roundabout", "rotary", "roundabout turn" ->
            if (s.exit > 0) "Alla rotatoria prendi la ${ORDINALS[min(s.exit, 8) - 1]} uscita$on" else "Entra nella rotatoria$on"
        "fork" -> "Alla biforcazione tieni la ${if (m.contains("left")) "sinistra" else "destra"}$on"
        "merge" -> "Immettiti$on"
        "on ramp" -> "Prendi la rampa$on"
        "off ramp" -> "Prendi la rampa di uscita$on"
        "new name", "continue" ->
            if (m.isNotEmpty() && m != "straight") "Svolta ${MOD_IT[m] ?: ""}$on".trim() else "Prosegui dritto$on"
        else -> "Prosegui$on"
    }
}

private val THRESH = mapOf("auto" to listOf(800, 300, 80), "bici" to listOf(250, 80), "piedi" to listOf(100, 30))
private val OFF_ROUTE = mapOf("auto" to 40.0, "bici" to 30.0, "piedi" to 25.0)
val SPEED_MS = mapOf("auto" to 13.9, "bici" to 4.8, "piedi" to 1.4)
val MODE_LABEL = mapOf("auto" to "in auto", "bici" to "in bici", "piedi" to "a piedi")

private val ROUTER_BASE = mapOf(
    "auto" to "https://routing.openstreetmap.de/routed-car/route/v1/driving/",
    "bici" to "https://routing.openstreetmap.de/routed-bike/route/v1/driving/",
    "piedi" to "https://routing.openstreetmap.de/routed-foot/route/v1/driving/",
)

fun parseOsrm(data: JSONObject, mode: String, origin: LatLon, dest: LatLon, label: String): Route {
    val routes = data.optJSONArray("routes")
    if (data.optString("code") != "Ok" || routes == null || routes.length() == 0) throw NavException("Non trovo un percorso tra i due punti.")
    val r = routes.getJSONObject(0)
    val ca = r.getJSONObject("geometry").getJSONArray("coordinates")
    val coords = ArrayList<LatLon>()
    for (i in 0 until ca.length()) {
        val c = ca.getJSONArray(i)
        coords.add(LatLon(c.getDouble(1), c.getDouble(0)))
    }
    val cum = buildCum(coords)
    val steps = ArrayList<Step>()
    var hint = 0
    val legs = r.getJSONArray("legs")
    for (li in 0 until legs.length()) {
        val st = legs.getJSONObject(li).getJSONArray("steps")
        for (si in 0 until st.length()) {
            val s = st.getJSONObject(si)
            val mv = s.getJSONObject("maneuver")
            val loc = mv.getJSONArray("location")
            val ll = LatLon(loc.getDouble(1), loc.getDouble(0))
            val (_, along, seg) = nearestOnRoute(ll, coords, cum, hint, 400)
            hint = max(hint, seg)
            steps.add(Step(mv.optString("type"), mv.optString("modifier"), s.optString("name"), s.optDouble("distance", 0.0), ll, mv.optInt("exit", 0), along))
        }
    }
    if (steps.isNotEmpty()) {
        steps.first().along = 0.0
        steps.last().along = cum.last()
    }
    return Route(coords, cum, steps, r.optDouble("distance", cum.last()), r.optDouble("duration", 0.0), mode, origin, dest, label)
}

private val NAV_RX = listOf(
    Regex("^(?:portami|accompagnami|guidami|navigami|indicami la strada)\\s+(?:a|ad|al|allo|alla|all'|in|verso|fino a|fino ad)\\s+(.+)$"),
    Regex("^(?:naviga|navigazione|indicazioni|percorso)\\s+(?:a|ad|al|allo|alla|all'|in|verso|per|fino a)\\s+(.+)$"),
    Regex("^come\\s+(?:arrivo|si arriva|faccio ad arrivare|posso arrivare)\\s+(?:a|ad|al|allo|alla|all'|in)\\s+(.+)$"),
)
private val MODE_WORDS = listOf(
    Regex("\\b(?:a piedi|camminando)\\b") to "piedi",
    Regex("\\b(?:in bici|in bicicletta|con la bici|in bike)\\b") to "bici",
    Regex("\\b(?:in auto|in macchina|con la macchina|con l'auto|in automobile)\\b") to "auto",
)

/** Riconosce «portami a X (a piedi)» e simili: (destinazione, modalità) oppure null. */
fun parseNavRequest(text: String): Pair<String, String>? {
    val t = text.lowercase().trim().replace(Regex("\\s+"), " ").trimEnd('.', '!', '?')
    for (rx in NAV_RX) {
        val m = rx.find(t) ?: continue
        var dest = m.groupValues[1].trim()
        var mode = "auto"
        for ((mrx, name) in MODE_WORDS) {
            if (mrx.containsMatchIn(dest)) {
                dest = mrx.replace(dest, "").trim(' ', ',')
                mode = name
            }
        }
        if (dest.split(" ").size > 1) dest = dest.replace(Regex("^(?:il|lo|la|l')\\s*"), "").trim()
        if (dest.isNotEmpty()) return Pair(dest, mode)
    }
    return null
}

fun shortLabel(label: String): String {
    val parts = label.split(",").map { it.trim() }.filter { it.isNotEmpty() }
        .filter { !Regex("(?i)^(provincia di|città metropolitana|metropolitan)").containsMatchIn(it) }
    return if (parts.isEmpty()) label else parts.take(2).joinToString(", ")
}

// --------------------------------------------------------------------------------------
// Posizione
// --------------------------------------------------------------------------------------

interface PositionProvider {
    fun get(): LatLon?
    fun attach(route: Route) {}
}

/** Cammina lungo il percorso a velocità realistica (moltiplicabile): per provare senza GPS. */
class SimulatedPosition(private val mult: Double) : PositionProvider {
    private var route: Route? = null
    private var t0 = 0L

    override fun attach(route: Route) {
        this.route = route
        t0 = System.currentTimeMillis()
    }

    override fun get(): LatLon? {
        val r = route ?: return null
        val dist = min(r.cum.last(), (System.currentTimeMillis() - t0) / 1000.0 * (SPEED_MS[r.mode] ?: 10.0) * mult)
        for (i in 1 until r.cum.size) {
            if (r.cum[i] >= dist) {
                val span = if (r.cum[i] - r.cum[i - 1] == 0.0) 1.0 else r.cum[i] - r.cum[i - 1]
                val f = (dist - r.cum[i - 1]) / span
                val a = r.coords[i - 1]
                val b = r.coords[i]
                return LatLon(a.lat + (b.lat - a.lat) * f, a.lon + (b.lon - a.lon) * f)
            }
        }
        return r.coords.last()
    }
}

/** GPS (e rete) del telefono: tiene l'ultima posizione ricevuta. */
class GpsPosition(private val ctx: Context) : PositionProvider, LocationListener {
    private val lm = ctx.getSystemService(Context.LOCATION_SERVICE) as LocationManager
    @Volatile private var last: Location? = null
    private var started = false

    private fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(ctx, android.Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED

    @SuppressLint("MissingPermission")
    fun start() {
        if (started || !hasPermission()) return
        started = true
        try {
            for (p in listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)) {
                if (lm.isProviderEnabled(p)) {
                    lm.requestLocationUpdates(p, 2000L, 0f, this, android.os.Looper.getMainLooper())
                    val l = lm.getLastKnownLocation(p)
                    if (l != null && (last == null || l.time > (last?.time ?: 0L))) last = l
                }
            }
        } catch (e: Exception) {
            started = false
        }
    }

    fun stop() {
        try {
            lm.removeUpdates(this)
        } catch (e: Exception) {
        }
        started = false
    }

    override fun get(): LatLon? {
        if (!started) start()
        val l = last ?: return null
        if (System.currentTimeMillis() - l.time > 120000L) return null
        return LatLon(l.latitude, l.longitude)
    }

    override fun onLocationChanged(location: Location) {
        val cur = last
        // preferisce la posizione più precisa se recente
        if (cur == null || location.time >= cur.time || location.accuracy <= cur.accuracy) last = location
    }

    @Deprecated("Deprecated in Java")
    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) {
    }

    override fun onProviderEnabled(provider: String) {
    }

    override fun onProviderDisabled(provider: String) {
    }
}

data class Resolved(
    val lat: Double,
    val lon: Double,
    val source: String, // gps | manuale | rete
    val label: String,
    val city: String = "",
    val region: String = "",
    val country: String = "",
)

/** Dove si trova l'utente: GPS, poi la posizione impostata, poi una stima dall'indirizzo IP (molto grossolana). */
class LocationResolver(private val store: ConfigStore, val gps: GpsPosition) {
    suspend fun resolve(allowIp: Boolean = true): Resolved? {
        val c = store.value
        if (c.locSource != "manuale") {
            val g = gps.get()
            if (g != null) return Resolved(g.lat, g.lon, "gps", "posizione del GPS")
        }
        val mp = c.myPlace
        if (mp != null) return Resolved(mp.lat, mp.lon, "manuale", mp.label)
        if (allowIp) {
            val info = ipInfo()
            if (info != null) return info
        }
        return null
    }

    suspend fun ipInfo(): Resolved? = withContext(Dispatchers.IO) {
        try {
            val r = Http.get("https://ipwho.is/", timeoutSec = 10)
            val g = r.json()
            if (!g.optBoolean("success", true) || !g.has("latitude")) return@withContext null
            val city = g.optString("city")
            val region = g.optString("region")
            val where = listOf(city, region).filter { it.isNotEmpty() }.joinToString(", ").ifEmpty { "zona" }
            Resolved(g.getDouble("latitude"), g.getDouble("longitude"), "rete", "$where (stima dalla rete, può essere molto sbagliata)", city, region, g.optString("country"))
        } catch (e: Exception) {
            null
        }
    }
}

// --------------------------------------------------------------------------------------
// Navigatore
// --------------------------------------------------------------------------------------

class Navigator(
    private val store: ConfigStore,
    private val scope: CoroutineScope,
    private val speak: (String) -> Unit,
    private val onActiveChanged: (Boolean) -> Unit,
) {
    val route = MutableStateFlow<Route?>(null)
    val state = MutableStateFlow(NavState())
    @Volatile var active = false
        private set
    private var job: Job? = null
    private var provider: PositionProvider? = null
    private val announced = HashSet<String>()
    private var off = 0
    private var lastReroute = 0L
    private var hint = 0
    private var noPosTold = false
    private var lastGeo = 0L

    // ---------------- luoghi ----------------
    fun places(): Map<String, Place> = store.value.places

    fun savePlace(name: String, place: Place) {
        val n = name.trim().lowercase().replace(Regex("\\s+"), " ").take(30)
        if (n.isEmpty()) return
        store.update { it.copy(places = it.places + (n to place)) }
    }

    fun deletePlace(name: String) {
        store.update { it.copy(places = it.places - name.lowercase()) }
    }

    private suspend fun throttle() {
        val wait = 1100L - (System.currentTimeMillis() - lastGeo)
        if (wait > 0) delay(wait)
        lastGeo = System.currentTimeMillis()
    }

    suspend fun geocode(text: String, near: LatLon? = null, limit: Int = 3): List<GeoHit> {
        val key = text.trim().lowercase().replace(Regex("\\s+"), " ")
        val saved = places()[key] ?: places()[key.replace(Regex("^(?:a|al|alla|in)\\s+"), "")]
        if (saved != null) return listOf(GeoHit(saved.label, saved.lat, saved.lon))
        throttle()
        return withContext(Dispatchers.IO) {
            var url = "https://nominatim.openstreetmap.org/search?format=json&limit=$limit&accept-language=it&q=${urlEnc(text)}"
            if (near != null) url += "&viewbox=${near.lon - 0.6},${near.lat + 0.6},${near.lon + 0.6},${near.lat - 0.6}"
            val r = Http.get(url)
            if (!r.ok) throw NavException("servizio di ricerca non disponibile (HTTP ${r.code})")
            val a = r.jsonArray()
            val out = ArrayList<GeoHit>()
            for (i in 0 until a.length()) {
                val o = a.getJSONObject(i)
                out.add(GeoHit(o.optString("display_name", text), o.getDouble("lat"), o.getDouble("lon")))
            }
            out
        }
    }

    suspend fun reverse(lat: Double, lon: Double): GeoInfo {
        throttle()
        return withContext(Dispatchers.IO) {
            val r = Http.get("https://nominatim.openstreetmap.org/reverse?format=json&zoom=16&addressdetails=1&accept-language=it&lat=$lat&lon=$lon")
            if (!r.ok) throw NavException("servizio non disponibile (HTTP ${r.code})")
            val d = r.json()
            val a = d.optJSONObject("address")
            fun f(k: String): String = a?.optString(k) ?: ""
            val city = listOf(f("city"), f("town"), f("village"), f("municipality"), f("hamlet")).firstOrNull { it.isNotEmpty() } ?: ""
            GeoInfo(d.optString("display_name"), f("road"), city, f("state"), f("country"))
        }
    }

    // ---------------- percorso ----------------
    private suspend fun routeBetween(origin: LatLon, dest: LatLon, mode: String, label: String): Route = withContext(Dispatchers.IO) {
        val url = (ROUTER_BASE[mode] ?: ROUTER_BASE.getValue("auto")) +
            "${origin.lon},${origin.lat};${dest.lon},${dest.lat}?overview=full&geometries=geojson&steps=true"
        val r = Http.get(url, timeoutSec = 30)
        if (!r.ok) throw NavException("servizio di percorsi non disponibile (HTTP ${r.code})")
        parseOsrm(r.json(), mode, origin, dest, label)
    }

    suspend fun plan(destText: String, mode: String, origin: LatLon?): Route {
        if (origin == null) throw NavException("Non conosco la tua posizione di partenza.")
        val cands = geocode(destText, origin)
        if (cands.isEmpty()) throw NavException("Non trovo «$destText».")
        val d = cands[0]
        val rt = routeBetween(origin, LatLon(d.lat, d.lon), mode, d.label)
        route.value = rt
        return rt
    }

    fun summary(r: Route): String =
        "Percorso ${MODE_LABEL[r.mode] ?: ""} verso ${r.destLabel.substringBefore(",")}: ${fmtDist(r.distance)}, circa ${fmtEta(r.duration)}."

    // ---------------- guida ----------------
    fun start(p: PositionProvider, intro: Boolean = true) {
        val r = route.value ?: throw NavException("Prima traccia un percorso.")
        stop(silent = true)
        provider = p
        p.attach(r)
        active = true
        onActiveChanged(true)
        announced.clear()
        off = 0
        hint = 0
        noPosTold = false
        lastReroute = System.currentTimeMillis()
        if (intro) speak(summary(r) + " " + (r.steps.firstOrNull()?.let { maneuverText(it) + "." } ?: ""))
        job = scope.launch(Dispatchers.Default) {
            while (isActive && active) {
                try {
                    val pos = p.get()
                    if (pos != null) tick(pos)
                    else if (!noPosTold) {
                        noPosTold = true
                        state.value = NavState(active = true, noPosition = true)
                    }
                } catch (e: Exception) {
                    state.value = NavState(active = true, error = (e.message ?: "errore").take(100))
                }
                delay(2000)
            }
        }
    }

    fun stop(silent: Boolean = false) {
        val was = active
        active = false
        job?.cancel()
        job = null
        state.value = NavState(active = false)
        if (was) onActiveChanged(false)
        if (was && !silent) speak("Navigazione interrotta.")
    }

    /** Un passo (ogni 2 secondi): confronta la posizione con il tracciato e, se serve, parla. */
    suspend fun tick(pos: LatLon) {
        val r = route.value ?: return
        val mode = r.mode
        val (dOff, along, seg) = nearestOnRoute(pos, r.coords, r.cum, hint)
        hint = seg
        val total = r.cum.last()
        val remaining = max(0.0, total - along)
        var st = NavState(
            active = true, pos = pos, remainingM = remaining,
            etaS = if (total > 0) r.duration * (remaining / total) else 0.0,
            offRoute = dOff > (OFF_ROUTE[mode] ?: 40.0),
        )
        if (remaining < 25) {
            speak("Sei arrivato a destinazione.")
            st = st.copy(arrived = true, nextText = "Arrivato")
            state.value = st
            active = false
            onActiveChanged(false)
            return
        }
        if (st.offRoute) {
            off++
            st = st.copy(nextText = "Fuori percorso")
            if (off >= 3 && System.currentTimeMillis() - lastReroute > 15000) reroute(pos)
        } else {
            off = 0
            var idx = r.steps.indexOfFirst { it.along > along + 8 }
            if (idx < 0) idx = r.steps.size - 1
            val step = r.steps[idx]
            val dist = max(0.0, step.along - along)
            st = st.copy(distToNext = dist, nextText = maneuverText(step))
            var spoke = false
            for (th in (THRESH[mode] ?: listOf(300)).sorted()) {
                val key = "$idx:$th"
                if (dist <= th && key !in announced) {
                    for (t in (THRESH[mode] ?: listOf(300))) if (t >= th) announced.add("$idx:$t")
                    speak(
                        if (step.type == "arrive") "Tra ${fmtDist(th.toDouble())} arriverai a destinazione"
                        else "Tra ${fmtDist(th.toDouble())}, ${lowerFirst(maneuverText(step))}"
                    )
                    spoke = true
                    break
                }
            }
            if (!spoke && dist <= 25 && "$idx:0" !in announced) {
                announced.add("$idx:0")
                speak(maneuverText(step))
            }
        }
        state.value = st
    }

    private suspend fun reroute(pos: LatLon) {
        lastReroute = System.currentTimeMillis()
        val old = route.value ?: return
        try {
            val nr = routeBetween(pos, old.destPt, old.mode, old.destLabel)
            route.value = nr
            announced.clear()
            off = 0
            hint = 0
            speak("Ricalcolo del percorso.")
        } catch (e: Exception) {
            state.value = NavState(active = true, error = "ricalcolo non riuscito: " + (e.message ?: "").take(80))
        }
    }

    fun statusText(): String {
        val st = state.value
        if (!active || !st.active) return "Non sto guidando nessun percorso."
        return "Mancano ${fmtDist(st.remainingM)}, arrivo tra circa ${fmtEta(st.etaS)}. Prossima indicazione: ${st.nextText.ifEmpty { "prosegui" }}."
    }
}
