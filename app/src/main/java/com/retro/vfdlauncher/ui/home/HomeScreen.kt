package com.retro.vfdlauncher.ui.home

import android.os.SystemClock
import android.text.format.DateFormat
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.retro.vfdlauncher.data.media.Playlist
import com.retro.vfdlauncher.data.media.PlaylistRepository
import com.retro.vfdlauncher.data.media.Preset
import com.retro.vfdlauncher.data.nav.NextTurn
import com.retro.vfdlauncher.data.weather.RoofFx
import com.retro.vfdlauncher.data.weather.RoofMeter
import com.retro.vfdlauncher.data.weather.RoofTier
import com.retro.vfdlauncher.data.weather.RoofVerdict
import com.retro.vfdlauncher.data.weather.Weather
import com.retro.vfdlauncher.ui.theme.Mr2Icon
import java.time.LocalDateTime
import java.time.ZoneOffset
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
    /** The six PLAYLISTS keys (null = empty slot). */
    val playlists: List<Playlist?> = List(PlaylistRepository.COUNT) { null },
    /** Index of the playlist last started from the launcher, or -1. */
    val currentPlaylist: Int = -1,
    val playlistsOpen: Boolean = false,
    /** Night mode is on (dims the screen; the moon key is lit). */
    val night: Boolean = false,
    /** PHONE selected: recent calls + favourites replace the media section. */
    val phoneOpen: Boolean = false,
    /** Current call (from the phone app's call notification), or null. */
    val call: com.retro.vfdlauncher.data.phone.CallInfo? = null,
    /** Latest calls from the call log; null while loading. */
    val recents: List<com.retro.vfdlauncher.data.phone.RecentCall>? = null,
    /** Why the recent-calls list is empty (no permission / phone app doesn't share its log). */
    val recentsNote: String? = null,
    val favorites: List<com.retro.vfdlauncher.data.phone.Favorite?> = List(6) { null },
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
    /** Scrub bar: seek the music source to this position (ms). */
    val onSeekTo: (Long) -> Unit = {},
    val onPlaylists: () -> Unit = {},
    val onPlaylistsClose: () -> Unit = {},
    /** Tap a playlist key: play it (empty key: set it up). */
    val onPlaylist: (Int) -> Unit = {},
    /** Long-press a playlist key (or PLAYLISTS): edit that slot. */
    val onPlaylistEdit: (Int) -> Unit = {},
    val onOpenMusicApp: () -> Unit = {},
    /** Moon key: switch night mode on/off until the next sunrise or sunset. */
    val onNight: () -> Unit = {},
    val onPhone: () -> Unit = {},
    /** Hold PHONE: choose the phone app (KEYPAD & CONTACTS / OPEN PHONE). */
    val onPhoneLongPress: () -> Unit = {},
    val onDial: (String) -> Unit = {},
    val onFavorite: (Int) -> Unit = {},
    val onFavoriteEdit: (Int) -> Unit = {},
    val onAnswer: () -> Unit = {},
    val onHangUp: () -> Unit = {},
    val onOpenPhone: () -> Unit = {},
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
            if (state.phoneOpen) {
                // Canvas "PHONE selected": recent calls, favourites, back / keypad.
                RecentCallsPanel(state.recents, state.recentsNote, actions.onDial, Modifier.offset(40.dp, 108.dp).size(780.dp, 300.dp))
                FavoritesRow(state.favorites, actions.onFavorite, actions.onFavoriteEdit, Modifier.offset(40.dp, 428.dp).size(780.dp, 156.dp))
                PhoneBottomRow(
                    if (state.source == Source.RADIO) "BACK TO RADIO" else "BACK TO MUSIC",
                    actions.onPhone, actions.onOpenPhone,
                    Modifier.offset(40.dp, 604.dp).size(780.dp, 84.dp),
                )
            } else {
                MainDisplay(state, actions, Modifier.offset(40.dp, 108.dp).size(780.dp, 300.dp))
            }
            if (state.phoneOpen) {
                // media rows are replaced above
            } else if (state.source == Source.RADIO) {
                PresetGrid(state, actions, Modifier.offset(40.dp, 428.dp).size(780.dp, 156.dp))
                RadioControls(state, actions, Modifier.offset(40.dp, 604.dp).size(780.dp, 84.dp))
            } else {
                MusicTransport(state, actions, Modifier.offset(40.dp, 428.dp).size(780.dp, 156.dp))
                MusicToggleRow(state, actions, Modifier.offset(40.dp, 604.dp).size(780.dp, 84.dp))
                if (state.playlistsOpen) {
                    PlaylistsPanel(state, actions, Modifier.offset(40.dp, 108.dp).size(780.dp, 580.dp))
                }
            }
            // During a call the call bar replaces the small control row (canvas: Call-Ringing/-Active/-Fallback).
            state.call?.let { c ->
                CallBar(c, actions.onAnswer, actions.onHangUp, actions.onOpenPhone, Modifier.offset(40.dp, 604.dp).size(780.dp, 84.dp))
            }
            val turn = state.nextTurn
            if (turn != null) {
                NextTurnPanel(turn, { actions.onSlot(Slot.MAPS) }, Modifier.offset(860.dp, 108.dp).size(380.dp, 96.dp))
            } else {
                WeatherOrRoofPanel(state.weather, state.night, Modifier.offset(860.dp, 108.dp).size(380.dp, 96.dp))
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
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            SourceTab("RADIO", VfdIcons.Radio, 196.dp, state.source == Source.RADIO && !state.phoneOpen,
                { actions.onSource(Source.RADIO) }, { actions.onSourceLongPress(Source.RADIO) })
            SourceTab("YT MUSIC", VfdIcons.Music, 232.dp, state.source == Source.MUSIC && !state.phoneOpen,
                { actions.onSource(Source.MUSIC) }, { actions.onSourceLongPress(Source.MUSIC) })
            val indicators = if (state.source == Source.RADIO) {
                val band = if (state.radio?.tuning?.band == Band.AM) "AM" else "FM"
                if (state.radio?.isPlaying == true) "$band · STEREO" else band
            } else {
                if (state.music?.isPlaying == true) "STREAMING" else "STANDBY"
            }
            SourceTab("PHONE", VfdIcons.Phone, 156.dp, state.phoneOpen, actions.onPhone, actions.onPhoneLongPress)
            BasicText(
                indicators,
                Modifier
                    .padding(start = 14.dp)
                    .weight(1f, fill = false),
                style = glow(Vfd.Teal, 20.sp, 3.sp, blur = 8f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(18.dp)) {
            MoonKey(state.night, actions.onNight)
            Clock()
        }
    }
}

/** Night mode on/off; lit amber while night mode is on. */
@Composable
private fun MoonKey(night: Boolean, onClick: () -> Unit) {
    VfdButton(
        Modifier.size(56.dp),
        border = if (night) Vfd.Amber else Vfd.Teal.copy(alpha = 0.5f),
        fill = if (night) Vfd.Amber.copy(alpha = 0.18f) else Color.Transparent,
        onClickLabel = if (night) "Night mode off" else "Night mode on",
        onClick = onClick,
    ) {
        GlowIcon(VfdIcons.Moon, if (night) Vfd.Amber else Vfd.Teal.copy(alpha = 0.75f), 26.dp, glow = false)
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
        if (state.source == Source.RADIO) RadioDisplay(state) else MusicDisplay(state, actions)
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
private fun MusicDisplay(state: HomeState, actions: HomeActions) {
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

    // Scrubbing: while a finger is on the bar (and until the app reports the new position),
    // the segments and the time show the target instead of the playing position.
    var scrub by remember { mutableStateOf<Float?>(null) }
    var dragging by remember { mutableStateOf(false) }
    LaunchedEffect(np?.positionUpdatedAt, np?.title) { if (!dragging) scrub = null }
    LaunchedEffect(scrub, dragging) {
        if (scrub != null && !dragging) {
            delay(2_000) // the app never confirmed: fall back to the reported position
            scrub = null
        }
    }
    val shownPos = scrub?.let { (it * duration).toLong() } ?: pos

    // "{playlist} · TRACK x/y": the app's queue title, else the playlist last picked here, else the app.
    val picked = state.playlists.getOrNull(state.currentPlaylist)?.name?.takeIf { it.isNotBlank() }
    val queueName = np?.queueTitle?.takeUnless { it.trim().lowercase(Locale.ROOT) in GENERIC_QUEUE_TITLES }
    val source = (queueName ?: picked ?: np?.appLabel ?: "YT MUSIC").uppercase(Locale.getDefault())
    // Complete queues: "TRACK 3/12". YouTube Music only shares the next ~10 songs, so name the next one instead.
    val header = np?.queuePosition?.let { "$source · TRACK $it" }
        ?: np?.nextTitle?.let { "$source · NEXT: ${it.uppercase(Locale.getDefault())}" }
        ?: source
    val title = when {
        !state.hasMediaAccess -> "TAP TO ALLOW"
        else -> np?.title?.uppercase(Locale.getDefault()) ?: "— — —"
    }
    val artist = when {
        !state.hasMediaAccess -> "MEDIA ACCESS"
        else -> np?.artist?.uppercase(Locale.getDefault()) ?: if (np == null) "TAP YT MUSIC TO START" else ""
    }

    Column(Modifier.fillMaxSize(), verticalArrangement = Arrangement.SpaceBetween) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(20.dp)) {
            BasicText(
                header, Modifier.weight(1f),
                style = glow(Vfd.Teal, 22.sp, 4.sp, blur = 6f), maxLines = 1, overflow = TextOverflow.Ellipsis,
            )
            if (np != null) {
                BasicText(
                    "${mmss(shownPos)} / ${if (duration > 0) mmss(duration) else "--:--"}",
                    style = glow(if (dragging) Vfd.Teal else Vfd.Amber, 22.sp, 4.sp, blur = 6f),
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
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.spacedBy(24.dp),
            ) {
                BasicText(
                    artist,
                    Modifier.weight(1f),
                    style = glow(Vfd.Amber, 38.sp, 6.sp, blur = 10f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                SpectrumAnalyzer(np?.isPlaying == true, Modifier.size(280.dp, 44.dp))
            }
        }
        val done = if (duration > 0) ((shownPos.toFloat() / duration) * 40).toInt().coerceIn(0, 40) else 0
        val canSeek = np != null && duration > 0
        // Taller than the bar so it is easy to hit while driving; tap jumps, drag scrubs.
        Box(
            Modifier
                .fillMaxWidth()
                .height(40.dp)
                .then(
                    if (!canSeek) Modifier else Modifier
                        .pointerInput(duration) {
                            detectTapGestures { o ->
                                val f = (o.x / size.width).coerceIn(0f, 1f)
                                scrub = f
                                actions.onSeekTo((f * duration).toLong())
                            }
                        }
                        .pointerInput(duration) {
                            detectHorizontalDragGestures(
                                onDragStart = { o -> dragging = true; scrub = (o.x / size.width).coerceIn(0f, 1f) },
                                onHorizontalDrag = { change, _ ->
                                    change.consume()
                                    scrub = (change.position.x / size.width).coerceIn(0f, 1f)
                                },
                                onDragEnd = {
                                    dragging = false
                                    scrub?.let { actions.onSeekTo((it * duration).toLong()) }
                                },
                                onDragCancel = { dragging = false; scrub = null },
                            )
                        },
                ),
            contentAlignment = Alignment.BottomCenter,
        ) {
            SegmentBar(total = 40, lit = done, height = if (dragging) 22.dp else 18.dp, ghost = Vfd.Teal.copy(alpha = 0.12f)) {
                if (dragging) Vfd.Amber else Vfd.Teal
            }
        }
    }
}

/** Queue titles apps use when no playlist is playing ("Up next" in YouTube Music); not shown as a name. */
private val GENERIC_QUEUE_TITLES = setOf(
    "up next", "now playing", "queue", "play queue", "playing next",
    "volgende", "als volgende", "wachtrij", "nu aan het afspelen", "nu afspelen",
)

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
        val key = Modifier
            .weight(1f)
            .fillMaxHeight()
        ToggleButton("SHUFFLE", VfdIcons.Shuffle, t.shuffle, actions.onShuffle, key)
        ToggleButton("REPEAT", VfdIcons.Repeat, t.repeat, actions.onRepeat, key)
        ToggleButton("LIKE", VfdIcons.Heart, t.liked, actions.onLike, key)
        RowKey(
            "PLAYLISTS", VfdIcons.Playlist, Vfd.Teal, key,
            onLongClick = { actions.onPlaylistEdit(state.currentPlaylist.coerceAtLeast(0)) },
            onClick = actions.onPlaylists,
        )
        RowKey("OPEN APP", VfdIcons.OpenApp, Vfd.Amber, key, halo = !state.playlistsOpen, onClick = actions.onOpenMusicApp)
    }
}

@Composable
private fun ToggleButton(word: String, icon: ImageVector, on: Boolean, onClick: () -> Unit, modifier: Modifier) {
    VfdButton(
        modifier,
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

/** Soft halo outside a key (the canvas' box-shadow glow). */
internal fun Modifier.halo(color: Color, radius: Dp, spread: Dp): Modifier = drawBehind {
    val p = spread.toPx()
    drawRoundRect(
        color = color,
        topLeft = Offset(-p, -p),
        size = Size(size.width + 2 * p, size.height + 2 * p),
        cornerRadius = CornerRadius(radius.toPx() + p),
    )
}

/** PLAYLISTS / OPEN APP: tinted keys in the music row. */
@Composable
private fun RowKey(
    word: String,
    icon: ImageVector,
    color: Color,
    modifier: Modifier,
    halo: Boolean = false,
    onLongClick: (() -> Unit)? = null,
    onClick: () -> Unit,
) {
    VfdButton(
        if (halo) modifier.halo(color.copy(alpha = 0.12f), 10.dp, 5.dp) else modifier,
        border = color,
        fill = color.copy(alpha = 0.08f),
        onClickLabel = word.lowercase(Locale.ROOT),
        onLongClick = onLongClick,
        onClick = onClick,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(6.dp)) {
            GlowIcon(icon, color, 28.dp, glow = false)
            BasicText(word, style = glow(color, 18.sp, 2.sp, blur = 4f), maxLines = 1)
        }
    }
}

/**
 * PLAYLISTS panel (artboard 8): six pinned YouTube Music playlists over the left area.
 * Tap plays; long-press edits; an empty key opens the editor.
 */
@Composable
private fun PlaylistsPanel(state: HomeState, actions: HomeActions, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(10.dp))
            .background(Vfd.Panel)
            .border(2.dp, Vfd.Teal.copy(alpha = 0.5f), RoundedCornerShape(10.dp))
            // Swallow taps between keys so they don't reach the screen underneath.
            .clickable(remember { MutableInteractionSource() }, indication = null) {}
            .padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(18.dp),
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            BasicText(
                "PLAYLISTS",
                style = glow(Vfd.Teal, 26.sp, 3.sp, blur = 8f, family = Vfd.Orbitron, weight = FontWeight.Bold),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
                BasicText("HOLD A KEY TO EDIT", style = glow(Vfd.Teal.copy(alpha = 0.7f), 18.sp, 2.sp, blur = 0f))
                VfdButton(Modifier.size(72.dp, 64.dp), border = BorderStrong, onClickLabel = "Close playlists", onClick = actions.onPlaylistsClose) {
                    GlowIcon(VfdIcons.Close, Vfd.Teal, 28.dp, glow = false)
                }
            }
        }
        for (row in 0 until 2) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .weight(1f),
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                for (col in 0 until 3) {
                    val i = row * 3 + col
                    PlaylistKey(
                        i, state.playlists.getOrNull(i), i == state.currentPlaylist, actions,
                        Modifier
                            .weight(1f)
                            .fillMaxHeight(),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlaylistKey(index: Int, p: Playlist?, playing: Boolean, actions: HomeActions, modifier: Modifier) {
    VfdButton(
        if (playing) modifier.halo(Vfd.Teal.copy(alpha = 0.16f), 12.dp, 6.dp) else modifier,
        border = if (playing) Vfd.Teal else Vfd.Teal.copy(alpha = if (p == null) 0.3f else 0.6f),
        fill = if (playing) Vfd.Teal.copy(alpha = 0.18f) else Color.Transparent,
        shape = RoundedCornerShape(12.dp),
        onClickLabel = if (p == null) "Set up playlist key ${index + 1}" else "Play playlist ${p.name}",
        onLongClick = { actions.onPlaylistEdit(index) },
        onClick = { actions.onPlaylist(index) },
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            BasicText(if (playing) "PLAYING" else "P${index + 1}", style = glow(Vfd.Amber, 18.sp, 3.sp, blur = 4f))
            BasicText(
                p?.name?.ifBlank { null }?.uppercase(Locale.getDefault()) ?: if (p == null) "EMPTY" else "PLAYLIST ${index + 1}",
                style = glow(if (p == null) Vfd.Teal.copy(alpha = 0.45f) else Vfd.Teal, 30.sp, 1.sp, blur = if (p == null) 0f else 8f)
                    .copy(lineHeight = 1.1.em),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            BasicText(
                if (p == null) "TAP TO SET" else "YT MUSIC",
                style = glow(if (p == null) Vfd.Amber.copy(alpha = 0.7f) else Vfd.Teal, 18.sp, 2.sp, blur = 0f),
            )
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

/**
 * Not navigating: the TOP-DOWN meter (canvas artboards 4-8) when the forecast has the data,
 * else the plain weather. Tap to switch between the two.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WeatherOrRoofPanel(weather: Weather?, night: Boolean, modifier: Modifier) {
    val roof = weather?.roof
    var showWeather by remember { mutableStateOf(false) }
    if (roof == null) {
        WeatherPanel(weather, modifier)
        return
    }
    // Re-evaluate every minute: "TILL 17:40" and the hour windows move with the clock.
    var minute by remember { mutableLongStateOf(System.currentTimeMillis() / 60_000L) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(60_000L - System.currentTimeMillis() % 60_000L + 50L)
            minute = System.currentTimeMillis() / 60_000L
        }
    }
    val live = remember(roof, minute) {
        RoofMeter.evaluate(roof, LocalDateTime.now(ZoneOffset.ofTotalSeconds(roof.utcOffsetSeconds)))
    }
    Box(
        modifier.clickable(
            interactionSource = remember { MutableInteractionSource() },
            indication = null,
            onClickLabel = "Switch between top-down meter and weather",
            onClick = { showWeather = !showWeather },
        ),
    ) {
        when {
            showWeather -> WeatherPanel(weather, Modifier.fillMaxSize())
            else -> TopDownMeter(live, night, Modifier.fillMaxSize())
        }
    }
}

private val RoofRed = Color(0xFFFF6A4D)

@Composable
private fun TopDownMeter(v: RoofVerdict, night: Boolean, modifier: Modifier) {
    val color = when (v.tier) {
        RoofTier.GOOD -> Vfd.Teal
        RoofTier.OK -> Vfd.Amber
        RoofTier.BAD -> RoofRed
    }
    Row(
        modifier
            .vfdPanel()
            .semantics(mergeDescendants = true) {
                contentDescription = "Top-down score ${v.score} out of 10, ${v.temp} degrees, ${v.verdict}"
            }
            .padding(horizontal = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        val car = rememberVectorPainter(if (v.roofDown) Mr2Icon.RoofDown else Mr2Icon.RoofUp)
        val clock = rememberWeatherClock()
        Box(Modifier.size(104.dp, 35.dp)) {
            // Phosphor glow: blurred copies under the line art and the animated weather.
            val glowLayer = Modifier.fillMaxSize().blur(4.dp, BlurredEdgeTreatment.Unbounded)
            Image(car, null, glowLayer, alpha = 0.7f)
            RoofWeatherFx(v.fx, v.roofDown, clock, glowLayer, night = night)
            Image(car, null, Modifier.fillMaxSize())
            RoofWeatherFx(v.fx, v.roofDown, clock, Modifier.fillMaxSize(), night = night)
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(7.dp)) {
            // Title on its own line; the score sits at the end of its bar, like a meter reading.
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom,
            ) {
                BasicText(
                    "TOP-DOWN SCORE",
                    Modifier.weight(1f, fill = false),
                    style = glow(Vfd.Teal.copy(alpha = 0.75f), 18.sp, 1.sp, blur = 4f),
                    maxLines = 1,
                    softWrap = false,
                )
                BasicText(
                    "${v.temp}°",
                    Modifier.padding(start = 6.dp),
                    style = glow(Vfd.Teal, 26.sp, blur = 8f).copy(lineHeight = 1.em),
                    maxLines = 1,
                    softWrap = false,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) {
                    SegmentBar(total = 10, lit = v.score, height = 14.dp, ghost = Vfd.Teal.copy(alpha = 0.12f)) { color }
                }
                BasicText(
                    "${v.score}/10",
                    style = glow(color, 20.sp, blur = 6f).copy(lineHeight = 1.em),
                    maxLines = 1,
                    softWrap = false,
                )
            }
            // With a second line ("RAIN ENDS 18:30") the line alternates every 3 s, like a VFD
            // cycling its text.
            var showDetail by remember(v.verdict, v.detail) { mutableStateOf(false) }
            LaunchedEffect(v.verdict, v.detail) {
                while (v.detail != null) {
                    delay(3_000)
                    showDetail = !showDetail
                }
            }
            BasicText(
                if (showDetail && v.detail != null) v.detail else v.verdict,
                style = glow(Vfd.Amber, 18.sp, 1.sp, blur = 6f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
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
                { actions.onSlot(Slot.SETTINGS) }, null) // always the launcher's own settings
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
