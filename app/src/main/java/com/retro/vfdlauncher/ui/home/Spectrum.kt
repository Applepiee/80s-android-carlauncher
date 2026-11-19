package com.retro.vfdlauncher.ui.home

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.unit.dp
import com.retro.vfdlauncher.ui.theme.Vfd
import kotlin.math.floor

private const val BARS = 28

/** Per-bar timing, same formulas as the canvas so the motion matches. */
private val durations = FloatArray(BARS) { i -> 0.42f + ((i * 37) % 23) / 40f }
private val delays = FloatArray(BARS) { i -> -((i * 53) % 17) / 20f }

/** CSS keyframes vfdBar: 0% .15, 35% .9, 60% .4, 100% .75, ease-in-out per segment, alternate. */
private fun keyframe(p: Float): Float {
    fun ease(t: Float) = t * t * (3 - 2 * t)
    return when {
        p < 0.35f -> lerp(0.15f, 0.9f, ease(p / 0.35f))
        p < 0.60f -> lerp(0.9f, 0.4f, ease((p - 0.35f) / 0.25f))
        else -> lerp(0.4f, 0.75f, ease((p - 0.60f) / 0.40f))
    }
}

private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

private fun level(bar: Int, time: Float): Float {
    val phase = (time - delays[bar]) / durations[bar]
    val cycle = floor(phase)
    var frac = phase - cycle
    if (cycle.toInt() % 2 != 0) frac = 1f - frac // animation-direction: alternate
    return keyframe(frac)
}

/**
 * 28-bar segmented analyser. It is an animation, not real audio: it runs while [playing]
 * and freezes in place when paused, like animation-play-state on the canvas.
 * Throttled to ~30 fps; only the draw phase re-runs per frame.
 */
@Composable
fun SpectrumAnalyzer(playing: Boolean, modifier: Modifier = Modifier) {
    val time = remember { mutableFloatStateOf(0f) }
    LaunchedEffect(playing) {
        if (!playing) return@LaunchedEffect
        var last = withFrameNanos { it }
        var acc = 0L
        while (true) {
            val now = withFrameNanos { it }
            acc += now - last
            last = now
            if (acc >= 33_000_000L) {
                time.floatValue += acc / 1e9f
                acc = 0L
            }
        }
    }
    Canvas(modifier) {
        val t = time.floatValue
        val gap = 6.dp.toPx()
        val barW = (size.width - gap * (BARS - 1)) / BARS
        val seg = 6.dp.toPx()
        val period = 9.dp.toPx()
        val segments = floor((size.height + (period - seg)) / period).toInt()
        val glowPad = 3.dp.toPx()
        for (i in 0 until BARS) {
            val x = i * (barW + gap)
            val color = if (i % 9 == 4) Vfd.Amber else Vfd.Teal
            val lit = (level(i, t) * segments + 0.5f).toInt().coerceIn(0, segments)
            // Phosphor glow behind the lit run.
            if (lit > 0) {
                val top = size.height - lit * period + (period - seg)
                drawRoundRect(
                    color = color.copy(alpha = 0.16f),
                    topLeft = Offset(x - glowPad, top - glowPad),
                    size = Size(barW + glowPad * 2, size.height - top + glowPad),
                    cornerRadius = CornerRadius(glowPad * 2),
                )
            }
            for (s in 0 until segments) {
                val y = size.height - seg - s * period
                drawRect(
                    color = if (s < lit) color else Vfd.GhostSegment,
                    topLeft = Offset(x, y),
                    size = Size(barW, seg),
                )
            }
        }
    }
}
