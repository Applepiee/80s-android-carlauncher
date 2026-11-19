package com.retro.vfdlauncher.ui.home

import androidx.compose.runtime.Composable
import androidx.compose.ui.tooling.preview.Preview
import com.retro.vfdlauncher.data.audio.Volume
import com.retro.vfdlauncher.data.media.Band
import com.retro.vfdlauncher.data.media.NowPlaying
import com.retro.vfdlauncher.data.media.Playlist
import com.retro.vfdlauncher.data.media.Preset
import com.retro.vfdlauncher.data.media.Tuning
import com.retro.vfdlauncher.data.nav.NextTurn
import com.retro.vfdlauncher.data.weather.DayForecast
import com.retro.vfdlauncher.data.weather.Weather
import com.retro.vfdlauncher.data.weather.WeatherIcon

/*
 * Previews of the home screen with sample data, matching the canvas artboards.
 * Open this file in Android Studio and pick "Split" or "Design" (top right of the editor)
 * to see them without a phone or emulator.
 */

private val noActions = HomeActions(
    onSource = {}, onSourceLongPress = {}, onSlot = {}, onSlotLongPress = {}, onApps = {},
    onPreset = {}, onPresetLongPress = {}, onSeek = {}, onTune = {}, onMute = {},
    onPrevious = {}, onNext = {}, onPlayPause = {}, onShuffle = {}, onRepeat = {}, onLike = {},
    onVolumeUp = {}, onVolumeDown = {}, onGrantMediaAccess = {}, onInspect = {},
)

private val samplePresets = listOf(
    88.1f to "RETRO FM", 91.7f to "CITY 91", 94.5f to "DRIVE 94",
    101.1f to "NIGHT 101", 104.3f to "COAST 104", 107.6f to "WAVE 107",
).map { (f, n) -> Preset(Tuning(Band.FM, f), n) }

private fun session(
    pkg: String,
    label: String,
    title: String,
    artist: String?,
    radio: Boolean,
    tuning: Tuning? = null,
) = NowPlaying(
    packageName = pkg, appLabel = label, title = title, artist = artist, album = null,
    isPlaying = true, tuning = tuning, isRadio = radio,
    positionMs = 102_000, positionUpdatedAt = 0, speed = 0f, durationMs = 226_000,
    queue = emptyList(), activeQueueId = -1, actions = 0, customActions = emptyList(),
)

private val radio = session("com.atoto.radio", "Radio", "NIGHT 101", null, radio = true, tuning = Tuning(Band.FM, 101.1f))
private val music = session("com.google.android.apps.youtube.music", "YT Music", "Take On Me", "A-ha", radio = false)
private val weather = Weather(12, "CLOUDY", WeatherIcon.PARTLY, listOf(DayForecast("FRI", 13)), fetchedAt = 0)
private val turn = NextTurn("300 m", "Kerkstraat", "15:17", arrow = null, packageName = "com.google.android.apps.maps")

private val samplePlaylists = listOf(
    Playlist("NIGHT DRIVE", "PLsample00001"), Playlist("80S SYNTH", "PLsample00002"),
    Playlist("ROAD TRIP", "PLsample00003"), Playlist("MR2 CLASSICS", "PLsample00004"),
    Playlist("CHILL WAVE", "PLsample00005"), null,
)

private fun state(source: Source, nav: Boolean, playlistsOpen: Boolean = false) = HomeState(
    source = source,
    radio = radio,
    music = music,
    hasMediaAccess = true,
    weather = weather,
    nextTurn = if (nav) turn else null,
    volume = Volume(level = 12, max = 20),
    presets = samplePresets,
    toggles = MusicToggles(),
    playlists = samplePlaylists,
    currentPlaylist = 0,
    playlistsOpen = playlistsOpen,
)

@Preview(name = "4 · Radio, navigating", widthDp = 1280, heightDp = 720)
@Composable
private fun RadioPreview() = HomeScreen(state(Source.RADIO, nav = true), noActions)

@Preview(name = "5 · YT Music", widthDp = 1280, heightDp = 720)
@Composable
private fun MusicPreview() = HomeScreen(state(Source.MUSIC, nav = true), noActions)

@Preview(name = "7 · Radio, not navigating (weather)", widthDp = 1280, heightDp = 720)
@Composable
private fun WeatherPreview() = HomeScreen(state(Source.RADIO, nav = false), noActions)

@Preview(name = "8 · YT Music, playlist picker", widthDp = 1280, heightDp = 720)
@Composable
private fun PlaylistsPreview() = HomeScreen(state(Source.MUSIC, nav = true, playlistsOpen = true), noActions)
