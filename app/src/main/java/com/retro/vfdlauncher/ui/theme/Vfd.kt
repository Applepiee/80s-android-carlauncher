package com.retro.vfdlauncher.ui.theme

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retro.vfdlauncher.R

/** Colours from the VFD Hi-Fi design canvas. */
object Vfd {
    val Teal = Color(0xFF3EF2D0)
    val Amber = Color(0xFFFFB347)
    val Panel = Color(0xFF020403)
    val PanelBorder = Teal.copy(alpha = 0.18f)
    val ButtonBorder = Teal.copy(alpha = 0.55f)
    val GhostDigits = Teal.copy(alpha = 0.06f)
    val GhostSegment = Teal.copy(alpha = 0.08f)
    val GhostText = Teal.copy(alpha = 0.16f)

    val Background = Brush.linearGradient(
        0f to Color(0xFF0B1110),
        0.45f to Color(0xFF050807),
        1f to Color(0xFF030504),
        start = Offset(0f, 0f),
        end = Offset(700f, 1280f), // ~160deg across the 1280x720 design canvas
    )
    val Reflection = Brush.linearGradient(
        0f to Color.White.copy(alpha = 0.05f),
        0.6f to Color.Transparent,
        start = Offset(0f, 0f),
        end = Offset(220f, 1250f),
    )

    val Mono = FontFamily(Font(R.font.share_tech_mono))

    @OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
    val Orbitron = FontFamily(
        Font(R.font.orbitron, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
        Font(R.font.orbitron, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700))),
    )

    val PanelShape = RoundedCornerShape(10.dp)
}

/** Text that glows like a lit VFD segment. Compose supports one shadow, so the blur approximates the canvas' double text-shadow. */
fun glow(
    color: Color,
    size: TextUnit,
    letterSpacing: TextUnit = 0.sp,
    blur: Float = 10f,
    family: FontFamily = Vfd.Mono,
    weight: FontWeight = FontWeight.Normal,
): TextStyle = TextStyle(
    color = color,
    fontSize = size,
    letterSpacing = letterSpacing,
    fontFamily = family,
    fontWeight = weight,
    shadow = if (blur > 0f) Shadow(color = color.copy(alpha = 0.75f), blurRadius = blur) else null,
)

/** Recessed black-glass panel with the 1px teal inset border. */
fun Modifier.vfdPanel(border: Color = Vfd.PanelBorder): Modifier = this
    .clip(Vfd.PanelShape)
    .background(Vfd.Panel)
    .background(
        Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.7f), 0.12f to Color.Transparent),
    )
    .border(1.dp, border, Vfd.PanelShape)
