package com.technomancer.pangu.logic.incubation

import com.technomancer.pangu.logic.art.EggMesh
import com.technomancer.pangu.logic.config.IncubationConfig
import com.technomancer.pangu.logic.config.SimConfig
import com.technomancer.pangu.logic.config.PetConfig
import com.technomancer.pangu.logic.gestures.HoldDetector
import com.technomancer.pangu.logic.gestures.TapCounter
import com.technomancer.pangu.logic.pet.NeedsSim
import com.technomancer.pangu.logic.sim.CatchUp
import com.technomancer.pangu.logic.time.FixedClock
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private val cfg = IncubationConfig()
private const val HOUR = 3_600_000L

private fun eggAt(warmth: Float) = EggState().apply { this.warmth = warmth }

class IncubationSimTest {
    private val sim = IncubationSim(cfg)

    @Test
    fun decay6BlanketHalves() {
        val e = eggAt(80f)
        sim.step(e, 1f, 0L)
        assertEquals(74f, e.warmth, 1e-4f)
        sim.tuckBlanket(e, 0L)
        sim.step(e, 2f, 0L)
        assertEquals(68f, e.warmth, 1e-4f)
    }

    @Test
    fun lamp10PerHourFor10h() {
        val e = eggAt(50f)
        sim.lightLamp(e, 0L)
        var t = 0L
        repeat(10) {
            sim.step(e, 1f, t)
            t += HOUR
        }
        assertEquals(50f + 10 * (10f - 6f), e.warmth, 1e-3f)
        assertFalse(e.lampOn(t))
        sim.step(e, 1f, t)
        assertEquals(84f, e.warmth, 1e-3f)
        assertEquals(0, e.lampHoursLeft(t))
    }

    @Test
    fun progressCozyOnlyToasty1_25() {
        val toasty = eggAt(100f)
        sim.step(toasty, 1f, 0L)
        assertEquals(1.25f, toasty.hatchProgressHours, 1e-5f)
        val cozy = eggAt(60f)
        sim.step(cozy, 1f, 0L)
        assertEquals(1f, cozy.hatchProgressHours, 1e-5f)
        val cool = eggAt(30f)
        sim.step(cool, 1f, 0L)
        assertEquals(0f, cool.hatchProgressHours, 1e-5f)
    }

    @Test
    fun readyAt72NoDecay() {
        val e = eggAt(60f).apply { hatchProgressHours = 71.9f }
        sim.step(e, 0.2f, 0L)
        assertTrue(sim.isReady(e))
        assertEquals(EggCondition.Ready, sim.condition(e))
        val w = e.warmth
        sim.step(e, 10f, 0L)
        assertEquals(w, e.warmth, 0f)
    }
}

class EggConditionTest {
    private val sim = IncubationSim(cfg)

    @Test
    fun fading24Dormant48() {
        val e = eggAt(0f)
        sim.step(e, 23.9f, 0L)
        assertEquals(EggCondition.Chilly, sim.condition(e))
        sim.step(e, 0.1f, 0L)
        assertEquals(EggCondition.Fading, sim.condition(e))
        sim.step(e, 24f, 0L)
        assertEquals(EggCondition.Dormant, sim.condition(e))
        assertTrue(e.dormant)
        sim.warmByHand(e, 60f)
        assertEquals(EggCondition.Dormant, sim.condition(e))
    }

    @Test
    fun warmAbove15ResetsCold() {
        val e = eggAt(0f)
        sim.step(e, 10f, 0L)
        assertEquals(10f, e.coldHours, 1e-4f)
        sim.warmByHand(e, 8f) // 12 warmth: still cold enough to keep counting
        assertEquals(10f, e.coldHours, 1e-4f)
        sim.warmByHand(e, 4f) // 18 warmth
        assertEquals(0f, e.coldHours, 1e-4f)
    }
}

class HoldDetectorTest {
    @Test
    fun continuous30sOneCompletion() {
        val hold = HoldDetector(30f)
        var completions = 0
        repeat(290) { if (hold.update(true, 0.1f)) completions++ }
        assertEquals(0, completions)
        assertFalse(hold.update(false, 0.1f))
        assertEquals(0f, hold.heldSec)
        repeat(600) { if (hold.update(true, 0.1f)) completions++ }
        assertEquals(1, completions)
    }
}

class ImprintLedgerTest {
    @Test
    fun cap3Cooldown10RolloverLocalMidnight() {
        // UTC+2: local midnight is 22:00 UTC.
        val clock = FixedClock(0L, 2 * HOUR)
        val ledger = ImprintLedger(cfg)
        val start = 20 * HOUR // 22:00 local on day 0
        fun day(t: Long) = clock.localDay(t)
        assertEquals(CapResult.Counted, ledger.complete(EggAction.Warm, start, day(start)))
        assertEquals(CapResult.Cooldown, ledger.complete(EggAction.Warm, start + 9 * 60_000L, day(start)))
        assertEquals(CapResult.Counted, ledger.complete(EggAction.Warm, start + 10 * 60_000L, day(start)))
        assertEquals(CapResult.Counted, ledger.complete(EggAction.Warm, start + 20 * 60_000L, day(start)))
        assertEquals(CapResult.DailyCap, ledger.complete(EggAction.Warm, start + 30 * 60_000L, day(start)))
        assertEquals(36, ledger.imprint[Trait.Cuddle.ordinal])
        // 22:00 UTC is local midnight: a new day.
        val next = 22 * HOUR
        assertEquals(CapResult.Counted, ledger.complete(EggAction.Warm, next, day(next)))
        assertEquals(1, ledger.completionsToday[EggAction.Warm.ordinal])
    }

    @Test
    fun blanketCap2Calm8() {
        val ledger = ImprintLedger(cfg)
        assertTrue(ledger.tuck(0L))
        assertTrue(ledger.tuck(0L))
        assertFalse(ledger.tuck(0L))
        assertEquals(16, ledger.imprint[Trait.Calm.ordinal])
        assertTrue(ledger.tuck(1L))
        assertEquals(24, ledger.imprint[Trait.Calm.ordinal])
    }
}

class TapCounterTest {
    @Test
    fun fiveTapsCrack() {
        val taps = TapCounter(5)
        repeat(4) { assertFalse(taps.tap()) }
        assertEquals(0.8f, taps.fraction, 1e-6f)
        assertTrue(taps.tap())
        assertFalse(taps.tap())
    }
}

class EggGaugeTest {
    @Test
    fun litCountsPerBand() {
        assertEquals(8, EggGauge.litCount(EggCondition.Cozy))
        assertEquals(10, EggGauge.litCount(EggCondition.Toasty))
        assertEquals(1, EggGauge.litCount(EggCondition.Chilly))
        assertEquals(0, EggGauge.litCount(EggCondition.Fading))
        assertEquals(0, EggGauge.litCount(EggCondition.Dormant))
        assertEquals(12, EggGauge.litCount(EggCondition.Ready))
        assertEquals(0xB4533F, EggGauge.litColor(EggCondition.Toasty))
        assertEquals(0xF2DE9B, EggGauge.litColor(EggCondition.Cozy))
        assertEquals(0x6E7680, EggGauge.litColor(EggCondition.Chilly))
    }
}

class EggMeshTest {
    @Test
    fun height0_11SeamSplitDeterministic() {
        val a = EggMesh.build(0xEDB9B1)
        val b = EggMesh.build(0xEDB9B1)
        val top = a.top.bounds()
        val bottom = a.bottom.bounds()
        assertEquals(0f, bottom[1], 1e-5f)
        assertEquals(0.11f, top[4], 1e-5f)
        assertEquals(bottom[4], (EggMesh.SEAM + EggMesh.SEAM_AMPLITUDE) * EggMesh.HEIGHT, 1e-5f)
        assertEquals(top[1], (EggMesh.SEAM - EggMesh.SEAM_AMPLITUDE) * EggMesh.HEIGHT, 1e-5f)
        assertTrue(EggMesh.maxRadius in 0.035f..0.041f)
        // The seam rows of the two halves meet exactly.
        for (j in 0 until EggMesh.SEGMENTS) {
            val bi = (a.bottom.vertexCount - EggMesh.SEGMENTS + j) * 3
            for (k in 0..2) assertEquals(a.bottom.positions[bi + k], a.top.positions[j * 3 + k], 0f)
        }
        assertArrayEquals(a.top.positions, b.top.positions)
        assertArrayEquals(a.bottom.colors, b.bottom.colors)
        assertArrayEquals(a.top.indices, b.top.indices)
        assertEquals(14, EggMesh.speckles().size)
        assertEquals(7, EggMesh.frost().size)
    }
}

class CatchUpTest {
    @Test
    fun eggFourDaysForgottenDormant() {
        val clock = FixedClock(0L)
        val catchUp = CatchUp(SimConfig(), NeedsSim(PetConfig()), clock)
        val egg = EggState().apply { warmth = cfg.startWarmth }
        val sim = IncubationSim(cfg)
        val start = 1_767_225_600_000L
        val steps = catchUp.egg(egg, sim, ImprintLedger(cfg), start, start + 96 * HOUR)
        assertTrue(egg.dormant)
        assertEquals(EggCondition.Dormant, sim.condition(egg))
        assertTrue(steps < 72 * 60)
    }
}
