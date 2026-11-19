package com.retro.vfdlauncher.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp
import com.retro.vfdlauncher.data.weather.WeatherIcon

/**
 * Line icons from the design canvas (24x24 viewBox, stroked), rebuilt as ImageVectors.
 * Tint them with ColorFilter.tint; the stroke colour here is just white.
 */
object VfdIcons {

    private fun rect(x: Float, y: Float, w: Float, h: Float, r: Float) =
        "M${x + r},${y}h${w - 2 * r}a$r,$r 0 0 1 $r,${r}v${h - 2 * r}a$r,$r 0 0 1 -$r,${r}" +
            "h${-(w - 2 * r)}a$r,$r 0 0 1 -$r,-${r}v${-(h - 2 * r)}a$r,$r 0 0 1 $r,-${r}z"

    private fun circle(cx: Float, cy: Float, r: Float) =
        "M${cx - r},${cy}a$r,$r 0 1,0 ${2 * r},0a$r,$r 0 1,0 ${-2 * r},0z"

    private fun icon(name: String, strokeWidth: Float, vararg paths: String, filled: Boolean = false): ImageVector =
        ImageVector.Builder(name, 24.dp, 24.dp, 24f, 24f).apply {
            paths.forEach { d ->
                addPath(
                    pathData = addPathNodes(d),
                    fill = if (filled) SolidColor(Color.White) else null,
                    stroke = if (filled) null else SolidColor(Color.White),
                    strokeLineWidth = strokeWidth,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val AndroidAuto = icon(
        "aa", 1.6f,
        "M5 15l1.6-4.6A2 2 0 0 1 8.5 9h7a2 2 0 0 1 1.9 1.4L19 15",
        rect(3f, 15f, 18f, 5f, 1.5f),
        "M7 20v1.5M17 20v1.5",
        "M9 5.5a4.5 4.5 0 0 1 6 0M10.7 7.2a2 2 0 0 1 2.6 0",
    )
    val Radio = icon("radio", 1.6f, rect(3f, 8f, 18f, 12f, 2f), "m7 8 10-5", circle(8.5f, 14f, 2.5f), "M14 12h4M14 16h4")
    val Music = icon("music", 1.6f, "M9 18V5l12-2v13", circle(6f, 18f, 3f), circle(18f, 16f, 3f))
    val Camera = icon("camera", 1.6f, rect(3f, 7f, 18f, 13f, 2f), "M8 7l2-3h4l2 3", circle(12f, 13.5f, 3.5f))
    val Settings = icon(
        "settings", 1.6f,
        circle(12f, 12f, 3f),
        "M12 2v3M12 19v3M2 12h3M19 12h3M4.9 4.9 7 7M17 17l2.1 2.1M4.9 19.1 7 17M17 7l2.1-2.1",
    )
    val Minus = icon("minus", 2.4f, "M5 12h14")
    val Plus = icon("plus", 2.4f, "M5 12h14M12 5v14")
    val Pause = icon("pause", 0f, rect(6f, 4f, 4f, 16f, 1f), rect(14f, 4f, 4f, 16f, 1f), filled = true)
    val Play = icon("play", 0f, "M7 4v16l13-8z", filled = true)

    private const val CLOUD = "M7 19h10a4 4 0 0 0 .6-8 5 5 0 0 0-9.7 1.6A3.2 3.2 0 0 0 7 19Z"
    private const val CLOUD_HIGH = "M7 16h10a4 4 0 0 0 .6-8 5 5 0 0 0-9.7 1.6A3.2 3.2 0 0 0 7 16Z"

    private val Sun = icon(
        "sun", 1.4f, circle(12f, 12f, 4f),
        "M12 2v2M12 20v2M2 12h2M20 12h2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4",
    )
    private val Partly = icon(
        "partly", 1.4f,
        "M12 2v2M5.6 4.6l1.4 1.4M2.5 11h2M18.4 4.6 17 6",
        "M8 10.5a4 4 0 0 1 7.7-1.5",
        "M7 21h10a4 4 0 0 0 .6-8 5 5 0 0 0-9.7 1.6A3.2 3.2 0 0 0 7 21Z",
    )
    private val Cloud = icon("cloud", 1.4f, CLOUD)
    private val Rain = icon("rain", 1.4f, CLOUD_HIGH, "M8.5 19l-1 2.5M12.5 19l-1 2.5M16.5 19l-1 2.5")
    private val Snow = icon("snow", 1.4f, CLOUD_HIGH, "M8 19.5h.01M12 21h.01M16 19.5h.01M10 22.5h.01M14 22.5h.01")
    private val Fog = icon("fog", 1.4f, CLOUD_HIGH, "M4 19h16M6 22h12")
    private val Storm = icon("storm", 1.4f, CLOUD_HIGH, "M13 16l-2.5 3.5h3L11 23")

    fun weather(icon: WeatherIcon): ImageVector = when (icon) {
        WeatherIcon.SUN -> Sun
        WeatherIcon.PARTLY -> Partly
        WeatherIcon.CLOUD -> Cloud
        WeatherIcon.FOG -> Fog
        WeatherIcon.RAIN -> Rain
        WeatherIcon.SNOW -> Snow
        WeatherIcon.STORM -> Storm
    }
}
