package com.technomancer.pangu.logic

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class PanguLogicTest {
    @Test
    fun pausedDoesNotTick() {
        val logic = PanguLogic()
        repeat(3) { logic.tick(0.016f) }
        logic.setPaused(true)
        repeat(5) { logic.tick(0.016f) }
        assertEquals(3L, logic.tickCount)
        logic.setPaused(false)
        logic.tick(0.016f)
        assertEquals(4L, logic.tickCount)
        assertEquals(0.064, logic.elapsedSec, 1e-6)
    }
}
