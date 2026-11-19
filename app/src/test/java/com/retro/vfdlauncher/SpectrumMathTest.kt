package com.retro.vfdlauncher

import com.retro.vfdlauncher.data.audio.SpectrumMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SpectrumMathTest {

    private val size = 1024
    private val rate = 44_100_000 // Visualizer reports milliHz

    /** FFT bytes with one loud bin at [hz]. */
    private fun tone(hz: Float, amp: Int = 127): ByteArray {
        val fft = ByteArray(size)
        val k = (hz / (rate / 1000f / size)).toInt()
        fft[2 * k] = amp.toByte()
        return fft
    }

    @Test
    fun silenceIsAllZero() {
        val b = SpectrumMath.bands(ByteArray(size), rate, 14)
        assertTrue(b.all { it == 0f })
    }

    @Test
    fun bassToneLightsLowBandOnly() {
        val b = SpectrumMath.bands(tone(100f), rate, 14)
        val loudest = b.indices.maxByOrNull { b[it] }!!
        assertTrue("loudest band $loudest", loudest <= 2)
        assertEquals(0f, b[13], 0f)
    }

    @Test
    fun trebleToneLightsHighBand() {
        val b = SpectrumMath.bands(tone(10_000f), rate, 14)
        val loudest = b.indices.maxByOrNull { b[it] }!!
        assertTrue("loudest band $loudest", loudest >= 11)
        assertTrue(b[loudest] > 0.9f)
    }
}
