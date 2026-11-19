package com.retro.vfdlauncher.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.addPathNodes
import androidx.compose.ui.unit.dp

/**
 * Toyota MR2 Spyder side view from the TOP-DOWN meter on the canvas (120x40 viewBox), drawn in
 * amber line-art, roof up or down. The rain, sun and breeze around it are animated separately
 * ([com.retro.vfdlauncher.ui.home.RoofWeatherFx]).
 */
object Mr2Icon {

    private class Line(val d: String, val width: Float, val color: Color = Vfd.Amber, val alpha: Float = 1f)

    private val body = listOf(
        Line(
            "M2.5 34.5 3.4 31V26.5C3.6 24.8 5 23.2 7 22.4C9.5 21.2 12 19.6 15 18.7C20 17.5 27 16.6 34 15.7L69 15.1" +
                "C70 14.6 70.6 13.2 71.4 12.6L74 12.4C82 12.4 90 12.4 95 13C101 13.6 106 14.2 109.5 15L111 15.8" +
                "C113.5 17.2 115.8 19.6 116.6 22.2L116.4 31 114.5 33.5H107.3A11.2 11.2 0 1 0 85.7 34H37.3" +
                "A11.2 11.2 0 1 0 15.7 34.5Z",
            1.6f,
        ),
        Line("M19.5 37A9.2 9.2 0 1 1 33.5 37M89.5 37A9.2 9.2 0 1 1 103.5 37", 2f),
        Line("M34 15.7 55 5.5 56.6 6.6 39 15.6", 1.5f),
        Line("M46.8 14.6 48.2 11.6 51.4 11.6 51.2 14.4", 1.1f),
        Line(
            "M38.5 16.2V33M75 16C74 22 71.5 28.5 68.5 33M37.3 32.4H85.7M36 24.4C55 24 72 23.4 86 22.8" +
                "M5.5 23.2C9 21.4 12.5 20 16.5 19.4M3.4 29.8H14.5M108.2 17.2 114.6 20.4",
            1f, alpha = 0.65f,
        ),
        Line("M77.5 17.6 83.4 17.2 82 23 76.6 26.4Z", 1.2f),
    )

    private val roofUp = listOf(
        Line("M55.6 5.7C58 4.6 61 4.4 66 4.4H74C77.5 4.8 80.5 6 83 7.6C86 9.5 89 11.6 92 12.5", 1.9f),
        Line("M50 15C52 11 54.5 8.6 58 8.1L68 8.2C71 8.4 72.6 9.8 73 11.5L73.2 15", 1.1f),
    )

    private val roofDown = listOf(
        Line("M70.6 14.6C70.6 10.6 71.4 9 72.5 9C73.6 9 74 10.4 74 12.4", 1.5f), // folded roof
    )

    private fun build(name: String, lines: List<Line>): ImageVector =
        ImageVector.Builder(name, 120.dp, 40.dp, 120f, 40f).apply {
            lines.forEach { l ->
                addPath(
                    pathData = addPathNodes(l.d),
                    stroke = SolidColor(l.color),
                    strokeAlpha = l.alpha,
                    strokeLineWidth = l.width,
                    strokeLineCap = StrokeCap.Round,
                    strokeLineJoin = StrokeJoin.Round,
                )
            }
        }.build()

    val RoofUp: ImageVector = build("mr2-roof-up", body + roofUp)
    val RoofDown: ImageVector = build("mr2-roof-down", body + roofDown)
}
