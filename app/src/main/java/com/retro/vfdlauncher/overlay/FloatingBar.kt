package com.retro.vfdlauncher.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.retro.vfdlauncher.data.audio.VolumeRepository
import com.retro.vfdlauncher.data.media.MediaRepository
import com.retro.vfdlauncher.ui.home.GlowIcon
import com.retro.vfdlauncher.ui.home.VfdButton
import com.retro.vfdlauncher.ui.theme.Vfd
import com.retro.vfdlauncher.ui.theme.VfdIcons
import com.retro.vfdlauncher.ui.theme.glow

private val RailShape = RoundedCornerShape(16.dp)
private val BorderStrong = Vfd.Teal.copy(alpha = 0.7f)

/**
 * Floating VFD controls over other apps, as a slim vertical rail on the left edge: Google Maps
 * keeps its own buttons along the top, bottom and right, so a column at the far left covers the
 * least. HOME, prev / play / next, volume, and an arrow that folds it down to HOME only.
 * Sizes are design pixels scaled by [scale].
 */
@Composable
fun FloatingBar(scale: Float, media: MediaRepository, volume: VolumeRepository, onHome: () -> Unit) {
    CompositionLocalProvider(LocalDensity provides Density(scale, fontScale = 1f)) {
        var expanded by remember { mutableStateOf(true) }
        val sessions by media.sessions.collectAsState()
        val vol by volume.volume.collectAsState()
        val np = sessions.firstOrNull { it.isPlaying } ?: sessions.firstOrNull()
        val pkg = np?.packageName
        val playing = np?.isPlaying == true
        val radio = np?.isRadio == true

        Column(
            Modifier
                .width(92.dp)
                .clip(RailShape)
                .background(Vfd.Panel.copy(alpha = 0.92f))
                .border(1.dp, Vfd.Teal.copy(alpha = 0.45f), RailShape)
                .padding(vertical = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            RailButton(72.dp, 72.dp, Vfd.Amber, "Home", onHome, fill = Vfd.Amber.copy(alpha = 0.1f)) {
                GlowIcon(VfdIcons.Home, Vfd.Amber, 34.dp, glow = false)
            }
            if (expanded) {
                Divider()
                RailButton(64.dp, 64.dp, BorderStrong, if (radio) "Seek down" else "Previous track", { media.previous(pkg) }) {
                    GlowIcon(VfdIcons.Previous, Vfd.Teal, 30.dp, glow = false)
                }
                RailButton(72.dp, 72.dp, Vfd.Amber, if (playing) "Pause" else "Play", { media.playPause(pkg) }, fill = Vfd.Amber.copy(alpha = 0.1f)) {
                    GlowIcon(if (playing) VfdIcons.Pause else VfdIcons.Play, Vfd.Amber, 34.dp, glow = false)
                }
                RailButton(64.dp, 64.dp, BorderStrong, if (radio) "Seek up" else "Next track", { media.next(pkg) }) {
                    GlowIcon(VfdIcons.Next, Vfd.Teal, 30.dp, glow = false)
                }
                Divider()
                RailButton(64.dp, 64.dp, BorderStrong, "Volume up", volume::up) {
                    GlowIcon(VfdIcons.Plus, Vfd.Teal, 24.dp, glow = false)
                }
                BasicText(
                    if (vol.muted) "--" else vol.segments.toString(),
                    Modifier.width(64.dp),
                    style = glow(Vfd.Amber, 26.sp, blur = 6f).copy(textAlign = TextAlign.Center),
                )
                RailButton(64.dp, 64.dp, BorderStrong, "Volume down", volume::down) {
                    GlowIcon(VfdIcons.Minus, Vfd.Teal, 24.dp, glow = false)
                }
            }
            // Fold arrow: points up when it will fold the rail, down when it will open it.
            RailButton(64.dp, 44.dp, null, if (expanded) "Hide controls" else "Show controls", { expanded = !expanded }) {
                Box(Modifier.rotate(if (expanded) 90f else -90f)) {
                    GlowIcon(VfdIcons.ChevronLeft, Vfd.Teal.copy(alpha = 0.8f), 26.dp, glow = false)
                }
            }
        }
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .size(56.dp, 1.dp)
            .background(Vfd.Teal.copy(alpha = 0.3f)),
    )
}

@Composable
private fun RailButton(
    width: Dp,
    height: Dp,
    border: Color?,
    label: String,
    onClick: () -> Unit,
    fill: Color = Color.Transparent,
    content: @Composable () -> Unit,
) {
    VfdButton(
        Modifier.size(width, height),
        border = border ?: Color.Transparent,
        borderWidth = if (border == null) 0.dp else 2.dp,
        shape = RoundedCornerShape(12.dp),
        fill = fill,
        onClickLabel = label,
        onClick = onClick,
        content = content,
    )
}
