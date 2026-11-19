package com.retro.vfdlauncher.ui.home

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.runtime.State
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import com.retro.vfdlauncher.data.weather.RoofFx
import com.retro.vfdlauncher.ui.theme.Vfd
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Seconds of the shared weather clock; loops every [LOOP_S] seconds. */
private const val LOOP_S = 120f

/** One clock for the rain/sun animation of a card; read only while drawing. */
@Composable
fun rememberWeatherClock(): State<Float> =
    rememberInfiniteTransition(label = "roof-weather").animateFloat(
        initialValue = 0f,
        targetValue = LOOP_S,
        animationSpec = infiniteRepeatable(tween((LOOP_S * 1000).toInt(), easing = LinearEasing), RepeatMode.Restart),
        label = "t",
    )

/**
 * Animated weather around the MR2 outline of the TOP-DOWN meter, in the same 120x40 viewBox:
 * roof up: rain falls and splashes on the roof line / road; roof down: the sun arcs over the
 * car with turning rays and breeze streaks drift past the nose. Drawn in teal like the canvas.
 */
@Composable
fun RoofWeatherFx(fx: RoofFx, roofDown: Boolean, clock: State<Float>, modifier: Modifier, color: Color = Vfd.Teal) {
    Canvas(modifier) {
        val t = clock.value
        val sx = size.width / 120f
        val sy = size.height / 40f
        fun p(x: Float, y: Float) = Offset(x * sx, y * sy)
        val w = 1.3f * sx
        when (fx) {
            RoofFx.RAIN -> drawRain(t, ::p, w, color, if (roofDown) ROOF_DOWN_TOP else ROOF_UP_TOP)
            RoofFx.SUN -> drawSun(t, ::p, w, color)
            RoofFx.NONE -> Unit
        }
    }
}

// ---------------------------------------------------------------- rain

/** Outline tops (x, y pairs) where drops land: over the roof, or into the open cabin. */
private val ROOF_UP_TOP = floatArrayOf(
    3f, 26.5f, 7f, 22.4f, 15f, 18.7f, 34f, 15.7f, 55.6f, 5.7f, 66f, 4.4f, 74f, 4.4f,
    83f, 7.6f, 92f, 12.5f, 109.5f, 15f, 116.6f, 22.2f,
)
private val ROOF_DOWN_TOP = floatArrayOf(
    3f, 26.5f, 7f, 22.4f, 15f, 18.7f, 34f, 15.7f, 55f, 5.5f, 56.6f, 6.6f, 58f, 15.3f,
    70.6f, 14.6f, 72.5f, 9f, 74f, 12.4f, 95f, 13f, 109.5f, 15f, 116.6f, 22.2f,
)

/** Top of the car (and the road beside it), in viewBox units, where a drop lands. */
private fun landingY(x: Float, pts: FloatArray): Float {
    if (x < pts[0] || x > pts[pts.size - 2]) return 38f // road
    var i = 0
    while (i < pts.size - 2 && x > pts[i + 2]) i += 2
    val f = (x - pts[i]) / (pts[i + 2] - pts[i])
    return pts[i + 1] + f * (pts[i + 3] - pts[i + 1])
}

/** Drop columns: x position, speed (units/s) and phase, fixed so the pattern looks random but stays calm. */
private val drops = List(11) { i ->
    val x = 4f + i * 10.9f + ((i * 37) % 7) // spread over the width
    Triple(x, 26f + (i * 13) % 11, (i * 0.37f) % 1f)
}

private fun DrawScope.drawRain(t: Float, p: (Float, Float) -> Offset, w: Float, color: Color, top: FloatArray) {
    val len = 4f
    for ((x, speed, phase) in drops) {
        val floor = landingY(x, top)
        val travel = floor + len + 6f // from above the top edge to the landing point
        val cycle = travel / speed + 0.35f // fall, then a short splash
        val c = ((t / cycle + phase) % 1f) * cycle
        val fallT = travel / speed
        if (c < fallT) {
            val y = -len - 6f + c * speed
            val yEnd = minOf(y + len, floor)
            if (yEnd > y) drawLine(color.copy(alpha = 0.85f), p(x, y), p(x - 0.6f, yEnd), w, StrokeCap.Round)
        } else {
            // Splash: two tiny droplets flying apart and fading.
            val s = (c - fallT) / 0.35f
            val a = (1f - s) * 0.8f
            val dx = 1.2f + s * 2.2f
            val dy = s * 2.2f - s * s * 2.6f
            drawCircle(color.copy(alpha = a), 0.55f * w, p(x - dx, floor - 0.6f - dy))
            drawCircle(color.copy(alpha = a), 0.55f * w, p(x + dx, floor - 0.6f - dy))
        }
    }
}

// ---------------------------------------------------------------- sun + breeze

private fun DrawScope.drawSun(t: Float, p: (Float, Float) -> Offset, w: Float, color: Color) {
    // The sun travels an arc over the car, left to right, every 24 s, then starts again.
    val arcT = (t % 24f) / 24f
    val angle = PI * (1 - arcT) // pi (left) .. 0 (right)
    val cx = 60f + 52f * cos(angle).toFloat()
    val cy = 13f - 10f * sin(angle).toFloat()
    // Fade in at the start and out at the end of the arc so the jump back is not seen.
    val fade = minOf(1f, arcT / 0.08f, (1f - arcT) / 0.08f)
    val r = 3.2f
    val c = color.copy(alpha = fade)
    drawCircle(c, r * size.width / 120f, p(cx, cy), style = androidx.compose.ui.graphics.drawscope.Stroke(w))
    // Eight rays, slowly turning.
    val spin = t * (2 * PI.toFloat() / 10f)
    for (k in 0 until 8) {
        val a = spin + k * (PI.toFloat() / 4f)
        val ca = cos(a)
        val sa = sin(a)
        drawLine(c, p(cx + ca * (r + 1.4f), cy + sa * (r + 1.4f)), p(cx + ca * (r + 3f), cy + sa * (r + 3f)), w, StrokeCap.Round)
    }
    // Airflow: the car drives to the left, so wind streams from ahead of the nose back over the
    // bonnet and cabin to the tail (left to right). Long streaks, three rows at different speeds,
    // fading in and out so they never pop.
    data class Row(val y: Float, val speed: Float, val length: Float, val offset: Float)
    val rows = listOf(Row(4.5f, 30f, 18f, 0f), Row(9f, 24f, 22f, 47f), Row(13.5f, 34f, 15f, 91f))
    val span = 150f // from x = -20 to 130: fully across the card
    for (r in rows) {
        for (d in 0 until 2) {
            val progress = ((t * r.speed + r.offset + d * span / 2f) % span) / span
            val head = -20f + progress * span
            val tail = head - r.length
            val a = sin(PI.toFloat() * progress) * 0.75f
            drawLine(color.copy(alpha = a), p(tail, r.y), p(head, r.y), w * 0.85f, StrokeCap.Round)
        }
    }
}
