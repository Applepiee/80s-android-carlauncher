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

data class CustomAction(val action: String, val name: String)

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
    val activeQueueId: Long,
    val actions: Long,
    val customActions: List<CustomAction>,
    /** MediaController.getQueueTitle(): the playlist/album name, when the app publishes one. */
    val queueTitle: String? = null,
) {
    /**
     * YouTube Music (and apps whose queue is titled "Up next") publish only a sliding window of
     * the next ~10 songs, not the whole playlist, so a position in it would be wrong.
     */
    val queueIsWindow: Boolean
        get() = "youtube" in packageName ||
            queueTitle?.trim()?.lowercase(Locale.ROOT) in setOf("up next", "playing next", "volgende", "als volgende")

    /** "3/12" when the session publishes a complete queue that contains the current item. */
    val queuePosition: String?
        get() {
            if (queueIsWindow) return null
            val i = queue.indexOfFirst { it.id == activeQueueId }
            return if (i >= 0) "${i + 1}/${queue.size}" else null
        }

    /** Title of the song after the current one in the published queue, if any. */
    val nextTitle: String?
        get() {
            val i = queue.indexOfFirst { it.id == activeQueueId }
            return queue.getOrNull(i + 1)?.title?.takeIf { i >= 0 && it.isNotBlank() }
        }

    /** Station name for radio (RDS PS or the app's title), without a bare frequency. */
    val stationName: String?
        get() = listOfNotNull(title, artist)
            .map { it.trim() }
            .firstOrNull { it.isNotEmpty() && !bareFrequency.matches(it) }
            ?.uppercase(Locale.getDefault())

    private companion object {
        val bareFrequency = Regex("""(?i)^(fm|am)?\s*\d{2,4}([.,]\d{1,2})?\s*(mhz|khz)?$""")
    }

    /** First custom action whose id or label contains one of [words] (case-insensitive). */
    fun findCustomAction(vararg words: String): CustomAction? = customActions.firstOrNull { a ->
        val hay = (a.action + " " + a.name).lowercase(Locale.ROOT)
        words.any { it in hay }
    }

    /** The amber line under the big digits: RDS text for radio, "TITLE · ARTIST" for music. */
    val infoLine: String
        get() = listOfNotNull(title, artist)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" · ")
            .uppercase(Locale.getDefault())
}
