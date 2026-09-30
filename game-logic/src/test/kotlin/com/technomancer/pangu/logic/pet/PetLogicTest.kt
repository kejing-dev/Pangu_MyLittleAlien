package com.technomancer.pangu.logic.pet

import com.technomancer.pangu.logic.config.PetConfig
import com.technomancer.pangu.logic.config.SimConfig
import com.technomancer.pangu.logic.gestures.RubDetector
import com.technomancer.pangu.logic.math.Vec3
import com.technomancer.pangu.logic.sim.CatchUp
import com.technomancer.pangu.logic.time.FixedClock
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

private val config = PetConfig()

class NeedsSimTest {
    @Test
    fun hungerDrops5PerHourAwake() {
        val needs = Needs(config.startNeeds)
        NeedsSim(config).step(needs, 1f, asleep = false)
        assertEquals(55f, needs[Need.Hunger], 1e-4f)
        assertEquals(76f, needs[Need.Energy], 1e-4f)
    }

    @Test
    fun asleepDecay0_3EnergyPlus10() {
        val needs = Needs(config.startNeeds)
        NeedsSim(config).step(needs, 1f, asleep = true)
        assertEquals(60f - 5f * 0.3f, needs[Need.Hunger], 1e-4f)
        assertEquals(80f - 4f * 0.3f + 10f, needs[Need.Energy], 1e-4f)
    }

    @Test
    fun nightWrapsMidnight() {
        val sim = NeedsSim(config)
        assertTrue(sim.isNight(23))
        assertTrue(sim.isNight(3))
        assertFalse(sim.isNight(7))
        assertFalse(sim.isNight(21))
    }
}

class MoodCalculatorTest {
    @Test
    fun weightedMeanThresholds() {
        val calc = MoodCalculator(config)
        val needs = Needs(listOf(100f, 100f, 100f, 100f, 100f))
        assertEquals(100f, calc.score(needs), 1e-4f)
        needs.values.fill(50f)
        needs.crumbs = 5 // Tidy 0
        // (50 × 5.5 + 0 × 0.5) / 6 = 45.83
        assertEquals(45.833f, calc.score(needs), 1e-3f)
        assertEquals(Mood.Joyful, calc.mood(80f))
        assertEquals(Mood.Content, calc.mood(55f))
        assertEquals(Mood.Meh, calc.mood(54.9f))
        assertEquals(Mood.Sad, calc.mood(20f))
        assertEquals(Mood.Distressed, calc.mood(19.9f))
    }
}

class SuggestionsTest {
    @Test
    fun lowFunSuggestsDanceAndPlay() {
        val needs = Needs(listOf(80f, 80f, 80f, 10f, 80f))
        assertEquals(listOf(PetAction.Dance, PetAction.Play), Suggestions.forPet(needs, config))
    }

    @Test
    fun lowTidySuggestsClean() {
        val needs = Needs(listOf(80f, 80f, 80f, 80f, 80f))
        needs.crumbs = 4
        assertEquals(listOf(PetAction.Clean), Suggestions.forPet(needs, config))
    }
}

class NeedsBarColorTest {
    @Test
    fun thresholds60And30() {
        assertEquals(NeedBand.Good, NeedBar.band(60f))
        assertEquals(NeedBand.Low, NeedBar.band(59.9f))
        assertEquals(NeedBand.Low, NeedBar.band(30f))
        assertEquals(NeedBand.Critical, NeedBar.band(29.9f))
    }
}

class PetActionsTest {
    @Test
    fun eatAtHunger95Refused() {
        val needs = Needs(listOf(95f, 80f, 80f, 70f, 70f))
        assertEquals(ActionResult.Refused("full"), PetActions(config).startEat(needs, 0L))
    }

    @Test
    fun eatCooldown20s() {
        val needs = Needs(config.startNeeds)
        val a = PetActions(config)
        assertEquals(ActionResult.Started, a.startEat(needs, 0L))
        assertTrue(a.update(needs, 3_000L, 3f))
        assertEquals(85f, needs[Need.Hunger], 1e-4f)
        assertEquals(ActionResult.Refused("cooldown"), a.startEat(needs, 22_000L))
        assertInstanceOf(ActionResult.Started::class.java, a.startEat(needs, 23_000L))
    }

    @Test
    fun napPlus2PerMinMax30() {
        val needs = Needs(listOf(60f, 10f, 80f, 70f, 70f))
        val a = PetActions(config)
        a.startNap(0L)
        var t = 0L
        repeat(29) {
            t += 60_000
            assertFalse(a.update(needs, t, 60f))
        }
        assertEquals(10f + 58f, needs[Need.Energy], 1e-3f)
        assertEquals(29, a.napMinutes(t))
        t += 60_000
        assertTrue(a.update(needs, t, 60f))
        assertEquals(PetActivity.Idle, a.activity)
    }

    @Test
    fun boopOncePer2s() {
        val needs = Needs(config.startNeeds)
        val a = PetActions(config)
        assertTrue(a.boop(needs, 10_000L))
        assertFalse(a.boop(needs, 11_500L))
        assertTrue(a.boop(needs, 12_000L))
        assertEquals(76f, needs[Need.Social], 1e-4f)
    }

    @Test
    fun strokeCappedAt20PerMinute() {
        val needs = Needs(listOf(60f, 80f, 80f, 70f, 10f))
        val a = PetActions(config)
        repeat(20) { a.stroke(needs, 1f, 1_000L + it * 1000L) }
        assertEquals(30f, needs[Need.Social], 1e-4f)
    }
}

class RubDetectorTest {
    @Test
    fun nearSurfaceAboveSpeedOnly() {
        val rub = RubDetector(maxDistance = 0.02f, minSpeed = 0.1f)
        assertFalse(rub.update(Vec3(0f, 0f, 0f), 0.01f, 0.1f)) // first sample only primes
        assertTrue(rub.update(Vec3(0.02f, 0f, 0f), 0.01f, 0.1f)) // 0.2 m/s, close
        assertFalse(rub.update(Vec3(0.021f, 0f, 0f), 0.01f, 0.1f)) // too slow
        assertFalse(rub.update(Vec3(0.05f, 0f, 0f), 0.05f, 0.1f)) // too far from the surface
        assertEquals(0.1f, rub.rubSeconds, 1e-6f)
    }
}

class CatchUpTest {
    private val clock = FixedClock(0L, 0L)

    private fun run(fromMs: Long, toMs: Long): Pair<Needs, Int> {
        val needs = Needs(config.startNeeds)
        val steps = CatchUp(SimConfig(), NeedsSim(config), clock).pet(needs, PetActions(config), fromMs, toMs)
        return needs to steps
    }

    @Test
    fun petDeterministic() {
        val start = 1_767_225_600_000L // 2026-01-01T00:00Z
        val (a, _) = run(start, start + 10 * 3_600_000L)
        val (b, _) = run(start, start + 10 * 3_600_000L)
        assertTrue(a.values.contentEquals(b.values))
        assertTrue(a[Need.Hunger] < 60f)
    }

    @Test
    fun capsAt72h() {
        val start = 1_767_225_600_000L
        val (_, steps) = run(start, start + 100 * 3_600_000L)
        assertEquals(72 * 60, steps)
    }
}
