package com.retro.vfdlauncher

import com.retro.vfdlauncher.data.audio.EqJson
import org.junit.Assert.assertEquals
import org.junit.Test

class EqJsonTest {
    @Test
    fun parsesBandsInOrder() {
        val json = """[{"freq":24,"q":3,"gain":17},{"freq":30,"q":3.0,"gain":15},{"freq":40,"q":2.5,"gain":"12"}]"""
        val bands = EqJson.parse(json, 0)
        assertEquals(3, bands.size)
        assertEquals(listOf(0, 1, 2), bands.map { it.index })
        assertEquals(listOf(24, 30, 40), bands.map { it.freq })
        assertEquals(listOf(2, 0, -3), bands.map { it.db })
        assertEquals(2.5f, bands[2].q)
    }

    @Test
    fun usesExplicitIndexAndChannel() {
        val bands = EqJson.parse("""{"bands":[{"index":5,"freq":95,"q":3,"gain":20,"channel":1}]}""", 0)
        assertEquals(5, bands.single().index)
        assertEquals(1, bands.single().channel)
    }

    @Test
    fun ignoresGarbage() {
        assertEquals(0, EqJson.parse("not json", 0).size)
    }
}
