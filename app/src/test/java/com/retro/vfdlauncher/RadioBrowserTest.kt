package com.retro.vfdlauncher

import com.retro.vfdlauncher.data.netradio.RadioJson
import com.retro.vfdlauncher.data.netradio.RadioStation
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class RadioBrowserTest {

    private val sample = """
        [
          {"stationuuid":"a1","name":"  Retro   FM ","url":"http://x/retro","url_resolved":"https://cdn/retro.mp3",
           "countrycode":"be","tags":"80s, Pop,hits","bitrate":128},
          {"stationuuid":"b2","name":"News 24","url":"https://x/news","url_resolved":"",
           "countrycode":"BE","tags":"news","bitrate":0},
          {"stationuuid":"c3","name":"","url":"https://x/noname"},
          {"stationuuid":"d4","name":"Bad scheme","url_resolved":"rtsp://x/stream"},
          {"stationuuid":"a1","name":"Retro FM duplicate","url_resolved":"https://cdn/dup.mp3"},
          {"stationuuid":"e5","name":"Jazz Cave","url_resolved":"https://x/jazz","countrycode":"FR","tags":"","bitrate":-1}
        ]
    """.trimIndent()

    @Test
    fun parsesStationsAndSkipsBrokenOnes() {
        val list = RadioJson.parseStations(sample)
        assertEquals(listOf("a1", "b2", "e5"), list.map { it.uuid })
        val retro = list[0]
        assertEquals("https://cdn/retro.mp3", retro.url) // url_resolved wins
        assertEquals("BE", retro.countryCode)
        assertEquals(listOf("80s", "pop", "hits"), retro.tags)
        assertEquals(128, retro.bitrate)
        assertEquals("https://x/news", list[1].url) // falls back to url
        assertEquals(0, list[2].bitrate) // negative bitrate clamped
    }

    @Test
    fun labelsForTheVfd() {
        val (retro, news, jazz) = RadioJson.parseStations(sample)
        assertEquals("RETRO FM", retro.displayName)
        assertEquals("80S", retro.genreLabel)
        assertEquals("BE · 80S · 128K", retro.infoShort)
        assertEquals("BELGIUM · 80S · 128K", retro.infoLong)
        assertEquals("BE · NEWS", news.infoShort)
        assertEquals("FRANCE", jazz.infoLong)
    }

    @Test
    fun presetsRoundTrip() {
        val a = RadioStation("a1", "Retro FM", "https://cdn/retro.mp3", "BE", listOf("80s", "pop"), 128)
        val b = RadioStation("b2", "News 24", "https://x/news", "BE", listOf("news"), 64)
        val presets = listOf(a, null, b, null, null, null)
        val decoded = RadioJson.decodePresets(RadioJson.encodePresets(presets))
        assertEquals(presets, decoded)
    }

    @Test
    fun presetsSurviveMissingOrBrokenStorage() {
        assertEquals(List(6) { null }, RadioJson.decodePresets(null))
        assertEquals(List(6) { null }, RadioJson.decodePresets("not json"))
        // Older/shorter lists are padded to six keys.
        val one = RadioJson.decodePresets("""[{"stationuuid":"a","name":"A","url_resolved":"https://a"}]""")
        assertEquals(6, one.size)
        assertEquals("a", one[0]?.uuid)
        assertTrue(one.drop(1).all { it == null })
    }

    @Test
    fun lastStationRoundTrip() {
        val a = RadioStation("a1", "Retro FM", "https://cdn/retro.mp3", "BE", listOf("80s"), 128)
        assertEquals(a, RadioJson.decode(RadioJson.encode(a)))
        assertNull(RadioJson.decode(null))
        assertNull(RadioJson.decode("{}"))
    }
}
