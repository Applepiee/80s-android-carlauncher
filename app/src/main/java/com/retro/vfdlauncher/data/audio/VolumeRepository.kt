package com.retro.vfdlauncher.data.audio

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.view.KeyEvent
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

    /**
     * Some head units (ATOTO and friends) ignore a plain STREAM_MUSIC change because their volume
     * lives in the car-audio MCU or on another stream. Try the phone way first (no system UI, the VFD
     * bar is the volume UI); if the index doesn't move, let Android route it to the active stream
     * with the unit's own volume UI, then fall back to volume key events and the playing session.
     */
    private fun adjust(direction: Int) {
        val before = snapshot()
        val atLimit = (direction == AudioManager.ADJUST_RAISE && before.music.first >= before.music.second) ||
            (direction == AudioManager.ADJUST_LOWER && before.music.first <= 0)
        val tried = mutableListOf<String>()
        fun attempt(name: String, block: () -> Unit): Boolean {
            tried += name
            val ok = runCatching(block).isSuccess
            if (!ok) tried[tried.lastIndex] = "$name (error)"
            return snapshot() != before
        }
        val changed = attempt("adjustStreamVolume(MUSIC)") {
            audio.adjustStreamVolume(AudioManager.STREAM_MUSIC, direction, 0)
        } || atLimit ||
            attempt("adjustSuggestedStreamVolume(SHOW_UI)") {
                audio.adjustSuggestedStreamVolume(direction, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI)
            } ||
            (direction != AudioManager.ADJUST_TOGGLE_MUTE && attempt("volume key event") {
                val code = if (direction == AudioManager.ADJUST_RAISE) KeyEvent.KEYCODE_VOLUME_UP else KeyEvent.KEYCODE_VOLUME_DOWN
                audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
                audio.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
            }) ||
            (sessionAdjust != null && attempt("media session adjustVolume") { sessionAdjust?.invoke(direction) })
        val after = snapshot()
        lastTry = buildString {
            append(tried.joinToString(" → "))
            append(if (atLimit) " · already at the limit" else if (changed) " · changed" else " · NOTHING CHANGED")
            append(" · music ${before.music.first} → ${after.music.first}")
        }
        _volume.value = read()
    }

    /** Set by the media repository: change the volume through the playing app's session. */
    var sessionAdjust: ((direction: Int) -> Unit)? = null

    private var lastTry: String = "no volume key pressed yet"

    private data class Snapshot(val music: Pair<Int, Int>, val all: List<Int>)

    private fun snapshot() = Snapshot(
        audio.getStreamVolume(AudioManager.STREAM_MUSIC) to audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
        STREAMS.map { (_, s) -> runCatching { audio.getStreamVolume(s) }.getOrDefault(-1) },
    )

    /** VOLUME section of the session inspector: every stream, and what the last key press did. */
    fun describe(): String = buildString {
        appendLine("VOLUME")
        appendLine("  fixed=${audio.isVolumeFixed} mode=${audio.mode}")
        STREAMS.forEach { (name, s) ->
            val v = runCatching { audio.getStreamVolume(s) }.getOrDefault(-1)
            val m = runCatching { audio.getStreamMaxVolume(s) }.getOrDefault(-1)
            val mute = runCatching { audio.isStreamMute(s) }.getOrDefault(false)
            appendLine("  $name $v/$m${if (mute) " muted" else ""}")
        }
        appendLine("  last: $lastTry")
    }

    private fun read() = Volume(
        level = audio.getStreamVolume(AudioManager.STREAM_MUSIC),
        max = audio.getStreamMaxVolume(AudioManager.STREAM_MUSIC),
        muted = audio.isStreamMute(AudioManager.STREAM_MUSIC),
    )

    fun audioManager(): AudioManager = audio

    private companion object {
        val STREAMS = listOf(
            "MUSIC" to AudioManager.STREAM_MUSIC,
            "SYSTEM" to AudioManager.STREAM_SYSTEM,
            "RING" to AudioManager.STREAM_RING,
            "NOTIFICATION" to AudioManager.STREAM_NOTIFICATION,
            "ALARM" to AudioManager.STREAM_ALARM,
            "VOICE_CALL" to AudioManager.STREAM_VOICE_CALL,
            "ACCESSIBILITY" to AudioManager.STREAM_ACCESSIBILITY,
        )
    }
}
