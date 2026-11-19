package com.retro.vfdlauncher.overlay

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
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
import java.util.Locale

private val BarShape = RoundedCornerShape(16.dp)
private val BorderStrong = Vfd.Teal.copy(alpha = 0.7f)

/** The floating VFD control bar from the "app open" artboard, in design pixels scaled by [scale]. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun FloatingBar(scale: Float, media: MediaRepository, volume: VolumeRepository, onHome: () -> Unit) {
    CompositionLocalProvider(LocalDensity provides Density(scale, fontScale = 1f)) {
        var expanded by remember { mutableStateOf(true) }
        val sessions by media.sessions.collectAsState()
        val vol by volume.volume.collectAsState()
        val np = sessions.firstOrNull { it.isPlaying } ?: sessions.firstOrNull()
        val pkg = np?.packageName

        Row(
            Modifier
                .height(92.dp)
                .clip(BarShape)
                .background(Vfd.Panel.copy(alpha = 0.92f))
                .border(1.dp, Vfd.Teal.copy(alpha = 0.45f), BarShape)
                .padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (expanded) 10.dp else 6.dp),
        ) {
            BarButton(72.dp, 72.dp, Vfd.Amber, "Home", onHome, fill = Vfd.Amber.copy(alpha = 0.1f)) {
                GlowIcon(VfdIcons.Home, Vfd.Amber, 34.dp, glow = false)
            }
            if (!expanded) {
                BarButton(48.dp, 64.dp, null, "Show controls", { expanded = true }) {
                    GlowIcon(VfdIcons.ChevronRight, Vfd.Teal.copy(alpha = 0.8f), 26.dp, glow = false)
                }
                return@Row
            }
            val sourceLabel = when {
                np == null -> "NO SOURCE"
                np.tuning != null -> "${np.tuning.band} ${np.tuning.display}"
                else -> np.appLabel.uppercase(Locale.getDefault())
            }
            val text = np?.let { if (it.isRadio) it.stationName else it.infoLine }?.takeIf { it.isNotBlank() } ?: "— — —"
            Column(
                Modifier
                    .width(236.dp)
                    .padding(horizontal = 6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                BasicText(sourceLabel, style = glow(Vfd.Amber, 18.sp, 3.sp, blur = 6f), maxLines = 1)
                BasicText(
                    text,
                    if (np?.isPlaying == true) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier,
                    style = glow(Vfd.Teal, 24.sp, 2.sp, blur = 8f),
                    maxLines = 1,
                )
            }
            BarButton(64.dp, 64.dp, BorderStrong, if (np?.isRadio == true) "Seek down" else "Previous track", { media.previous(pkg) }) {
                GlowIcon(VfdIcons.Previous, Vfd.Teal, 30.dp, glow = false)
            }
            BarButton(76.dp, 72.dp, Vfd.Amber, if (np?.isPlaying == true) "Pause" else "Play", { media.playPause(pkg) }, fill = Vfd.Amber.copy(alpha = 0.1f)) {
                GlowIcon(if (np?.isPlaying == true) VfdIcons.Pause else VfdIcons.Play, Vfd.Amber, 34.dp, glow = false)
            }
            BarButton(64.dp, 64.dp, BorderStrong, if (np?.isRadio == true) "Seek up" else "Next track", { media.next(pkg) }) {
                GlowIcon(VfdIcons.Next, Vfd.Teal, 30.dp, glow = false)
            }
            Box(
                Modifier
                    .padding(horizontal = 4.dp)
                    .size(1.dp, 56.dp)
                    .background(Vfd.Teal.copy(alpha = 0.3f)),
            )
            BarButton(64.dp, 64.dp, BorderStrong, "Volume down", volume::down) {
                GlowIcon(VfdIcons.Minus, Vfd.Teal, 24.dp, glow = false)
            }
            BasicText(
                if (vol.muted) "--" else vol.segments.toString(),
                Modifier.width(40.dp),
                style = glow(Vfd.Amber, 26.sp, blur = 6f).copy(textAlign = TextAlign.Center),
            )
            BarButton(64.dp, 64.dp, BorderStrong, "Volume up", volume::up) {
                GlowIcon(VfdIcons.Plus, Vfd.Teal, 24.dp, glow = false)
            }
            BarButton(48.dp, 64.dp, null, "Hide controls", { expanded = false }) {
                GlowIcon(VfdIcons.ChevronLeft, Vfd.Teal.copy(alpha = 0.8f), 26.dp, glow = false)
            }
        }
    }
}

@Composable
private fun BarButton(
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
