package com.retro.vfdlauncher.ui.home

import android.os.SystemClock
import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.retro.vfdlauncher.data.apps.Slot
import com.retro.vfdlauncher.data.audio.Volume
import com.retro.vfdlauncher.data.media.Band
import com.retro.vfdlauncher.data.media.NowPlaying
import com.retro.vfdlauncher.data.media.Tuning
import com.retro.vfdlauncher.data.weather.Weather
import com.retro.vfdlauncher.ui.theme.Vfd
import com.retro.vfdlauncher.ui.theme.VfdIcons
import com.retro.vfdlauncher.ui.theme.glow
import com.retro.vfdlauncher.ui.theme.vfdPanel
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Everything the home screen shows. */
data class HomeState(
    val nowPlaying: NowPlaying?,
    val hasMediaAccess: Boolean,
    val weather: Weather?,
    val volume: Volume,
    val presets: List<Tuning?>,
    val slotLabels: Map<Slot, String?>,
)

/** Everything the home screen can do. */
class HomeActions(
    val onSlot: (Slot) -> Unit,
    val onSlotLongPress: (Slot) -> Unit,
    val onPreset: (Int) -> Unit,
    val onPresetLongPress: (Int) -> Unit,
    val onPlayPause: () -> Unit,
    val onVolumeUp: () -> Unit,
    val onVolumeDown: () -> Unit,
    val onGrantMediaAccess: () -> Unit,
    val onInspect: () -> Unit,
)

const val DESIGN_W = 1280f
const val DESIGN_H = 720f

/**
 * The canvas is a fixed 1280x720 layout. Instead of reflowing it, scale it: 1 dp here equals
 * one design pixel times the fit factor, so the screen looks the same on any density or size.
 */
@Composable
fun ScaledDesignCanvas(content: @Composable () -> Unit) {
    BoxWithConstraints(
        Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center,
    ) {
        val scale = minOf(constraints.maxWidth / DESIGN_W, constraints.maxHeight / DESIGN_H)
        CompositionLocalProvider(LocalDensity provides Density(scale, fontScale = 1f)) {
            Box(Modifier.requiredSize(DESIGN_W.dp, DESIGN_H.dp)) { content() }
        }
    }
}

@Composable
fun HomeScreen(state: HomeState, actions: HomeActions) {
    ScaledDesignCanvas {
        Box(
            Modifier
                .fillMaxSize()
                .background(Vfd.Background),
        ) {
            // Faint diagonal reflection on the glass.
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(Vfd.Reflection),
            )
            StatusRow(state, Modifier.offset(40.dp, 28.dp).width(1200.dp).height(64.dp))
            TunerPanel(state, actions, Modifier.offset(40.dp, 108.dp).size(780.dp, 300.dp))
            Box(
                Modifier
                    .offset(40.dp, 428.dp)
                    .size(780.dp, 156.dp)
                    .vfdPanel()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
            ) {
                SpectrumAnalyzer(state.nowPlaying?.isPlaying == true, Modifier.fillMaxSize())
            }
            WeatherPanel(state.weather, Modifier.offset(860.dp, 108.dp).size(380.dp, 150.dp))
            AppButtons(state, actions, Modifier.offset(860.dp, 274.dp).width(380.dp))
            PresetRow(state, actions, Modifier.offset(40.dp, 604.dp).size(780.dp, 84.dp))
            VolumePanel(state, actions, Modifier.offset(860.dp, 604.dp).size(380.dp, 84.dp))
        }
    }
}

// ---------------------------------------------------------------- status row + clock

@Composable
private fun StatusRow(state: HomeState, modifier: Modifier) {
    val np = state.nowPlaying
    val band = np?.tuning?.band
    val radio = np?.isRadio == true
    Row(modifier, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            Indicator("FM", radio && band != Band.AM)
            Indicator("STEREO", np?.isPlaying == true)
            Indicator("RDS", radio && !np?.title.isNullOrBlank())
            Indicator("AM", band == Band.AM)
            Indicator("TA", false)
            Indicator("LOUD", state.volume.segments >= 16, color = Vfd.Amber)
        }
        Clock()
    }
}

@Composable
private fun Indicator(text: String, lit: Boolean, color: Color = Vfd.Teal) {
    BasicText(
        text,
        style = if (lit) glow(color, 18.sp, 3.sp, blur = 8f) else glow(Vfd.GhostText, 18.sp, 3.sp, blur = 0f),
    )
}

@Composable
private fun Clock() {
    val context = LocalContext.current
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = System.currentTimeMillis()
            delay(60_000L - now % 60_000L + 50L) // wake right after the minute flips
        }
    }
    val is24 = DateFormat.is24HourFormat(context)
    val date = remember(now / 60_000L) {
        SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(now)).replace(".", "")
            .uppercase(Locale.getDefault())
    }
    val time = remember(now / 60_000L, is24) {
        SimpleDateFormat(if (is24) "HH:mm" else "h:mm", Locale.getDefault()).format(Date(now))
    }
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        BasicText(date, Modifier.padding(bottom = 10.dp), style = glow(Vfd.Amber, 20.sp, 4.sp, blur = 8f))
        BasicText(time, style = glow(Vfd.Teal, 54.sp, blur = 18f, family = Vfd.Orbitron, weight = FontWeight.Bold))
    }
}

// ---------------------------------------------------------------- tuner

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TunerPanel(state: HomeState, actions: HomeActions, modifier: Modifier) {
    val np = state.nowPlaying
    val tuning = np?.tuning
    val presetNo = tuning?.let { t -> state.presets.indexOfFirst { it?.matches(t) == true } }?.takeIf { it >= 0 }

    // Music sources: show elapsed time in the big digits, ticking once a second.
    var nowMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(np?.isPlaying, tuning) {
        while (np?.isPlaying == true && tuning == null) {
            nowMs = SystemClock.elapsedRealtime()
            delay(1_000L - (nowMs % 1_000L))
        }
    }

    val header: String
    val digits: String
    val ghost: String
    val unit: String
    val info: String
    when {
        !state.hasMediaAccess -> {
            header = "TUNER"; digits = "--.--"; ghost = "888.88"; unit = "MHz"
            info = "TAP TO ALLOW MEDIA ACCESS"
        }
        np == null -> {
            header = "TUNER"; digits = "--.--"; ghost = "888.88"; unit = "MHz"; info = "NO SOURCE"
        }
        tuning != null -> {
            header = "TUNER · " + (presetNo?.let { "PRESET ${it + 1}" } ?: np.appLabel.uppercase())
            digits = tuning.display
            ghost = if (tuning.band == Band.FM) "888.88" else "8888"
            unit = tuning.unit
            info = np.infoLine.ifEmpty { np.appLabel.uppercase() }
        }
        else -> {
            header = (if (np.isRadio) "TUNER · " else "MEDIA · ") + np.appLabel.uppercase()
            val pos = if (np.isPlaying) {
                np.positionMs + ((nowMs - np.positionUpdatedAt) * np.speed).toLong()
            } else np.positionMs
            val totalSec = (pos.coerceAtLeast(0L) / 1000L).coerceAtMost(99 * 60 + 59)
            digits = String.format(Locale.US, "%02d:%02d", totalSec / 60, totalSec % 60)
            ghost = "88:88"
            unit = "MIN"
            info = np.infoLine.ifEmpty { np.appLabel.uppercase() }
        }
    }

    Column(
        modifier
            .vfdPanel()
            .combinedClickable(
                onClick = { if (!state.hasMediaAccess) actions.onGrantMediaAccess() },
                onLongClick = actions.onInspect,
                onLongClickLabel = "Show media session details",
            )
            .padding(horizontal = 28.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BasicText(
                header, Modifier.weight(1f),
                style = glow(Vfd.Teal, 16.sp, 4.sp, blur = 6f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            val bars = if (np?.isPlaying == true) 4 else if (np != null) 2 else 0
            BasicText(
                "SIGNAL " + "▮".repeat(bars) + "▯".repeat(5 - bars),
                style = glow(Vfd.Amber, 16.sp, 4.sp, blur = 6f),
            )
        }
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            val bigStyle = glow(Vfd.Teal, 190.sp, (-4).sp, blur = 30f).copy(
                textAlign = TextAlign.End,
                lineHeight = 0.8.em,
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            )
            Box(Modifier.widthIn(min = 560.dp), contentAlignment = Alignment.BottomEnd) {
                BasicText(ghost, style = bigStyle.copy(color = Vfd.GhostDigits, shadow = null))
                BasicText(digits, style = bigStyle, maxLines = 1)
            }
            BasicText(unit, Modifier.padding(bottom = 6.dp), style = glow(Vfd.Amber, 40.sp, blur = 8f))
        }
        BasicText(
            info,
            style = glow(Vfd.Amber, 36.sp, 6.sp, blur = 10f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

// ---------------------------------------------------------------- weather

@Composable
private fun WeatherPanel(weather: Weather?, modifier: Modifier) {
    Row(
        modifier
            .vfdPanel()
            .padding(horizontal = 22.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        GlowIcon(VfdIcons.weather(weather?.icon ?: com.retro.vfdlauncher.data.weather.WeatherIcon.CLOUD), Vfd.Amber, 70.dp)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                BasicText(
                    weather?.let { "${it.temp}°" } ?: "--°",
                    style = glow(Vfd.Teal, 62.sp, blur = 12f).copy(lineHeight = 1.em),
                )
                BasicText(
                    when {
                        weather == null -> "NO DATA"
                        weather.stale -> "OFFLINE"
                        else -> weather.condition
                    },
                    Modifier.padding(bottom = 8.dp),
                    style = glow(Vfd.Amber, 22.sp, 3.sp, blur = 6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            BasicText(
                weather?.forecast?.joinToString(" · ") { "${it.day} ${it.maxTemp}°" } ?: "WAITING FOR NETWORK",
                style = glow(Vfd.Teal, 17.sp, 2.sp, blur = 6f),
                maxLines = 1,
            )
        }
    }
}

// ---------------------------------------------------------------- app buttons

@Composable
private fun AppButtons(state: HomeState, actions: HomeActions, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        VfdButton(
            Modifier
                .fillMaxWidth()
                .height(110.dp),
            border = Vfd.Amber,
            fill = Vfd.Amber.copy(alpha = 0.06f),
            pressedFill = Vfd.Amber.copy(alpha = 0.16f),
            onLongClick = { actions.onSlotLongPress(Slot.ANDROID_AUTO) },
            onClick = { actions.onSlot(Slot.ANDROID_AUTO) },
        ) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 18.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                GlowIcon(VfdIcons.AndroidAuto, Vfd.Amber, 44.dp)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BasicText(
                        "ANDROID AUTO",
                        style = glow(Vfd.Amber, 24.sp, 2.sp, blur = 8f, family = Vfd.Orbitron, weight = FontWeight.Bold),
                    )
                    BasicText(
                        state.slotLabels[Slot.ANDROID_AUTO]?.uppercase() ?: "HOLD TO ASSIGN APP",
                        style = glow(Vfd.Amber, 15.sp, 2.sp, blur = 0f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SlotButton(Slot.RADIO, VfdIcons.Radio, actions, Modifier.weight(1f))
            SlotButton(Slot.MUSIC, VfdIcons.Music, actions, Modifier.weight(1f))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            SlotButton(Slot.CAMERA, VfdIcons.Camera, actions, Modifier.weight(1f))
            SlotButton(Slot.SETTINGS, VfdIcons.Settings, actions, Modifier.weight(1f))
        }
    }
}

@Composable
private fun SlotButton(
    slot: Slot,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    actions: HomeActions,
    modifier: Modifier,
) {
    VfdButton(
        modifier.height(92.dp),
        border = Vfd.ButtonBorder,
        pressedFill = Vfd.Teal.copy(alpha = 0.08f),
        onLongClick = { actions.onSlotLongPress(slot) },
        onClick = { actions.onSlot(slot) },
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GlowIcon(icon, Vfd.Teal, 28.dp, glow = false)
            BasicText(slot.label, style = glow(Vfd.Teal, 20.sp, 4.sp, blur = 6f))
        }
    }
}

// ---------------------------------------------------------------- presets

@Composable
private fun PresetRow(state: HomeState, actions: HomeActions, modifier: Modifier) {
    val current = state.nowPlaying?.tuning
    val queue = state.nowPlaying?.queue.orEmpty()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        state.presets.forEachIndexed { i, stored ->
            val shown = stored ?: queue.getOrNull(i)?.tuning
            val active = shown != null && shown.matches(current)
            val label = shown?.let {
                if (it.band == Band.FM) String.format(Locale.US, "%.1f", it.value) else it.value.toInt().toString()
            } ?: queue.getOrNull(i)?.title?.take(6)?.uppercase() ?: "--.-"
            VfdButton(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                border = if (active) Vfd.Teal else Vfd.Teal.copy(alpha = 0.3f),
                fill = if (active) Vfd.Teal.copy(alpha = 0.12f) else Color.Transparent,
                onLongClick = { actions.onPresetLongPress(i) },
                onClickLabel = "Preset ${i + 1}",
                onClick = { actions.onPreset(i) },
            ) {
                val fg = if (active) Vfd.Teal else Vfd.Teal.copy(alpha = 0.6f)
                Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    BasicText("P${i + 1}", style = glow(fg, 13.sp, 3.sp, blur = 0f))
                    BasicText(label, style = glow(fg, 28.sp, blur = if (active) 10f else 0f), maxLines = 1)
                }
            }
        }
    }
}

// ---------------------------------------------------------------- volume

@Composable
private fun VolumePanel(state: HomeState, actions: HomeActions, modifier: Modifier) {
    val vol = state.volume
    val playing = state.nowPlaying?.isPlaying == true
    Row(
        modifier
            .vfdPanel()
            .padding(horizontal = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        RoundButton(Vfd.ButtonBorder, "Volume down", actions.onVolumeDown) {
            GlowIcon(VfdIcons.Minus, Vfd.Teal, 20.dp, glow = false)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText("VOL", style = glow(Vfd.Teal, 15.sp, 2.sp, blur = 4f))
                BasicText("${vol.dbLabel} dB", style = glow(Vfd.Amber, 15.sp, 2.sp, blur = 6f))
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(14.dp),
                horizontalArrangement = Arrangement.spacedBy(3.dp),
            ) {
                repeat(Volume.SEGMENTS) { i ->
                    val on = i < vol.segments
                    val c = if (i >= 16) Vfd.Amber else Vfd.Teal
                    Box(
                        Modifier
                            .weight(1f)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (on) c else Vfd.GhostSegment),
                    )
                }
            }
        }
        RoundButton(Vfd.ButtonBorder, "Volume up", actions.onVolumeUp) {
            GlowIcon(VfdIcons.Plus, Vfd.Teal, 20.dp, glow = false)
        }
        RoundButton(Vfd.Amber, "Play or pause", actions.onPlayPause) {
            GlowIcon(if (playing) VfdIcons.Pause else VfdIcons.Play, Vfd.Amber, 20.dp, glow = false)
        }
    }
}

@Composable
private fun RoundButton(border: Color, label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    VfdButton(
        Modifier.size(52.dp),
        border = border,
        shape = CircleShape,
        onClickLabel = label,
        onClick = onClick,
        content = content,
    )
}
