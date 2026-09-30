package com.technomancer.pangu.logic.pet

import com.technomancer.pangu.logic.config.PetConfig
import com.technomancer.pangu.logic.config.SimConfig
import com.technomancer.pangu.logic.math.Vec3
import com.technomancer.pangu.logic.sim.CatchUp
import com.technomancer.pangu.logic.time.FixedClock
import com.technomancer.pangu.logic.time.XorShift32
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

private val cfg = PetConfig()

class CareActionsTest {
    @Test
    fun clean45sPlus50ScrubDoubles() {
        val needs = Needs(listOf(60f, 80f, 20f, 70f, 70f))
        val a = PetActions(cfg)
        a.startClean()
        repeat(44) { assertFalse(a.update(needs, 0L, 1f)) }
        assertTrue(a.update(needs, 0L, 1f))
        assertEquals(70f, needs[Need.Hygiene], 1e-3f)

        val scrubbed = Needs(listOf(60f, 80f, 20f, 70f, 70f))
        val b = PetActions(cfg)
        b.startClean()
        var seconds = 0
        while (!b.update(scrubbed, 0L, 1f, scrubbing = true)) seconds++
        assertEquals(22, seconds) // finishes after 23 one-second steps at double rate
        assertEquals(70f, scrubbed[Need.Hygiene], 1e-3f)
    }

    @Test
    fun danceAlongAddsSocial() {
        fun dance(along: Boolean): Needs {
            val needs = Needs(listOf(60f, 80f, 80f, 40f, 50f))
            val a = PetActions(cfg)
            a.startDance()
            repeat(30) { a.update(needs, 0L, 1f) { along } }
            return needs
        }
        val alone = dance(false)
        val together = dance(true)
        assertEquals(70f, alone[Need.Fun], 1e-3f)
        assertEquals(72f, alone[Need.Energy], 1e-3f)
        assertEquals(75f, alone[Need.Hygiene], 1e-3f)
        assertEquals(50f, alone[Need.Social], 1e-3f)
        assertEquals(60f, together[Need.Social], 1e-3f)
    }

    @Test
    fun fetchRewards() {
        val needs = Needs(listOf(60f, 80f, 80f, 40f, 50f))
        val a = PetActions(cfg)
        a.startPlay()
        a.fetch(needs)
        assertEquals(48f, needs[Need.Fun], 1e-4f)
        assertEquals(53f, needs[Need.Social], 1e-4f)
        assertEquals(77f, needs[Need.Energy], 1e-4f)
    }

    @Test
    fun resumeAt60PercentFullGain() {
        val needs = Needs(listOf(60f, 80f, 20f, 70f, 70f))
        val a = PetActions(cfg)
        a.startClean()
        repeat(27) { a.update(needs, 0L, 1f) } // 60 % of 45 s
        assertTrue(a.finishOrCancelOnPause(needs))
        assertEquals(70f, needs[Need.Hygiene], 1e-3f)

        val early = Needs(listOf(60f, 80f, 20f, 70f, 70f))
        val b = PetActions(cfg)
        b.startClean()
        repeat(10) { b.update(early, 0L, 1f) }
        assertFalse(b.finishOrCancelOnPause(early))
        assertEquals(PetActivity.Idle, b.activity)
    }
}

class CrumbSpawnerTest {
    @Test
    fun every4hCap5Tidy() {
        val spawner = CrumbSpawner(cfg, XorShift32(7u), 0.25f)
        assertFalse(spawner.update(3.9f, awake = true))
        assertTrue(spawner.update(0.2f, awake = true))
        repeat(10) { spawner.update(4f, awake = true) }
        assertEquals(5, spawner.positions.size)
        assertFalse(spawner.update(10f, awake = false))
        val needs = Needs(cfg.startNeeds)
        needs.crumbs = spawner.positions.size
        assertEquals(0f, needs.value(Need.Tidy, cfg), 1e-4f)
        assertTrue(spawner.positions.all { sqrt(it.x * it.x + it.z * it.z) <= 0.25f - cfg.rimMargin + 1e-5f })
    }
}

class AutonomyPlannerTest {
    @Test
    fun lowEnergyNapHalf() {
        val planner = AutonomyPlanner(cfg)
        val needs = Needs(listOf(60f, 20f, 80f, 70f, 70f))
        assertEquals(Autonomy.None, planner.update(19f, needs, idle = true, ballOut = false))
        assertEquals(Autonomy.Nap, planner.update(1f, needs, idle = true, ballOut = false))

        val a = PetActions(cfg)
        a.startNap(0L, cfg.autonomyEfficiency)
        a.update(needs, 60_000L, 60f)
        assertEquals(21f, needs[Need.Energy], 1e-3f) // 2/min at half efficiency
    }
}

class DistressTrackerTest {
    @Test
    fun stages8_16_24() {
        val d = DistressTracker(cfg)
        d.update(7.9f, 10f)
        assertEquals(Distress.None, d.stage())
        d.update(0.1f, 10f)
        assertEquals(Distress.Mopey, d.stage())
        d.update(8f, 10f)
        assertEquals(Distress.Homesick, d.stage())
        d.update(8f, 10f)
        assertEquals(Distress.WentHome, d.stage())
    }

    @Test
    fun recoversTwiceAsFast() {
        val d = DistressTracker(cfg)
        d.update(10f, 10f)
        d.update(3f, 60f)
        assertEquals(4f, d.hours, 1e-4f)
        d.update(5f, 30f) // between 20 and 50: no change
        assertEquals(4f, d.hours, 1e-4f)
    }
}

class AwaySummaryTest {
    @Test
    fun picksTwoLargestDropsAndCrumbs() {
        val before = floatArrayOf(72f, 60f, 80f, 55f, 70f)
        val after = floatArrayOf(42f, 58f, 70f, 24f, 65f)
        val s = AwaySummary.of(before, after, 0, 2, 6 * 3_600_000L, 60f)!!
        assertEquals(listOf(Need.Fun, Need.Hunger), s.drops.map { it.first })
        assertEquals(72 to 42, s.drops[1].second)
        assertEquals(2, s.newCrumbs)
        assertEquals(360L, s.minutesAway)
    }

    @Test
    fun under60MinNoScreen() {
        val v = floatArrayOf(50f, 50f, 50f, 50f, 50f)
        assertNull(AwaySummary.of(v, v, 0, 0, 59 * 60_000L, 60f))
        assertNotNull(AwaySummary.of(v, v, 0, 0, 60 * 60_000L, 60f))
    }
}

class BallSimTest {
    @Test
    fun staysInsidePadWall() {
        val ball = BallSim(0.25f)
        ball.throwWith(Vec3(2f, 1f, 1.5f))
        repeat(600) {
            ball.update(1f / 60f)
            val r = sqrt(ball.position.x * ball.position.x + ball.position.z * ball.position.z)
            if (ball.position.y < BallSim.WALL_HEIGHT) assertTrue(r <= 0.25f, "ball left the pad at r=$r")
        }
        assertTrue(ball.position.y >= BallSim.RADIUS - 1e-4f)
    }

    @Test
    fun respawnsWhenOffPad() {
        val ball = BallSim(0.25f)
        ball.throwWith(Vec3(3f, 4f, 0f)) // over the wall
        repeat(300) { ball.update(1f / 60f) }
        val r = sqrt(ball.position.x * ball.position.x + ball.position.z * ball.position.z)
        assertTrue(r <= 0.25f, "ball should be back on the pad, r=$r")
    }
}

class NeglectCatchUpTest {
    @Test
    fun neglectedPetGoesHomeWithin72h() {
        val needs = Needs(cfg.startNeeds)
        val distress = DistressTracker(cfg)
        val clock = FixedClock(0L, 0L)
        val start = 1_767_225_600_000L
        CatchUp(SimConfig(), NeedsSim(cfg), clock).pet(
            needs, PetActions(cfg), start, start + 72 * 3_600_000L,
            crumbs = CrumbSpawner(cfg, XorShift32(3u), 0.25f), distress = distress, mood = MoodCalculator(cfg),
        )
        assertEquals(Distress.WentHome, distress.stage())
    }
}

class PetBodyTest {
    @Test
    fun staysStableWithLongFrames() {
        val body = PetBody(cfg, XorShift32(5u), 0.25f)
        body.kickAntenna(4f)
        repeat(20) { body.update(1f, PetActivity.Idle, null) } // one-second frames
        assertTrue(kotlin.math.abs(body.antennaAngle) < 0.1f, "antenna ${body.antennaAngle}")
        assertTrue(body.hopHeight in 0f..cfg.hopHeight)
        assertTrue(kotlin.math.abs(body.roll) < 0.1f)
        assertTrue(sqrt(body.position.x * body.position.x + body.position.z * body.position.z) <= 0.25f)
        assertEquals(0f, body.position.y, 0f)
    }
}
