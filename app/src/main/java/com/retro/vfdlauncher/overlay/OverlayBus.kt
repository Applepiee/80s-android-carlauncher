package com.retro.vfdlauncher.overlay

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/** What the launcher tells the overlay service. Same process, so a singleton is enough. */
object OverlayBus {
    private val _launcherVisible = MutableStateFlow(true)
    val launcherVisible: StateFlow<Boolean> = _launcherVisible.asStateFlow()

    /** CRT effect only for apps it was designed for (maps, Android Auto). */
    private val _crtWanted = MutableStateFlow(false)
    val crtWanted: StateFlow<Boolean> = _crtWanted.asStateFlow()

    fun setLauncherVisible(visible: Boolean) {
        _launcherVisible.value = visible
    }

    fun setCrtWanted(wanted: Boolean) {
        _crtWanted.value = wanted
    }
}
