package com.retro.vfdlauncher.overlay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the launcher tells the overlay service. Same process, so a singleton is enough. */
object OverlayBus {
    private val _launcherVisible = MutableStateFlow(true)
    val launcherVisible: StateFlow<Boolean> = _launcherVisible.asStateFlow()

    /** Scanlines only over the apps they were designed for (maps, Android Auto). */
    private val _crtWanted = MutableStateFlow(false)
    val crtWanted: StateFlow<Boolean> = _crtWanted.asStateFlow()

    /** Settings > DISPLAY > SCANLINES OVER APPS (saved in prefs "display"). */
    private val _crtEnabled = MutableStateFlow(true)
    val crtEnabled: StateFlow<Boolean> = _crtEnabled.asStateFlow()

    fun loadCrtEnabled(context: android.content.Context) {
        _crtEnabled.value = context.getSharedPreferences("display", android.content.Context.MODE_PRIVATE).getBoolean("crt", true)
    }

    fun setCrtEnabled(context: android.content.Context, on: Boolean) {
        context.getSharedPreferences("display", android.content.Context.MODE_PRIVATE).edit().putBoolean("crt", on).apply()
        _crtEnabled.value = on
    }

    fun setLauncherVisible(visible: Boolean) {
        _launcherVisible.value = visible
    }

    fun setCrtWanted(wanted: Boolean) {
        _crtWanted.value = wanted
    }
}
