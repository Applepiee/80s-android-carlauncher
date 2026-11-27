package com.retro.vfdlauncher.data.audio

import android.content.Context
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings

/**
 * Keeps the head unit's DSP equalizer across power cuts.
 *
 * On the ATOTO S8L the EQ lives in the car-audio MCU, which loses it whenever the unit loses
 * power (it does a full boot on every ignition). The DSP app's settings are mirrored into
 * Settings.System as JSON (key_new_dsp_eq0, one object per band: freq, q, gain 0..30), and the
 * mirror is reset to defaults at boot.
 *
 * So: while the car runs, every change the driver makes in the DSP app is copied into our own
 * storage. After a boot, once the unit has settled, any band that differs from that copy is sent
 * back to the MCU through the kernel service, the same call the DSP app uses.
 */
class EqKeeper(private val context: Context, private val kernel: NwdKernel) {

    private val prefs = context.getSharedPreferences("eq_keeper", Context.MODE_PRIVATE)
    private val resolver = context.contentResolver
    private val main = Handler(Looper.getMainLooper())

    /** Capturing starts only after this boot's restore has run, so boot defaults are never saved. */
    private var capturing = false
    private var started = false
    private var restoreTries = 0

    /** Inspector line: what the keeper last did. */
    var status: String = "idle"
        private set

    private val observer = object : ContentObserver(main) {
        override fun onChange(selfChange: Boolean) {
            main.removeCallbacks(captureRun)
            main.postDelayed(captureRun, CAPTURE_DEBOUNCE_MS)
        }
    }
    private val captureRun = Runnable { capture() }

    fun start() {
        if (started || !kernel.isPresent()) return
        started = true
        runCatching { resolver.registerContentObserver(Settings.System.CONTENT_URI, true, observer) }
        if (prefs.getLong(KEY_RESTORED_BOOT, -1L) == bootId()) {
            // The launcher restarted mid-drive: this boot was already restored.
            capturing = true
            status = "already restored this boot; saving changes"
            return
        }
        kernel.connect {
            val wait = (SETTLE_AFTER_BOOT_MS - SystemClock.elapsedRealtime()).coerceAtLeast(SETTLE_MIN_MS)
            status = "waiting ${wait / 1000}s for the unit to settle"
            main.postDelayed({ restore() }, wait)
        }
    }

    fun stop() {
        if (!started) return
        runCatching { resolver.unregisterContentObserver(observer) }
        main.removeCallbacksAndMessages(null)
        started = false
    }

    private fun readKeys(): Map<String, String> = EQ_KEYS.mapNotNull { k ->
        runCatching { Settings.System.getString(resolver, k) }.getOrNull()?.let { k to it }
    }.toMap()

    private fun saved(): Map<String, String> =
        EQ_KEYS.mapNotNull { k -> prefs.getString("v_$k", null)?.let { k to it } }.toMap()

    private fun restore() {
        val saved = saved()
        if (saved.isEmpty()) {
            finishRestore("nothing saved yet; change the EQ in the DSP app once and it will be kept")
            return
        }
        val current = readKeys()
        var sent = 0
        var failed = 0
        saved.forEach { (key, json) ->
            val want = EqJson.parse(json, channelOf(key))
            val have = current[key]?.let { EqJson.parse(it, channelOf(key)) }.orEmpty().associateBy { it.index }
            want.filter { have[it.index]?.gain != it.gain || have[it.index]?.q != it.q || have[it.index]?.freq != it.freq }
                .forEach { if (kernel.setBand(it)) sent++ else failed++ }
        }
        if (sent == 0 && failed == 0) {
            finishRestore("EQ already matches the saved one")
            return
        }
        status = "restore #${restoreTries + 1}: sent $sent band(s)" + if (failed > 0) ", $failed failed (${kernel.lastError})" else ""
        // Check it landed; the MCU echo rewrites the mirror. Retry once if the unit was still busy.
        main.postDelayed({
            val ok = saved.all { (key, json) ->
                val now = readKeys()[key] ?: return@all false
                EqJson.parse(now, channelOf(key)).map { it.gain } == EqJson.parse(json, channelOf(key)).map { it.gain }
            }
            if (ok || ++restoreTries >= MAX_RESTORE_TRIES) {
                finishRestore(status + if (ok) " · verified" else " · could not verify (mirror still differs)")
            } else {
                restore()
            }
        }, VERIFY_DELAY_MS)
    }

    private fun finishRestore(msg: String) {
        status = msg
        prefs.edit().putLong(KEY_RESTORED_BOOT, bootId()).apply()
        capturing = true
    }

    private fun capture() {
        if (!capturing) return
        val now = readKeys()
        if (now.isEmpty()) return
        val e = prefs.edit()
        var changed = false
        now.forEach { (k, v) ->
            if (EqJson.parse(v, channelOf(k)).isEmpty()) return@forEach
            if (prefs.getString("v_$k", null) != v) { e.putString("v_$k", v); changed = true }
        }
        if (changed) {
            e.putLong(KEY_SAVED_AT, System.currentTimeMillis()).apply()
            status = "saved the EQ from the DSP app"
        }
    }

    /** Changes once per boot: the wall-clock time the unit booted, to the minute. */
    private fun bootId(): Long {
        val count = runCatching { Settings.Global.getInt(resolver, Settings.Global.BOOT_COUNT) }.getOrNull()
        if (count != null) return count.toLong()
        return (System.currentTimeMillis() - SystemClock.elapsedRealtime()) / 60_000L
    }

    /** EQ section of the session inspector. */
    fun describe(): String = buildString {
        appendLine("EQ KEEPER")
        appendLine("  kernel=${if (kernel.isPresent()) (if (kernel.available) "bound" else "present, not bound") else "absent"}")
        appendLine("  status: $status")
        kernel.lastError?.let { appendLine("  last error: $it") }
        val savedAt = prefs.getLong(KEY_SAVED_AT, 0L)
        appendLine("  saved: " + if (savedAt == 0L) "never" else java.text.DateFormat.getDateTimeInstance().format(savedAt))
        val cur = readKeys()
        val sav = saved()
        EQ_KEYS.forEach { k ->
            val c = cur[k]?.let { EqJson.parse(it, channelOf(k)) } ?: return@forEach
            val s = sav[k]?.let { EqJson.parse(it, channelOf(k)) }
            appendLine("  $k now:   " + c.joinToString(" ") { it.db.toString() })
            if (s != null) appendLine("  $k saved: " + s.joinToString(" ") { it.db.toString() })
        }
        runCatching { Settings.System.getString(resolver, "eq_user_mode") }.getOrNull()?.let { appendLine("  eq_user_mode=$it") }
        kernel.getAudioParam(NwdKernel.PARAM_SYSTEM_VOLUME)?.let { appendLine("  mcu volume (kernel)=$it") }
    }

    private companion object {
        val EQ_KEYS = listOf("key_new_dsp_eq0", "key_new_dsp_eq1", "key_new_dsp_eq2", "key_new_dsp_eq3")
        fun channelOf(key: String) = key.removePrefix("key_new_dsp_eq").toIntOrNull() ?: 0

        const val KEY_RESTORED_BOOT = "restored_boot"
        const val KEY_SAVED_AT = "saved_at"
        const val CAPTURE_DEBOUNCE_MS = 2_000L
        const val SETTLE_AFTER_BOOT_MS = 25_000L
        const val SETTLE_MIN_MS = 3_000L
        const val VERIFY_DELAY_MS = 4_000L
        const val MAX_RESTORE_TRIES = 3
    }
}

/** Tolerant reader for the DSP mirror JSON: a list of {freq, q, gain[, channel]} objects. */
object EqJson {
    private val obj = Regex("\\{[^{}]*\\}")
    private fun num(s: String, name: String): String? =
        Regex("\"$name\"\\s*:\\s*\"?(-?[0-9]+(?:\\.[0-9]+)?)").find(s)?.groupValues?.get(1)

    fun parse(json: String, defaultChannel: Int): List<DspBand> =
        obj.findAll(json).mapIndexedNotNull { i, m ->
            val o = m.value
            val freq = num(o, "freq")?.toFloatOrNull()?.toInt() ?: return@mapIndexedNotNull null
            val gain = num(o, "gain")?.toFloatOrNull()?.toInt() ?: return@mapIndexedNotNull null
            val q = num(o, "q")?.toFloatOrNull() ?: 3f
            val ch = num(o, "channel")?.toIntOrNull() ?: defaultChannel
            val idx = num(o, "index")?.toIntOrNull() ?: i
            DspBand(idx, freq, q, gain.coerceIn(0, 30), ch)
        }.toList()
}
