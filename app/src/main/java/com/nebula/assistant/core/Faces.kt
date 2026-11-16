package com.nebula.assistant.core

import android.content.Context
import android.graphics.Bitmap
import org.json.JSONArray
import java.io.File
import kotlin.math.sqrt

/** Similarità del coseno sopra questa soglia = stesso volto (modello SFace di OpenCV). */
const val FACE_THRESHOLD = 0.363f

interface FaceEngine {
    /** Scarica i modelli (una tantum) e li prepara. */
    suspend fun prepare(onStatus: (String) -> Unit)

    /** Vettore del volto più grande nell'immagine, oppure null se non c'è nessun volto. */
    suspend fun embedding(bmp: Bitmap): FloatArray?
}

fun cosine(a: FloatArray, b: FloatArray): Float {
    var dot = 0f
    var na = 0f
    var nb = 0f
    val n = minOf(a.size, b.size)
    for (i in 0 until n) {
        dot += a[i] * b[i]
        na += a[i] * a[i]
        nb += b[i] * b[i]
    }
    val d = sqrt(na) * sqrt(nb)
    return if (d == 0f) 0f else dot / d
}

/** Volti registrati (solo sul telefono, in un file privato dell'app). */
class FaceStore(ctx: Context) {
    private val file = File(ctx.filesDir, "faces.json")

    fun load(): List<FloatArray> {
        return try {
            if (!file.exists()) return emptyList()
            val a = JSONArray(file.readText())
            List(a.length()) { i ->
                val v = a.getJSONArray(i)
                FloatArray(v.length()) { k -> v.getDouble(k).toFloat() }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun save(list: List<FloatArray>) {
        val a = JSONArray()
        for (f in list) {
            val v = JSONArray()
            for (x in f) v.put(x.toDouble())
            a.put(v)
        }
        file.writeText(a.toString())
    }
}
