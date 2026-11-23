package com.retro.vfdlauncher.data.audio

import android.content.Context
import android.database.ContentObserver
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.Settings
import android.app.Instrumentation
import android.view.KeyEvent
import java.util.concurrent.Executors
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

/** The system media volume (STREAM_MUSIC), kept in sync with the unit's knob and buttons. */
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

    /**
     * The VOL keys drive the system volume, the same one the head unit's own buttons and knob
     * change. ATOTO-style units keep their real volume in the car-audio MCU and put Android's music
     * stream back to it whenever another app opens, so setting the music stream directly does not
     * stick there (and the music stream may not even show the MCU level). So a VOL key press:
     *  1. presses a real volume key into our own window, which takes the same path as a hardware key;
     *  2. only if Android refuses that, asks Android to adjust the active stream with the system
     *     volume UI (what the volume keys do inside the system).
     * Exactly one of them runs per press, so one press is never counted twice.
     */
    private val main = Handler(Looper.getMainLooper())
    private val keys = Executors.newSingleThreadExecutor()

    private fun press(direction: Int) {
        val before = snapshot()
        if (direction == AudioManager.ADJUST_TOGGLE_MUTE) {
            val err = runCatching { systemAdjust(direction) }.exceptionOrNull()
            report(before, "system volume (SHOW_UI)" + (err?.let { " (${it.javaClass.simpleName})" } ?: ""))
            return
        }
        val code = if (direction == AudioManager.ADJUST_RAISE) KeyEvent.KEYCODE_VOLUME_UP else KeyEvent.KEYCODE_VOLUME_DOWN
        // sendKeyDownUpSync must not run on the main thread, and it waits for our own window (the
        // main thread) to handle the key, so never block on it.
        keys.execute {
            val err = runCatching { Instrumentation().sendKeyDownUpSync(code) }.exceptionOrNull()
            main.post {
                if (err == null) {
                    report(before, "volume key")
                } else {
                    val err2 = runCatching { systemAdjust(direction) }.exceptionOrNull()
                    report(
                        before,
                        "volume key refused (${err.javaClass.simpleName}) → system volume (SHOW_UI)" +
                            (err2?.let { " (${it.javaClass.simpleName})" } ?: ""),
                    )
                }
            }
        }
    }

    private fun systemAdjust(direction: Int) =
        audio.adjustSuggestedStreamVolume(direction, AudioManager.USE_DEFAULT_STREAM_TYPE, AudioManager.FLAG_SHOW_UI)

    /** Inspector line for the last press, read a moment later so the change has landed. */
    private fun report(before: Snapshot, how: String) {
        main.postDelayed({
            val after = snapshot()
            lastTry = "$how · music ${before.music.first} → ${after.music.first}/${after.music.second}" +
                if (after == before) " (unchanged here; the unit may keep its own level)" else ""
            _volume.value = read()
        }, REPORT_DELAY_MS)
        _volume.value = read()
    }

    fun up() = press(AudioManager.ADJUST_RAISE)
    fun down() = press(AudioManager.ADJUST_LOWER)
    fun toggleMute() = press(AudioManager.ADJUST_TOGGLE_MUTE)

    /** Set by the media repository: change the volume through the playing app's session. */
    var sessionAdjust: ((direction: Int) -> Unit)? = null

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
        const val REPORT_DELAY_MS = 450L

        /** Shared by every instance (home screen and inspector), so the inspector shows the last press. */
        @Volatile var lastTry: String = "no volume key pressed yet"

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
