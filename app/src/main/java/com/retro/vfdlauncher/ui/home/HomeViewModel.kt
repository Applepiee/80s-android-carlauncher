package com.retro.vfdlauncher.ui.home

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.retro.vfdlauncher.data.apps.AppEntry
import com.retro.vfdlauncher.data.apps.AppRepository
import com.retro.vfdlauncher.data.apps.Slot
import com.retro.vfdlauncher.data.audio.AudioSpectrum
import com.retro.vfdlauncher.data.audio.VolumeRepository
import com.retro.vfdlauncher.data.media.MediaRepository
import com.retro.vfdlauncher.data.media.NowPlaying
import com.retro.vfdlauncher.data.media.PresetRepository
import com.retro.vfdlauncher.data.nav.NavigationState
import com.retro.vfdlauncher.data.weather.WeatherRepository
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

enum class Source { RADIO, MUSIC }

data class PickerState(val slot: Slot, val showHidden: Boolean, val entries: List<AppEntry>?)

/** Shuffle/repeat/like as last set from this screen (sessions don't report them to non-AndroidX controllers). */
data class MusicToggles(val shuffle: Boolean = false, val repeat: Boolean = false, val liked: Boolean = false)

class HomeViewModel(app: Application) : AndroidViewModel(app) {

    val volumeRepo = VolumeRepository(app)
    private val weatherRepo = WeatherRepository(app)
    private val mediaRepo = MediaRepository(app, viewModelScope, volumeRepo)
    private val presetRepo = PresetRepository(app)
    val appRepo = AppRepository(app)
    /** Real spectrum for the analysers; started/stopped with the activity. */
    val spectrum = AudioSpectrum(app)
    private val prefs = app.getSharedPreferences("home", Context.MODE_PRIVATE)

    val weather = weatherRepo.weather
    val volume = volumeRepo.volume
    val hasMediaAccess = mediaRepo.hasAccess
    val presets = presetRepo.presets
    val nextTurn = NavigationState.nextTurn

    private val _source = MutableStateFlow(
        runCatching { Source.valueOf(prefs.getString("source", null) ?: "") }.getOrDefault(Source.RADIO),
    )
    val source: StateFlow<Source> = _source.asStateFlow()

    /** Packages the RADIO and MUSIC sources resolve to (assigned or auto-detected). */
    private val sourcePackages = MutableStateFlow<Map<Source, String?>>(emptyMap())

    val radio: StateFlow<NowPlaying?> = combine(mediaRepo.sessions, sourcePackages) { list, pkgs ->
        list.firstOrNull { it.packageName == pkgs[Source.RADIO] }
            ?: list.filter { it.isRadio }.let { r -> r.firstOrNull { it.isPlaying } ?: r.firstOrNull() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val music: StateFlow<NowPlaying?> = combine(mediaRepo.sessions, sourcePackages) { list, pkgs ->
        val radioPkg = pkgs[Source.RADIO]
        val candidates = list.filter { !it.isRadio && it.packageName != radioPkg }
        candidates.firstOrNull { it.packageName == pkgs[Source.MUSIC] && it.isPlaying }
            ?: candidates.firstOrNull { it.isPlaying }
            ?: candidates.firstOrNull { it.packageName == pkgs[Source.MUSIC] }
            ?: candidates.firstOrNull()
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private val _toggles = MutableStateFlow(MusicToggles())
    val toggles: StateFlow<MusicToggles> = _toggles.asStateFlow()

    private val _picker = MutableStateFlow<PickerState?>(null)
    val picker: StateFlow<PickerState?> = _picker.asStateFlow()

    private val _drawer = MutableStateFlow<List<AppEntry>?>(null)
    val drawer: StateFlow<List<AppEntry>?> = _drawer.asStateFlow()

    /** Pinned apps for the top row of the drawer. */
    private val _favorites = MutableStateFlow(appRepo.favorites())
    val favorites: StateFlow<List<ComponentName>> = _favorites.asStateFlow()

    private val _inspector = MutableStateFlow<String?>(null)
    val inspector: StateFlow<String?> = _inspector.asStateFlow()

    private var weatherJob: Job? = null

    init {
        refreshSourcePackages()
        // Follow the audio: when a source starts playing elsewhere (steering wheel, app), switch to it.
        viewModelScope.launch {
            var radioWasPlaying = radio.value?.isPlaying == true
            var musicWasPlaying = music.value?.isPlaying == true
            combine(radio, music) { r, m -> (r?.isPlaying == true) to (m?.isPlaying == true) }.collect { (r, m) ->
                if (r && !radioWasPlaying) setSource(Source.RADIO)
                else if (m && !musicWasPlaying) setSource(Source.MUSIC)
                radioWasPlaying = r
                musicWasPlaying = m
            }
        }
        // A new track resets LIKE.
        viewModelScope.launch {
            var lastTitle: String? = null
            music.collect { np ->
                if (np?.title != lastTitle) _toggles.value = _toggles.value.copy(liked = false)
                lastTitle = np?.title
            }
        }
    }

    fun onResume() {
        volumeRepo.start()
        mediaRepo.start()
        if (weatherJob?.isActive != true) {
            weatherJob = viewModelScope.launch {
                while (isActive) {
                    weatherRepo.refresh()
                    delay(10 * 60_000L)
                }
            }
        }
    }

    fun onPause() {
        weatherJob?.cancel()
    }

    fun onLocationGranted() {
        viewModelScope.launch { weatherRepo.refresh(maxAgeMs = 0) }
    }

    override fun onCleared() {
        spectrum.stop()
        volumeRepo.stop()
        mediaRepo.stop()
    }

    fun mediaAccessIntent(): Intent = mediaRepo.accessSettingsIntent()

    private fun setSource(s: Source) {
        _source.value = s
        prefs.edit().putString("source", s.name).apply()
    }

    /**
     * Source switch: start that source's session and pause the other. Returns an intent to open
     * the source's app when it has no session yet (e.g. radio app not started since boot).
     */
    suspend fun selectSource(s: Source): Intent? {
        setSource(s)
        val np = if (s == Source.RADIO) radio.value else music.value
        if (np != null && mediaRepo.playOnly(np.packageName)) return null
        return appRepo.intentFor(if (s == Source.RADIO) Slot.RADIO else Slot.MUSIC)
    }

    // ---- Shared ----
    fun volumeUp() = volumeRepo.up()
    fun volumeDown() = volumeRepo.down()
    fun toggleMute() = volumeRepo.toggleMute()

    private fun currentPkg(): String? =
        (if (_source.value == Source.RADIO) radio.value else music.value)?.packageName

    fun playPause() = mediaRepo.playPause(currentPkg())
    fun next() = mediaRepo.next(currentPkg())
    fun previous() = mediaRepo.previous(currentPkg())

    // ---- Radio ----
    fun seek(up: Boolean) {
        val pkg = radio.value?.packageName
        if (up) mediaRepo.next(pkg) else mediaRepo.previous(pkg)
    }

    fun tune(up: Boolean) = mediaRepo.tune(radio.value?.packageName, up)

    fun onPresetTap(index: Int) {
        val np = radio.value
        val stored = presets.value.getOrNull(index)
        when {
            stored != null -> mediaRepo.seekTo(np?.packageName, stored.tuning)
            // The radio app publishes its presets as a queue: jump straight to that entry.
            np != null && index < np.queue.size -> mediaRepo.skipToQueueItem(np.packageName, np.queue[index].id)
            // Empty key on a tuner: store what is playing now.
            np?.tuning != null -> presetRepo.store(index, np.tuning, np.stationName)
        }
    }

    fun onPresetLongPress(index: Int) {
        val np = radio.value
        if (np?.tuning != null) presetRepo.store(index, np.tuning, np.stationName) else presetRepo.clear(index)
    }

    // ---- Music ----
    fun toggleShuffle() {
        val np = music.value ?: return
        val on = !_toggles.value.shuffle
        np.findCustomAction("shuffle")?.let { mediaRepo.sendCustomAction(np.packageName, it.action) }
            ?: mediaRepo.setShuffle(np.packageName, on)
        _toggles.value = _toggles.value.copy(shuffle = on)
    }

    fun toggleRepeat() {
        val np = music.value ?: return
        val on = !_toggles.value.repeat
        np.findCustomAction("repeat")?.let { mediaRepo.sendCustomAction(np.packageName, it.action) }
            ?: mediaRepo.setRepeat(np.packageName, on)
        _toggles.value = _toggles.value.copy(repeat = on)
    }

    fun toggleLike() {
        val np = music.value ?: return
        val action = np.findCustomAction("thumbs_up", "thumb_up", "thumbs up", "like", "favorite", "heart") ?: return
        mediaRepo.sendCustomAction(np.packageName, action.action)
        _toggles.value = _toggles.value.copy(liked = !_toggles.value.liked)
    }

    // ---- Inspector ----
    fun openInspector() {
        _inspector.value = mediaRepo.describeSessions()
    }

    fun closeInspector() {
        _inspector.value = null
    }

    // ---- Apps ----
    suspend fun intentFor(slot: Slot): Intent? = appRepo.intentFor(slot)

    fun openDrawer() {
        _drawer.value = emptyList()
        viewModelScope.launch { _drawer.value = appRepo.allEntries(includeHidden = false, refresh = true) }
    }

    fun toggleFavorite(component: ComponentName) {
        _favorites.value = appRepo.toggleFavorite(component)
    }

    fun closeDrawer() {
        _drawer.value = null
    }

    fun openPicker(slot: Slot, showHidden: Boolean = slot != Slot.MUSIC && slot != Slot.MAPS) {
        _picker.value = PickerState(slot, showHidden, null)
        viewModelScope.launch {
            val entries = appRepo.allEntries(includeHidden = showHidden, refresh = true)
            if (_picker.value?.slot == slot) _picker.value = PickerState(slot, showHidden, entries)
        }
    }

    fun closePicker() {
        _picker.value = null
    }

    fun assign(slot: Slot, component: ComponentName?) {
        appRepo.assign(slot, component)
        _picker.value = null
        refreshSourcePackages()
    }

    private fun refreshSourcePackages() {
        viewModelScope.launch {
            sourcePackages.value = mapOf(
                Source.RADIO to appRepo.packageFor(Slot.RADIO),
                Source.MUSIC to appRepo.packageFor(Slot.MUSIC),
            )
        }
    }
}
