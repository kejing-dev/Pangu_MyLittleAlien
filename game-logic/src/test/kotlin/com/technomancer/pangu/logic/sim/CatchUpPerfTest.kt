package com.technomancer.pangu.logic.sim

import com.technomancer.pangu.logic.config.IncubationConfig
import com.technomancer.pangu.logic.config.PetConfig
import com.technomancer.pangu.logic.config.SimConfig
import com.technomancer.pangu.logic.incubation.EggState
import com.technomancer.pangu.logic.incubation.ImprintLedger
import com.technomancer.pangu.logic.incubation.IncubationSim
import com.technomancer.pangu.logic.pet.CrumbSpawner
import com.technomancer.pangu.logic.pet.DistressTracker
import com.technomancer.pangu.logic.pet.MoodCalculator
import com.technomancer.pangu.logic.pet.Needs
import com.technomancer.pangu.logic.pet.NeedsSim
import com.technomancer.pangu.logic.pet.PetActions
import com.technomancer.pangu.logic.time.FixedClock
import com.technomancer.pangu.logic.time.XorShift32
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

/** 72 h of catch-up must stay cheap; the device budget is 50 ms, the host check is a looser 200 ms. */
class CatchUpPerfTest {
    private val start = 1_767_225_600_000L
    private val end = start + 72 * 3_600_000L

    private fun bestOf(runs: Int, block: () -> Unit): Double =
        (1..runs).minOf {
            val t = System.nanoTime()
            block()
            (System.nanoTime() - t) / 1e6
        }

    @Test
    fun pet72hUnder200msHost() {
        val cfg = PetConfig()
        var steps = 0
        val ms = bestOf(5) {
            val catchUp = CatchUp(SimConfig(), NeedsSim(cfg), FixedClock(0L, 0L))
            // A mood that never drops: distress is tracked but the pet stays, so the whole 72 h is replayed.
            val needs = Needs(listOf(100f, 100f, 100f, 100f, 100f))
            val distress = DistressTracker(cfg.copy(departHours = 1000f))
            steps = catchUp.pet(needs, PetActions(cfg), start, end, CrumbSpawner(cfg, XorShift32(3u), 0.25f), distress, MoodCalculator(cfg))
        }
        assertEquals(72 * 60, steps)
        assertTrue(ms < 200.0, "72 h pet catch-up took $ms ms")
    }

    @Test
    fun egg72hUnder200msHost() {
        val cfg = IncubationConfig()
        var steps = 0
        val ms = bestOf(5) {
            val catchUp = CatchUp(SimConfig(), NeedsSim(PetConfig()), FixedClock(0L, 0L))
            val egg = EggState().apply { warmth = 100f; lampOffMs = end }
            steps = catchUp.egg(egg, IncubationSim(cfg), ImprintLedger(cfg), start, end)
        }
        assertEquals(72 * 60, steps)
        assertTrue(ms < 200.0, "72 h egg catch-up took $ms ms")
    }
}
