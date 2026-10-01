package com.technomancer.pangu.logic.gestures

import com.technomancer.pangu.logic.config.IncubationConfig
import com.technomancer.pangu.logic.incubation.EggAction
import com.technomancer.pangu.logic.incubation.ImprintLedger
import com.technomancer.pangu.logic.incubation.Trait
import com.technomancer.pangu.logic.math.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class StillnessDetectorTest {
    @Test
    fun listen15sResetsOnMovement() {
        val d = StillnessDetector(0.30f, 0.05f, 15f, 15f)
        val target = Vec3(0f, 1f, -0.5f)
        val head = Vec3(0f, 1.1f, -0.3f)
        val forward = Vec3(0f, -0.4f, -1f).normalized()
        repeat(100) { d.update(head, forward, target, 0.1f) }
        assertEquals(10f, d.stillSec, 1e-3f)
        // A quick 2 cm move in one frame (0.2 m/s) resets it.
        d.update(head + Vec3(0.02f, 0f, 0f), forward, target, 0.1f)
        assertEquals(0f, d.stillSec)
        var done = false
        repeat(151) { if (d.update(head + Vec3(0.02f, 0f, 0f), forward, target, 0.1f)) done = true }
        assertTrue(done)
        // Too far away never counts.
        val far = StillnessDetector(0.30f, 0.05f, 15f, 15f)
        repeat(200) { far.update(Vec3(0f, 1.6f, 0.5f), forward, target, 0.1f) }
        assertEquals(0f, far.stillSec)
    }
}

class RubCircleDetectorTest {
    private val centre = Vec3(0f, 1f, 0f)
    private val normal = Vec3(0f, 0f, 1f)

    @Test
    fun eightCirclesCompletes() {
        val d = RubCircleDetector(0.02f, 0.10f)
        val dt = 1f / 60f
        var completions = 0
        // 1 cm circles on the front of the shell at 2 turns per second (about 0.13 m/s).
        for (i in 0 until 60 * 5) {
            val a = 2f * PI.toFloat() * 2f * i * dt
            val p = centre + Vec3(0.01f * cos(a), 0.01f * sin(a), 0.005f)
            if (d.update(p, normal, 0.005f, dt)) completions++
        }
        assertTrue(d.circles >= 8, "circles ${d.circles}")
        assertEquals(d.circles, completions)
    }

    @Test
    fun straightStrokesNoCircle() {
        val d = RubCircleDetector(0.02f, 0.10f)
        val dt = 1f / 60f
        for (i in 0 until 60 * 10) {
            // Back and forth along x at about 0.19 m/s.
            val x = 0.015f * sin(2f * PI.toFloat() * 2f * i * dt)
            d.update(centre + Vec3(x, 0f, 0.005f), normal, 0.005f, dt)
        }
        assertEquals(0, d.circles)
    }
}

class SingDetectorTest {
    @Test
    fun tenSecondsAboveThresholdInRange() {
        val d = SingDetector(0.02f, 0.8f, 10f)
        repeat(50) { assertFalse(d.update(0.05f, 0.5f, 0.1f)) } // 5 s
        repeat(50) { d.update(0.01f, 0.5f, 0.1f) } // too quiet: paused
        repeat(50) { d.update(0.05f, 1.2f, 0.1f) } // too far: paused
        assertEquals(5f, d.sungSec, 1e-3f)
        var done = false
        repeat(51) { if (d.update(0.05f, 0.5f, 0.1f)) done = true }
        assertTrue(done)
    }
}

class CrankDetectorTest {
    @Test
    fun twoTurnsStartsSong() {
        val d = CrankDetector(2f)
        var started = 0
        for (i in 0..72) {
            val a = 2f * PI.toFloat() * i / 36f // 10° steps, two full turns at i = 72
            if (d.update(0.02f * cos(a), 0.02f * sin(a))) started++
        }
        assertEquals(1, started)
        assertTrue(d.turns >= 2f)
        val back = CrankDetector(2f)
        for (i in 0..30) back.update(0.02f * cos(i * 0.2f), 0.02f * sin(i * 0.2f))
        for (i in 30 downTo 0) back.update(0.02f * cos(i * 0.2f), 0.02f * sin(i * 0.2f))
        assertTrue(back.turns < 0.1f) // winding back undoes the turns
    }
}

class MicRmsTest {
    @Test
    fun rmsOfFullScaleSineIs0_707() {
        val n = 1600
        val samples = ShortArray(n) { (32767 * sin(2 * PI * 100 * it / 16000.0)).toInt().toShort() }
        assertEquals(0.7071f, MicRms.rms(samples), 1e-3f)
        assertEquals(0f, MicRms.rms(ShortArray(1024)), 0f)
    }
}

class RitualImprintTest {
    @Test
    fun tableSingZestListenWonderPolishNeatWarmCuddle() {
        val ledger = ImprintLedger(IncubationConfig())
        ledger.complete(EggAction.Sing, 0L, 0L)
        ledger.complete(EggAction.Listen, 0L, 0L)
        ledger.complete(EggAction.Polish, 0L, 0L)
        ledger.complete(EggAction.Warm, 0L, 0L)
        assertEquals(listOf(12, 12, 12, 0, 12), Trait.entries.map { ledger.imprint[it.ordinal] })
        assertEquals(3, ledger.ritualsToday)
    }
}
