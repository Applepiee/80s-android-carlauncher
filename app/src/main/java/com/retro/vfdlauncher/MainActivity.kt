package com.retro.vfdlauncher

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.pm.PackageManager
import android.os.Bundle
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
import com.retro.vfdlauncher.ui.home.HomeActions
import com.retro.vfdlauncher.ui.home.HomeScreen
import com.retro.vfdlauncher.ui.home.HomeState
import com.retro.vfdlauncher.ui.home.HomeViewModel
import com.retro.vfdlauncher.ui.home.ScaledDesignCanvas
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
            }
        })

        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            locationPermission.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        setContent {
            val nowPlaying by vm.nowPlaying.collectAsStateWithLifecycle()
            val hasAccess by vm.hasMediaAccess.collectAsStateWithLifecycle()
            val weather by vm.weather.collectAsStateWithLifecycle()
            val volume by vm.volume.collectAsStateWithLifecycle()
            val presets by vm.presets.collectAsStateWithLifecycle()
            val labels by vm.slotLabels.collectAsStateWithLifecycle()
            val picker by vm.picker.collectAsStateWithLifecycle()
            val inspector by vm.inspector.collectAsStateWithLifecycle()
            val scope = rememberCoroutineScope()

            val actions = HomeActions(
                onSlot = { slot -> scope.launch { launchSlot(slot) } },
                onSlotLongPress = { vm.openPicker(it) },
                onPreset = vm::onPresetTap,
                onPresetLongPress = vm::onPresetLongPress,
                onPlayPause = vm::playPause,
                onVolumeUp = vm::volumeUp,
                onVolumeDown = vm::volumeDown,
                onGrantMediaAccess = { safeStart(vm.mediaAccessIntent()) },
                onInspect = vm::openInspector,
            )

            Box {
                HomeScreen(HomeState(nowPlaying, hasAccess, weather, volume, presets, labels), actions)
                val p = picker
                val text = inspector
                if (p != null || text != null) {
                    ScaledDesignCanvas {
                        if (p != null) {
                            AppPicker(
                                slot = p.slot,
                                showHidden = p.showHidden,
                                entries = p.entries,
                                onPick = { vm.assign(p.slot, it.component) },
                                onReset = { vm.assign(p.slot, null) },
                                onToggleHidden = { vm.openPicker(p.slot, !p.showHidden) },
                                onDismiss = vm::closePicker,
                            )
                        } else if (text != null) {
                            SessionInspector(text, vm::closeInspector)
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
        vm.onResume()
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
        if (intent == null || !safeStart(intent)) {
            // Nothing found or it failed to start: let the user choose.
            vm.openPicker(slot)
        }
    }

    private fun safeStart(intent: android.content.Intent): Boolean = try {
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
