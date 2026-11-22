package com.retro.vfdlauncher.data.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSession
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.view.KeyEvent
import androidx.core.app.NotificationManagerCompat
import com.retro.vfdlauncher.data.audio.VolumeRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Mirrors every app that has a media session (ATOTO radio, YT Music, ...) and sends controls
 * back to the one you pick by package name. Needs notification-listener access.
 */
class MediaRepository(
    private val context: Context,
    private val scope: CoroutineScope,
    private val volume: VolumeRepository,
) {
    private val sessionManager = context.getSystemService(MediaSessionManager::class.java)
    private val listener = ComponentName(context, MediaListenerService::class.java)
    private val main = Handler(Looper.getMainLooper())

    private val _hasAccess = MutableStateFlow(checkAccess())
    val hasAccess: StateFlow<Boolean> = _hasAccess.asStateFlow()

    /** All sessions, in the system's priority order. */
    private val _sessions = MutableStateFlow<List<NowPlaying>>(emptyList())
    val sessions: StateFlow<List<NowPlaying>> = _sessions.asStateFlow()

    private var controllers: List<MediaController> = emptyList()
    private var started = false
    private var seekJob: Job? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        attach(list.orEmpty())
    }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = refresh()
        override fun onMetadataChanged(metadata: MediaMetadata?) = refresh()
        override fun onQueueChanged(queue: MutableList<MediaSession.QueueItem>?) = refresh()
        override fun onSessionDestroyed() = refresh()
    }

    fun checkAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** Call from onResume: access may have been granted in Settings meanwhile. */
    fun start() {
        _hasAccess.value = checkAccess()
        if (started) {
            refresh()
            return
        }
        if (!_hasAccess.value) return
        try {
            sessionManager.addOnActiveSessionsChangedListener(sessionsListener, listener, main)
            attach(sessionManager.getActiveSessions(listener))
            started = true
        } catch (e: SecurityException) {
            _hasAccess.value = false
        }
    }

    fun stop() {
        if (!started) return
        runCatching { sessionManager.removeOnActiveSessionsChangedListener(sessionsListener) }
        controllers.forEach { runCatching { it.unregisterCallback(callback) } }
        controllers = emptyList()
        started = false
    }

    fun accessSettingsIntent(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun attach(list: List<MediaController>) {
        controllers.forEach { runCatching { it.unregisterCallback(callback) } }
        controllers = list
        list.forEach { it.registerCallback(callback, main) }
        refresh()
    }

    private fun refresh() {
        _sessions.value = controllers.map { snapshot(it) }
    }

    private fun controller(pkg: String?): MediaController? =
        if (pkg == null) null else controllers.firstOrNull { it.packageName == pkg }

    private fun snapshot(c: MediaController): NowPlaying {
        val md = c.metadata
        val st = c.playbackState
        fun s(key: String) = md?.getString(key)?.takeIf { it.isNotBlank() }
        val title = s(MediaMetadata.METADATA_KEY_TITLE) ?: s(MediaMetadata.METADATA_KEY_DISPLAY_TITLE)
        val artist = s(MediaMetadata.METADATA_KEY_ARTIST) ?: s(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE)
        val album = s(MediaMetadata.METADATA_KEY_ALBUM)
        val tuning = Tuning.parse(
            title, artist, album,
            s(MediaMetadata.METADATA_KEY_DISPLAY_TITLE),
            s(MediaMetadata.METADATA_KEY_DISPLAY_SUBTITLE),
            s(MediaMetadata.METADATA_KEY_DISPLAY_DESCRIPTION),
            md?.description?.title?.toString(),
            md?.description?.subtitle?.toString(),
        )
        val pkg = c.packageName
        val label = runCatching {
            val pm = context.packageManager
            pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
        val lower = pkg.lowercase()
        val isRadio = tuning != null || "radio" in lower || ".fm" in lower || "tuner" in lower
        val queue = c.queue.orEmpty().map { q ->
            val t = q.description.title?.toString().orEmpty()
            QueueEntry(q.queueId, t, Tuning.parse(t, q.description.subtitle?.toString()))
        }
        return NowPlaying(
            packageName = pkg,
            appLabel = label,
            title = title,
            artist = artist,
            album = album,
            isPlaying = st?.state == PlaybackState.STATE_PLAYING,
            tuning = tuning,
            isRadio = isRadio,
            positionMs = st?.position ?: 0L,
            positionUpdatedAt = st?.lastPositionUpdateTime?.takeIf { it > 0 } ?: SystemClock.elapsedRealtime(),
            speed = st?.playbackSpeed ?: 0f,
            durationMs = md?.getLong(MediaMetadata.METADATA_KEY_DURATION) ?: 0L,
            queue = queue,
            activeQueueId = st?.activeQueueItemId ?: MediaSession.QueueItem.UNKNOWN_ID.toLong(),
            actions = st?.actions ?: 0L,
            customActions = st?.customActions.orEmpty().map { CustomAction(it.action, it.name.toString()) },
            queueTitle = c.queueTitle?.toString()?.takeIf { it.isNotBlank() },
        )
    }

    // ---- Controls (by package; a null or missing session falls back to media keys) ----

    fun playPause(pkg: String?) {
        val c = controller(pkg) ?: return sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
        else c.transportControls.play()
    }

    /** Start [pkg] and pause any other session that is playing, like a source selector. */
    fun playOnly(pkg: String): Boolean {
        val c = controller(pkg) ?: return false
        controllers.filter { it !== c && it.playbackState?.state == PlaybackState.STATE_PLAYING }
            .forEach { it.transportControls.pause() }
        c.transportControls.play()
        return true
    }

    fun next(pkg: String?) {
        controller(pkg)?.transportControls?.skipToNext() ?: sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
    }

    fun previous(pkg: String?) {
        controller(pkg)?.transportControls?.skipToPrevious() ?: sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)
    }

    /**
     * Fine tuning. There is no standard "tune" command; many head-unit radio apps map
     * fast-forward/rewind to a manual step, so use those when the session offers them.
     */
    fun tune(pkg: String?, up: Boolean) {
        val c = controller(pkg) ?: return sendMediaKey(
            if (up) KeyEvent.KEYCODE_MEDIA_FAST_FORWARD else KeyEvent.KEYCODE_MEDIA_REWIND,
        )
        val actions = c.playbackState?.actions ?: 0L
        val needed = if (up) PlaybackState.ACTION_FAST_FORWARD else PlaybackState.ACTION_REWIND
        when {
            actions and needed != 0L -> if (up) c.transportControls.fastForward() else c.transportControls.rewind()
            else -> {
                val code = if (up) KeyEvent.KEYCODE_MEDIA_FAST_FORWARD else KeyEvent.KEYCODE_MEDIA_REWIND
                c.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
                c.dispatchMediaButtonEvent(KeyEvent(KeyEvent.ACTION_UP, code))
            }
        }
    }

    /** Jump to [positionMs] in the current track (the scrub bar). */
    fun seekToPosition(pkg: String?, positionMs: Long) {
        controller(pkg)?.transportControls?.seekTo(positionMs.coerceAtLeast(0L))
    }

    fun skipToQueueItem(pkg: String?, id: Long) {
        controller(pkg)?.transportControls?.skipToQueueItem(id)
    }

    fun sendCustomAction(pkg: String?, action: String, args: Bundle? = null) {
        controller(pkg)?.transportControls?.sendCustomAction(action, args)
    }

    /**
     * Shuffle/repeat. The framework controller has no setter, but AndroidX/Media3 sessions accept
     * the same custom actions MediaControllerCompat sends, so this works for most music apps.
     */
    fun setShuffle(pkg: String?, on: Boolean) = sendCustomAction(
        pkg, "android.support.v4.media.session.action.SET_SHUFFLE_MODE",
        Bundle().apply { putInt("android.support.v4.media.session.action.ARGUMENT_SHUFFLE_MODE", if (on) 1 else 0) },
    )

    fun setRepeat(pkg: String?, on: Boolean) = sendCustomAction(
        pkg, "android.support.v4.media.session.action.SET_REPEAT_MODE",
        Bundle().apply { putInt("android.support.v4.media.session.action.ARGUMENT_REPEAT_MODE", if (on) 2 else 0) },
    )

    /**
     * Step the session with next/previous until its metadata shows [target]. Bounded, and it
     * stops if a step moves away from the target, so a session that never reports a frequency
     * cannot loop forever.
     */
    fun seekTo(pkg: String?, target: Tuning) {
        val c = controller(pkg) ?: return
        seekJob?.cancel()
        seekJob = scope.launch {
            var lastDistance = Float.MAX_VALUE
            repeat(MAX_SEEK_STEPS) {
                val current = _sessions.value.firstOrNull { it.packageName == c.packageName }?.tuning ?: return@launch
                if (target.matches(current)) return@launch
                val distance = kotlin.math.abs(current.value - target.value)
                if (distance > lastDistance + 0.01f) return@launch
                lastDistance = distance
                if (target.value > current.value) c.transportControls.skipToNext()
                else c.transportControls.skipToPrevious()
                withTimeoutOrNull(1_500) {
                    _sessions.first { list ->
                        val t = list.firstOrNull { it.packageName == c.packageName }?.tuning
                        t != null && !t.matches(current)
                    }
                } ?: delay(300)
            }
        }
    }

    private fun sendMediaKey(code: Int) {
        val am = volume.audioManager()
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    /** Human-readable dump of all sessions, to see what ATOTO's radio app exposes. */
    fun describeSessions(): String {
        if (!_hasAccess.value) return "Media access not granted."
        return buildString {
            if (controllers.isEmpty()) appendLine("No active media sessions.")
            controllers.forEachIndexed { i, c ->
                val st = c.playbackState
                appendLine("#$i ${c.packageName}")
                appendLine("  state=${st?.state} actions=${actionNames(st?.actions ?: 0)}")
                st?.customActions?.forEach { appendLine("  custom: ${it.action} '${it.name}'") }
                c.metadata?.let { md ->
                    md.keySet().sorted().forEach { k ->
                        val v = md.getText(k) ?: runCatching { md.getLong(k).toString() }.getOrNull()
                        appendLine("  md $k = $v")
                    }
                }
                c.queue?.let { q ->
                    appendLine("  queue (${q.size}):")
                    q.take(20).forEach { appendLine("    ${it.queueId}: ${it.description.title} / ${it.description.subtitle}") }
                }
                c.extras?.let { ex -> ex.keySet().forEach { appendLine("  extra $it") } }
                appendLine("  flags=${c.flags} playbackType=${c.playbackInfo?.playbackType}")
            }
            // What the phone app's call notification looks like (to tune call detection to ATOTO).
            appendLine()
            appendLine("PHONE CALL (last call notification seen)")
            appendLine(com.retro.vfdlauncher.data.phone.PhoneCalls.lastRaw ?: "  none seen yet: start or receive a call, then look again")
        }
    }

    private fun actionNames(a: Long): String = listOf(
        PlaybackState.ACTION_PLAY to "PLAY",
        PlaybackState.ACTION_PAUSE to "PAUSE",
        PlaybackState.ACTION_SKIP_TO_NEXT to "NEXT",
        PlaybackState.ACTION_SKIP_TO_PREVIOUS to "PREV",
        PlaybackState.ACTION_SKIP_TO_QUEUE_ITEM to "QUEUE_ITEM",
        PlaybackState.ACTION_PLAY_FROM_MEDIA_ID to "FROM_MEDIA_ID",
        PlaybackState.ACTION_PLAY_FROM_SEARCH to "FROM_SEARCH",
        PlaybackState.ACTION_PLAY_FROM_URI to "FROM_URI",
        PlaybackState.ACTION_FAST_FORWARD to "FF",
        PlaybackState.ACTION_REWIND to "REW",
        PlaybackState.ACTION_SEEK_TO to "SEEK_TO",
    ).filter { (bit, _) -> a and bit != 0L }.joinToString(",") { it.second }.ifEmpty { "none" }

    companion object {
        private const val MAX_SEEK_STEPS = 30
    }
}
