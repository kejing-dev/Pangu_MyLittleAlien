package com.technomancer.pangu.logic.input

import com.technomancer.pangu.logic.math.Quat
import com.technomancer.pangu.logic.math.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNull
import org.junit.jupiter.api.Test

class ControllerActivityTest {
    private fun frame(snapshot: InputSnapshot, left: Vec3?, right: Vec3?) {
        snapshot.clear()
        left?.let { snapshot.setControllerTip(Side.Left, it, Quat.IDENTITY, 0f) }
        right?.let { snapshot.setControllerTip(Side.Right, it, Quat.IDENTITY, 0f) }
    }

    @Test
    fun movingControllerIsActive() {
        val s = InputSnapshot()
        val a = ControllerActivity()
        // Both held still: unknown.
        repeat(10) {
            frame(s, Vec3(-0.2f, 1f, -0.3f), Vec3(0.2f, 1f, -0.3f))
            a.update(s, 0.1f)
        }
        assertNull(a.active())
        // The right one moves.
        repeat(10) { i ->
            frame(s, Vec3(-0.2f, 1f, -0.3f), Vec3(0.2f + i * 0.01f, 1f, -0.3f))
            a.update(s, 0.1f)
        }
        assertEquals(Side.Right, a.active())
        // Only the left one is tracked.
        frame(s, Vec3(-0.2f, 1f, -0.3f), null)
        a.update(s, 0.1f)
        assertEquals(Side.Left, a.active())
        frame(s, null, null)
        a.update(s, 0.1f)
        assertNull(a.active())
    }
}
