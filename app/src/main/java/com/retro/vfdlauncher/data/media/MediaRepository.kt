package com.retro.vfdlauncher.data.media

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.media.session.PlaybackState
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
 * Mirrors whichever app is playing (ATOTO radio, Spotify, ...) through its MediaSession and
 * sends transport controls back to it. Needs notification-listener access.
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

    private val _nowPlaying = MutableStateFlow<NowPlaying?>(null)
    val nowPlaying: StateFlow<NowPlaying?> = _nowPlaying.asStateFlow()

    private var controllers: List<MediaController> = emptyList()
    private var active: MediaController? = null
    private var started = false
    private var seekJob: Job? = null

    private val sessionsListener = MediaSessionManager.OnActiveSessionsChangedListener { list ->
        attach(list.orEmpty())
    }

    private val callback = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = reselect()
        override fun onMetadataChanged(metadata: MediaMetadata?) = reselect()
        override fun onQueueChanged(queue: MutableList<android.media.session.MediaSession.QueueItem>?) = reselect()
        override fun onSessionDestroyed() = reselect()
    }

    fun checkAccess(): Boolean =
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)

    /** Call from onResume: access may have been granted in Settings meanwhile. */
    fun start() {
        _hasAccess.value = checkAccess()
        if (!_hasAccess.value || started) {
            if (started) reselect()
            return
        }
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
        reselect()
    }

    /** Prefer a session that is playing; otherwise the highest-priority one (list order). */
    private fun reselect() {
        val pick = controllers.firstOrNull { it.playbackState?.state == PlaybackState.STATE_PLAYING }
            ?: controllers.firstOrNull()
        active = pick
        _nowPlaying.value = pick?.let { snapshot(it) }
    }

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
            actions = st?.actions ?: 0L,
        )
    }

    // ---- Controls ----

    fun playPause() {
        val c = active
        if (c == null) {
            sendMediaKey(KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE)
            return
        }
        if (c.playbackState?.state == PlaybackState.STATE_PLAYING) c.transportControls.pause()
        else c.transportControls.play()
    }

    fun next() = active?.transportControls?.skipToNext() ?: sendMediaKey(KeyEvent.KEYCODE_MEDIA_NEXT)
    fun previous() = active?.transportControls?.skipToPrevious() ?: sendMediaKey(KeyEvent.KEYCODE_MEDIA_PREVIOUS)

    fun skipToQueueItem(id: Long) {
        active?.transportControls?.skipToQueueItem(id)
    }

    /**
     * Step the active session with next/previous until its metadata shows [target].
     * Works for radio apps that map skip to "next preset" or "seek". Bounded so a session
     * that never reports a frequency cannot loop forever.
     */
    fun seekTo(target: Tuning) {
        val c = active ?: return
        seekJob?.cancel()
        seekJob = scope.launch {
            var lastDistance = Float.MAX_VALUE
            repeat(MAX_SEEK_STEPS) {
                val current = _nowPlaying.value?.tuning ?: return@launch
                if (target.matches(current)) return@launch
                val distance = kotlin.math.abs(current.value - target.value)
                // If we overshot (distance grew after a step), stop instead of oscillating.
                if (distance > lastDistance + 0.01f) return@launch
                lastDistance = distance
                if (target.value > current.value) c.transportControls.skipToNext()
                else c.transportControls.skipToPrevious()
                withTimeoutOrNull(1_500) {
                    _nowPlaying.first { it?.tuning != null && !it.tuning.matches(current) }
                } ?: delay(300)
            }
        }
    }

    private fun sendMediaKey(code: Int) {
        val am = volume.audioManager()
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        am.dispatchMediaKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    /**
     * Human-readable dump of the active session, for working out what ATOTO's radio app
     * exposes (actions, metadata keys, queue, custom actions) before relying on it.
     */
    fun describeActiveSession(): String {
        if (!_hasAccess.value) return "Media access not granted."
        if (controllers.isEmpty()) return "No active media sessions."
        return buildString {
            controllers.forEachIndexed { i, c ->
                val st = c.playbackState
                appendLine("#$i ${c.packageName}${if (c == active) "  (shown)" else ""}")
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
