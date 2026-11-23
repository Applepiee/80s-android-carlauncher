package com.retro.vfdlauncher.data.netradio

import android.content.Context
import android.content.Intent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class NetStatus { IDLE, BUFFERING, PLAYING, STOPPED, OFFLINE }

/** What the RADIO screen shows for internet radio (canvas page "Internet radio"). */
data class NetRadioState(
    val station: RadioStation? = null,
    val status: NetStatus = NetStatus.IDLE,
    /** ICY "now playing" line from the stream, when it sends one. */
    val songLine: String? = null,
    /** BUFFER nn % while connecting. */
    val bufferPct: Int = 0,
    /** Seconds until the next automatic retry when there's no data. */
    val retryIn: Int = 0,
)

/**
 * Internet radio, played by the launcher itself ([RadioService], media3 ExoPlayer). This object is
 * the shared state the home screen reads, the six station presets, and the commands; the service
 * does the playing. Same process, so a singleton is enough (like OverlayBus).
 */
object NetRadio {
    private const val PREFS = "netradio"
    private const val KEY_PRESETS = "presets"
    private const val KEY_LAST = "last"
    private const val KEY_MODE = "mode" // "net" (default) or "app" (the head unit's own radio app)
    const val USER_AGENT = "RoadsterLauncher/0.1 (+https://github.com/Applepiee/80s-android-carlauncher)"

    private val _state = MutableStateFlow(NetRadioState())
    val state: StateFlow<NetRadioState> = _state.asStateFlow()

    private val _presets = MutableStateFlow<List<RadioStation?>>(List(6) { null })
    val presets: StateFlow<List<RadioStation?>> = _presets.asStateFlow()

    private val _enabled = MutableStateFlow(true)
    /** RADIO tab = internet radio (true) or the head unit's radio app (false). */
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    val browser = RadioBrowser(USER_AGENT)

    @Volatile private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        val p = prefs(context)
        _presets.value = RadioJson.decodePresets(p.getString(KEY_PRESETS, null))
        _enabled.value = p.getString(KEY_MODE, "net") != "app"
        if (_state.value.station == null) {
            _state.value = _state.value.copy(station = RadioJson.decode(p.getString(KEY_LAST, null)))
        }
    }

    fun setEnabled(context: Context, on: Boolean) {
        prefs(context).edit().putString(KEY_MODE, if (on) "net" else "app").apply()
        _enabled.value = on
        if (!on) stop(context)
    }

    // ---- Commands (handled by RadioService) ----

    fun play(context: Context, station: RadioStation) {
        prefs(context).edit().putString(KEY_LAST, RadioJson.encode(station)).apply()
        _state.value = NetRadioState(station = station, status = NetStatus.BUFFERING)
        send(context, RadioService.ACTION_PLAY, RadioJson.encode(station))
    }

    /** Play the current (or last) station again. */
    fun resume(context: Context) {
        load(context)
        val s = _state.value.station ?: return
        play(context, s)
    }

    fun stop(context: Context) {
        if (_state.value.status == NetStatus.IDLE) return
        send(context, RadioService.ACTION_STOP, null)
    }

    /** PREV / NEXT: step through the stored presets. */
    fun step(context: Context, delta: Int) {
        val list = _presets.value.withIndex().filter { it.value != null }
        if (list.isEmpty()) return
        val cur = list.indexOfFirst { it.value?.uuid == _state.value.station?.uuid }
        val from = if (cur >= 0) cur else if (delta > 0) -1 else 0
        val n = ((from + delta) % list.size + list.size) % list.size
        play(context, list[n].value!!)
    }

    fun playPreset(context: Context, index: Int) {
        _presets.value.getOrNull(index)?.let { play(context, it) }
    }

    /** Hold a preset key: store the current station there. */
    fun savePreset(context: Context, index: Int): Boolean {
        val s = _state.value.station ?: return false
        val list = _presets.value.toMutableList()
        if (index !in list.indices) return false
        list[index] = s
        _presets.value = list
        prefs(context).edit().putString(KEY_PRESETS, RadioJson.encodePresets(list)).apply()
        return true
    }

    // ---- From the service ----

    internal fun update(transform: (NetRadioState) -> NetRadioState) {
        _state.value = transform(_state.value)
    }

    private fun send(context: Context, action: String, station: String?) {
        val i = Intent(context, RadioService::class.java).setAction(action)
        if (station != null) i.putExtra(RadioService.EXTRA_STATION, station)
        runCatching { context.startService(i) }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
