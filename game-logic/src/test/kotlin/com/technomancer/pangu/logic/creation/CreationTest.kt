package com.technomancer.pangu.logic.creation

import com.technomancer.pangu.logic.flow.FlowMachine
import com.technomancer.pangu.logic.flow.FlowState
import com.technomancer.pangu.logic.flow.ScreenEffect
import com.technomancer.pangu.logic.flow.ScreenId
import com.technomancer.pangu.logic.flow.ScreenMachine
import com.technomancer.pangu.logic.personality.SparkValue
import com.technomancer.pangu.logic.time.FixedClock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class NameWheelsTest {
    private val wheels = NameWheels()

    @Test
    fun defaultBobabo() {
        assertEquals("Bobabo", wheels.name(LookCatalog().default()))
    }

    @Test
    fun cycleWrapsEmptyShowsDot() {
        var look = PetLook(a = 7, b = 0, c = 7)
        look = wheels.cycle(look, 0)
        assertEquals(0, look.a)
        look = wheels.cycle(look, 2)
        assertEquals(0, look.c)
        assertEquals(NameWheels.EMPTY, wheels.display(2, look.c))
        assertEquals("Boba", wheels.name(look))
        assertEquals("ba", wheels.display(1, 0))
    }
}

class LookCatalogTest {
    @Test
    fun swatchesShapesAndDefaultsMatchMockup() {
        val c = LookCatalog()
        assertEquals(
            listOf(0xFFFFFF, 0xEDB9B1, 0xA8BFD4, 0xBFD3AE, 0xF2DE9B, 0xC9B7DA, 0xD9644F, 0x6E7680),
            c.swatches.toList(),
        )
        assertEquals(listOf("ball", "star", "heart", "spiral", "flower", "bolt"), LookCatalog.SHAPES)
        val d = c.default()
        assertEquals(0xEDB9B1, c.bodyColor(d)) // Chalk pink
        assertEquals(0xD9644F, c.tipColor(d)) // Tomato
        assertEquals("heart", LookCatalog.SHAPES[d.shape])
    }
}

class KeeperIdsTest {
    @Test
    fun generatedOnce() {
        val clock = FixedClock(1_767_225_600_123L)
        val first = KeeperIds.ensure(null, clock)
        assertTrue(first.keeperId in 0..0xFFFF && first.secretId in 0..0xFFFF)
        clock.advanceMillis(5_000L)
        assertEquals(first, KeeperIds.ensure(first, clock))
        assertNotEquals(first, KeeperIds.ensure(null, clock))
    }
}

class FlowMachineTest {
    @Test
    fun firstLaunchC01C02C03() {
        val screens = ScreenMachine(ScreenId.C01)
        val flow = FlowMachine(screens)
        assertEquals(FlowState.Onboarding, flow.flow)
        flow.continueFromLanguage()
        assertEquals(ScreenId.C02, screens.current)
        assertEquals(ScreenEffect.ConfirmPad, screens.primary())
        flow.padConfirmed()
        assertEquals(ScreenId.C03, screens.current)
        assertEquals(FlowState.Creation, flow.flow)
    }

    @Test
    fun setEggIncubationRollsSV() {
        val screens = ScreenMachine(ScreenId.C03)
        val flow = FlowMachine(screens)
        flow.flow = FlowState.Creation
        assertTrue(flow.toName())
        assertTrue(flow.toConfirm())
        val ids = KeeperIds(0x1234, 0xBEEF)
        val sv = flow.setEgg(1_767_225_600_000L, ids)
        assertEquals(SparkValue.roll(1_767_225_600_000L, ids), sv)
        assertTrue(sv in 0L..0xFFFFFFFFL)
        assertEquals(FlowState.Incubation, flow.flow)
        assertEquals(ScreenId.C06, screens.current)
        flow.sealDone()
        assertEquals(ScreenId.C07, screens.current)
    }

    @Test
    fun restartSkipsOnboardingPreselectsLook() {
        val screens = ScreenMachine(ScreenId.C05)
        val flow = FlowMachine(screens)
        val chosen = PetLook(body = 3, tip = 4, shape = 5, a = 2, b = 6, c = 0)
        flow.look = chosen
        flow.setEgg(0L, KeeperIds(1, 2))
        flow.look = PetLook()
        flow.goDormant()
        assertEquals(ScreenId.C19, screens.current)
        flow.startNewEgg()
        assertEquals(ScreenId.C03, screens.current)
        assertEquals(FlowState.Creation, flow.flow)
        assertEquals(chosen, flow.look)
        assertEquals(ScreenId.C07, FlowMachine.resumeScreen(FlowState.Incubation, ScreenId.C06))
    }
}
