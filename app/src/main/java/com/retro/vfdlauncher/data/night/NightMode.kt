package com.retro.vfdlauncher.data.night

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDateTime
import java.time.ZoneOffset

/** Sunrise and sunset of one day, local time at the car. */
data class SunDay(val rise: LocalDateTime, val set: LocalDateTime)

/** Night mode as the screen should show it right now. */
data class NightState(
    val isNight: Boolean = false,
    /** How dark the black layer is, 0..0.9 (0 in daytime); over other apps at most 0.8. */
    val dim: Float = 0f,
    /** The user overrode the automatic choice with the moon key. */
    val manual: Boolean = false,
    /** Next automatic switch (sunrise or sunset), for "NIGHT UNTIL 07:58". */
    val nextSwitch: LocalDateTime? = null,
)

/** Pure sunrise/sunset logic, unit-tested. */
object NightClock {
    /** Night if [now] is before today's sunrise or after its sunset. Fallback without data: 19:00-07:00. */
    fun isNight(days: List<SunDay>, now: LocalDateTime): Boolean {
        val today = days.firstOrNull { it.rise.toLocalDate() == now.toLocalDate() }
            ?: return now.hour >= 19 || now.hour < 7
        return now.isBefore(today.rise) || !now.isBefore(today.set)
    }

    /** The next sunrise or sunset after [now], or null without data. */
    fun nextSwitch(days: List<SunDay>, now: LocalDateTime): LocalDateTime? =
        days.flatMap { listOf(it.rise, it.set) }.filter { it.isAfter(now) }.minOrNull()
}

/**
 * Night mode for the whole launcher: the home screen, the floating rail and the shade over other
 * apps all read [state]. Automatic from sunrise/sunset (Open-Meteo), with a manual moon key that
 * holds until the next sunrise or sunset. At night it also lowers the real backlight when the user
 * allowed "Modify system settings", and restores the daytime brightness in the morning.
 */
object NightMode {
    private val _state = MutableStateFlow(NightState())
    val state: StateFlow<NightState> = _state.asStateFlow()

    @Volatile private var days: List<SunDay> = emptyList()
    @Volatile private var utcOffsetSeconds: Int? = null

    private const val PREFS = "night"
    private const val KEY_DIM = "dim"
    private const val KEY_OVERRIDE = "override" // "on" / "off"
    private const val KEY_OVERRIDE_UNTIL = "overrideUntil" // epoch seconds
    private const val KEY_DAY_BRIGHTNESS = "dayBrightness"
    private const val KEY_DAY_AUTO = "dayAuto"
    private const val KEY_BACKLIGHT_LOWERED = "lowered"
    const val DEFAULT_DIM = 0.7f
    /** 0-80 % in steps of 10 (canvas Settings); Android caps see-through overlays at 80 % anyway. */
    const val MAX_DIM = 0.8f

    /** New forecast: sunrise/sunset days and the location's UTC offset. */
    fun setSun(context: Context, sun: List<SunDay>, offsetSeconds: Int) {
        days = sun
        utcOffsetSeconds = offsetSeconds
        update(context)
    }

    fun dimLevel(context: Context): Float =
        prefs(context).getFloat(KEY_DIM, DEFAULT_DIM).coerceIn(0f, MAX_DIM)

    fun setDimLevel(context: Context, level: Float) {
        prefs(context).edit().putFloat(KEY_DIM, level.coerceIn(0f, MAX_DIM)).apply()
        update(context)
    }

    /**
     * DAY NOW / NIGHT NOW in settings: force day or night until the next sunrise or sunset.
     * Pressing the one that is already forced goes back to AUTO.
     */
    fun force(context: Context, night: Boolean) {
        val st = _state.value
        if (st.manual && st.isNight == night) {
            prefs(context).edit().remove(KEY_OVERRIDE).remove(KEY_OVERRIDE_UNTIL).apply()
            update(context)
            return
        }
        val now = now()
        val nextAuto = NightClock.nextSwitch(days, now) ?: now.plusHours(12)
        prefs(context).edit()
            .putString(KEY_OVERRIDE, if (night) "on" else "off")
            .putLong(KEY_OVERRIDE_UNTIL, nextAuto.toEpochSecond(offset()))
            .apply()
        update(context)
    }

    /** True when the next switch is a sunrise (it is night now by the clock). */
    fun nextIsSunrise(): Boolean = NightClock.isNight(days, now())

    /** Moon key: flip night on/off now; holds until the next sunrise or sunset. */
    fun toggle(context: Context) {
        val now = now()
        val nextAuto = NightClock.nextSwitch(days, now) ?: now.plusHours(12)
        val want = !_state.value.isNight
        prefs(context).edit()
            .putString(KEY_OVERRIDE, if (want) "on" else "off")
            .putLong(KEY_OVERRIDE_UNTIL, nextAuto.toEpochSecond(offset()))
            .apply()
        update(context)
    }

    /** Re-evaluate (call every minute and on resume). */
    fun update(context: Context) {
        val p = prefs(context)
        val now = now()
        val auto = NightClock.isNight(days, now)
        var override = p.getString(KEY_OVERRIDE, null)
        if (override != null && now.toEpochSecond(offset()) >= p.getLong(KEY_OVERRIDE_UNTIL, 0L)) {
            p.edit().remove(KEY_OVERRIDE).remove(KEY_OVERRIDE_UNTIL).apply()
            override = null
        }
        val night = when (override) {
            "on" -> true
            "off" -> false
            else -> auto
        }
        _state.value = NightState(
            isNight = night,
            dim = if (night) dimLevel(context) else 0f,
            manual = override != null,
            nextSwitch = NightClock.nextSwitch(days, now),
        )
        applyBacklight(context, night)
    }

    // ---- Real backlight ----

    fun canChangeBacklight(context: Context): Boolean = Settings.System.canWrite(context)

    fun backlightPermissionIntent(context: Context): Intent =
        Intent(Settings.ACTION_MANAGE_WRITE_SETTINGS, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /** Night: remember the day setting, then go to the lowest brightness. Day: put it back. */
    private fun applyBacklight(context: Context, night: Boolean) {
        if (!canChangeBacklight(context)) return
        val p = prefs(context)
        val r = context.contentResolver
        val lowered = p.getBoolean(KEY_BACKLIGHT_LOWERED, false)
        runCatching {
            if (night && !lowered) {
                val auto = Settings.System.getInt(r, Settings.System.SCREEN_BRIGHTNESS_MODE, 0) ==
                    Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC
                val level = Settings.System.getInt(r, Settings.System.SCREEN_BRIGHTNESS, 128)
                p.edit().putInt(KEY_DAY_BRIGHTNESS, level).putBoolean(KEY_DAY_AUTO, auto)
                    .putBoolean(KEY_BACKLIGHT_LOWERED, true).apply()
                Settings.System.putInt(r, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_MANUAL)
                Settings.System.putInt(r, Settings.System.SCREEN_BRIGHTNESS, NIGHT_BRIGHTNESS)
            } else if (!night && lowered) {
                Settings.System.putInt(r, Settings.System.SCREEN_BRIGHTNESS, p.getInt(KEY_DAY_BRIGHTNESS, 128))
                if (p.getBoolean(KEY_DAY_AUTO, false)) {
                    Settings.System.putInt(r, Settings.System.SCREEN_BRIGHTNESS_MODE, Settings.System.SCREEN_BRIGHTNESS_MODE_AUTOMATIC)
                }
                p.edit().putBoolean(KEY_BACKLIGHT_LOWERED, false).apply()
            }
        }
    }

    /** Lowest backlight at night (0-255); not 0, some screens switch off entirely at 0. */
    private const val NIGHT_BRIGHTNESS = 8

    private fun offset(): ZoneOffset =
        utcOffsetSeconds?.let { ZoneOffset.ofTotalSeconds(it) }
            ?: java.time.ZoneId.systemDefault().rules.getOffset(java.time.Instant.now())

    private fun now(): LocalDateTime = LocalDateTime.now(offset())

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
