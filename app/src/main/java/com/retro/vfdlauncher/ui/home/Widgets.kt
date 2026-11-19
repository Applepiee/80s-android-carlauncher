package com.retro.vfdlauncher.ui.home

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Outlined VFD button: a faint teal wash while pressed, optional long-press.
 * No ripple; a ripple looks wrong on a fluorescent display.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun VfdButton(
    modifier: Modifier = Modifier,
    border: Color,
    borderWidth: Dp = 2.dp,
    shape: Shape = RoundedCornerShape(10.dp),
    fill: Color = Color.Transparent,
    pressedFill: Color = border.copy(alpha = 0.10f),
    onLongClick: (() -> Unit)? = null,
    onClickLabel: String? = null,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val pressed by interaction.collectIsPressedAsState()
    Box(
        modifier = modifier
            .clip(shape)
            .background(if (pressed) pressedFill else fill)
            .border(borderWidth, border, shape)
            .combinedClickable(
                interactionSource = interaction,
                indication = null,
                onClickLabel = onClickLabel,
                onLongClick = onLongClick,
                onClick = onClick,
            ),
        contentAlignment = Alignment.Center,
    ) { content() }
}

/** A line icon tinted [color], with a soft blurred copy underneath as the phosphor glow. */
@Composable
fun GlowIcon(icon: ImageVector, color: Color, size: Dp, glow: Boolean = true) {
    val painter = rememberVectorPainter(icon)
    Box(Modifier.size(size), contentAlignment = Alignment.Center) {
        if (glow) {
            Image(
                painter = painter,
                contentDescription = null,
                colorFilter = ColorFilter.tint(color.copy(alpha = 0.8f)),
                modifier = Modifier.size(size).blur(4.dp, BlurredEdgeTreatment.Unbounded),
            )
        }
        Image(
            painter = painter,
            contentDescription = null,
            colorFilter = ColorFilter.tint(color),
            modifier = Modifier.size(size),
        )
    }
}
