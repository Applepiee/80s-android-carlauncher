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
import android.app.role.RoleManager
import android.os.Build
import com.retro.vfdlauncher.data.night.NightMode
import com.retro.vfdlauncher.data.netradio.NetRadio
import com.retro.vfdlauncher.data.netradio.NetStatus
import com.retro.vfdlauncher.data.netradio.RadioCountries
import com.retro.vfdlauncher.data.netradio.RadioGenres
import com.retro.vfdlauncher.data.netradio.RadioStation
import com.retro.vfdlauncher.data.phone.Favorite
import com.retro.vfdlauncher.data.phone.PhoneCalls
import com.retro.vfdlauncher.data.phone.PhoneRepository
import com.retro.vfdlauncher.data.phone.RecentCall
import com.retro.vfdlauncher.data.update.UpdateState
import com.retro.vfdlauncher.ui.picker.LauncherStatus
import com.retro.vfdlauncher.data.update.Updater
import com.retro.vfdlauncher.data.audio.VolumeRepository
import com.retro.vfdlauncher.data.media.MediaRepository
import com.retro.vfdlauncher.data.media.NowPlaying
import com.retro.vfdlauncher.data.media.Playlist
import com.retro.vfdlauncher.data.media.PlaylistRepository
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
    private val playlistRepo = PlaylistRepository(app)
    val appRepo = AppRepository(app)
    /** Real spectrum for the analysers; started/stopped with the activity. */
    val spectrum = AudioSpectrum(app)
    /** Over-the-air updates from the public releases repo. */
    val updater = Updater(app).also { Updater.instance = it }
    val updateState = updater.state
    private val prefs = app.getSharedPreferences("home", Context.MODE_PRIVATE)

    val weather = weatherRepo.weather
    val volume = volumeRepo.volume
    val hasMediaAccess = mediaRepo.hasAccess
    val presets = presetRepo.presets
    val playlists = playlistRepo.playlists
    val currentPlaylist = playlistRepo.current

    private val _playlistsOpen = MutableStateFlow(false)
    val playlistsOpen: StateFlow<Boolean> = _playlistsOpen.asStateFlow()

    /** Playlist key being edited, or null. */
    private val _playlistEditor = MutableStateFlow<Int?>(null)
    val playlistEditor: StateFlow<Int?> = _playlistEditor.asStateFlow()
    val nextTurn = NavigationState.nextTurn

    private val _source = MutableStateFlow(
        runCatching { Source.valueOf(prefs.getString("source", null) ?: "") }.getOrDefault(Source.RADIO),
    )
    val source: StateFlow<Source> = _source.asStateFlow()

    /** Packages the RADIO and MUSIC sources resolve to (assigned or auto-detected). */
    private val sourcePackages = MutableStateFlow<Map<Source, String?>>(emptyMap())

    private val ownPkg = app.packageName

    val radio: StateFlow<NowPlaying?> = combine(mediaRepo.sessions, sourcePackages) { all, pkgs ->
        // Our own session is the internet radio; it is shown from NetRadio, not as a media app.
        val list = all.filter { it.packageName != ownPkg }
        list.firstOrNull { it.packageName == pkgs[Source.RADIO] }
            ?: list.filter { it.isRadio }.let { r -> r.firstOrNull { it.isPlaying } ?: r.firstOrNull() }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    val music: StateFlow<NowPlaying?> = combine(mediaRepo.sessions, sourcePackages) { all, pkgs ->
        val list = all.filter { it.packageName != ownPkg }
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

    val night = NightMode.state

    // ---- Internet radio (canvas page "Internet radio") ----
    private val _netPicker = MutableStateFlow<com.retro.vfdlauncher.ui.home.StationPicker?>(null)
    private var searchJob: Job? = null

    /** RADIO tab as internet radio, or null when it uses the head unit's radio app. */
    val netUi: StateFlow<NetRadioUi?> = combine(NetRadio.enabled, NetRadio.state, NetRadio.presets, _netPicker, NetRadio.online) { on, st, pr, pk, online ->
        if (on) NetRadioUi(st, pr, pk, online) else null
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    private fun netOn() = NetRadio.enabled.value

    fun netPreset(i: Int) {
        if (NetRadio.presets.value.getOrNull(i) != null) {
            mediaRepo.pauseOthers(ownPkg)
            NetRadio.playPreset(getApplication(), i)
        }
    }

    fun netPresetSave(i: Int) {
        NetRadio.savePreset(getApplication(), i)
    }

    fun netPlayKey() {
        val app = getApplication<Application>()
        when (NetRadio.state.value.status) {
            NetStatus.PLAYING, NetStatus.BUFFERING -> NetRadio.stop(app)
            else -> if (NetRadio.state.value.station == null) openStations() else {
                mediaRepo.pauseOthers(ownPkg)
                NetRadio.resume(app)
            }
        }
    }

    fun netStep(delta: Int) {
        mediaRepo.pauseOthers(ownPkg)
        NetRadio.step(getApplication(), delta)
    }

    fun openStations() {
        if (_netPicker.value == null) _netPicker.value = com.retro.vfdlauncher.ui.home.StationPicker()
        searchStations(0)
    }

    fun closeStations() {
        searchJob?.cancel()
        _netPicker.value = null
    }

    fun stationQuery(q: String) {
        _netPicker.value = _netPicker.value?.copy(query = q) ?: return
        searchStations(400)
    }

    fun stationCountry() {
        val p = _netPicker.value ?: return
        _netPicker.value = p.copy(countryIndex = (p.countryIndex + 1) % RadioCountries.list.size)
        searchStations(0)
    }

    fun stationGenre(i: Int) {
        _netPicker.value = _netPicker.value?.copy(genreIndex = i) ?: return
        searchStations(0)
    }

    fun pickStation(s: RadioStation) {
        closeStations()
        mediaRepo.pauseOthers(ownPkg)
        setSource(Source.RADIO)
        NetRadio.play(getApplication(), s)
    }

    private fun searchStations(debounceMs: Long) {
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            if (debounceMs > 0) delay(debounceMs)
            val p = _netPicker.value ?: return@launch
            _netPicker.value = p.copy(results = null, error = false)
            val result = runCatching {
                NetRadio.browser.search(p.query, RadioCountries.list[p.countryIndex].code, RadioGenres[p.genreIndex].tag)
            }
            val now = _netPicker.value ?: return@launch
            result.getOrNull()?.let { NetRadio.setBrowseList(it) }
            _netPicker.value = result.fold(
                { now.copy(results = it, error = false) },
                { now.copy(results = emptyList(), error = true) },
            )
        }
    }

    /** RADIO picker: "INTERNET RADIO" goes back to the built-in internet radio. */
    fun useInternetRadio() {
        NetRadio.setEnabled(getApplication(), true)
        _picker.value = null
    }

    // ---- Phone (canvas page "Phone calls") ----
    val phoneRepo = PhoneRepository(app)
    val call = PhoneCalls.call
    val favoriteContacts = phoneRepo.favorites

    private val _phoneOpen = MutableStateFlow(false)
    val phoneOpen: StateFlow<Boolean> = _phoneOpen.asStateFlow()

    private val _recents = MutableStateFlow<List<RecentCall>?>(null)
    val recents: StateFlow<List<RecentCall>?> = _recents.asStateFlow()

    private val _recentsNote = MutableStateFlow<String?>(null)
    val recentsNote: StateFlow<String?> = _recentsNote.asStateFlow()

    /** Favourite key being edited, or null. */
    private val _favoriteEditor = MutableStateFlow<Int?>(null)
    val favoriteEditor: StateFlow<Int?> = _favoriteEditor.asStateFlow()

    fun togglePhone() {
        _phoneOpen.value = !_phoneOpen.value
        if (_phoneOpen.value) loadRecents()
    }

    fun loadRecents() {
        viewModelScope.launch {
            val list = phoneRepo.recentCalls()
            _recents.value = list
            _recentsNote.value = when {
                !phoneRepo.canReadCallLog() -> "ALLOW CALL LOG ACCESS TO SEE RECENT CALLS"
                list.isEmpty() -> "NO CALLS IN ANDROID'S CALL LOG · USE KEYPAD & CONTACTS"
                else -> null
            }
        }
    }

    fun editFavorite(i: Int) {
        _favoriteEditor.value = i
    }

    fun closeFavoriteEditor() {
        _favoriteEditor.value = null
    }

    fun saveFavorite(i: Int, f: Favorite?) {
        phoneRepo.setFavorite(i, f)
        _favoriteEditor.value = null
    }

    init {
        NetRadio.load(app)
        // Night mode: new sunrise/sunset with every forecast, and a re-check every minute.
        viewModelScope.launch {
            weather.collect { w -> if (w != null) NightMode.setSun(getApplication(), w.sun, w.utcOffsetSeconds) }
        }
        viewModelScope.launch {
            while (isActive) {
                NightMode.update(getApplication())
                delay(60_000L - System.currentTimeMillis() % 60_000L + 200L)
            }
        }
        refreshSourcePackages()
        // Follow the audio: when a source starts playing elsewhere (steering wheel, app), switch to it.
        viewModelScope.launch {
            fun radioOn(r: NowPlaying?, n: NetRadioUi?) =
                if (n != null) n.state.status == NetStatus.PLAYING || n.state.status == NetStatus.BUFFERING else r?.isPlaying == true
            var radioWasPlaying = radioOn(radio.value, netUi.value)
            var musicWasPlaying = music.value?.isPlaying == true
            combine(radio, music, netUi) { r, m, n -> radioOn(r, n) to (m?.isPlaying == true) }.collect { (r, m) ->
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
        refreshStatus()
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

    /** Quiet background check (at most every 6 h); the drawer shows the result. */
    fun checkForUpdateQuietly() {
        viewModelScope.launch { updater.check(force = false) }
    }

    /**
     * The drawer's UPDATE key. Returns an intent to open when Android first needs
     * "install unknown apps" allowed for the launcher.
     */
    fun onUpdateKey(): Intent? {
        when (val st = updater.state.value) {
            is UpdateState.Available -> viewModelScope.launch { updater.install(st.release) }
            is UpdateState.NeedsPermission -> {
                val r = updater.pending
                if (getApplication<Application>().packageManager.canRequestPackageInstalls() && r != null) {
                    viewModelScope.launch { updater.install(r) }
                } else {
                    return updater.permissionIntent()
                }
            }
            is UpdateState.Downloading, UpdateState.Installing, UpdateState.Checking -> Unit
            else -> viewModelScope.launch { updater.check(force = true) }
        }
        return null
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

    // ---- Launcher settings (the SETTINGS key) ----
    private val _settingsOpen = MutableStateFlow(false)
    val settingsOpen: StateFlow<Boolean> = _settingsOpen.asStateFlow()

    private val _status = MutableStateFlow(LauncherStatus())
    val status: StateFlow<LauncherStatus> = _status.asStateFlow()

    fun openSettings() {
        refreshStatus()
        _settingsOpen.value = true
    }

    fun toggleNight() = NightMode.toggle(getApplication())

    /** Settings DAY NOW / NIGHT NOW (pressing the forced one again returns to AUTO). */
    fun forceNight(night: Boolean) = NightMode.force(getApplication(), night)

    fun toggleCrt() {
        val app = getApplication<Application>()
        com.retro.vfdlauncher.overlay.OverlayBus.loadCrtEnabled(app)
        com.retro.vfdlauncher.overlay.OverlayBus.setCrtEnabled(app, !com.retro.vfdlauncher.overlay.OverlayBus.crtEnabled.value)
        refreshStatus()
    }

    fun changeNightDim(delta: Float) {
        val app = getApplication<Application>()
        NightMode.setDimLevel(app, ((NightMode.dimLevel(app) + delta) * 10).let { Math.round(it) / 10f })
        refreshStatus()
    }

    fun closeSettings() {
        _settingsOpen.value = false
    }

    /** Re-read what Android has granted; called on resume (the user may come back from Settings). */
    fun refreshStatus() {
        val app = getApplication<Application>()
        NightMode.update(app)
        _status.value = LauncherStatus(
            backlight = NightMode.canChangeBacklight(app),
            location = app.checkSelfPermission(android.Manifest.permission.ACCESS_COARSE_LOCATION) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED,
            crt = com.retro.vfdlauncher.overlay.OverlayBus.crtEnabled.value,
            nightDim = NightMode.dimLevel(app),
            isDefaultHome = isDefaultHome(app),
            mediaAccess = mediaRepo.checkAccess(),
            overlay = android.provider.Settings.canDrawOverlays(app),
            version = updater.currentVersion,
        )
    }

    private fun isDefaultHome(app: Application): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val rm = app.getSystemService(RoleManager::class.java)
            if (rm != null && rm.isRoleAvailable(RoleManager.ROLE_HOME)) return rm.isRoleHeld(RoleManager.ROLE_HOME)
        }
        val home = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val resolved = app.packageManager.resolveActivity(home, android.content.pm.PackageManager.MATCH_DEFAULT_ONLY)
        return resolved?.activityInfo?.packageName == app.packageName
    }

    /** The system's "make this the home app" request, or null where it doesn't exist. */
    fun requestHomeRoleIntent(): Intent? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        val rm = getApplication<Application>().getSystemService(RoleManager::class.java) ?: return null
        return if (rm.isRoleAvailable(RoleManager.ROLE_HOME)) rm.createRequestRoleIntent(RoleManager.ROLE_HOME) else null
    }

    private fun setSource(s: Source) {
        _source.value = s
        prefs.edit().putString("source", s.name).apply()
    }

    /**
     * Source switch: start that source's session and pause the other. Returns an intent to open
     * the source's app when it has no session yet (e.g. radio app not started since boot).
     */
    suspend fun selectSource(s: Source): Intent? {
        val fromPhone = _phoneOpen.value
        _phoneOpen.value = false
        setSource(s)
        if (s == Source.RADIO && netOn()) {
            if (!fromPhone) {
                mediaRepo.pauseOthers(ownPkg)
                val st = NetRadio.state.value.status
                if (st != NetStatus.PLAYING && st != NetStatus.BUFFERING) {
                    if (NetRadio.state.value.station == null) openStations() else NetRadio.resume(getApplication())
                }
            }
            return null
        }
        val np = if (s == Source.RADIO) radio.value else music.value
        return when (SourceTap.decide(fromPhone, np != null)) {
            SourceTap.SHOW_ONLY -> null
            SourceTap.PLAY -> if (np != null && mediaRepo.playOnly(np.packageName)) null
                else appRepo.intentFor(if (s == Source.RADIO) Slot.RADIO else Slot.MUSIC)
            SourceTap.OPEN_APP -> appRepo.intentFor(if (s == Source.RADIO) Slot.RADIO else Slot.MUSIC)
        }
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

    /** Scrub bar: seek the music source to [positionMs]. */
    fun seekMusic(positionMs: Long) = mediaRepo.seekToPosition(music.value?.packageName, positionMs)

    // ---- Playlists ----
    fun openPlaylists() {
        _playlistsOpen.value = true
    }

    fun closePlaylists() {
        _playlistsOpen.value = false
    }

    fun editPlaylist(index: Int) {
        _playlistEditor.value = index
    }

    fun closePlaylistEditor() {
        _playlistEditor.value = null
    }

    fun savePlaylist(index: Int, p: Playlist?) {
        playlistRepo.set(index, p)
        _playlistEditor.value = null
    }

    /** The playlist to start for a key (and remembers it as playing), or null for an empty key. */
    fun startPlaylist(index: Int): Playlist? {
        val p = playlists.value.getOrNull(index) ?: return null
        playlistRepo.markPlaying(index)
        _playlistsOpen.value = false
        setSource(Source.MUSIC)
        return p
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
        // Choosing an app (or AUTO) for RADIO switches the tab to the head unit's own radio.
        if (slot == Slot.RADIO) NetRadio.setEnabled(getApplication(), false)
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

/**
 * What a RADIO / YT MUSIC tab tap does. Leaving the PHONE view only switches the view (first tap,
 * whatever is playing); otherwise play that source, or open its app when it has no session.
 */
enum class SourceTap {
    SHOW_ONLY, PLAY, OPEN_APP;

    companion object {
        fun decide(fromPhone: Boolean, hasSession: Boolean): SourceTap = when {
            fromPhone -> SHOW_ONLY
            hasSession -> PLAY
            else -> OPEN_APP
        }
    }
}
