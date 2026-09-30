package com.technomancer.pangu.logic.habitat

import com.technomancer.pangu.logic.math.Quat
import com.technomancer.pangu.logic.math.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI

class PadPlacementTest {
    private val placement = PadPlacement()
    private val eps = 1e-4f

    @Test
    fun defaultPoseFlattenedYawAtDistanceAndDrop() {
        // Head at 1.6 m, turned 90° left (facing −X) and pitched 40° down: pitch must not shorten the distance.
        val head = Vec3(0.2f, 1.6f, -0.3f)
        val yaw = (PI / 2).toFloat()
        val rotation = Quat.fromYaw(yaw) * Quat.fromAxisAngle(Vec3.RIGHT, -0.7f)
        val pose = placement.defaultPose(head, rotation)
        assertEquals(0.2f - 0.75f, pose.position.x, eps)
        assertEquals(1.6f - 0.55f, pose.position.y, eps)
        assertEquals(-0.3f, pose.position.z, eps)
        assertEquals(yaw, pose.yaw, eps)
        // The pad's front (+Z) faces the head.
        val front = pose.rotation.rotate(Vec3(0f, 0f, 1f))
        val toHead = (head - pose.position).flattened().normalized()
        assertEquals(1f, front dot toHead, eps)
    }

    @Test
    fun fallbackIsAheadOfTheStageOrigin() {
        val pose = placement.fallbackPose()
        assertEquals(0f, pose.position.x, eps)
        assertEquals(0.45f, pose.position.y, eps)
        assertEquals(-0.75f, pose.position.z, eps)
    }

    @Test
    fun shouldRestoreFalseBeyond2_5m() {
        val saved = PadPose(Vec3(0f, 1f, -0.75f), 0f)
        assertTrue(placement.shouldRestore(saved, Vec3(0f, 1.6f, 0f)))
        assertTrue(placement.shouldRestore(saved, Vec3(0f, 1f, -0.75f + 2.5f)))
        assertFalse(placement.shouldRestore(saved, Vec3(0f, 1f, -0.75f + 2.51f)))
    }
}
