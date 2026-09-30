package com.technomancer.pangu.logic.input

import com.technomancer.pangu.logic.math.Quat
import com.technomancer.pangu.logic.math.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI

class InputSnapshotTest {
    @Test
    fun controllerTipOffsetAlongForward() {
        val s = InputSnapshot()
        // Controller at (1, 1, 0) turned 90° left: forward is −X, so the tip is 6 cm further along −X.
        s.setControllerTip(Side.Right, Vec3(1f, 1f, 0f), Quat.fromYaw((PI / 2).toFloat()), 0.06f)
        val tip = s.pokes[InputSnapshot.CONTROLLER_RIGHT]
        assertTrue(tip.valid)
        assertEquals(0.94f, tip.position.x, 1e-5f)
        assertEquals(1f, tip.position.y, 1e-5f)
        assertEquals(0f, tip.position.z, 1e-5f)
        s.clear()
        assertFalse(tip.valid)
    }

    @Test
    fun queueKeepsOrderAndDropsOldestWhenFull() {
        val q = InteractionQueue(capacity = 2)
        q.push(InteractionType.Tap, 1)
        q.push(InteractionType.Tap, 2)
        q.push(InteractionType.Tap, 3)
        val seen = ArrayList<Int>()
        q.drain { seen += it.target }
        assertEquals(listOf(2, 3), seen)
        assertEquals(0, q.size)
    }
}
