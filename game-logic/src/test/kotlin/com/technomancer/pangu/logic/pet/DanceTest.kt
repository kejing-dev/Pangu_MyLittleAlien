package com.technomancer.pangu.logic.pet

import com.technomancer.pangu.logic.config.PetConfig
import com.technomancer.pangu.logic.time.XorShift32
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.abs

class DanceTest {
    private val cfg = PetConfig()

    private fun dancer(spins: Boolean) = PetBody(cfg, XorShift32(5u), 0.25f).apply {
        place(PetBody.NEST)
        danceSpins = spins
    }

    @Test
    fun danceMovesTheWholeBody() {
        for (spins in listOf(false, true)) {
            val body = dancer(spins)
            var hop = 0f
            var lean = 0f
            var tall = 1f
            var short = 1f
            var step = 0f
            var turn = 0f
            // Sixteen beats at 110 BPM is a little under nine seconds.
            repeat(540) {
                body.update(1f / 60f, PetActivity.Dancing, null)
                hop = maxOf(hop, body.hopHeight)
                lean = maxOf(lean, abs(body.roll))
                tall = maxOf(tall, body.stretch)
                short = minOf(short, body.stretch)
                step = maxOf(step, abs(body.danceOffset.x) + abs(body.danceOffset.z))
                turn = maxOf(turn, abs(body.spin))
            }
            assertTrue(hop > cfg.hopHeight, "jumps higher than a walking hop (spins=$spins)")
            assertTrue(lean > 0.1f, "leans from side to side")
            assertTrue(tall > 1.08f && short < 0.95f, "stretches and squashes")
            assertTrue(step > 0.01f, "steps sideways")
            assertTrue(turn > 3f, "turns right round in a jump")
        }
    }

    @Test
    fun danceEasesBackToRest() {
        val body = dancer(true)
        repeat(200) { body.update(1f / 60f, PetActivity.Dancing, null) }
        repeat(60) { body.update(1f / 60f, PetActivity.Idle, null) }
        assertEquals(0f, body.spin)
        assertEquals(1f, body.stretch)
        assertEquals(0f, body.hopHeight)
        assertEquals(0f, abs(body.danceOffset.x) + abs(body.danceOffset.z))
    }
}
