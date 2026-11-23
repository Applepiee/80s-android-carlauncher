package com.retro.vfdlauncher

import com.retro.vfdlauncher.data.netradio.RadioStation
import com.retro.vfdlauncher.data.netradio.stepStation
import org.junit.Assert.assertEquals
import org.junit.Test

class StationStepTest {

    private fun st(id: String) = RadioStation(id, "Station $id", "http://example.invalid/$id", "BE", emptyList(), 128)
    private val list = listOf(st("a"), st("b"), st("c"))

    @Test
    fun nextAndPrevWrapAround() {
        assertEquals("b", stepStation(list, "a", 1).uuid)
        assertEquals("a", stepStation(list, "c", 1).uuid)
        assertEquals("c", stepStation(list, "a", -1).uuid)
    }

    @Test
    fun startsAtTheEndsWhenTheCurrentStationIsNotInTheList() {
        assertEquals("a", stepStation(list, "zzz", 1).uuid)
        assertEquals("c", stepStation(list, null, -1).uuid)
    }
}
