package com.nebula.assistant.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.Dp
import com.nebula.assistant.core.AiState
import com.nebula.assistant.core.DANGER_COLOR
import com.nebula.assistant.core.OK_COLOR
import com.nebula.assistant.core.WARN_COLOR
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** La sfera centrale: respira da ferma, ruota quando elabora, pulsa quando parla. */
@Composable
fun Orb(state: AiState, size: Dp, light: Boolean, modifier: Modifier = Modifier) {
    val p = LocalPal.current
    val inf = rememberInfiniteTransition(label = "orb")
    val t = inf.animateFloat(
        initialValue = 0f, targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(if (light) 16000 else 8000, easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )
    val err = state == AiState.ERROR
    val accent = if (err) DANGER_COLOR else p.accent
    val a3 = if (err) Color(0xFFFFE1E4) else p.a3
    val dim = if (err) Color(0xFF7A2B35) else p.dim
    val ring = if (state == AiState.THINKING) WARN_COLOR else accent
    Canvas(modifier.size(size)) {
        val tt = t.value * 2f * PI.toFloat()           // 0..2π durante il ciclo
        val w = this.size.width
        val k = w / 200f
        val c = Offset(w / 2f, w / 2f)
        // alone morbido
        drawCircle(
            brush = Brush.radialGradient(listOf(accent.copy(alpha = 0.24f), Color.Transparent), center = c, radius = w * 0.5f),
            radius = w * 0.5f, center = c,
        )
        // anello tratteggiato che ruota
        val speed = if (state == AiState.THINKING) 8f else 1f
        rotate(degrees = t.value * 360f * speed, pivot = c) {
            drawCircle(
                color = ring.copy(alpha = if (state == AiState.LISTENING || state == AiState.THINKING) 0.75f else 0.35f),
                radius = 86f * k, center = c,
                style = Stroke(width = 1.4f * k, pathEffect = PathEffect.dashPathEffect(floatArrayOf(2f * k, 6f * k))),
            )
        }
        // barre attorno alla sfera (si muovono mentre ascolta o parla)
        val active = state == AiState.LISTENING || state == AiState.SPEAKING
        val n = if (light) 24 else 40
        for (i in 0 until n) {
            val ang = i.toFloat() / n * 2f * PI.toFloat()
            var amp = 0f
            if (active) {
                amp = 0.5f + 0.5f * sin(tt * 5f + i * 0.9f) * sin(tt * 2f + i * 0.37f)
                if (state == AiState.LISTENING) amp *= 0.75f
            }
            val len = (4f + amp * 22f) * k
            val r0 = 60f * k
            drawLine(
                color = accent.copy(alpha = if (active) 0.9f else 0.32f),
                start = Offset(c.x + r0 * cos(ang), c.y + r0 * sin(ang)),
                end = Offset(c.x + (r0 + len) * cos(ang), c.y + (r0 + len) * sin(ang)),
                strokeWidth = 2.4f * k, cap = StrokeCap.Round,
            )
        }
        // disco centrale
        var scale = 1f + 0.03f * sin(tt)
        if (state == AiState.SPEAKING) scale = 1f + 0.06f * sin(tt * 6f)
        val r = 48f * k * scale
        drawCircle(
            brush = Brush.radialGradient(
                0f to a3, 0.55f to accent, 1f to dim, center = Offset(c.x - 6f * k, c.y - 9f * k), radius = r * 1.35f,
            ),
            radius = r, center = c,
        )
        drawCircle(color = a3.copy(alpha = 0.45f), radius = 31f * k * scale, center = c, style = Stroke(width = 1.4f * k))
        drawCircle(color = Color.White.copy(alpha = 0.35f), radius = 7f * k, center = Offset(c.x - 14f * k, c.y - 16f * k))
    }
}

val STATE_LABELS: Map<AiState, String> = mapOf(
    AiState.IDLE to "Inattivo", AiState.LISTENING to "In ascolto", AiState.THINKING to "Elaborazione",
    AiState.SPEAKING to "Risposta", AiState.ERROR to "Errore",
)

fun stateColor(s: AiState, textDim: Color, accent: Color): Color = when (s) {
    AiState.IDLE -> textDim
    AiState.LISTENING -> accent
    AiState.THINKING -> WARN_COLOR
    AiState.SPEAKING -> OK_COLOR
    AiState.ERROR -> DANGER_COLOR
}
