package com.retro.vfdlauncher.data.media

import java.util.Locale

enum class Band { FM, AM }

data class Tuning(val band: Band, val value: Float) {
    /** "101.10" for FM in MHz, "1215" for AM in kHz. */
    val display: String
        get() = if (band == Band.FM) String.format(Locale.US, "%.2f", value) else value.toInt().toString()
    val unit: String get() = if (band == Band.FM) "MHz" else "kHz"

    fun matches(other: Tuning?): Boolean =
        other != null && other.band == band && kotlin.math.abs(other.value - value) < 0.05f

    companion object {
        private val fmRegex = Regex("""(?<![\d.,])(\d{2,3})[.,](\d{1,2})(?![\d.,])""")
        private val amRegex = Regex("""(?<!\d)(\d{3,4})\s*(?:kHz|KHz|KHZ|khz|AM)\b""")

        /** Pulls an FM/AM frequency out of free text such as "FM1 101.1 MHz" or "RADIO 2 - 94,5". */
        fun parse(text: String?): Tuning? {
            if (text.isNullOrBlank()) return null
            fmRegex.findAll(text).forEach { m ->
                val v = "${m.groupValues[1]}.${m.groupValues[2]}".toFloatOrNull()
                if (v != null && v in 64f..108.1f) return Tuning(Band.FM, v)
            }
            amRegex.findAll(text).forEach { m ->
                val v = m.groupValues[1].toFloatOrNull()
                if (v != null && v in 150f..1710f) return Tuning(Band.AM, v)
            }
            return null
        }

        fun parse(vararg texts: String?): Tuning? = texts.firstNotNullOfOrNull { parse(it) }
    }
}

data class QueueEntry(val id: Long, val title: String, val tuning: Tuning?)

data class NowPlaying(
    val packageName: String,
    val appLabel: String,
    val title: String?,
    val artist: String?,
    val album: String?,
    val isPlaying: Boolean,
    /** Frequency found in the metadata; null for music sources. */
    val tuning: Tuning?,
    val isRadio: Boolean,
    val positionMs: Long,
    /** SystemClock.elapsedRealtime() at which [positionMs] was valid. */
    val positionUpdatedAt: Long,
    val speed: Float,
    val durationMs: Long,
    val queue: List<QueueEntry>,
    val actions: Long,
) {
    /** The amber line under the big digits: RDS text for radio, "TITLE · ARTIST" for music. */
    val infoLine: String
        get() = listOfNotNull(title, artist)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" · ")
            .uppercase(Locale.getDefault())
}
