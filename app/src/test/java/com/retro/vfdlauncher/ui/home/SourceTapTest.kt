package com.retro.vfdlauncher.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class SourceTapTest {
    @Test
    fun leavingPhoneOnlySwitchesTheView() {
        assertEquals(SourceTap.SHOW_ONLY, SourceTap.decide(fromPhone = true, hasSession = true))
        assertEquals(SourceTap.SHOW_ONLY, SourceTap.decide(fromPhone = true, hasSession = false))
    }

    @Test
    fun tabTapOutsidePhonePlaysOrOpensTheApp() {
        assertEquals(SourceTap.PLAY, SourceTap.decide(fromPhone = false, hasSession = true))
        assertEquals(SourceTap.OPEN_APP, SourceTap.decide(fromPhone = false, hasSession = false))
    }
}
