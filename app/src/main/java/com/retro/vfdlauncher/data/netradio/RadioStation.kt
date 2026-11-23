package com.retro.vfdlauncher.data.netradio

import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

/** One internet radio station from Radio Browser (radio-browser.info). */
data class RadioStation(
    val uuid: String,
    val name: String,
    /** Stream URL (url_resolved when Radio Browser has one). */
    val url: String,
    /** ISO country code, e.g. "BE"; empty when unknown. */
    val countryCode: String,
    /** Lower-case tags, e.g. ["80s", "pop"]. */
    val tags: List<String>,
    /** kbit/s, 0 when unknown. */
    val bitrate: Int,
) {
    /** Name as shown on the VFD: upper case, single spaces. */
    val displayName: String get() = name.trim().replace(Regex("\\s+"), " ").uppercase(Locale.ROOT)

    /** Short genre label for presets and the info line: one of the picker genres if it has one. */
    val genreLabel: String
        get() = RadioGenres.firstNotNullOfOrNull { g -> g.tag?.takeIf { it in tags }?.let { g.label } }
            ?: tags.firstOrNull()?.uppercase(Locale.ROOT)?.take(8).orEmpty()

    /** "BE · 80S · 128K" (canvas top-right info). */
    val infoShort: String
        get() = listOf(countryCode.ifEmpty { "--" }, genreLabel, if (bitrate > 0) "${bitrate}K" else "")
            .filter { it.isNotEmpty() }.joinToString(" · ")

    /** "BELGIUM · 80S · 128K" (picker result rows). */
    val infoLong: String
        get() = listOf(RadioCountries.nameFor(countryCode), genreLabel, if (bitrate > 0) "${bitrate}K" else "")
            .filter { it.isNotEmpty() }.joinToString(" · ")
}

data class RadioGenre(val label: String, /** Radio Browser tag; null = all. */ val tag: String?)

/** The seven genre chips of the STATIONS picker. */
val RadioGenres = listOf(
    RadioGenre("ALL", null),
    RadioGenre("80S", "80s"),
    RadioGenre("POP", "pop"),
    RadioGenre("ROCK", "rock"),
    RadioGenre("DANCE", "dance"),
    RadioGenre("NEWS", "news"),
    RadioGenre("JAZZ", "jazz"),
)

data class RadioCountry(val name: String, /** ISO code; null = all. */ val code: String?)

object RadioCountries {
    /** The COUNTRY key cycles through these; Belgium is the default. */
    val list = listOf(
        RadioCountry("ALL COUNTRIES", null),
        RadioCountry("BELGIUM", "BE"),
        RadioCountry("NETHERLANDS", "NL"),
        RadioCountry("GERMANY", "DE"),
        RadioCountry("UK", "GB"),
        RadioCountry("FRANCE", "FR"),
    )
    const val DEFAULT_INDEX = 1

    fun nameFor(code: String): String =
        list.firstOrNull { it.code.equals(code, ignoreCase = true) }?.name
            ?: code.uppercase(Locale.ROOT)
}

/** Radio Browser JSON → stations, and the small JSON form used to store presets. */
object RadioJson {

    /** Parse a /json/stations/... response. Skips entries without a name or stream URL. */
    fun parseStations(json: String): List<RadioStation> {
        val arr = JSONArray(json)
        val out = ArrayList<RadioStation>(arr.length())
        val seen = HashSet<String>()
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val s = parseStation(o) ?: continue
            if (seen.add(s.uuid)) out += s
        }
        return out
    }

    private fun parseStation(o: JSONObject): RadioStation? {
        val name = o.optString("name").trim()
        val url = o.optString("url_resolved").trim().ifEmpty { o.optString("url").trim() }
        val uuid = o.optString("stationuuid").trim().ifEmpty { url }
        if (name.isEmpty() || url.isEmpty() || !(url.startsWith("http://") || url.startsWith("https://"))) return null
        val tags = o.optString("tags").split(',').map { it.trim().lowercase(Locale.ROOT) }.filter { it.isNotEmpty() }
        return RadioStation(
            uuid = uuid,
            name = name,
            url = url,
            countryCode = o.optString("countrycode").trim().uppercase(Locale.ROOT),
            tags = tags,
            bitrate = o.optInt("bitrate", 0).coerceAtLeast(0),
        )
    }

    fun encode(s: RadioStation): String = JSONObject()
        .put("stationuuid", s.uuid)
        .put("name", s.name)
        .put("url_resolved", s.url)
        .put("countrycode", s.countryCode)
        .put("tags", s.tags.joinToString(","))
        .put("bitrate", s.bitrate)
        .toString()

    fun decode(json: String?): RadioStation? =
        json?.let { runCatching { parseStation(JSONObject(it)) }.getOrNull() }

    /** Six preset keys, empty ones as null. */
    fun encodePresets(presets: List<RadioStation?>): String =
        JSONArray().apply { presets.forEach { put(it?.let { s -> JSONObject(encode(s)) } ?: JSONObject.NULL) } }.toString()

    fun decodePresets(json: String?, size: Int = 6): List<RadioStation?> {
        val arr = json?.let { runCatching { JSONArray(it) }.getOrNull() }
        return List(size) { i -> arr?.optJSONObject(i)?.let { parseStation(it) } }
    }
}
