package com.technomancer.pangu.logic.flow

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class ScreenMachineTest {
    @Test
    fun c02PlaceHereSavesAndAdvances() {
        val m = ScreenMachine(ScreenId.C02)
        assertTrue(m.isPlacingPad)
        assertEquals(ScreenEffect.ConfirmPad, m.primary())
        assertEquals(ScreenId.C03, m.current)
        assertFalse(m.isPlacingPad)
    }

    @Test
    fun pauseShowsC34ResumeRestores() {
        val m = ScreenMachine(ScreenId.C02)
        m.pause()
        assertEquals(ScreenId.C34, m.current)
        m.pause()
        m.resume()
        assertEquals(ScreenId.C02, m.current)
    }

    @Test
    fun recenterShowsC37ThenReturns() {
        val m = ScreenMachine(ScreenId.C03)
        m.recenter()
        assertEquals(ScreenId.C37, m.current)
        assertTrue(m.isPlacingPad)
        assertEquals(ScreenEffect.ConfirmPad, m.primary())
        assertEquals(ScreenId.C03, m.current)
    }

    @Test
    fun placingScreensHaveContent() {
        for (id in listOf(ScreenId.C02, ScreenId.C34, ScreenId.C37)) {
            assertEquals("scr.${id.name}.t", ScreenContent.of(id)!!.titleKey)
        }
    }
}
