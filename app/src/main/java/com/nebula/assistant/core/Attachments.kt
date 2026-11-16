package com.nebula.assistant.core

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.net.Uri
import android.provider.OpenableColumns
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext
import java.util.zip.ZipInputStream

const val MAX_FILE_CHARS = 15000

/** Estrae il testo dagli allegati: testo, Word (.docx), Excel (.xlsx), PDF e immagini (con OCR). */
object Attachments {
    fun displayName(ctx: Context, uri: Uri): String {
        try {
            ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
                val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (i >= 0 && c.moveToFirst()) return c.getString(i) ?: "file"
            }
        } catch (e: Exception) {
        }
        return uri.lastPathSegment ?: "file"
    }

    suspend fun ocr(bmp: Bitmap): String {
        val rec = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
        return rec.process(InputImage.fromBitmap(bmp, 0)).await().text
    }

    suspend fun extract(ctx: Context, uri: Uri): Pair<String, String> {
        val name = displayName(ctx, uri)
        val lower = name.lowercase()
        val text = withContext(Dispatchers.IO) {
            when {
                lower.endsWith(".pdf") -> pdfText(ctx, uri)
                lower.endsWith(".docx") -> docxText(ctx, uri)
                lower.endsWith(".xlsx") -> xlsxText(ctx, uri)
                listOf(".png", ".jpg", ".jpeg", ".webp", ".bmp", ".gif").any { lower.endsWith(it) } -> imageText(ctx, uri)
                else -> plainText(ctx, uri)
            }
        }
        return Pair(name, text)
    }

    private fun plainText(ctx: Context, uri: Uri): String {
        val bytes = ctx.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return ""
        val head = bytes.take(4096)
        if (head.any { it.toInt() == 0 }) return "" // file binario non leggibile come testo
        return String(bytes, Charsets.UTF_8)
    }

    private suspend fun imageText(ctx: Context, uri: Uri): String {
        val bmp = ctx.contentResolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it) } ?: return ""
        val t = ocr(bmp).trim()
        return "[Nota: Nebula non «vede» l'immagine, ha solo letto il testo scritto al suo interno con l'OCR]\n\n" +
            t.ifEmpty { "(nessun testo riconosciuto nell'immagine)" }
    }

    private suspend fun pdfText(ctx: Context, uri: Uri): String {
        val pfd = ctx.contentResolver.openFileDescriptor(uri, "r") ?: return ""
        val out = StringBuilder()
        pfd.use {
            val renderer = PdfRenderer(it)
            renderer.use { r ->
                val pages = minOf(r.pageCount, 5)
                for (i in 0 until pages) {
                    val page = r.openPage(i)
                    val w = (page.width * 1.6).toInt()
                    val h = (page.height * 1.6).toInt()
                    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                    Canvas(bmp).drawColor(android.graphics.Color.WHITE)
                    page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    page.close()
                    out.append(ocr(bmp)).append("\n\n")
                }
                if (r.pageCount > pages) out.append("\n[...documento troncato: lette solo le prime $pages pagine di ${r.pageCount}...]")
            }
        }
        return out.toString()
    }

    private fun zipEntries(ctx: Context, uri: Uri, wanted: (String) -> Boolean): Map<String, String> {
        val out = LinkedHashMap<String, String>()
        ctx.contentResolver.openInputStream(uri)?.use { ins ->
            ZipInputStream(ins).use { z ->
                var e = z.nextEntry
                while (e != null) {
                    if (wanted(e.name)) out[e.name] = String(z.readBytes(), Charsets.UTF_8)
                    e = z.nextEntry
                }
            }
        }
        return out
    }

    private fun unxml(s: String): String = s.replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")

    private fun docxText(ctx: Context, uri: Uri): String {
        val xml = zipEntries(ctx, uri) { it == "word/document.xml" }["word/document.xml"] ?: return ""
        val t = xml.replace(Regex("</w:p>"), "\n").replace(Regex("<w:tab/>"), "\t").replace(Regex("<[^>]+>"), "")
        return unxml(t)
    }

    private fun xlsxText(ctx: Context, uri: Uri): String {
        val files = zipEntries(ctx, uri) { it == "xl/sharedStrings.xml" || it.startsWith("xl/worksheets/sheet") }
        val shared = ArrayList<String>()
        files["xl/sharedStrings.xml"]?.let { xml ->
            for (m in Regex("(?s)<si>(.*?)</si>").findAll(xml)) {
                shared.add(unxml(Regex("(?s)<t[^>]*>(.*?)</t>").findAll(m.groupValues[1]).joinToString("") { it.groupValues[1] }))
            }
        }
        val out = StringBuilder()
        for ((name, xml) in files) {
            if (!name.startsWith("xl/worksheets/sheet")) continue
            out.append("--- Foglio: ").append(name.substringAfterLast('/').removeSuffix(".xml")).append(" ---\n")
            for (row in Regex("(?s)<row[^>]*>(.*?)</row>").findAll(xml)) {
                val cells = ArrayList<String>()
                for (c in Regex("(?s)<c([^>]*?)(?:/>|>(.*?)</c>)").findAll(row.groupValues[1])) {
                    val attrs = c.groupValues[1]
                    val v = Regex("<v>(.*?)</v>").find(c.groupValues[2])?.groupValues?.get(1) ?: ""
                    cells.add(if (attrs.contains("t=\"s\"")) shared.getOrElse(v.toIntOrNull() ?: -1) { "" } else unxml(v))
                }
                out.append(cells.joinToString(",")).append('\n')
            }
        }
        return out.toString()
    }
}
