package com.nebula.assistant.core

import android.content.Context
import android.graphics.Bitmap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.opencv.android.OpenCVLoader
import org.opencv.android.Utils
import org.opencv.core.Mat
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import org.opencv.objdetect.FaceDetectorYN
import org.opencv.objdetect.FaceRecognizerSF
import java.io.File
import java.io.FileOutputStream

/** Build con riconoscimento dei volti: OpenCV (YuNet + SFace), tutto sul telefono. Sperimentale. */
object FaceEngineFactory {
    val available: Boolean = true

    fun create(ctx: Context): FaceEngine? = OpenCvFaceEngine(ctx.applicationContext)
}

private val MODELS = listOf(
    "face_detection_yunet_2023mar.onnx" to "https://github.com/opencv/opencv_zoo/raw/main/models/face_detection_yunet/face_detection_yunet_2023mar.onnx",
    "face_recognition_sface_2021dec.onnx" to "https://github.com/opencv/opencv_zoo/raw/main/models/face_recognition_sface/face_recognition_sface_2021dec.onnx",
)

class OpenCvFaceEngine(private val ctx: Context) : FaceEngine {
    private var det: FaceDetectorYN? = null
    private var rec: FaceRecognizerSF? = null

    override suspend fun prepare(onStatus: (String) -> Unit) {
        if (det != null && rec != null) return
        withContext(Dispatchers.IO) {
            if (!OpenCVLoader.initLocal()) throw IllegalStateException("OpenCV non si carica su questo telefono.")
            val files = ArrayList<File>()
            for ((name, url) in MODELS) {
                val f = File(ctx.filesDir, name)
                if (!f.exists() || f.length() < 10000) {
                    onStatus("Scarico il modello $name (una tantum)…")
                    val bytes = Http.getBytes(url, 180) ?: throw IllegalStateException("download del modello non riuscito ($name)")
                    FileOutputStream(f).use { it.write(bytes) }
                }
                files.add(f)
            }
            det = FaceDetectorYN.create(files[0].absolutePath, "", Size(320.0, 320.0), 0.8f, 0.3f, 5000)
            rec = FaceRecognizerSF.create(files[1].absolutePath, "")
        }
    }

    override suspend fun embedding(bmp: Bitmap): FloatArray? = withContext(Dispatchers.Default) {
        val d = det ?: return@withContext null
        val r = rec ?: return@withContext null
        val rgba = Mat()
        Utils.bitmapToMat(bmp, rgba)
        val bgr = Mat()
        Imgproc.cvtColor(rgba, bgr, Imgproc.COLOR_RGBA2BGR)
        d.setInputSize(Size(bgr.width().toDouble(), bgr.height().toDouble()))
        val faces = Mat()
        d.detect(bgr, faces)
        if (faces.rows() == 0) return@withContext null
        var best = 0
        var bestArea = -1.0
        for (i in 0 until faces.rows()) {
            val area = faces.get(i, 2)[0] * faces.get(i, 3)[0]
            if (area > bestArea) {
                bestArea = area
                best = i
            }
        }
        val aligned = Mat()
        r.alignCrop(bgr, faces.row(best), aligned)
        val feat = Mat()
        r.feature(aligned, feat)
        val arr = FloatArray(feat.cols() * feat.rows())
        feat.get(0, 0, arr)
        arr
    }
}
