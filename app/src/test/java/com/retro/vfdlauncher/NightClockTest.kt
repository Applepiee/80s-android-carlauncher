package com.retro.vfdlauncher

import com.retro.vfdlauncher.data.night.NightClock
import com.retro.vfdlauncher.data.night.SunDay
import com.retro.vfdlauncher.data.weather.WeatherRepository
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDateTime

class NightClockTest {

    private fun t(d: Int, h: Int, m: Int = 0) = LocalDateTime.of(2026, 11, d, h, m)
    private val days = listOf(SunDay(t(22, 8, 21), t(22, 16, 47)), SunDay(t(23, 8, 22), t(23, 16, 46)))

    @Test
    fun dayAndNightFromSunTimes() {
        assertTrue(NightClock.isNight(days, t(22, 7, 0)))
        assertFalse(NightClock.isNight(days, t(22, 12, 0)))
        assertTrue(NightClock.isNight(days, t(22, 16, 47)))
        assertTrue(NightClock.isNight(days, t(22, 23, 30)))
    }

    @Test
    fun nextSwitchIsTheNextSunriseOrSunset() {
        assertEquals(t(22, 16, 47), NightClock.nextSwitch(days, t(22, 12, 0)))
        assertEquals(t(23, 8, 22), NightClock.nextSwitch(days, t(22, 20, 0)))
    }

    @Test
    fun withoutDataNightIs19To7() {
        assertTrue(NightClock.isNight(emptyList(), t(22, 20, 0)))
        assertFalse(NightClock.isNight(emptyList(), t(22, 10, 0)))
    }

    @Test
    fun parsesSunriseSunsetFromTheForecast() {
        val json = """{"utc_offset_seconds":3600,"current":{"temperature_2m":5.0,"weather_code":3},
          "daily":{"time":["2026-11-22","2026-11-23"],"weather_code":[3,3],"temperature_2m_max":[7,8],
          "sunrise":["2026-11-22T08:21","2026-11-23T08:22"],"sunset":["2026-11-22T16:47","2026-11-23T16:46"]}}"""
        val w = WeatherRepository.parse(json, 0L)
        assertEquals(days, w.sun)
        assertEquals(3600, w.utcOffsetSeconds)
    }
}
