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
    fun dragMovesHandleWithHandKeepsPadLevelAndFacesHead() {
        val start = PadPose(Vec3(0f, 0.9f, -0.75f), 0f)
        val head = Vec3(0f, 1.5f, 0f)
        val handleBefore = placement.handlePosition(start)
        val moved = placement.drag(start, Vec3(0.4f, 0.25f, 0.1f), head)
        // The handle follows the horizontal part of the hand movement.
        val handleAfter = placement.handlePosition(moved)
        assertEquals(handleBefore.x + 0.4f, handleAfter.x, 1e-3f)
        assertEquals(handleBefore.z + 0.1f, handleAfter.z, 1e-3f)
        assertEquals(0.9f, moved.position.y, eps)
        val front = moved.rotation.rotate(Vec3(0f, 0f, 1f))
        val toHead = (head - moved.position).flattened().normalized()
        assertEquals(1f, front dot toHead, 1e-3f)
        assertEquals(0f, front.y, 1e-6f)
    }

    @Test
    fun rayDragKeepsHandleUnderPointer() {
        val start = PadPose(Vec3(0f, 0.9f, -0.75f), 0f)
        val head = Vec3(0f, 1.5f, 0f)
        val handle = placement.handlePosition(start)
        // The pointer moves straight up by 5 cm on a plane facing the player: the pad goes further away.
        val moved = placement.drag(start, Vec3(0f, 0.05f, 0f), head, viaRay = true)
        val after = placement.handlePosition(moved)
        assertTrue(after.z < handle.z - 0.05f, "handle z ${after.z} from ${handle.z}")
        // The new handle lies on the line from the head through the moved pointer.
        val pointer = handle + Vec3(0f, 0.05f, 0f)
        val a = (pointer - head).normalized()
        val b = (Vec3(after.x, handle.y, after.z) - head).normalized()
        assertEquals(1f, a dot b, 1e-3f)
        // Down pulls it closer.
        val closer = placement.handlePosition(placement.drag(start, Vec3(0f, -0.05f, 0f), head, viaRay = true))
        assertTrue(closer.z > handle.z)
    }

    @Test
    fun shouldRestoreFalseBeyond2_5m() {
        val saved = PadPose(Vec3(0f, 1f, -0.75f), 0f)
        assertTrue(placement.shouldRestore(saved, Vec3(0f, 1.6f, 0f)))
        assertTrue(placement.shouldRestore(saved, Vec3(0f, 1f, -0.75f + 2.5f)))
        assertFalse(placement.shouldRestore(saved, Vec3(0f, 1f, -0.75f + 2.51f)))
    }
}
