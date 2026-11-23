package com.retro.vfdlauncher.data.netradio

import android.app.PendingIntent
import android.content.Intent
import android.os.Handler
import android.os.Looper
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.retro.vfdlauncher.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import java.util.Locale

/**
 * Plays internet radio for the RADIO tab: ExoPlayer in a media3 MediaSessionService, so the
 * head unit's buttons, steering-wheel keys and the spectrum treat it like any media app.
 * NEXT / PREVIOUS step through the six presets. When the stream drops it retries every 5 s.
 */
@UnstableApi
class RadioService : MediaSessionService() {

    private lateinit var exo: ExoPlayer
    private var session: MediaSession? = null
    private val main = Handler(Looper.getMainLooper())
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var current: RadioStation? = null
    private var userStopped = false

    private val bufferTick = object : Runnable {
        override fun run() {
            if (exo.playbackState == Player.STATE_BUFFERING) {
                val pct = (exo.totalBufferedDuration * 100 / BUFFER_FOR_PLAYBACK_MS).toInt().coerceIn(0, 99)
                NetRadio.update { it.copy(bufferPct = pct) }
                main.postDelayed(this, 200)
            }
        }
    }

    private val retryTick = object : Runnable {
        override fun run() {
            val left = NetRadio.state.value.retryIn - 1
            if (left > 0) {
                NetRadio.update { it.copy(retryIn = left) }
                main.postDelayed(this, 1_000)
            } else {
                current?.let { start(it, countClick = false) }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        val http = DefaultHttpDataSource.Factory()
            .setUserAgent(NetRadio.USER_AGENT)
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(10_000)
            .setReadTimeoutMs(15_000)
        exo = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(this).setDataSourceFactory(http))
            .setAudioAttributes(
                AudioAttributes.Builder().setUsage(C.USAGE_MEDIA).setContentType(C.AUDIO_CONTENT_TYPE_MUSIC).build(),
                true,
            )
            .setHandleAudioBecomingNoisy(true)
            .build()
        exo.addListener(listener)
        val player = object : ForwardingPlayer(exo) {
            override fun getAvailableCommands(): Player.Commands = super.getAvailableCommands().buildUpon()
                .addAll(Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_PREVIOUS,
                    Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM)
                .build()
            override fun isCommandAvailable(command: Int) = availableCommands.contains(command)
            override fun seekToNext() = NetRadio.step(this@RadioService, 1)
            override fun seekToNextMediaItem() = NetRadio.step(this@RadioService, 1)
            override fun seekToPrevious() = NetRadio.step(this@RadioService, -1)
            override fun seekToPreviousMediaItem() = NetRadio.step(this@RadioService, -1)
            // Play after a stop (or from the steering wheel): reconnect to the live stream.
            override fun play() {
                val s = current
                if (s != null && (exo.playbackState == Player.STATE_IDLE || exo.playbackState == Player.STATE_ENDED)) {
                    start(s, countClick = false)
                } else {
                    super.play()
                }
            }
            // Pausing a live stream = stop (no time-shift buffer to come back to).
            override fun pause() = stopPlayback()
            override fun stop() = stopPlayback()
        }
        session = MediaSession.Builder(this, player)
            .setSessionActivity(
                PendingIntent.getActivity(
                    this, 0, Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()
        NetRadio.load(this)
        current = NetRadio.state.value.station
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_PLAY -> RadioJson.decode(intent.getStringExtra(EXTRA_STATION))?.let { start(it, countClick = true) }
            ACTION_STOP -> stopPlayback()
        }
        return super.onStartCommand(intent, flags, startId)
    }

    private fun start(station: RadioStation, countClick: Boolean) {
        main.removeCallbacks(retryTick)
        current = station
        userStopped = false
        NetRadio.update { NetRadioState(station = station, status = NetStatus.BUFFERING) }
        val item = MediaItem.Builder()
            .setMediaId(station.uuid)
            .setUri(station.url)
            .setLiveConfiguration(MediaItem.LiveConfiguration.Builder().build())
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(station.displayName)
                    .setStation(station.displayName)
                    .setArtist(station.infoShort)
                    .build(),
            )
            .build()
        exo.setMediaItem(item)
        exo.prepare()
        exo.playWhenReady = true
        main.removeCallbacks(bufferTick)
        main.post(bufferTick)
        if (countClick) scope.launch { NetRadio.browser.click(station.uuid) }
    }

    private fun stopPlayback() {
        userStopped = true
        main.removeCallbacks(retryTick)
        main.removeCallbacks(bufferTick)
        exo.stop()
        NetRadio.update { it.copy(status = NetStatus.STOPPED, bufferPct = 0, retryIn = 0) }
    }

    private val listener = object : Player.Listener {
        override fun onPlaybackStateChanged(state: Int) = sync()
        override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) = sync()

        override fun onMediaMetadataChanged(md: MediaMetadata) {
            // ICY StreamTitle ("ARTIST - TITLE") arrives as the title; skip it when it's just the station name.
            val t = md.title?.toString()?.trim()?.takeIf { it.isNotEmpty() }
            val line = t?.takeIf { !it.equals(current?.name, ignoreCase = true) && !it.equals(current?.displayName, ignoreCase = true) }
                ?.replace(" - ", " · ")?.uppercase(Locale.ROOT)
            NetRadio.update { it.copy(songLine = line) }
        }

        override fun onPlayerError(error: PlaybackException) {
            if (userStopped) return
            main.removeCallbacks(bufferTick)
            NetRadio.update { it.copy(status = NetStatus.OFFLINE, bufferPct = 0, retryIn = RETRY_SECONDS) }
            main.removeCallbacks(retryTick)
            main.postDelayed(retryTick, 1_000)
        }
    }

    private fun sync() {
        if (userStopped) return
        when (exo.playbackState) {
            Player.STATE_BUFFERING -> {
                NetRadio.update { it.copy(status = NetStatus.BUFFERING) }
                main.removeCallbacks(bufferTick)
                main.post(bufferTick)
            }
            Player.STATE_READY -> NetRadio.update {
                it.copy(status = if (exo.playWhenReady) NetStatus.PLAYING else NetStatus.STOPPED, bufferPct = 100, retryIn = 0)
            }
            Player.STATE_ENDED -> NetRadio.update { it.copy(status = NetStatus.STOPPED) }
            else -> Unit
        }
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep playing with the launcher in the background; stop only if nothing is playing.
        if (!exo.isPlaying) stopSelf()
    }

    override fun onDestroy() {
        main.removeCallbacksAndMessages(null)
        scope.cancel()
        session?.run {
            player.release()
            release()
        }
        session = null
        NetRadio.update { it.copy(status = if (it.status == NetStatus.IDLE) NetStatus.IDLE else NetStatus.STOPPED) }
        super.onDestroy()
    }

    companion object {
        const val ACTION_PLAY = "com.retro.vfdlauncher.netradio.PLAY"
        const val ACTION_STOP = "com.retro.vfdlauncher.netradio.STOP"
        const val EXTRA_STATION = "station"
        private const val RETRY_SECONDS = 5
        private const val BUFFER_FOR_PLAYBACK_MS = 2_500L
    }
}
