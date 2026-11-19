package com.retro.vfdlauncher.data.weather

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.TextStyle
import java.util.Locale
import kotlin.math.roundToInt

enum class WeatherIcon { SUN, PARTLY, CLOUD, FOG, RAIN, SNOW, STORM }

data class DayForecast(val day: String, val maxTemp: Int)

data class Weather(
    val temp: Int,
    val condition: String,
    val icon: WeatherIcon,
    val forecast: List<DayForecast>,
    val fetchedAt: Long,
    val stale: Boolean = false,
    /** Hourly / 15-minute forecast for the TOP-DOWN meter; null in caches from older versions. */
    val roof: RoofInputs? = null,
)

/**
 * Open-Meteo (no API key). The last good response is cached in SharedPreferences so the
 * screen shows something immediately after wake and while the car has no signal.
 */
class WeatherRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences("weather", Context.MODE_PRIVATE)
    private val _weather = MutableStateFlow(loadCached())
    val weather: StateFlow<Weather?> = _weather.asStateFlow()

    /** Fetch if the cache is older than [maxAgeMs]. Never throws. */
    suspend fun refresh(maxAgeMs: Long = 15 * 60_000L) {
        val cached = _weather.value
        // A cache from before the TOP-DOWN meter has no forecast: fetch now rather than wait.
        if (cached != null && !cached.stale && cached.roof != null && System.currentTimeMillis() - cached.fetchedAt < maxAgeMs) return
        val json = withContext(Dispatchers.IO) {
            runCatching { download(currentLocation()) }.getOrNull()
        }
        if (json != null) {
            runCatching { parse(json, System.currentTimeMillis()) }.getOrNull()?.let {
                prefs.edit().putString(KEY_JSON, json).putLong(KEY_TIME, it.fetchedAt).apply()
                _weather.value = it
                return
            }
        }
        // Offline: keep showing the cached reading, marked stale after 3 hours.
        _weather.value = cached?.copy(stale = System.currentTimeMillis() - cached.fetchedAt > 3 * 3_600_000L)
    }

    private fun loadCached(): Weather? {
        val json = prefs.getString(KEY_JSON, null) ?: return null
        return runCatching { parse(json, prefs.getLong(KEY_TIME, 0L)) }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    private fun currentLocation(): Pair<Double, Double> {
        val granted = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (granted) {
            val lm = context.getSystemService(LocationManager::class.java)
            val best: Location? = listOf(
                LocationManager.NETWORK_PROVIDER,
                LocationManager.GPS_PROVIDER,
                LocationManager.PASSIVE_PROVIDER,
            ).mapNotNull { p -> runCatching { lm.getLastKnownLocation(p) }.getOrNull() }
                .maxByOrNull { it.time }
            if (best != null) {
                prefs.edit()
                    .putFloat(KEY_LAT, best.latitude.toFloat())
                    .putFloat(KEY_LON, best.longitude.toFloat())
                    .apply()
                return best.latitude to best.longitude
            }
        }
        // Last place we knew, else the fixed fallback.
        if (prefs.contains(KEY_LAT)) {
            return prefs.getFloat(KEY_LAT, 0f).toDouble() to prefs.getFloat(KEY_LON, 0f).toDouble()
        }
        return FALLBACK_LAT to FALLBACK_LON
    }

    private fun download(loc: Pair<Double, Double>): String {
        val url = URL(
            "https://api.open-meteo.com/v1/forecast?latitude=${"%.3f".format(Locale.US, loc.first)}" +
                "&longitude=${"%.3f".format(Locale.US, loc.second)}" +
                "&current=temperature_2m,apparent_temperature,precipitation,weather_code,wind_gusts_10m,uv_index,is_day" +
                "&hourly=precipitation_probability,apparent_temperature,wind_gusts_10m,uv_index&forecast_hours=4" +
                "&minutely_15=precipitation&forecast_minutely_15=13" +
                "&daily=weather_code,temperature_2m_max&forecast_days=4&timezone=auto",
        )
        val conn = url.openConnection() as HttpURLConnection
        conn.connectTimeout = 8_000
        conn.readTimeout = 8_000
        try {
            check(conn.responseCode == 200) { "HTTP ${conn.responseCode}" }
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        /** Used until the unit reports a location. Brussels; change to your home town. */
        const val FALLBACK_LAT = 50.85
        const val FALLBACK_LON = 4.35

        private const val KEY_JSON = "json"
        private const val KEY_TIME = "time"
        private const val KEY_LAT = "lat"
        private const val KEY_LON = "lon"

        fun parse(json: String, fetchedAt: Long): Weather {
            val root = JSONObject(json)
            val current = root.getJSONObject("current")
            val code = current.getInt("weather_code")
            val daily = root.getJSONObject("daily")
            val dates = daily.getJSONArray("time")
            val maxes = daily.getJSONArray("temperature_2m_max")
            val forecast = (1 until minOf(4, dates.length())).map { i ->
                val day = LocalDate.parse(dates.getString(i)).dayOfWeek
                    .getDisplayName(TextStyle.SHORT, Locale.getDefault())
                    .uppercase(Locale.getDefault()).take(3)
                DayForecast(day, maxes.getDouble(i).roundToInt())
            }
            return Weather(
                temp = current.getDouble("temperature_2m").roundToInt(),
                condition = conditionFor(code),
                icon = iconFor(code),
                forecast = forecast,
                fetchedAt = fetchedAt,
                roof = runCatching { parseRoof(root) }.getOrNull(),
            )
        }

        /** The TOP-DOWN meter's inputs; throws if the response predates them. */
        fun parseRoof(root: JSONObject): RoofInputs {
            val current = root.getJSONObject("current")
            fun JSONObject.num(key: String) = optDouble(key, 0.0).let { if (it.isNaN()) 0.0 else it }
            val hourly = root.getJSONObject("hourly")
            val hTimes = hourly.getJSONArray("time")
            val hours = (0 until hTimes.length()).map { i ->
                HourSample(
                    time = LocalDateTime.parse(hTimes.getString(i)),
                    rainProbability = hourly.getJSONArray("precipitation_probability").optInt(i, 0),
                    feelsLike = hourly.getJSONArray("apparent_temperature").optDouble(i, current.num("apparent_temperature")),
                    gusts = hourly.getJSONArray("wind_gusts_10m").optDouble(i, 0.0),
                    uv = hourly.getJSONArray("uv_index").optDouble(i, 0.0),
                )
            }
            val quarters = root.optJSONObject("minutely_15")?.let { m ->
                val t = m.getJSONArray("time")
                val p = m.getJSONArray("precipitation")
                (0 until t.length()).map { i -> RainSample(LocalDateTime.parse(t.getString(i)), p.optDouble(i, 0.0)) }
            }.orEmpty()
            return RoofInputs(
                utcOffsetSeconds = root.optInt("utc_offset_seconds", 0),
                temp = current.getDouble("temperature_2m"),
                feelsLikeNow = current.getDouble("apparent_temperature"),
                precipitationNow = current.num("precipitation"),
                weatherCode = current.getInt("weather_code"),
                gustsNow = current.num("wind_gusts_10m"),
                uvNow = current.num("uv_index"),
                hours = hours,
                quarters = quarters,
            )
        }

        /** WMO weather interpretation codes, as used by Open-Meteo. */
        fun conditionFor(code: Int): String = when (code) {
            0 -> "CLEAR"
            1 -> "FAIR"
            2 -> "PARTLY CLOUDY"
            3 -> "CLOUDY"
            45, 48 -> "FOG"
            51, 53, 55, 56, 57 -> "DRIZZLE"
            61, 63, 66 -> "RAIN"
            65, 67 -> "HEAVY RAIN"
            71, 73, 75, 77 -> "SNOW"
            80, 81, 82 -> "SHOWERS"
            85, 86 -> "SNOW SHOWERS"
            95, 96, 99 -> "STORM"
            else -> "---"
        }

        fun iconFor(code: Int): WeatherIcon = when (code) {
            0, 1 -> WeatherIcon.SUN
            2 -> WeatherIcon.PARTLY
            3 -> WeatherIcon.CLOUD
            45, 48 -> WeatherIcon.FOG
            71, 73, 75, 77, 85, 86 -> WeatherIcon.SNOW
            95, 96, 99 -> WeatherIcon.STORM
            in 51..67, in 80..82 -> WeatherIcon.RAIN
            else -> WeatherIcon.CLOUD
        }
    }
}
