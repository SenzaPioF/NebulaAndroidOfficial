package com.nebula.assistant.ui

import android.Manifest
import android.graphics.Bitmap
import android.graphics.Matrix
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.nebula.assistant.core.Assistant
import com.nebula.assistant.core.CameraHub
import com.nebula.assistant.core.FaceEngineFactory
import kotlinx.coroutines.launch
import java.util.concurrent.Executors

/** Anteprima della fotocamera: aggiorna anche l'ultimo fotogramma per visione e sorveglianza. */
@Composable
fun CameraBox(modifier: Modifier, a: Assistant, front: Boolean = false) {
    val ctx = LocalContext.current
    val owner = LocalLifecycleOwner.current
    val previewView = remember { PreviewView(ctx).apply { scaleType = PreviewView.ScaleType.FILL_CENTER } }
    DisposableEffect(owner, front) {
        val future = ProcessCameraProvider.getInstance(ctx)
        val preview = Preview.Builder().build()
        preview.setSurfaceProvider(previewView.surfaceProvider)
        val analysis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        val executor = Executors.newSingleThreadExecutor()
        analysis.setAnalyzer(executor) { img ->
            try {
                val now = System.currentTimeMillis()
                if (now - CameraHub.latestAt > 400) {
                    var bmp: Bitmap = img.toBitmap()
                    val rot = img.imageInfo.rotationDegrees
                    if (rot != 0) {
                        val m = Matrix()
                        m.postRotate(rot.toFloat())
                        bmp = Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
                    }
                    CameraHub.latest = bmp
                    CameraHub.latestAt = now
                }
            } catch (e: Exception) {
            } finally {
                img.close()
            }
        }
        var provider: ProcessCameraProvider? = null
        future.addListener({
            try {
                val pr = future.get()
                provider = pr
                pr.unbind(preview, analysis)
                pr.bindToLifecycle(
                    owner,
                    if (front) CameraSelector.DEFAULT_FRONT_CAMERA else CameraSelector.DEFAULT_BACK_CAMERA,
                    preview, analysis,
                )
            } catch (e: Exception) {
            }
        }, ContextCompat.getMainExecutor(ctx))
        onDispose {
            try {
                provider?.unbind(preview, analysis)
            } catch (e: Exception) {
            }
            executor.shutdown()
        }
    }
    AndroidView(factory = { previewView }, modifier = modifier)
}

@Composable
fun SurveillanceSheet(a: Assistant, act: ActivityActions, onClose: () -> Unit) {
    val s = a.surveillance
    val active by s.active.collectAsState()
    val status by s.status.collectAsState()
    val enroll by s.enrollStatus.collectAsState()
    val scope = rememberCoroutineScope()
    var camOk by remember { mutableStateOf(act.hasPermission(Manifest.permission.CAMERA)) }

    NSheet("Sorveglianza", onClose) {
        if (!FaceEngineFactory.available) {
            NNote("Questa versione dell'app è stata compilata senza il riconoscimento dei volti (per avere un APK leggero). Per attivarlo, nel workflow GitHub scegli «Run workflow» e spunta «con_volti».")
        }
        NNote("Registra il tuo volto, poi attiva la sorveglianza: se la fotocamera inquadra un volto diverso dai tuoi scatta l'allarme. Funziona solo con l'app aperta e la fotocamera attiva. Non è un sistema di sicurezza professionale.")
        NCard {
            if (camOk) CameraBox(Modifier.fillMaxWidth().height(220.dp), a, front = true)
            else NButton("Consenti la fotocamera", { act.askPermissions(listOf(Manifest.permission.CAMERA)) { camOk = it } }, Modifier.padding(14.dp))
        }
        NNote(enroll)
        NButton("Registra il mio volto", { scope.launch { s.enroll() } }, Modifier.fillMaxWidth(), kind = "primary")
        NButton("Cancella i volti registrati", { s.clear() }, Modifier.fillMaxWidth(), kind = "danger")
        NCard {
            RowSwitch("Sorveglianza attiva", status, active) { v -> if (v) s.start() else s.stop() }
        }
    }
}
