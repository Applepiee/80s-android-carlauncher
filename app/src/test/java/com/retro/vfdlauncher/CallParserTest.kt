package com.retro.vfdlauncher

import com.retro.vfdlauncher.data.phone.ActionInfo
import com.retro.vfdlauncher.data.phone.CallParser
import com.retro.vfdlauncher.data.phone.CallStage
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CallParserTest {

    private fun acts(vararg t: String) = t.map { ActionInfo(it) }

    @Test
    fun ringingWithButtons() {
        val (stage, name, number) = CallParser.read("Sarah", "Incoming call", acts("Decline", "Answer"))
        assertEquals(CallStage.RINGING, stage)
        assertEquals("Sarah", name)
        assertNull(number)
        assertEquals(1, CallParser.answerIndex(acts("Decline", "Answer")))
        assertEquals(0, CallParser.hangUpIndex(acts("Decline", "Answer")))
    }

    @Test
    fun dutchActiveCallWithNumberOnly() {
        val (stage, name, number) = CallParser.read("+32 470 12 34 56", "Lopend gesprek", acts("Ophangen"))
        assertEquals(CallStage.ACTIVE, stage)
        assertEquals("+32 470 12 34 56", name)
        assertNull(number)
    }

    @Test
    fun incomingWithoutButtonsIsTheFallback() {
        val (stage, name, number) = CallParser.read("Incoming call", "+32 475 98 76 54", emptyList())
        assertEquals(CallStage.RINGING_NO_BUTTONS, stage)
        assertEquals("+32 475 98 76 54", name)
        assertNull(number)
    }

    @Test
    fun outgoing() {
        val (stage, name, _) = CallParser.read("Mom", "Calling…", acts("Hang up"))
        assertEquals(CallStage.CALLING, stage)
        assertEquals("Mom", name)
    }

    @Test
    fun recognisesCallNotifications() {
        assertTrue(CallParser.isCall("call", "com.anything", ongoing = false, actions = emptyList()))
        assertTrue(CallParser.isCall(null, "com.atoto.btphone", ongoing = true, actions = acts("Answer")))
        assertFalse(CallParser.isCall(null, "com.atoto.btphone", ongoing = true, actions = acts("Play", "Next")))
        assertFalse(CallParser.isCall(null, "com.google.android.apps.maps", ongoing = true, actions = acts("Exit")))
    }
}
