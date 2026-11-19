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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.retro.vfdlauncher.data.apps.Slot
import com.retro.vfdlauncher.overlay.OverlayBus
import com.retro.vfdlauncher.overlay.OverlayService
import com.retro.vfdlauncher.ui.home.HomeActions
import com.retro.vfdlauncher.ui.home.HomeScreen
import com.retro.vfdlauncher.ui.home.HomeState
import com.retro.vfdlauncher.ui.home.HomeViewModel
import com.retro.vfdlauncher.ui.home.ScaledDesignCanvas
import com.retro.vfdlauncher.ui.home.Source
import com.retro.vfdlauncher.ui.picker.AppDrawer
import com.retro.vfdlauncher.ui.picker.AppPicker
import com.retro.vfdlauncher.ui.picker.SessionInspector
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    private val vm: HomeViewModel by viewModels()

    private val locationPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) vm.onLocationGranted()
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
            }
        })

        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

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
            val scope = rememberCoroutineScope()

            val actions = HomeActions(
                onSource = { s -> scope.launch { vm.selectSource(s)?.let { launchApp(it, crt = false) } } },
                onSourceLongPress = { s -> vm.openPicker(if (s == Source.RADIO) Slot.RADIO else Slot.MUSIC) },
                onSlot = { slot -> scope.launch { launchSlot(slot) } },
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
            )

            Box {
                HomeScreen(
                    HomeState(source, radio, music, hasAccess, weather, nextTurn, volume, presets, toggles),
                    actions,
                )
                val p = picker
                val d = drawer
                val text = inspector
                if (p != null || d != null || text != null) {
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
                                loadIcon = { vm.appRepo.icon(it, 96) },
                                onOpen = {
                                    vm.closeDrawer()
                                    launchApp(vm.appRepo.launchIntent(it.component), crt = false)
                                },
                                onDismiss = vm::closeDrawer,
                            )
                            text != null -> SessionInspector(text, vm::closeInspector)
                        }
                    }
                }
            }
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
        OverlayService.ensureRunning(this)
    }

    override fun onPause() {
        super.onPause()
        vm.onPause()
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
        OverlayBus.setCrtWanted(crt)
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
