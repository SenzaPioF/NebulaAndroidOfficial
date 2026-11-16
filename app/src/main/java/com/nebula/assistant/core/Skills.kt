package com.nebula.assistant.core

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sqrt

/*
 * Abilità locali: rispondono SENZA usare nessuna chiave di IA (meno chiamate, risposte più rapide).
 * Calcoli, conversioni, date, meteo (Open-Meteo, senza chiave), ricerche, appunti, promemoria, volume e musica,
 * navigazione, Google. Tutto ciò che non riconosce passa all'IA.
 */

private class Calc(private val s: String) {
    private var i = 0

    fun parse(): Double {
        val v = expr()
        if (i != s.length) throw IllegalArgumentException("espressione non valida")
        return v
    }

    private fun peek(): Char = if (i < s.length) s[i] else '\u0000'

    private fun expr(): Double {
        var v = term()
        while (peek() == '+' || peek() == '-') {
            val op = s[i++]
            val r = term()
            v = if (op == '+') v + r else v - r
        }
        return v
    }

    private fun term(): Double {
        var v = power()
        while ((peek() == '*' && !(i + 1 < s.length && s[i + 1] == '*')) || peek() == '/' || peek() == '%') {
            val op = s[i++]
            val r = power()
            v = when (op) {
                '*' -> v * r
                '/' -> v / r
                else -> v % r
            }
        }
        return v
    }

    private fun power(): Double {
        val base = unary()
        if (peek() == '^' || (peek() == '*' && i + 1 < s.length && s[i + 1] == '*')) {
            i += if (peek() == '^') 1 else 2
            val e = power()
            if (abs(e) > 12 || abs(base) > 1e9) throw IllegalArgumentException("potenza troppo grande")
            return base.pow(e)
        }
        return base
    }

    private fun unary(): Double {
        if (peek() == '-') {
            i++
            return -unary()
        }
        if (peek() == '+') {
            i++
            return unary()
        }
        return primary()
    }

    private fun primary(): Double {
        if (peek() == '(') {
            i++
            val v = expr()
            if (peek() != ')') throw IllegalArgumentException("parentesi")
            i++
            return v
        }
        if (s.startsWith("sqrt(", i)) {
            i += 5
            val v = expr()
            if (peek() != ')') throw IllegalArgumentException("parentesi")
            i++
            return sqrt(v)
        }
        val st = i
        while (i < s.length && (s[i].isDigit() || s[i] == '.')) i++
        if (st == i) throw IllegalArgumentException("numero atteso")
        return s.substring(st, i).toDouble()
    }
}

fun numIt(v: Double): String {
    if (v == Math.rint(v) && abs(v) < 1e15) {
        val l = v.toLong()
        return if (abs(l) >= 10000) String.format(Locale.ITALY, "%,d", l) else l.toString()
    }
    return String.format(Locale.ITALY, "%.6f", v).trimEnd('0').trimEnd(',')
}

private fun parseNum(s: String): Double {
    var t = s.trim()
    t = if (t.contains(",") && t.contains(".")) t.replace(".", "").replace(",", ".") else t.replace(",", ".")
    return t.toDouble()
}

private fun wordsToExpr(input: String): String {
    var t = input.trim().replace(Regex("^(?:il|lo|la)\\s+"), "")
    t = t.replace(Regex("radice quadrata di\\s*(\\d+(?:[.,]\\d+)?)"), "sqrt(\$1)")
    t = t.replace(Regex("(\\d+(?:[.,]\\d+)?)\\s*(?:%|percento|per cento)\\s+di\\s+(\\d+(?:[.,]\\d+)?)"), "(\$1/100)*\$2")
    val pairs = listOf(
        "\\belevato a\\b" to "**", "\\balla seconda\\b|\\bal quadrato\\b" to "**2", "\\balla terza\\b|\\bal cubo\\b" to "**3",
        "\\bdiviso\\b|\\bfratto\\b" to "/", "\\bper\\b|×" to "*", "\\bpiù\\b" to "+", "\\bmeno\\b" to "-", "÷" to "/",
    )
    for ((a, b) in pairs) t = t.replace(Regex(a), b)
    t = t.replace(Regex("(?<=\\d)\\s*x\\s*(?=\\d)"), "*")
    t = t.replace(Regex("(\\d),(\\d)"), "\$1.\$2")
    return t.replace(Regex("\\s+"), "")
}

private val LEN = mapOf(
    "m" to 1.0, "metro" to 1.0, "metri" to 1.0, "km" to 1000.0, "chilometro" to 1000.0, "chilometri" to 1000.0,
    "cm" to 0.01, "centimetro" to 0.01, "centimetri" to 0.01, "mm" to 0.001, "millimetro" to 0.001, "millimetri" to 0.001,
    "mi" to 1609.344, "miglio" to 1609.344, "miglia" to 1609.344, "ft" to 0.3048, "piede" to 0.3048, "piedi" to 0.3048,
    "pollice" to 0.0254, "pollici" to 0.0254, "yd" to 0.9144, "iarda" to 0.9144, "iarde" to 0.9144,
)
private val MASS = mapOf(
    "kg" to 1.0, "chilo" to 1.0, "chili" to 1.0, "chilogrammo" to 1.0, "chilogrammi" to 1.0, "g" to 0.001, "grammo" to 0.001,
    "grammi" to 0.001, "lb" to 0.45359237, "libbra" to 0.45359237, "libbre" to 0.45359237, "oz" to 0.0283495,
    "oncia" to 0.0283495, "once" to 0.0283495,
)
private val VOL = mapOf(
    "l" to 1.0, "litro" to 1.0, "litri" to 1.0, "ml" to 0.001, "millilitro" to 0.001, "millilitri" to 0.001,
    "gal" to 3.78541, "gallone" to 3.78541, "galloni" to 3.78541,
)
private val SPD = mapOf(
    "km/h" to 1 / 3.6, "kmh" to 1 / 3.6, "m/s" to 1.0, "mph" to 0.44704, "nodi" to 0.514444, "nodo" to 0.514444, "kn" to 0.514444,
)
private val TEMP = mapOf(
    "c" to "c", "°c" to "c", "celsius" to "c", "gradi" to "c", "f" to "f", "°f" to "f", "fahrenheit" to "f", "k" to "k", "kelvin" to "k",
)

fun convertUnits(v: Double, a: String, b: String): Double? {
    for (table in listOf(LEN, MASS, VOL, SPD)) {
        val fa = table[a]
        val fb = table[b]
        if (fa != null && fb != null) return v * fa / fb
    }
    val ta = TEMP[a]
    val tb = TEMP[b]
    if (ta != null && tb != null) {
        val c = when (ta) {
            "c" -> v
            "f" -> (v - 32) * 5 / 9
            else -> v - 273.15
        }
        return when (tb) {
            "c" -> c
            "f" -> c * 9 / 5 + 32
            else -> c + 273.15
        }
    }
    return null
}

private val MONTHS_IT = listOf("gennaio", "febbraio", "marzo", "aprile", "maggio", "giugno", "luglio", "agosto", "settembre", "ottobre", "novembre", "dicembre")
private val WEEKDAYS_IT = listOf("lunedì", "martedì", "mercoledì", "giovedì", "venerdì", "sabato", "domenica")

fun easter(year: Int): LocalDate {
    val a = year % 19
    val b = year / 100
    val c = year % 100
    val d = b / 4
    val e = b % 4
    val f = (b + 8) / 25
    val g = (b - f + 1) / 3
    val h = (19 * a + b - d - g + 15) % 30
    val i = c / 4
    val k = c % 4
    val l = (32 + 2 * e + 2 * i - h - k) % 7
    val m = (a + 11 * h + 22 * l) / 451
    val month = (h + l - 7 * m + 114) / 31
    val day = (h + l - 7 * m + 114) % 31 + 1
    return LocalDate.of(year, month, day)
}

private val FIXED_DAYS = mapOf(
    "natale" to Pair(12, 25), "capodanno" to Pair(1, 1), "ferragosto" to Pair(8, 15), "epifania" to Pair(1, 6),
    "halloween" to Pair(10, 31), "san valentino" to Pair(2, 14), "festa della repubblica" to Pair(6, 2), "primo maggio" to Pair(5, 1),
)

fun nextOccurrence(name: String, today: LocalDate): LocalDate? {
    val n = name.trim().lowercase().replace(Regex("^(?:il|lo|la|l')\\s*"), "")
    if (n == "pasqua") {
        val d = easter(today.year)
        return if (!d.isBefore(today)) d else easter(today.year + 1)
    }
    FIXED_DAYS[n]?.let { (mo, dd) ->
        val d = LocalDate.of(today.year, mo, dd)
        return if (!d.isBefore(today)) d else LocalDate.of(today.year + 1, mo, dd)
    }
    val m = Regex("^(\\d{1,2})[/\\s-](\\d{1,2}|${MONTHS_IT.joinToString("|")})(?:[/\\s-](\\d{2,4}))?$").find(n) ?: return null
    return try {
        val day = m.groupValues[1].toInt()
        val mo = m.groupValues[2].toIntOrNull() ?: (MONTHS_IT.indexOf(m.groupValues[2]) + 1)
        if (m.groupValues[3].isNotEmpty()) {
            val y = m.groupValues[3].toInt().let { if (it < 100) it + 2000 else it }
            LocalDate.of(y, mo, day)
        } else {
            val d = LocalDate.of(today.year, mo, day)
            if (!d.isBefore(today)) d else LocalDate.of(today.year + 1, mo, day)
        }
    } catch (e: Exception) {
        null
    }
}

private val WMO = mapOf(
    0 to "cielo sereno", 1 to "prevalentemente sereno", 2 to "parzialmente nuvoloso", 3 to "coperto", 45 to "nebbia",
    48 to "nebbia con brina", 51 to "pioggerella leggera", 53 to "pioggerella", 55 to "pioggerella intensa",
    61 to "pioggia debole", 63 to "pioggia", 65 to "pioggia forte", 66 to "pioggia gelata", 67 to "pioggia gelata forte",
    71 to "neve debole", 73 to "neve", 75 to "neve forte", 77 to "granelli di neve", 80 to "rovesci deboli", 81 to "rovesci",
    82 to "rovesci violenti", 85 to "rovesci di neve", 86 to "forti rovesci di neve", 95 to "temporale",
    96 to "temporale con grandine", 99 to "temporale con forte grandine",
)

private val SEARCH_SITES = mapOf(
    "google" to "https://www.google.com/search?q=", "youtube" to "https://www.youtube.com/results?search_query=",
    "wikipedia" to "https://it.wikipedia.org/w/index.php?search=", "amazon" to "https://www.amazon.it/s?k=",
    "maps" to "https://www.openstreetmap.org/search?query=", "mappe" to "https://www.openstreetmap.org/search?query=",
)

class LocalSkills(private val a: Assistant) {
    private val notesPrefs = a.ctx.getSharedPreferences("nebula_notes", Context.MODE_PRIVATE)

    suspend fun handle(raw: String): String? {
        val t = raw.lowercase().trim().replace(Regex("\\s+"), " ").trimEnd('.', '!', '?', ',', ';')
        val steps: List<suspend (String) -> String?> = listOf(
            { x -> identity(x) }, { x -> place(x) }, { x -> nav(x) }, { x -> google(x) }, { x -> calc(x) },
            { x -> units(x) }, { x -> dates(x) }, { x -> weather(x) }, { x -> search(x) }, { x -> notes(x) },
            { x -> remind(x) }, { x -> media(x) },
        )
        for (fn in steps) {
            val r = try {
                fn(t)
            } catch (e: Exception) {
                "Non ci sono riuscita: " + (e.message ?: "").take(120)
            }
            if (r != null) return r
        }
        return null
    }

    private suspend fun identity(t: String): String? =
        if (Regex("^(?:come ti chiami|chi sei|qual è il tuo nome|dimmi il tuo nome)$").matches(t))
            "Mi chiamo ${a.name()}, sono il tuo assistente vocale." else null

    private suspend fun place(t: String): String? {
        val m = Regex("^(?:imposta|metti|aggiorna|cambia|salva)\\s+(?:la\\s+)?(?:mia\\s+)?posizione\\s+(?:a|ad|in|su|come)\\s+(.+)$").find(t)
            ?: Regex("^(?:mi trovo|ora mi trovo|adesso mi trovo|la mia posizione è)\\s+(?:a|ad|in|nel|nella|su)\\s+(.+)$").find(t)
            ?: return null
        return a.setMyPlace(m.groupValues[1])
    }

    private suspend fun nav(t: String): String? {
        val req = parseNavRequest(t)
        if (req != null) return a.startNavigation(req.first, req.second, true)
        if (Regex("^(?:ferma|interrompi|chiudi|annulla|termina|stop)\\s+(?:la\\s+)?(?:navigazione|navigatore|percorso)$").matches(t)) {
            if (!a.nav.active) return "Non sto guidando nessun percorso."
            a.nav.stop(silent = true)
            return "Navigazione interrotta."
        }
        if (Regex("^(?:quanto manca|tra quanto arrivo|a che ora arrivo|quanto tempo manca)").containsMatchIn(t)) return a.nav.statusText()
        if (Regex("^(?:ripeti|dimmi) (?:l')?(?:ultima |prossima )?(?:indicazione|svolta)|^prossima (?:indicazione|svolta)").containsMatchIn(t)) {
            val st = a.nav.state.value
            return if (a.nav.active && st.nextText.isNotEmpty()) st.nextText else "Non sto guidando nessun percorso."
        }
        val m = Regex("^salva (?:questo posto|la destinazione|il posto|la meta) come (.+)$").find(t)
        if (m != null) {
            val r = a.nav.route.value ?: return "Prima traccia un percorso: salvo la sua destinazione."
            a.nav.savePlace(m.groupValues[1], Place(r.destPt.lat, r.destPt.lon, r.destLabel))
            return "Ho salvato «${m.groupValues[1].trim()}»: ora puoi dire «portami a ${m.groupValues[1].trim()}»."
        }
        return null
    }

    private suspend fun google(t: String): String? {
        googleIntentKind(t) ?: return null
        if (!a.google.connected()) return "Per questo devo essere collegata a Google: apri Servizi, poi Account Google."
        return a.google.handle(t)
    }

    private suspend fun calc(t: String): String? {
        val m = Regex("^(?:quanto fa|calcola|dimmi quanto fa|quanto viene|quanto è)\\s+(.+)$").find(t)
        val body: String = when {
            m != null -> m.groupValues[1]
            Regex("^(?:il\\s+)?\\d+(?:[.,]\\d+)?\\s*(?:%|percento|per cento)\\s+di\\s+\\d+(?:[.,]\\d+)?$").matches(t) -> t
            Regex("^[\\d\\s.,+\\-*/x×÷()%^]+$").matches(t) && Regex("\\d\\s*[+\\-*/x×÷^%]\\s*[\\d(]").containsMatchIn(t) -> t
            else -> return null
        }
        val expr = wordsToExpr(body)
        if (!Regex("^[\\d.+\\-*/()sqrt^%]+$").matches(expr)) return null
        return "${body.trim()} fa ${numIt(Calc(expr).parse())}."
    }

    private suspend fun units(t: String): String? {
        val m1 = Regex("^(?:converti|quanti|quante|quanto)?\\s*(-?\\d+(?:[.,]\\d+)?)\\s*([a-zàèéìòù°/]+)\\s+(?:in|a|to)\\s+([a-zàèéìòù°/]+)$").find(t)
        val v: String
        val ua: String
        val ub: String
        if (m1 != null) {
            v = m1.groupValues[1]; ua = m1.groupValues[2]; ub = m1.groupValues[3]
        } else {
            val m2 = Regex("^(?:quanti|quante)\\s+([a-zàèéìòù°/]+)\\s+(?:sono|fanno|ci sono in)\\s+(-?\\d+(?:[.,]\\d+)?)\\s*([a-zàèéìòù°/]+)$").find(t) ?: return null
            ub = m2.groupValues[1]; v = m2.groupValues[2]; ua = m2.groupValues[3]
        }
        val res = convertUnits(parseNum(v), ua, ub) ?: return null
        return "$v $ua sono ${numIt(res)} $ub."
    }

    private suspend fun dates(t: String): String? {
        val today = LocalDate.now()
        val m = Regex("che (?:giorno|data) (?:sarà|è) (?:tra|fra) (\\d+) (giorn[oi]|settiman[ae]|mes[ei])").find(t)
        if (m != null) {
            val n = m.groupValues[1].toLong()
            val u = m.groupValues[2]
            val d = if (u.startsWith("giorn")) today.plusDays(n) else if (u.startsWith("settim")) today.plusWeeks(n) else today.plusMonths(n)
            return "${WEEKDAYS_IT[d.dayOfWeek.value - 1]} ${d.dayOfMonth} ${MONTHS_IT[d.monthValue - 1]} ${d.year}."
        }
        val m2 = Regex("^quanti giorni (?:mancano|ci sono|restano) (?:a|al|alla|allo|per|fino a|fino al)\\s+(.+)$").find(t) ?: return null
        val target = nextOccurrence(m2.groupValues[1], today) ?: return null
        val n = java.time.temporal.ChronoUnit.DAYS.between(today, target)
        return if (n == 0L) "È oggi!" else "Mancano $n giorn${if (n == 1L) "o" else "i"} a ${m2.groupValues[1].trim()}."
    }

    private suspend fun weather(t: String): String? {
        val m = Regex("^(?:che tempo fa|com'è il tempo|come è il tempo|meteo|previsioni(?: del tempo)?|che tempo farà)(?: (oggi|domani|adesso|ora|dopodomani))?(?: (?:a|ad|in|per|su))?\\s*(.*)$").find(t) ?: return null
        val whenW = m.groupValues[1].ifEmpty { "ora" }
        val city = m.groupValues[2].replace(Regex("\\b(oggi|domani|adesso|ora|dopodomani)\\b"), "").trim()
        var lat: Double
        var lon: Double
        var name: String
        if (city.isNotEmpty()) {
            val g = withContext(Dispatchers.IO) {
                Http.get("https://geocoding-api.open-meteo.com/v1/search?count=1&language=it&name=${urlEnc(city)}").json().optJSONArray("results")
            }
            if (g == null || g.length() == 0) return "Non trovo la città «$city»."
            val o = g.getJSONObject(0)
            lat = o.getDouble("latitude"); lon = o.getDouble("longitude"); name = o.optString("name", city)
        } else {
            val pos = a.position() ?: return "Non riesco a capire dove sei: dimmi la città, per esempio «che tempo fa a Roma»."
            lat = pos.lat; lon = pos.lon
            name = if (pos.source == "gps") "qui" else (pos.city.ifEmpty { shortLabel(pos.label).substringBefore(" (") })
            if (pos.source == "rete") name += " (stima dalla rete)"
        }
        val f = withContext(Dispatchers.IO) {
            Http.get(
                "https://api.open-meteo.com/v1/forecast?latitude=$lat&longitude=$lon&timezone=auto&forecast_days=3" +
                    "&current=temperature_2m,weather_code,wind_speed_10m" +
                    "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_probability_max"
            ).json()
        }
        val d = f.getJSONObject("daily")
        val day = when (whenW) { "domani" -> 1; "dopodomani" -> 2; else -> 0 }
        val desc = WMO[d.getJSONArray("weather_code").getInt(day)] ?: "tempo variabile"
        val part = "minima ${Math.round(d.getJSONArray("temperature_2m_min").getDouble(day))}, massima ${Math.round(d.getJSONArray("temperature_2m_max").getDouble(day))}, " +
            "probabilità di pioggia ${d.getJSONArray("precipitation_probability_max").optInt(day, 0)}%"
        if (day == 0) {
            val c = f.getJSONObject("current")
            return "A $name ora ci sono ${Math.round(c.getDouble("temperature_2m"))} gradi, ${WMO[c.getInt("weather_code")] ?: desc}, " +
                "vento a ${Math.round(c.getDouble("wind_speed_10m"))} chilometri orari. Oggi $part."
        }
        return "${if (day == 1) "Domani" else "Dopodomani"} a $name: $desc, $part."
    }

    private suspend fun search(t: String): String? {
        val m = Regex("^(?:cerca|trova|googla)\\s+(.+?)\\s+(?:su|in|sul|nel)\\s+(google|youtube|wikipedia|amazon|maps|mappe)$").find(t)
        val q: String
        val site: String
        if (m != null) {
            q = m.groupValues[1]; site = m.groupValues[2]
        } else {
            val m2 = Regex("^(?:cerca su internet|cerca sul web|googla|cerca su google)\\s+(.+)$").find(t) ?: Regex("^cerca\\s+(.+)$").find(t) ?: return null
            q = m2.groupValues[1]; site = "google"
        }
        val opened = a.openUrl((SEARCH_SITES[site] ?: SEARCH_SITES.getValue("google")) + urlEnc(q.trim()), "${q.trim()} su $site")
        return if (opened) "Cerco «${q.trim()}» su $site." else "Ecco il link per cercare «${q.trim()}» su $site."
    }

    private fun loadNotes(): JSONArray = try { JSONArray(notesPrefs.getString("notes", "[]")) } catch (e: Exception) { JSONArray() }

    private suspend fun notes(t: String): String? {
        val m = Regex("^(?:prendi nota|annota|appunta|scrivi un appunto|segna)[:,]?\\s*(.+)$").find(t)
        if (m != null) {
            val arr = loadNotes()
            arr.put(JSONObject().put("ts", System.currentTimeMillis()).put("text", m.groupValues[1].trim()))
            notesPrefs.edit().putString("notes", arr.toString()).apply()
            return "Annotato."
        }
        if (Regex("^(?:leggi|mostra|dimmi|quali sono) (?:le )?(?:mie )?(?:note|appunti)$").matches(t)) {
            val arr = loadNotes()
            if (arr.length() == 0) return "Non hai note salvate."
            val last = (maxOf(0, arr.length() - 5) until arr.length()).joinToString(". ") { arr.getJSONObject(it).optString("text") }
            return "Hai ${arr.length()} not${if (arr.length() == 1) "a" else "e"}. Le ultime: $last."
        }
        if (Regex("^(?:cancella|svuota|elimina) (?:tutte )?(?:le )?(?:mie )?(?:note|appunti)$").matches(t)) {
            notesPrefs.edit().putString("notes", "[]").apply()
            return "Ho cancellato tutte le note."
        }
        return null
    }

    private suspend fun remind(t: String): String? {
        val m = Regex("^ricordami (?:di |che )?(.+?) (?:tra|fra) (\\d+) (secondi|secondo|minuti|minuto|ore|ora)$").find(t) ?: return null
        val label = m.groupValues[1].trim()
        val n = m.groupValues[2].toInt()
        val unit = m.groupValues[3]
        val secs = n * (if (unit.startsWith("or")) 3600 else if (unit.startsWith("minut")) 60 else 1)
        a.startTimer(secs, label)
        return "Va bene, tra $n $unit ti ricordo di $label."
    }

    private val mediaRules = listOf(
        Triple("^(?:alza|aumenta|più forte)(?: il)? volume|^volume (?:su|alto)", "volume_up", 3),
        Triple("^(?:abbassa|diminuisci|riduci)(?: il)? volume|^volume (?:giù|basso)", "volume_down", 3),
        Triple("^(?:muto|silenzia|togli l'audio|metti in muto|riattiva l'audio)$", "mute", 1),
        Triple("^(?:metti in pausa|pausa|ferma la musica|riprendi la musica|riavvia la musica|play|metti la musica)$", "play_pause", 1),
        Triple("^(?:prossima|successiva|salta la) (?:canzone|traccia|brano)|^(?:canzone|traccia) successiva", "next", 1),
        Triple("^(?:precedente|canzone precedente|traccia precedente|brano precedente)$", "previous", 1),
    )

    private suspend fun media(t: String): String? {
        for ((rx, action, times) in mediaRules) {
            if (Regex(rx).containsMatchIn(t)) return a.mediaControl(action, times)
        }
        return null
    }
}
