package com.retro.vfdlauncher

import com.retro.vfdlauncher.ui.home.BarPhysics
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BarPhysicsTest {

    @Test
    fun pausedFullBarSinksSlowlyToZero() {
        val speed = FloatArray(1)
        var level = 1f
        var t = 0f
        var halfAt = -1f
        while (level > 0f && t < 5f) {
            val next = BarPhysics.step(level, speed, 0, target = 0f, dt = 1f / 30)
            assertTrue("never rises while falling", next <= level)
            level = next
            t += 1f / 30
            if (halfAt < 0 && level <= 0.5f) halfAt = t
        }
        assertEquals(0f, level, 0f)
        assertTrue("slow, not instant: half way after $halfAt s", halfAt > 0.8f)
        assertTrue("empty within 2 s, took $t s", t in 1.2f..2.0f)
    }

    @Test
    fun louderTargetJumpsUpAtOnce() {
        val speed = FloatArray(1) { 0.5f }
        assertEquals(0.8f, BarPhysics.step(0.2f, speed, 0, target = 0.8f, dt = 1f / 30), 0f)
        assertEquals(0f, speed[0], 0f)
    }
}
