package com.nebula.assistant.core

import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.concurrent.TimeUnit

class HttpResult(val code: Int, val body: String, val headers: Map<String, String>) {
    val ok: Boolean get() = code in 200..299
    fun json(): JSONObject = JSONObject(body)
    fun jsonArray(): JSONArray = JSONArray(body)
}

object Http {
    val client: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .writeTimeout(60, TimeUnit.SECONDS)
        .build()

    private fun run(req: Request, timeoutSec: Long): HttpResult {
        val c = client.newBuilder()
            .readTimeout(timeoutSec, TimeUnit.SECONDS)
            .callTimeout(timeoutSec + 15, TimeUnit.SECONDS)
            .build()
        c.newCall(req).execute().use { r ->
            val h = HashMap<String, String>()
            for (n in r.headers.names()) h[n.lowercase()] = r.header(n) ?: ""
            return HttpResult(r.code, r.body?.string() ?: "", h)
        }
    }

    fun get(url: String, headers: Map<String, String> = emptyMap(), timeoutSec: Long = 20): HttpResult {
        val b = Request.Builder().url(url).header("User-Agent", "Nebula/1.0 (Android; assistente personale)")
        for ((k, v) in headers) b.header(k, v)
        return run(b.get().build(), timeoutSec)
    }

    fun postJson(url: String, json: String, headers: Map<String, String> = emptyMap(), timeoutSec: Long = 60): HttpResult {
        val b = Request.Builder().url(url).header("User-Agent", "Nebula/1.0 (Android; assistente personale)")
        for ((k, v) in headers) b.header(k, v)
        val body = json.toRequestBody("application/json".toMediaType())
        return run(b.post(body).build(), timeoutSec)
    }

    fun send(
        method: String, url: String, body: String?, contentType: String,
        headers: Map<String, String> = emptyMap(), timeoutSec: Long = 60,
    ): HttpResult {
        val b = Request.Builder().url(url).header("User-Agent", "Nebula/1.0 (Android; assistente personale)")
        for ((k, v) in headers) b.header(k, v)
        val rb = body?.toRequestBody(contentType.toMediaType())
        return run(b.method(method, rb).build(), timeoutSec)
    }

    fun getBytes(url: String, timeoutSec: Long = 120): ByteArray? {
        val c = client.newBuilder()
            .readTimeout(timeoutSec, TimeUnit.SECONDS)
            .callTimeout(timeoutSec + 15, TimeUnit.SECONDS)
            .build()
        val req = Request.Builder().url(url).header("User-Agent", "Nebula/1.0 (Android; assistente personale)").build()
        c.newCall(req).execute().use { r ->
            if (!r.isSuccessful) return null
            return r.body?.bytes()
        }
    }
}

fun clean(t: String?, n: Int): String = (t ?: "").replace(Regex("\\s+"), " ").trim().take(n)

fun lowerFirst(s: String): String = if (s.isEmpty()) s else s.substring(0, 1).lowercase() + s.substring(1)

fun urlEnc(s: String): String = URLEncoder.encode(s, "UTF-8")

fun JSONObject.str(key: String, default: String = ""): String =
    if (has(key) && !isNull(key)) optString(key, default) else default

fun JSONObject.bool(key: String, default: Boolean): Boolean =
    if (has(key) && !isNull(key)) optBoolean(key, default) else default

fun JSONObject.int(key: String, default: Int): Int =
    if (has(key) && !isNull(key)) optInt(key, default) else default

fun JSONObject.dbl(key: String, default: Double): Double =
    if (has(key) && !isNull(key)) optDouble(key, default) else default

/** Estrae il primo oggetto JSON da un testo (le IA a volte aggiungono parole o ```json). */
fun extractJson(text: String): JSONObject {
    var t = text.trim().removePrefix("```json").removePrefix("```").removeSuffix("```").trim()
    val a = t.indexOf('{')
    val b = t.lastIndexOf('}')
    if (a == -1 || b <= a) throw IllegalArgumentException("risposta senza JSON")
    t = t.substring(a, b + 1)
    return JSONObject(t)
}
