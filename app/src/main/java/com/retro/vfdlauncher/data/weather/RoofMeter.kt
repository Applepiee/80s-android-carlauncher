package com.retro.vfdlauncher.data.weather

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import kotlin.math.roundToInt

/** One hour of the Open-Meteo forecast. [time] is local time at the car's location. */
data class HourSample(
    val time: LocalDateTime,
    val rainProbability: Int,
    val feelsLike: Double,
    val gusts: Double,
    val uv: Double,
)

/** Precipitation (mm) in one 15-minute slot starting at [time]. */
data class RainSample(val time: LocalDateTime, val mm: Double)

/** Everything the TOP-DOWN meter needs, kept with the cached weather. */
data class RoofInputs(
    val utcOffsetSeconds: Int,
    val temp: Double,
    val feelsLikeNow: Double,
    val precipitationNow: Double,
    val weatherCode: Int,
    val gustsNow: Double,
    val uvNow: Double,
    val hours: List<HourSample>,
    val quarters: List<RainSample>,
)

enum class RoofTier { GOOD, OK, BAD }

/** What animates around the car: rain (now or within the hour), sun (roof down, dry) or nothing. */
enum class RoofFx { RAIN, SPEED_RAIN, SUN, NONE }

data class RoofVerdict(
    val score: Int,
    val temp: Int,
    val verdict: String,
    val roofDown: Boolean,
    val tier: RoofTier,
    val fx: RoofFx = if (roofDown) RoofFx.SUN else RoofFx.NONE,
)

/**
 * TOP-DOWN meter: should the MR2's roof go down? Starts at 10 and loses points.
 *
 * - Rain: raining now caps the score at 1. Highest rain chance over the next 2 hours:
 *   60%+ costs 6, 40-60% costs 3, 20-40% costs 1.
 * - "Feels like" temperature, now and over the next hour: 18-26 °C is ideal; 14-18 costs 2,
 *   10-14 costs 4, below 10 costs 6; above 30 costs 2.
 * - Gusts (now and next hour): over 35 km/h costs 1, over 50 km/h costs 2.
 *
 * Roof drawn down from 5; 7+ is teal, 4-6 amber, under 4 red. The verdict looks up to 3 hours
 * ahead for the time rain starts (15-minute forecast).
 *
 * The middle case: warm (feels like 18 C+ now and next hour), only light rain (at most 0.5 mm per
 * quarter hour now and in the next hour), no thunderstorm or downpour, gusts at most 50 km/h.
 * In an MR2 at speed light rain flies over the cabin, so: roof down with rain, score capped at 5,
 * "SPEED KEEPS YOU DRY".
 */
object RoofMeter {

    private val clock = DateTimeFormatter.ofPattern("HH:mm")

    /** WMO codes that mean it is raining (drizzle, rain, showers, thunderstorm). */
    private fun isRainCode(code: Int) = code in 51..67 || code in 80..82 || code in 95..99

    /** Heavy rain, freezing rain, violent showers or thunderstorms: never a top-down drive. */
    private fun isHeavyCode(code: Int) = code in setOf(65, 66, 67, 82) || code in 95..99

    /** At most this much rain per 15 minutes (about 2 mm/h) counts as light. */
    private const val LIGHT_RAIN_MM = 0.5

    fun evaluate(i: RoofInputs, now: LocalDateTime): RoofVerdict {
        val hourStart = now.withMinute(0).withSecond(0).withNano(0)
        val nextHour = i.hours.filter { !it.time.isBefore(hourStart) && !it.time.isAfter(now.plusHours(1)) }
        val nextTwoHours = i.hours.filter { !it.time.isBefore(hourStart) && it.time.isBefore(now.plusHours(2)) }

        val rainingNow = i.precipitationNow >= 0.1 || isRainCode(i.weatherCode)
        val rainChance = nextTwoHours.maxOfOrNull { it.rainProbability } ?: 0
        val feels = listOf(i.feelsLikeNow) + nextHour.map { it.feelsLike }
        val coldest = feels.min()
        val warmest = feels.max()
        val gusts = (listOf(i.gustsNow) + nextHour.map { it.gusts }).max()
        val uv = (listOf(i.uvNow) + nextHour.map { it.uv }).max()

        var score = 10
        score -= rainPenalty(rainChance)
        score -= when {
            coldest < 10 -> 6
            coldest < 14 -> 4
            coldest < 18 -> 2
            else -> 0
        }
        if (warmest > 30) score -= 2
        score -= when {
            gusts > 50 -> 2
            gusts > 35 -> 1
            else -> 0
        }
        score = score.coerceIn(0, 10)

        val rainAt = rainStart(i, now)
        val nextHourRain = i.quarters.filter { it.time.plusMinutes(15).isAfter(now) && it.time.isBefore(now.plusHours(1)) }
        val wetSoon = rainingNow || (rainAt != null && rainAt.isBefore(now.plusHours(1)))
        val lightOnly = i.precipitationNow <= LIGHT_RAIN_MM && nextHourRain.all { it.mm <= LIGHT_RAIN_MM }
        val speedKeepsDry = wetSoon && lightOnly && coldest >= 18 && gusts <= 50 && !isHeavyCode(i.weatherCode)
        if (speedKeepsDry) {
            // Score as if dry (comfort only), then cap it in the amber middle with the roof down.
            val dryScore = (score + rainPenalty(rainChance)).coerceIn(0, 10)
            if (dryScore >= 5) {
                return RoofVerdict(
                    score = minOf(dryScore, 5),
                    temp = i.temp.roundToInt(),
                    verdict = "SPEED KEEPS YOU DRY",
                    roofDown = true,
                    tier = RoofTier.OK,
                    fx = RoofFx.SPEED_RAIN,
                )
            }
        }
        if (rainingNow) score = minOf(score, 1)

        val roofDown = score >= 5
        val verdict = when {
            rainingNow -> "ROOF UP · RAINING"
            !roofDown && rainAt != null -> "ROOF UP · RAIN ${rainAt.format(clock)}"
            roofDown && rainAt != null -> "TOP DOWN TILL ${rainAt.format(clock)}"
            !roofDown && coldest < 14 -> "ROOF UP · TOO COLD"
            !roofDown && gusts > 50 -> "ROOF UP · STORMY"
            !roofDown -> "ROOF UP"
            coldest < 18 -> "TOP DOWN, HEATER ON"
            warmest > 30 || uv >= 6 -> "TOP DOWN, SUNSCREEN"
            gusts > 35 -> "TOP DOWN, WINDY"
            score >= 8 -> "GO TOPLESS!"
            else -> "TOP DOWN"
        }
        return RoofVerdict(
            score = score,
            temp = i.temp.roundToInt(),
            verdict = verdict,
            roofDown = roofDown,
            tier = when {
                score >= 7 -> RoofTier.GOOD
                score >= 4 -> RoofTier.OK
                else -> RoofTier.BAD
            },
            fx = when {
                wetSoon -> RoofFx.RAIN
                roofDown -> RoofFx.SUN
                else -> RoofFx.NONE // roof up because it is cold or stormy, but dry
            },
        )
    }

    private fun rainPenalty(chance: Int) = when {
        chance >= 60 -> 6
        chance >= 40 -> 3
        chance >= 20 -> 1
        else -> 0
    }

    /**
     * When rain starts within 3 hours: the first 15-minute slot with 0.1 mm or more, else the
     * first hour with a 50%+ chance. Null if it stays dry (or it is already raining).
     */
    fun rainStart(i: RoofInputs, now: LocalDateTime): LocalDateTime? {
        val until = now.plusHours(3)
        val slot = i.quarters.firstOrNull { it.mm >= 0.1 && it.time.plusMinutes(15).isAfter(now) && it.time.isBefore(until) }
        if (slot != null) return if (slot.time.isAfter(now)) slot.time else now
        return i.hours.firstOrNull { it.rainProbability >= 50 && it.time.isAfter(now) && it.time.isBefore(until) }?.time
    }
}
