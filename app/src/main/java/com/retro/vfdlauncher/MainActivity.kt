package com.retro.vfdlauncher

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retro.vfdlauncher.data.apps.Slot
import com.retro.vfdlauncher.data.media.PlaylistRepository
import com.retro.vfdlauncher.data.nav.NavigationState
import com.retro.vfdlauncher.overlay.OverlayBus
import com.retro.vfdlauncher.overlay.OverlayService
import com.retro.vfdlauncher.ui.home.HomeActions
import com.retro.vfdlauncher.ui.home.HomeScreen
import com.retro.vfdlauncher.ui.home.HomeState
import com.retro.vfdlauncher.ui.home.HomeViewModel
import com.retro.vfdlauncher.ui.home.LocalSpectrumLevels
import com.retro.vfdlauncher.ui.home.ScaledDesignCanvas
import com.retro.vfdlauncher.ui.home.Source
import com.retro.vfdlauncher.ui.picker.AppDrawer
import com.retro.vfdlauncher.ui.picker.AppPicker
import com.retro.vfdlauncher.ui.picker.PlaylistEditor
import com.retro.vfdlauncher.ui.picker.SessionInspector
import com.retro.vfdlauncher.ui.picker.LauncherSettings
import com.retro.vfdlauncher.ui.picker.SettingsActions
import com.retro.vfdlauncher.ui.picker.updateLabel
import com.retro.vfdlauncher.overlay.RailAccessibilityService
import com.retro.vfdlauncher.data.night.NightMode
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxSize
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: HomeViewModel by viewModels()

    /** Location (weather) and microphone access (the real spectrum analyser), asked together at first start. */
    private val permissions =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { granted ->
            if (granted[Manifest.permission.ACCESS_COARSE_LOCATION] == true) vm.onLocationGranted()
            if (granted[Manifest.permission.RECORD_AUDIO] == true) vm.spectrum.start()
        }

    /**
     * "Make default launcher": Android's own home-app request. If it is declined or missing
     * (some head units), fall back to the Default apps > Home app screen.
     */
    private val homeRole =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            vm.refreshStatus()
            if (!vm.status.value.isDefaultHome) safeStart(Intent(Settings.ACTION_HOME_SETTINGS))
        }

    private fun makeDefaultLauncher() {
        val request = vm.requestHomeRoleIntent()
        if (request == null || runCatching { homeRole.launch(request) }.isFailure) {
            safeStart(Intent(Settings.ACTION_HOME_SETTINGS))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()

        // A home screen has nowhere to go back to; Back only closes our own overlays.
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                vm.closePicker()
                vm.closeInspector()
                vm.closeDrawer()
                vm.closeSettings()
                vm.closePlaylistEditor()
                vm.closePlaylists()
            }
        })

        val missing = listOf(Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.RECORD_AUDIO)
            .filter { checkSelfPermission(it) != PackageManager.PERMISSION_GRANTED }
        if (missing.isNotEmpty()) permissions.launch(missing.toTypedArray())

        setContent {
            val source by vm.source.collectAsStateWithLifecycle()
            val radio by vm.radio.collectAsStateWithLifecycle()
            val music by vm.music.collectAsStateWithLifecycle()
            val hasAccess by vm.hasMediaAccess.collectAsStateWithLifecycle()
            val weather by vm.weather.collectAsStateWithLifecycle()
            val nextTurn by vm.nextTurn.collectAsStateWithLifecycle()
            val volume by vm.volume.collectAsStateWithLifecycle()
            val presets by vm.presets.collectAsStateWithLifecycle()
            val toggles by vm.toggles.collectAsStateWithLifecycle()
            val picker by vm.picker.collectAsStateWithLifecycle()
            val drawer by vm.drawer.collectAsStateWithLifecycle()
            val inspector by vm.inspector.collectAsStateWithLifecycle()
            val favorites by vm.favorites.collectAsStateWithLifecycle()
            val update by vm.updateState.collectAsStateWithLifecycle()
            val settingsOpen by vm.settingsOpen.collectAsStateWithLifecycle()
            val night by vm.night.collectAsStateWithLifecycle()
            val status by vm.status.collectAsStateWithLifecycle()
            val playlists by vm.playlists.collectAsStateWithLifecycle()
            val currentPlaylist by vm.currentPlaylist.collectAsStateWithLifecycle()
            val playlistsOpen by vm.playlistsOpen.collectAsStateWithLifecycle()
            val playlistEditor by vm.playlistEditor.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()

            val actions = HomeActions(
                onSource = { s -> scope.launch { vm.selectSource(s)?.let { launchApp(it, crt = false) } } },
                onSourceLongPress = { s -> vm.openPicker(if (s == Source.RADIO) Slot.RADIO else Slot.MUSIC) },
                // SETTINGS opens the launcher's own settings (with a way through to Android's).
                onSlot = { slot -> if (slot == Slot.SETTINGS) vm.openSettings() else scope.launch { launchSlot(slot) } },
                onSlotLongPress = { vm.openPicker(it) },
                onApps = vm::openDrawer,
                onPreset = vm::onPresetTap,
                onPresetLongPress = vm::onPresetLongPress,
                onSeek = vm::seek,
                onTune = vm::tune,
                onMute = vm::toggleMute,
                onPrevious = vm::previous,
                onNext = vm::next,
                onPlayPause = vm::playPause,
                onShuffle = vm::toggleShuffle,
                onRepeat = vm::toggleRepeat,
                onLike = vm::toggleLike,
                onVolumeUp = vm::volumeUp,
                onVolumeDown = vm::volumeDown,
                onGrantMediaAccess = { safeStart(vm.mediaAccessIntent()) },
                onInspect = vm::openInspector,
                onSeekTo = vm::seekMusic,
                onPlaylists = vm::openPlaylists,
                onPlaylistsClose = vm::closePlaylists,
                onPlaylist = { i ->
                    val p = vm.startPlaylist(i)
                    if (p == null) {
                        vm.editPlaylist(i)
                    } else if (!launchApp(PlaylistRepository.openIntent(p), crt = false)) {
                        // YouTube Music not installed: let any app (browser, YouTube) open the link.
                        launchApp(PlaylistRepository.openIntent(p, inYtMusic = false), crt = false)
                    }
                },
                onPlaylistEdit = vm::editPlaylist,
                onOpenMusicApp = { scope.launch { launchSlot(Slot.MUSIC) } },
                onNight = vm::toggleNight,
            )

            CompositionLocalProvider(LocalSpectrumLevels provides vm.spectrum.levels) { Box {
                HomeScreen(
                    HomeState(
                        source, radio, music, hasAccess, weather, nextTurn, volume, presets, toggles,
                        playlists = playlists,
                        currentPlaylist = currentPlaylist,
                        playlistsOpen = playlistsOpen,
                        night = night.isNight,
                    ),
                    actions,
                )
                val p = picker
                val d = drawer
                val text = inspector
                val edit = playlistEditor
                if (p != null || d != null || text != null || edit != null || settingsOpen) {
                    ScaledDesignCanvas {
                        when {
                            p != null -> AppPicker(
                                slot = p.slot,
                                showHidden = p.showHidden,
                                entries = p.entries,
                                onPick = { vm.assign(p.slot, it.component) },
                                onReset = { vm.assign(p.slot, null) },
                                onToggleHidden = { vm.openPicker(p.slot, !p.showHidden) },
                                onDismiss = vm::closePicker,
                            )
                            d != null -> AppDrawer(
                                entries = d,
                                favorites = favorites,
                                update = update,
                                currentVersion = vm.updater.currentVersion,
                                onUpdate = { vm.onUpdateKey()?.let { safeStart(it) } },
                                onToggleFavorite = { vm.toggleFavorite(it.component) },
                                loadIcon = { vm.appRepo.icon(it, 96) },
                                onOpen = {
                                    vm.closeDrawer()
                                    launchApp(vm.appRepo.launchIntent(it.component), crt = false)
                                },
                                onDismiss = vm::closeDrawer,
                            )
                            text != null -> SessionInspector(text, vm::closeInspector)
                            settingsOpen -> LauncherSettings(
                                status = status,
                                night = night,
                                updateLabel = updateLabel(update, vm.updater.currentVersion).first,
                                a = SettingsActions(
                                    onMakeDefault = ::makeDefaultLauncher,
                                    onMediaAccess = { safeStart(vm.mediaAccessIntent()) },
                                    onOverlay = {
                                        safeStart(
                                            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")),
                                        )
                                    },
                                    onBackButton = { safeStart(RailAccessibilityService.settingsIntent()) },
                                    onResetRail = { OverlayService.resetRailPosition(this@MainActivity) },
                                    onBacklight = { safeStart(NightMode.backlightPermissionIntent(this@MainActivity)) },
                                    onNightDim = vm::changeNightDim,
                                    onNightToggle = vm::toggleNight,
                                    onUpdate = { vm.onUpdateKey()?.let { safeStart(it) } },
                                    onAndroidSettings = { safeStart(Intent(Settings.ACTION_SETTINGS)) },
                                    onDismiss = vm::closeSettings,
                                ),
                            )
                            edit != null -> PlaylistEditor(
                                index = edit,
                                current = playlists.getOrNull(edit),
                                onSave = { vm.savePlaylist(edit, it) },
                                onClear = { vm.savePlaylist(edit, null) },
                                onDismiss = vm::closePlaylistEditor,
                            )
                        }
                    }
                }
                if (night.dim > 0f) {
                    androidx.compose.foundation.layout.Box(
                        androidx.compose.ui.Modifier
                            .fillMaxSize()
                            .background(androidx.compose.ui.graphics.Color.Black.copy(alpha = night.dim)),
                    )
                }
            } }
        }
    }

    override fun onStart() {
        super.onStart()
        OverlayBus.setLauncherVisible(true)
    }

    override fun onStop() {
        super.onStop()
        OverlayBus.setLauncherVisible(false)
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        vm.onResume()
        vm.spectrum.start()
        vm.checkForUpdateQuietly()
        OverlayService.ensureRunning(this)
    }

    override fun onPause() {
        super.onPause()
        vm.onPause()
        vm.spectrum.stop()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    private suspend fun launchSlot(slot: Slot) {
        val intent = vm.intentFor(slot)
        val crt = slot == Slot.MAPS || slot == Slot.ANDROID_AUTO
        if (intent == null || !launchApp(intent, crt)) {
            // Nothing found or it failed to start: let the user choose.
            vm.openPicker(slot)
        }
    }

    /**
     * Opens another app with the floating VFD bar over it (and the CRT effect for maps and
     * Android Auto). The first time, asks for "display over other apps" instead.
     */
    private fun launchApp(intent: Intent, crt: Boolean): Boolean {
        if (!Settings.canDrawOverlays(this) && askOverlayOnce()) return true
        // CRT over navigation apps however they were opened (MAPS key, drawer, favourites).
        val pkg = intent.component?.packageName ?: intent.`package`
        OverlayBus.setCrtWanted(crt || pkg in NavigationState.NAV_PACKAGES)
        OverlayService.ensureRunning(this)
        return safeStart(intent)
    }

    private fun askOverlayOnce(): Boolean {
        val prefs = getSharedPreferences("home", Context.MODE_PRIVATE)
        if (prefs.getBoolean("overlayAsked", false)) return false
        prefs.edit().putBoolean("overlayAsked", true).apply()
        return safeStart(
            Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
    }

    private fun safeStart(intent: Intent): Boolean = try {
        startActivity(intent)
        true
    } catch (e: ActivityNotFoundException) {
        false
    } catch (e: SecurityException) {
        false
    }

    private fun hideSystemBars() {
        WindowCompat.getInsetsController(window, window.decorView).apply {
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }
    }
}
