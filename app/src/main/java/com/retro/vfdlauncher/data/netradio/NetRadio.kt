package com.retro.vfdlauncher.data.netradio

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
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

    private val _online = MutableStateFlow(true)
    /**
     * A data connection is up (SIM or Wi-Fi). Head units on a SIM often never pass Android's
     * "validated" check, so only INTERNET is required. The NET chip says NO NET only when this is false.
     */
    val online: StateFlow<Boolean> = _online.asStateFlow()

    /** Stations PREV / NEXT walk through when no presets are saved: the last STATIONS search. */
    @Volatile private var browseList: List<RadioStation> = emptyList()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    @Volatile private var loaded = false

    fun load(context: Context) {
        if (loaded) return
        loaded = true
        watchNetwork(context.applicationContext)
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

    /**
     * PREV / NEXT: step through the stored presets. With no presets saved, step through the last
     * STATIONS search instead, or (before any search) the most popular stations of the current
     * station's country.
     */
    fun step(context: Context, delta: Int) {
        val presets = _presets.value.filterNotNull()
        if (presets.isNotEmpty()) {
            play(context, stepIn(presets, delta))
            return
        }
        val list = browseList
        if (list.isNotEmpty()) {
            play(context, stepIn(list, delta))
            return
        }
        val app = context.applicationContext
        val country = _state.value.station?.countryCode?.takeIf { it.isNotEmpty() } ?: RadioCountries.list.first().code
        scope.launch {
            val top = runCatching { browser.search("", country, null, limit = 40) }.getOrDefault(emptyList())
            if (top.isNotEmpty()) {
                browseList = top
                play(app, stepIn(top, delta))
            }
        }
    }

    private fun stepIn(list: List<RadioStation>, delta: Int) = stepStation(list, _state.value.station?.uuid, delta)

    /** Results of the last STATIONS search (PREV / NEXT use them while no presets are saved). */
    fun setBrowseList(list: List<RadioStation>) {
        if (list.isNotEmpty()) browseList = list
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

    private fun watchNetwork(context: Context) {
        val cm = context.getSystemService(ConnectivityManager::class.java) ?: return
        fun hasInternet(n: Network?) =
            n != null && cm.getNetworkCapabilities(n)?.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET) == true
        _online.value = hasInternet(cm.activeNetwork)
        runCatching {
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) { _online.value = true }
                override fun onLost(network: Network) { _online.value = hasInternet(cm.activeNetwork) }
                override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                    _online.value = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                }
            })
        }
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}

/**
 * The station [delta] steps away from [currentUuid] in [list], wrapping around. When the current
 * station isn't in the list, NEXT starts at the first one and PREV at the last.
 */
internal fun stepStation(list: List<RadioStation>, currentUuid: String?, delta: Int): RadioStation {
    val cur = list.indexOfFirst { it.uuid == currentUuid }
    val from = if (cur >= 0) cur else if (delta > 0) -1 else 0
    return list[((from + delta) % list.size + list.size) % list.size]
}
