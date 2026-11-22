package com.retro.vfdlauncher.overlay

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
 * Floating VFD controls over other apps, as a slim vertical rail: grip (drag to move), HOME,
 * BACK, prev / play / next, volume, and an arrow that folds it down to grip + HOME + BACK.
 * The rail snaps to the left or right edge when let go. Sizes are design pixels scaled by [scale].
 */
@Composable
fun FloatingBar(
    scale: Float,
    media: MediaRepository,
    volume: VolumeRepository,
    onHome: () -> Unit,
    onBack: () -> Unit,
    /** Raw finger movement in screen pixels while the grip is dragged. */
    onDrag: (dx: Float, dy: Float) -> Unit,
    onDragEnd: () -> Unit,
    onNight: () -> Unit = {},
) {
    CompositionLocalProvider(LocalDensity provides Density(scale, fontScale = 1f)) {
        var expanded by remember { mutableStateOf(true) }
        val sessions by media.sessions.collectAsState()
        val vol by volume.volume.collectAsState()
        val np = sessions.firstOrNull { it.isPlaying } ?: sessions.firstOrNull()
        val pkg = np?.packageName
        val playing = np?.isPlaying == true
        val radio = np?.isRadio == true
        val night by com.retro.vfdlauncher.data.night.NightMode.state.collectAsState()

        Column(
            Modifier
                .width(84.dp)
                .clip(RailShape)
                .background(Vfd.Panel.copy(alpha = 0.92f))
                .border(1.dp, Vfd.Teal.copy(alpha = 0.45f), RailShape)
                .padding(bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Grip(onDrag, onDragEnd)
            RailButton(64.dp, 64.dp, Vfd.Amber, "Home", onHome, fill = Vfd.Amber.copy(alpha = 0.1f)) {
                GlowIcon(VfdIcons.Home, Vfd.Amber, 32.dp, glow = false)
            }
            RailButton(64.dp, 56.dp, BorderStrong, "Back", onBack) {
                GlowIcon(VfdIcons.ChevronLeft, Vfd.Teal, 30.dp, glow = false)
            }
            if (expanded) {
                Divider()
                RailButton(56.dp, 56.dp, BorderStrong, if (radio) "Seek down" else "Previous track", { media.previous(pkg) }) {
                    GlowIcon(VfdIcons.Previous, Vfd.Teal, 28.dp, glow = false)
                }
                RailButton(64.dp, 64.dp, Vfd.Amber, if (playing) "Pause" else "Play", { media.playPause(pkg) }, fill = Vfd.Amber.copy(alpha = 0.1f)) {
                    GlowIcon(if (playing) VfdIcons.Pause else VfdIcons.Play, Vfd.Amber, 32.dp, glow = false)
                }
                RailButton(56.dp, 56.dp, BorderStrong, if (radio) "Seek up" else "Next track", { media.next(pkg) }) {
                    GlowIcon(VfdIcons.Next, Vfd.Teal, 28.dp, glow = false)
                }
                Divider()
                RailButton(56.dp, 56.dp, BorderStrong, "Volume up", volume::up) {
                    GlowIcon(VfdIcons.Plus, Vfd.Teal, 22.dp, glow = false)
                }
                BasicText(
                    if (vol.muted) "--" else vol.segments.toString(),
                    Modifier.width(56.dp),
                    style = glow(Vfd.Amber, 24.sp, blur = 6f).copy(textAlign = TextAlign.Center),
                )
                RailButton(56.dp, 56.dp, BorderStrong, "Volume down", volume::down) {
                    GlowIcon(VfdIcons.Minus, Vfd.Teal, 22.dp, glow = false)
                }
            }
            // Night mode on/off (lit amber at night).
            RailButton(
                56.dp, 44.dp, if (night.isNight) Vfd.Amber else null,
                if (night.isNight) "Night mode off" else "Night mode on", onNight,
                fill = if (night.isNight) Vfd.Amber.copy(alpha = 0.12f) else Color.Transparent,
            ) {
                GlowIcon(VfdIcons.Moon, if (night.isNight) Vfd.Amber else Vfd.Teal.copy(alpha = 0.8f), 22.dp, glow = false)
            }
            // Fold arrow: points up when it will fold the rail, down when it will open it.
            RailButton(56.dp, 36.dp, null, if (expanded) "Hide controls" else "Show controls", { expanded = !expanded }) {
                Box(Modifier.rotate(if (expanded) 90f else -90f)) {
                    GlowIcon(VfdIcons.ChevronLeft, Vfd.Teal.copy(alpha = 0.8f), 24.dp, glow = false)
                }
            }
        }
    }
}

/** Drag handle: three short lines; drag it to move the rail, let go to snap to an edge. */
@Composable
private fun Grip(onDrag: (Float, Float) -> Unit, onDragEnd: () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .height(30.dp)
            .semantics { contentDescription = "Drag to move the controls" }
            .pointerInput(Unit) {
                detectDragGestures(
                    onDragEnd = onDragEnd,
                    onDragCancel = onDragEnd,
                ) { change, amount ->
                    change.consume()
                    onDrag(amount.x, amount.y)
                }
            }
            .padding(top = 9.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        repeat(3) {
            Box(
                Modifier
                    .size(32.dp, 2.dp)
                    .background(Vfd.Teal.copy(alpha = 0.55f), RoundedCornerShape(1.dp)),
            )
        }
    }
}

@Composable
private fun Divider() {
    Box(
        Modifier
            .size(52.dp, 1.dp)
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
