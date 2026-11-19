package com.retro.vfdlauncher

import com.retro.vfdlauncher.data.media.Band
import com.retro.vfdlauncher.data.media.Tuning
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class TuningTest {
    @Test fun fmWithUnit() = assertEquals(Tuning(Band.FM, 101.1f), Tuning.parse("FM1 101.1 MHz"))
    @Test fun fmWithComma() = assertEquals(Tuning(Band.FM, 94.5f), Tuning.parse("Radio 2 - 94,5"))
    @Test fun fmTwoDecimals() = assertEquals("88.10", Tuning.parse("88.10")!!.display)
    @Test fun am() = assertEquals(Tuning(Band.AM, 1215f), Tuning.parse("AM 1215 kHz"))
    @Test fun songTitleIsNotAFrequency() = assertNull(Tuning.parse("Take On Me"))
    @Test fun versionNumberOutOfRange() = assertNull(Tuning.parse("Track 2.5"))
    @Test fun firstMatchingField() = assertEquals(Tuning(Band.FM, 107.6f), Tuning.parse(null, "A-ha", "107.6"))
}
