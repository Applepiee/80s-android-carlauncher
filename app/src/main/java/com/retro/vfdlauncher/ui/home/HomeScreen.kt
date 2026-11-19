package com.retro.vfdlauncher.ui.home

import android.os.SystemClock
import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
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
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.LineHeightStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.retro.vfdlauncher.data.apps.Slot
import com.retro.vfdlauncher.data.audio.Volume
import com.retro.vfdlauncher.data.media.Band
import com.retro.vfdlauncher.data.media.NowPlaying
import com.retro.vfdlauncher.data.media.Preset
import com.retro.vfdlauncher.data.nav.NextTurn
import com.retro.vfdlauncher.data.weather.Weather
import com.retro.vfdlauncher.data.weather.WeatherIcon
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
    val source: Source,
    val radio: NowPlaying?,
    val music: NowPlaying?,
    val hasMediaAccess: Boolean,
    val weather: Weather?,
    val nextTurn: NextTurn?,
    val volume: Volume,
    val presets: List<Preset?>,
    val toggles: MusicToggles,
)

/** Everything the home screen can do. */
class HomeActions(
    val onSource: (Source) -> Unit,
    val onSourceLongPress: (Source) -> Unit,
    val onSlot: (Slot) -> Unit,
    val onSlotLongPress: (Slot) -> Unit,
    val onApps: () -> Unit,
    val onPreset: (Int) -> Unit,
    val onPresetLongPress: (Int) -> Unit,
    val onSeek: (up: Boolean) -> Unit,
    val onTune: (up: Boolean) -> Unit,
    val onMute: () -> Unit,
    val onPrevious: () -> Unit,
    val onNext: () -> Unit,
    val onPlayPause: () -> Unit,
    val onShuffle: () -> Unit,
    val onRepeat: () -> Unit,
    val onLike: () -> Unit,
    val onVolumeUp: () -> Unit,
    val onVolumeDown: () -> Unit,
    val onGrantMediaAccess: () -> Unit,
    val onInspect: () -> Unit,
)

const val DESIGN_W = 1280f
const val DESIGN_H = 720f

private val BorderStrong = Vfd.Teal.copy(alpha = 0.7f)
private val PanelBorder22 = Vfd.Teal.copy(alpha = 0.22f)

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
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .background(Vfd.Reflection),
            )
            TopBar(state, actions, Modifier.offset(40.dp, 24.dp).size(1200.dp, 68.dp))
            MainDisplay(state, actions, Modifier.offset(40.dp, 108.dp).size(780.dp, 300.dp))
            if (state.source == Source.RADIO) {
                PresetGrid(state, actions, Modifier.offset(40.dp, 428.dp).size(780.dp, 156.dp))
                RadioControls(state, actions, Modifier.offset(40.dp, 604.dp).size(780.dp, 84.dp))
            } else {
                MusicTransport(state, actions, Modifier.offset(40.dp, 428.dp).size(780.dp, 156.dp))
                MusicToggleRow(state, actions, Modifier.offset(40.dp, 604.dp).size(780.dp, 84.dp))
            }
            val turn = state.nextTurn
            if (turn != null) {
                NextTurnPanel(turn, { actions.onSlot(Slot.MAPS) }, Modifier.offset(860.dp, 108.dp).size(380.dp, 96.dp))
            } else {
                WeatherPanel(state.weather, Modifier.offset(860.dp, 108.dp).size(380.dp, 96.dp))
            }
            AppGrid(actions, Modifier.offset(860.dp, 220.dp).width(380.dp))
            VolumePanel(state.volume, actions, Modifier.offset(860.dp, 604.dp).size(380.dp, 84.dp))
        }
    }
}

// ---------------------------------------------------------------- top bar

@Composable
private fun TopBar(state: HomeState, actions: HomeActions, modifier: Modifier) {
    Row(modifier, horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            SourceTab("RADIO", VfdIcons.Radio, 196.dp, state.source == Source.RADIO,
                { actions.onSource(Source.RADIO) }, { actions.onSourceLongPress(Source.RADIO) })
            SourceTab("YT MUSIC", VfdIcons.Music, 232.dp, state.source == Source.MUSIC,
                { actions.onSource(Source.MUSIC) }, { actions.onSourceLongPress(Source.MUSIC) })
            val indicators = if (state.source == Source.RADIO) {
                val band = if (state.radio?.tuning?.band == Band.AM) "AM" else "FM"
                if (state.radio?.isPlaying == true) "$band · STEREO" else band
            } else {
                if (state.music?.isPlaying == true) "STREAMING" else "STANDBY"
            }
            BasicText(indicators, Modifier.padding(start = 14.dp), style = glow(Vfd.Teal, 20.sp, 3.sp, blur = 8f))
        }
        Clock()
    }
}

@Composable
private fun SourceTab(
    label: String,
    icon: ImageVector,
    width: Dp,
    active: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val fg = if (active) Vfd.Panel else Vfd.Teal
    VfdButton(
        Modifier
            .size(width, 64.dp),
        border = Vfd.Teal,
        fill = if (active) Vfd.Teal else Color.Transparent,
        pressedFill = if (active) Vfd.Teal else Vfd.Teal.copy(alpha = 0.2f),
        onLongClick = onLongClick,
        onClickLabel = "Switch to $label",
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            GlowIcon(icon, fg, 26.dp, glow = false)
            BasicText(
                label,
                style = glow(fg, 20.sp, 2.sp, blur = if (active) 0f else 8f, family = Vfd.Orbitron, weight = FontWeight.Bold),
            )
        }
    }
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
    val minute = now / 60_000L
    val date = remember(minute) {
        SimpleDateFormat("EEE d MMM", Locale.getDefault()).format(Date(now)).replace(".", "")
            .uppercase(Locale.getDefault())
    }
    val time = remember(minute, is24) {
        SimpleDateFormat(if (is24) "HH:mm" else "h:mm", Locale.getDefault()).format(Date(now))
    }
    Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
        BasicText(date, Modifier.padding(bottom = 10.dp), style = glow(Vfd.Amber, 22.sp, 4.sp, blur = 8f))
        BasicText(time, style = glow(Vfd.Teal, 54.sp, blur = 18f, family = Vfd.Orbitron, weight = FontWeight.Bold))
    }
}

// ---------------------------------------------------------------- main display

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun MainDisplay(state: HomeState, actions: HomeActions, modifier: Modifier) {
    Box(
        modifier
            .vfdPanel(PanelBorder22)
            .combinedClickable(
                onClick = { if (!state.hasMediaAccess) actions.onGrantMediaAccess() },
                onLongClick = actions.onInspect,
                onLongClickLabel = "Show media session details",
            )
            .padding(horizontal = 28.dp, vertical = 22.dp),
    ) {
        if (state.source == Source.RADIO) RadioDisplay(state) else MusicDisplay(state)
    }
}

@Composable
private fun RadioDisplay(state: HomeState) {
    val np = state.radio
    val tuning = np?.tuning
    val presetIdx = tuning?.let { t -> state.presets.indexOfFirst { it?.tuning?.matches(t) == true } } ?: -1
    val band = if (tuning?.band == Band.AM) "AM" else "FM"
    val label = when {
        np == null -> "$band · NO SOURCE"
        presetIdx >= 0 -> "$band · PRESET ${presetIdx + 1}"
        else -> "$band · MANUAL"
    }
    val station = when {
        !state.hasMediaAccess -> "TAP TO ALLOW MEDIA ACCESS"
        presetIdx >= 0 && state.presets[presetIdx]?.name != null -> state.presets[presetIdx]!!.name!!
        else -> np?.stationName ?: "— — —"
    }
    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        BasicText(label, style = glow(Vfd.Teal, 22.sp, 4.sp, blur = 6f))
        Row(verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            val bigStyle = glow(Vfd.Teal, 180.sp, (-4).sp, blur = 28f).copy(
                textAlign = TextAlign.End,
                lineHeight = 0.8.em,
                lineHeightStyle = LineHeightStyle(LineHeightStyle.Alignment.Center, LineHeightStyle.Trim.Both),
            )
            Box(Modifier.widthIn(min = 540.dp), contentAlignment = Alignment.BottomEnd) {
                BasicText(if (tuning?.band == Band.AM) "8888" else "888.88", style = bigStyle.copy(color = Vfd.GhostDigits, shadow = null))
                BasicText(tuning?.display ?: "--.--", style = bigStyle, maxLines = 1)
            }
            BasicText(tuning?.unit ?: "MHz", Modifier.padding(bottom = 4.dp), style = glow(Vfd.Amber, 40.sp, blur = 8f))
        }
        Row(
            Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(24.dp),
        ) {
            BasicText(
                station,
                Modifier.weight(1f),
                style = glow(Vfd.Amber, 38.sp, 6.sp, blur = 10f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            SpectrumAnalyzer(np?.isPlaying == true, Modifier.size(320.dp, 48.dp))
        }
    }
}

@Composable
private fun MusicDisplay(state: HomeState) {
    val np = state.music
    // Elapsed time ticks once a second while playing.
    var nowMs by remember { mutableLongStateOf(SystemClock.elapsedRealtime()) }
    LaunchedEffect(np?.isPlaying, np?.positionUpdatedAt) {
        while (np?.isPlaying == true) {
            nowMs = SystemClock.elapsedRealtime()
            delay(1_000L - (nowMs % 1_000L))
        }
    }
    val pos = when {
        np == null -> 0L
        np.isPlaying -> np.positionMs + ((nowMs - np.positionUpdatedAt) * np.speed).toLong()
        else -> np.positionMs
    }.coerceIn(0L, np?.durationMs?.takeIf { it > 0 } ?: Long.MAX_VALUE)
    val duration = np?.durationMs ?: 0L

    val source = (np?.appLabel ?: "YT MUSIC").uppercase(Locale.getDefault())
    val header = np?.queuePosition?.let { "$source · TRACK $it" } ?: source
    val title = when {
        !state.hasMediaAccess -> "TAP TO ALLOW"
        else -> np?.title?.uppercase(Locale.getDefault()) ?: "— — —"
    }
    val artist = when {
        !state.hasMediaAccess -> "MEDIA ACCESS"
        else -> np?.artist?.uppercase(Locale.getDefault()) ?: if (np == null) "TAP YT MUSIC TO START" else ""
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            BasicText(
                header, Modifier.weight(1f),
                style = glow(Vfd.Teal, 22.sp, 4.sp, blur = 6f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (np != null) {
                BasicText(
                    "${mmss(pos)} / ${if (duration > 0) mmss(duration) else "--:--"}",
                    style = glow(Vfd.Amber, 22.sp, 4.sp, blur = 6f),
                )
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
            BasicText(
                title,
                style = glow(Vfd.Teal, 74.sp, 2.sp, blur = 24f).copy(lineHeight = 1.em),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                artist,
                style = glow(Vfd.Amber, 38.sp, 6.sp, blur = 10f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        val done = if (duration > 0) ((pos.toFloat() / duration) * 40).toInt().coerceIn(0, 40) else 0
        SegmentBar(total = 40, lit = done, height = 18.dp, ghost = Vfd.Teal.copy(alpha = 0.12f)) { Vfd.Teal }
    }
}

private fun mmss(ms: Long): String {
    val s = ms / 1000
    return "${s / 60}:${(s % 60).toString().padStart(2, '0')}"
}

@Composable
private fun SegmentBar(total: Int, lit: Int, height: Dp, ghost: Color, colorAt: (Int) -> Color) {
    Row(
        Modifier
            .fillMaxWidth()
            .height(height),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        repeat(total) { i ->
            Box(
                Modifier
                    .weight(1f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(2.dp))
                    .background(if (i < lit) colorAt(i) else ghost),
            )
        }
    }
}

// ---------------------------------------------------------------- radio controls

@Composable
private fun PresetGrid(state: HomeState, actions: HomeActions, modifier: Modifier) {
    val current = state.radio?.tuning
    val queue = state.radio?.queue.orEmpty()
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        state.presets.forEachIndexed { i, stored ->
            val shownTuning = stored?.tuning ?: queue.getOrNull(i)?.tuning
            val active = shownTuning != null && shownTuning.matches(current)
            val freq = shownTuning?.let {
                if (it.band == Band.FM) String.format(Locale.US, "%.1f", it.value) else it.value.toInt().toString()
            } ?: "--.-"
            val name = stored?.name ?: queue.getOrNull(i)?.title?.uppercase(Locale.getDefault())
                ?: if (shownTuning == null) "HOLD = SAVE" else ""
            VfdButton(
                Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                border = if (active) Vfd.Teal else Vfd.Teal.copy(alpha = 0.6f),
                shape = RoundedCornerShape(12.dp),
                fill = if (active) Vfd.Teal.copy(alpha = 0.18f) else Color.Transparent,
                onLongClick = { actions.onPresetLongPress(i) },
                onClickLabel = "Preset ${i + 1}",
                onClick = { actions.onPreset(i) },
            ) {
                Column(
                    Modifier.padding(horizontal = 4.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    BasicText("P${i + 1}", style = glow(Vfd.Teal, 20.sp, 3.sp, blur = 4f))
                    BasicText(freq, style = glow(Vfd.Teal, 34.sp, blur = if (active) 12f else 6f).copy(lineHeight = 1.em))
                    BasicText(
                        name,
                        style = glow(Vfd.Amber, 18.sp, 1.sp, blur = 4f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
        }
    }
}

@Composable
private fun RadioControls(state: HomeState, actions: HomeActions, modifier: Modifier) {
    val muted = state.volume.muted
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        WordButton(Modifier.weight(1f), "SEEK", VfdIcons.SeekBack, iconFirst = true, label = "Seek down") { actions.onSeek(false) }
        WordButton(Modifier.weight(1f), "TUNE", VfdIcons.ChevronLeft, iconFirst = true, label = "Tune down") { actions.onTune(false) }
        WordButton(
            Modifier.weight(1.2f), if (muted) "UNMUTE" else "MUTE", VfdIcons.Mute, iconFirst = true,
            color = Vfd.Amber, border = Vfd.Amber,
            fill = if (muted) Vfd.Amber.copy(alpha = 0.22f) else Color.Transparent,
            label = if (muted) "Unmute" else "Mute",
            onClick = actions.onMute,
        )
        WordButton(Modifier.weight(1f), "TUNE", VfdIcons.ChevronRight, iconFirst = false, label = "Tune up") { actions.onTune(true) }
        WordButton(Modifier.weight(1f), "SEEK", VfdIcons.SeekForward, iconFirst = false, label = "Seek up") { actions.onSeek(true) }
    }
}

@Composable
private fun WordButton(
    modifier: Modifier,
    word: String,
    icon: ImageVector,
    iconFirst: Boolean,
    color: Color = Vfd.Teal,
    border: Color = BorderStrong,
    fill: Color = Color.Transparent,
    label: String,
    onClick: () -> Unit,
) {
    VfdButton(
        modifier.fillMaxHeight(),
        border = border,
        fill = fill,
        pressedFill = color.copy(alpha = 0.2f),
        onClickLabel = label,
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            if (iconFirst) GlowIcon(icon, color, 28.dp, glow = false)
            BasicText(word, style = glow(color, 18.sp, 2.sp, blur = 4f))
            if (!iconFirst) GlowIcon(icon, color, 28.dp, glow = false)
        }
    }
}

// ---------------------------------------------------------------- music controls

@Composable
private fun MusicTransport(state: HomeState, actions: HomeActions, modifier: Modifier) {
    val playing = state.music?.isPlaying == true
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        BigTransportButton(Modifier.weight(1f), VfdIcons.Previous, 52.dp, "PREV", Vfd.Teal, "Previous track", actions.onPrevious)
        BigTransportButton(
            Modifier.weight(1.4f),
            if (playing) VfdIcons.Pause else VfdIcons.Play, 60.dp,
            if (playing) "PAUSE" else "PLAY", Vfd.Amber,
            if (playing) "Pause" else "Play",
            actions.onPlayPause,
        )
        BigTransportButton(Modifier.weight(1f), VfdIcons.Next, 52.dp, "NEXT", Vfd.Teal, "Next track", actions.onNext)
    }
}

@Composable
private fun RowScope.BigTransportButton(
    modifier: Modifier,
    icon: ImageVector,
    iconSize: Dp,
    word: String,
    color: Color,
    label: String,
    onClick: () -> Unit,
) {
    val amber = color == Vfd.Amber
    VfdButton(
        modifier.fillMaxHeight(),
        border = if (amber) Vfd.Amber else BorderStrong,
        shape = RoundedCornerShape(12.dp),
        fill = if (amber) Vfd.Amber.copy(alpha = 0.08f) else Color.Transparent,
        pressedFill = color.copy(alpha = 0.2f),
        onClickLabel = label,
        onClick = onClick,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            GlowIcon(icon, color, iconSize, glow = amber)
            BasicText(word, style = glow(color, 20.sp, 3.sp, blur = 6f))
        }
    }
}

@Composable
private fun MusicToggleRow(state: HomeState, actions: HomeActions, modifier: Modifier) {
    val t = state.toggles
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        ToggleButton("SHUFFLE", VfdIcons.Shuffle, t.shuffle, actions.onShuffle)
        ToggleButton("REPEAT", VfdIcons.Repeat, t.repeat, actions.onRepeat)
        ToggleButton("LIKE", VfdIcons.Heart, t.liked, actions.onLike)
        Box(
            Modifier
                .weight(1f)
                .fillMaxHeight()
                .vfdPanel()
                .padding(horizontal = 14.dp, vertical = 10.dp),
        ) {
            SpectrumAnalyzer(
                state.music?.isPlaying == true,
                Modifier.fillMaxSize(),
                segment = 5.dp,
                period = 8.dp,
            )
        }
    }
}

@Composable
private fun ToggleButton(word: String, icon: ImageVector, on: Boolean, onClick: () -> Unit) {
    VfdButton(
        Modifier
            .width(140.dp)
            .fillMaxHeight(),
        border = if (on) Vfd.Teal else BorderStrong,
        fill = if (on) Vfd.Teal.copy(alpha = 0.18f) else Color.Transparent,
        onClickLabel = word.lowercase(Locale.ROOT),
        onClick = onClick,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GlowIcon(icon, Vfd.Teal, 28.dp, glow = on)
            BasicText(word, style = glow(Vfd.Teal, 18.sp, 2.sp, blur = 4f))
        }
    }
}

// ---------------------------------------------------------------- right column

@Composable
private fun NextTurnPanel(turn: NextTurn, onOpen: () -> Unit, modifier: Modifier) {
    VfdButton(
        modifier,
        border = Vfd.Amber.copy(alpha = 0.6f),
        fill = Vfd.Panel,
        pressedFill = Vfd.Amber.copy(alpha = 0.12f),
        onClickLabel = "Open maps",
        onClick = onOpen,
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 18.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            val arrow = turn.arrow
            if (arrow != null) {
                Image(
                    arrow.asImageBitmap(),
                    contentDescription = null,
                    colorFilter = ColorFilter.tint(Vfd.Amber),
                    modifier = Modifier.size(58.dp),
                )
            } else {
                GlowIcon(VfdIcons.TurnRight, Vfd.Amber, 58.dp)
            }
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                BasicText(
                    turn.distance.uppercase(Locale.getDefault()),
                    style = glow(Vfd.Teal, 44.sp, blur = 10f).copy(lineHeight = 1.em),
                    maxLines = 1,
                )
                BasicText(
                    turn.street.uppercase(Locale.getDefault()),
                    style = glow(Vfd.Amber, 22.sp, 2.sp, blur = 6f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (turn.eta != null) {
                Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    BasicText("ETA", style = glow(Vfd.Teal, 18.sp, 2.sp, blur = 4f))
                    BasicText(turn.eta, style = glow(Vfd.Amber, 24.sp, 2.sp, blur = 6f))
                }
            }
        }
    }
}

@Composable
private fun WeatherPanel(weather: Weather?, modifier: Modifier) {
    Row(
        modifier
            .vfdPanel(PanelBorder22)
            .padding(horizontal = 22.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        GlowIcon(VfdIcons.weather(weather?.icon ?: WeatherIcon.CLOUD), Vfd.Amber, 52.dp)
        BasicText(
            weather?.let { "${it.temp}°" } ?: "--°",
            style = glow(Vfd.Teal, 56.sp, blur = 12f).copy(lineHeight = 1.em),
        )
        BasicText(
            when {
                weather == null -> "NO DATA"
                weather.stale -> "OFFLINE"
                else -> weather.condition
            },
            style = glow(Vfd.Amber, 24.sp, 3.sp, blur = 6f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

@Composable
private fun AppGrid(actions: HomeActions, modifier: Modifier) {
    Column(modifier, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        VfdButton(
            Modifier
                .fillMaxWidth()
                .height(112.dp),
            border = Vfd.Amber,
            fill = Vfd.Amber.copy(alpha = 0.08f),
            pressedFill = Vfd.Amber.copy(alpha = 0.2f),
            onLongClick = { actions.onSlotLongPress(Slot.MAPS) },
            onClickLabel = "Open Google Maps",
            onClick = { actions.onSlot(Slot.MAPS) },
        ) {
            Row(
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 20.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(18.dp),
            ) {
                GlowIcon(VfdIcons.Maps, Vfd.Amber, 48.dp)
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    BasicText(
                        "MAPS",
                        style = glow(Vfd.Amber, 26.sp, 2.sp, blur = 8f, family = Vfd.Orbitron, weight = FontWeight.Bold),
                    )
                    BasicText("GOOGLE MAPS", style = glow(Vfd.Amber, 18.sp, 2.sp, blur = 0f))
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GridButton("ANDROID AUTO", VfdIcons.AndroidAuto, Modifier.weight(1f),
                { actions.onSlot(Slot.ANDROID_AUTO) }, { actions.onSlotLongPress(Slot.ANDROID_AUTO) })
            GridButton("APPS", VfdIcons.Apps, Modifier.weight(1f), actions.onApps, null)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            GridButton("CAMERA", VfdIcons.Camera, Modifier.weight(1f),
                { actions.onSlot(Slot.CAMERA) }, { actions.onSlotLongPress(Slot.CAMERA) })
            GridButton("SETTINGS", VfdIcons.Settings, Modifier.weight(1f),
                { actions.onSlot(Slot.SETTINGS) }, { actions.onSlotLongPress(Slot.SETTINGS) })
        }
    }
}

@Composable
private fun GridButton(
    word: String,
    icon: ImageVector,
    modifier: Modifier,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)?,
) {
    VfdButton(
        modifier.height(100.dp),
        border = BorderStrong,
        onLongClick = onLongClick,
        onClickLabel = "Open ${word.lowercase(Locale.ROOT)}",
        onClick = onClick,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(8.dp)) {
            GlowIcon(icon, Vfd.Teal, 32.dp, glow = false)
            BasicText(word, style = glow(Vfd.Teal, 20.sp, 3.sp, blur = 6f), maxLines = 1)
        }
    }
}

// ---------------------------------------------------------------- volume

@Composable
private fun VolumePanel(vol: Volume, actions: HomeActions, modifier: Modifier) {
    Row(
        modifier
            .vfdPanel(PanelBorder22)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        SquareButton("Volume down", actions.onVolumeDown) { GlowIcon(VfdIcons.Minus, Vfd.Teal, 26.dp, glow = false) }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                BasicText("VOL", style = glow(Vfd.Teal, 20.sp, 2.sp, blur = 4f))
                BasicText(
                    if (vol.muted) "MUTE" else vol.segments.toString(),
                    style = glow(Vfd.Amber, 20.sp, 2.sp, blur = 6f),
                )
            }
            SegmentBar(
                total = Volume.SEGMENTS,
                lit = if (vol.muted) 0 else vol.segments,
                height = 16.dp,
                ghost = Vfd.Teal.copy(alpha = 0.12f),
            ) { i -> if (i >= 16) Vfd.Amber else Vfd.Teal }
        }
        SquareButton("Volume up", actions.onVolumeUp) { GlowIcon(VfdIcons.Plus, Vfd.Teal, 26.dp, glow = false) }
    }
}

@Composable
private fun SquareButton(label: String, onClick: () -> Unit, content: @Composable () -> Unit) {
    VfdButton(
        Modifier.size(66.dp),
        border = BorderStrong,
        onClickLabel = label,
        onClick = onClick,
        content = content,
    )
}
