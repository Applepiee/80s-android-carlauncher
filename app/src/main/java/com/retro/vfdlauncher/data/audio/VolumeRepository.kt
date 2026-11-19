package com.retro.vfdlauncher.data.audio

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlin.math.roundToInt

data class Volume(val level: Int, val max: Int, val muted: Boolean = false) {
    /** Position on the 20-segment bar. */
    val segments: Int get() = if (max <= 0) 0 else ((level.toFloat() / max) * SEGMENTS).roundToInt()

    /** Same pseudo-dB scale as the design canvas: 0 dB at full, 2 dB per segment, -inf when muted. */
    val dbLabel: String get() = if (segments == 0) "-∞" else ((segments - SEGMENTS) * 2).toString()

    companion object { const val SEGMENTS = 20 }
}

/** STREAM_MUSIC volume, kept in sync with hardware knobs and the system volume UI. */
class VolumeRepository(context: Context) {

    private val audio = context.getSystemService(AudioManager::class.java)
    private val resolver = context.contentResolver
    private val _volume = MutableStateFlow(read())
    val volume: StateFlow<Volume> = _volume.asStateFlow()

    private val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
        override fun onChange(selfChange: Boolean) {
            _volume.value = read()
        }
    }

    fun start() {
        // Volume changes are written to Settings.System, so an observer catches knob and steering-wheel changes too.
        resolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer)
        _volume.value = read()
    }

    fun stop() = resolver.unregisterContentObserver(observer)

    fun up() = adjust(AudioManager.ADJUST_RAISE)
    fun down() = adjust(AudioManager.ADJUST_LOWER)
    fun toggleMute() = adjust(AudioManager.ADJUST_TOGGLE_MUTE)

    private fun adjust(direction: Int) {
        // No FLAG_SHOW_UI: the VFD bar is the volume UI.
        runCatching { audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0) }
        _volume.value = read()
    }

    private fun read() = Volume(
        level = audio.getStreamVolume(AudioManager.STREAM_MUSIC),
        max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
        muted = audio.isStreamMute(AudioManager.STREAM_MUSIC),
    )

    fun audioManager(): AudioManager = audio
}
