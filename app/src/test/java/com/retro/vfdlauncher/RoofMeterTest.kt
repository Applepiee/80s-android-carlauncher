package com.retro.vfdlauncher

import com.retro.vfdlauncher.data.weather.HourSample
import com.retro.vfdlauncher.data.weather.RainSample
import com.retro.vfdlauncher.data.weather.RoofInputs
import com.retro.vfdlauncher.data.weather.RoofFx
import com.retro.vfdlauncher.data.weather.RoofMeter
import com.retro.vfdlauncher.data.weather.RoofTier
import com.retro.vfdlauncher.data.weather.WeatherRepository
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class RoofMeterTest {

    private val now = LocalDateTime.of(2026, 6, 20, 14, 10)

    private fun inputs(
        feels: Double = 22.0,
        rainChance: Int = 0,
        gusts: Double = 15.0,
        uv: Double = 3.0,
        precipNow: Double = 0.0,
        code: Int = 1,
        rainFrom: LocalDateTime? = null,
    ) = RoofInputs(
        utcOffsetSeconds = 7200,
        temp = feels + 1,
        feelsLikeNow = feels,
        precipitationNow = precipNow,
        weatherCode = code,
        gustsNow = gusts,
        uvNow = uv,
        hours = (0..3).map { h -> HourSample(now.withMinute(0).plusHours(h.toLong()), rainChance, feels, gusts, uv) },
        quarters = (0..12).map { q ->
            val t = now.withMinute(0).plusMinutes(15L * q)
            RainSample(t, if (rainFrom != null && !t.isBefore(rainFrom)) 0.4 else 0.0)
        },
    )

    @Test
    fun perfectSummerAfternoon() {
        val v = RoofMeter.evaluate(inputs(), now)
        assertEquals(10, v.score)
        assertTrue(v.roofDown)
        assertEquals(RoofTier.GOOD, v.tier)
        assertEquals("GO TOPLESS!", v.verdict)
    }

    @Test
    fun coolButDryMeansHeaterOn() {
        val v = RoofMeter.evaluate(inputs(feels = 15.0), now) // -2
        assertEquals(8, v.score)
        assertEquals("TOP DOWN, HEATER ON", v.verdict)
        assertEquals(RoofFx.BREEZE, v.fx) // cool: no sun
    }

    @Test
    fun rainLaterStillTopDownTillThen() {
        val v = RoofMeter.evaluate(inputs(rainChance = 30, rainFrom = now.withMinute(0).plusMinutes(150)), now)
        assertEquals(9, v.score)
        assertEquals("TOP DOWN TILL 16:30", v.verdict)
    }

    @Test
    fun likelyRainSoonMeansRoofUpWithTime() {
        val v = RoofMeter.evaluate(inputs(feels = 13.0, rainChance = 70, rainFrom = now.withMinute(45)), now)
        assertEquals(0, v.score) // 10 - 6 (rain) - 4 (cold)
        assertFalse(v.roofDown)
        assertEquals(RoofTier.BAD, v.tier)
        assertEquals("ROOF UP · RAIN 14:45", v.verdict)
    }

    @Test
    fun rainingNowCapsAtOne() {
        val v = RoofMeter.evaluate(inputs(precipNow = 0.6, code = 61), now)
        assertEquals(1, v.score)
        assertEquals("ROOF UP · RAINING", v.verdict)
    }

    @Test
    fun gustsAndHeat() {
        val v = RoofMeter.evaluate(inputs(feels = 32.0, gusts = 55.0), now) // -2 heat, -2 gusts
        assertEquals(6, v.score)
        assertEquals(RoofTier.OK, v.tier)
        assertEquals("TOP DOWN, SUNSCREEN", v.verdict)
    }

    /** Shape of a real Open-Meteo response (Brussels, a wet November evening). */
    @Test
    fun parsesOpenMeteoResponse() {
        val json = """{"utc_offset_seconds":3600,
          "current":{"time":"2026-11-19T17:45","temperature_2m":10.2,"apparent_temperature":7.5,"precipitation":0.0,"weather_code":3,"wind_gusts_10m":33.8,"uv_index":0.0,"is_day":0},
          "minutely_15":{"time":["2026-11-19T17:45","2026-11-19T18:00","2026-11-19T18:15","2026-11-19T18:30","2026-11-19T18:45","2026-11-19T19:00","2026-11-19T19:15"],"precipitation":[0,0,0,0,0,0,0.1]},
          "hourly":{"time":["2026-11-19T17:00","2026-11-19T18:00","2026-11-19T19:00","2026-11-19T20:00"],"precipitation_probability":[91,77,69,72],"apparent_temperature":[7.2,7.6,7.3,6.8],"wind_gusts_10m":[37.4,33.1,32.4,36.7],"uv_index":[0.05,0,0,0]},
          "daily":{"time":["2026-11-19","2026-11-20"],"weather_code":[61,63],"temperature_2m_max":[10.9,10.0]}}"""
        val w = WeatherRepository.parse(json, 0L)
        val v = RoofMeter.evaluate(w.roof!!, LocalDateTime.of(2026, 11, 19, 17, 50))
        assertEquals(0, v.score) // rain 91% -6, feels 7 -6, gusts 37 -1
        assertEquals("ROOF UP · RAIN 19:15", v.verdict)
        assertEquals(10, v.temp)
    }

    @Test
    fun oldCacheWithoutForecastHasNoMeter() {
        val json = """{"current":{"temperature_2m":10.2,"weather_code":3},"daily":{"time":["2026-11-19"],"weather_code":[61],"temperature_2m_max":[10.9]}}"""
        assertEquals(null, WeatherRepository.parse(json, 0L).roof)
        JSONObject(json) // well-formed
    }

    @Test
    fun warmLightRainKeepsRoofDown() {
        val v = RoofMeter.evaluate(
            inputs(feels = 22.0, rainChance = 80, precipNow = 0.2, code = 61, rainFrom = now.withMinute(0)).let { i ->
                i.copy(quarters = i.quarters.map { it.copy(mm = if (it.mm > 0) 0.2 else 0.0) })
            },
            now,
        )
        assertEquals(5, v.score)
        assertTrue(v.roofDown)
        assertEquals(RoofTier.OK, v.tier)
        assertEquals(RoofFx.SPEED_RAIN, v.fx)
        assertEquals("SPEED KEEPS YOU DRY", v.verdict)
    }

    @Test
    fun warmButPouringIsRoofUp() {
        val v = RoofMeter.evaluate(inputs(feels = 22.0, rainChance = 90, precipNow = 1.5, code = 65, rainFrom = now.withMinute(0)), now)
        assertEquals(1, v.score)
        assertEquals("ROOF UP · RAINING", v.verdict)
        assertEquals(RoofFx.RAIN, v.fx)
    }

    @Test
    fun coldDrizzleIsRoofUp() {
        val v = RoofMeter.evaluate(inputs(feels = 12.0, rainChance = 80, precipNow = 0.2, code = 51), now)
        assertFalse(v.roofDown)
        assertEquals("ROOF UP · RAINING", v.verdict)
    }

    @Test
    fun coldButDryShowsNoRain() {
        val v = RoofMeter.evaluate(inputs(feels = 6.0), now) // -6: roof up, dry
        assertFalse(v.roofDown)
        assertEquals(RoofFx.NONE, v.fx)
        assertEquals("ROOF UP · TOO COLD", v.verdict)
    }

    @Test
    fun speedDryBeforeRainSaysWhenItStarts() {
        val base = inputs(feels = 22.0, rainChance = 70, rainFrom = now.withMinute(45))
        val light = base.copy(quarters = base.quarters.map { it.copy(mm = if (it.mm > 0) 0.2 else 0.0) })
        val v = RoofMeter.evaluate(light, now)
        assertEquals("SPEED KEEPS YOU DRY", v.verdict)
        assertEquals("RAIN FROM 14:45", v.detail)
    }

    @Test
    fun speedDryWhileRainingSaysWhenItEnds() {
        val base = inputs(feels = 22.0, rainChance = 70, precipNow = 0.2, code = 61)
        // Light rain 14:00-15:15, dry from 15:30.
        val q = base.quarters.map { it.copy(mm = if (it.time.isBefore(now.withMinute(0).plusMinutes(90))) 0.2 else 0.0) }
        val v = RoofMeter.evaluate(base.copy(quarters = q), now)
        assertEquals("SPEED KEEPS YOU DRY", v.verdict)
        assertEquals("RAIN ENDS 15:30", v.detail)
    }

    @Test
    fun roofUpRainingSaysWhenItEndsOrPast() {
        val base = inputs(feels = 12.0, rainChance = 90, precipNow = 1.2, code = 63)
        val ends = base.copy(quarters = base.quarters.map { it.copy(mm = if (it.time.isBefore(now.withMinute(0).plusMinutes(60))) 1.2 else 0.0) })
        assertEquals("RAIN ENDS 15:00", RoofMeter.evaluate(ends, now).detail)
        val allWet = base.copy(quarters = base.quarters.map { it.copy(mm = 1.2) })
        val v = RoofMeter.evaluate(allWet, now)
        assertEquals("ROOF UP · RAINING", v.verdict)
        assertEquals("RAIN PAST 17:15", v.detail) // last 15-min slot 17:00 ends 17:15
    }

    @Test
    fun dryVerdictsHaveNoSecondLine() {
        assertEquals(null, RoofMeter.evaluate(inputs(), now).detail)
    }

    @Test
    fun freezingDryDayScoresZero() {
        val v = RoofMeter.evaluate(inputs(feels = -2.0), now) // was 4/10 before
        assertEquals(0, v.score)
        assertFalse(v.roofDown)
        assertEquals(RoofTier.BAD, v.tier)
        assertEquals("ROOF UP · TOO COLD", v.verdict)
    }

    @Test
    fun coldScalesDownSteeply() {
        assertEquals(4, RoofMeter.evaluate(inputs(feels = 8.0), now).score)
        assertEquals(2, RoofMeter.evaluate(inputs(feels = 4.0), now).score)
        assertEquals(0, RoofMeter.evaluate(inputs(feels = 1.0), now).score)
    }

    @Test
    fun windChillBelowAirTemperatureCounts() {
        // Air 5 °C, but gusts make it feel like 0 °C.
        val v = RoofMeter.evaluate(inputs(feels = 0.0, gusts = 40.0).copy(temp = 5.0), now)
        assertEquals(0, v.score)
    }

    @Test
    fun snowIsAlwaysZero() {
        val v = RoofMeter.evaluate(inputs(feels = 3.0, code = 73), now)
        assertEquals(0, v.score)
        assertFalse(v.roofDown)
    }
}
