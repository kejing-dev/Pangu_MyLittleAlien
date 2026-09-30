package com.technomancer.pangu.logic.math

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.PI

class QuatTest {
    private val eps = 1e-5f

    @Test
    fun yawRotatesForwardVector() {
        // +90° yaw (counter-clockwise seen from above) turns −Z forward into −X.
        val f = Quat.fromYaw((PI / 2).toFloat()).rotate(Vec3.FORWARD)
        assertEquals(-1f, f.x, eps)
        assertEquals(0f, f.y, eps)
        assertEquals(0f, f.z, eps)
    }

    @Test
    fun yawRoundTrips() {
        for (deg in listOf(-170, -90, -30, 0, 45, 120, 179)) {
            val r = Math.toRadians(deg.toDouble()).toFloat()
            assertEquals(r, Quat.fromYaw(r).yaw(), 1e-4f)
        }
    }

    @Test
    fun compositionMatchesSequentialRotation() {
        val a = Quat.fromYaw(0.4f)
        val b = Quat.fromAxisAngle(Vec3.RIGHT, 0.7f)
        val v = Vec3(0.3f, -1.2f, 2f)
        val composed = (a * b).rotate(v)
        val sequential = a.rotate(b.rotate(v))
        assertEquals(sequential.x, composed.x, eps)
        assertEquals(sequential.y, composed.y, eps)
        assertEquals(sequential.z, composed.z, eps)
    }

    @Test
    fun fromToTurnsOneDirectionOntoAnother() {
        val target = Vec3(-0.55f, -0.75f, -0.9f).normalized()
        val r = Quat.fromTo(Vec3.FORWARD, target).rotate(Vec3.FORWARD)
        assertEquals(target.x, r.x, eps)
        assertEquals(target.y, r.y, eps)
        assertEquals(target.z, r.z, eps)
        val flipped = Quat.fromTo(Vec3.FORWARD, -Vec3.FORWARD).rotate(Vec3.FORWARD)
        assertEquals(1f, flipped.z, eps)
    }

    @Test
    fun poseInverseRoundTrips() {
        val pose = Pose(Vec3(1f, 2f, 3f), Quat.fromYaw(1.1f))
        val p = Vec3(-0.5f, 0.25f, 4f)
        val back = pose.inverseTransformPoint(pose.transformPoint(p))
        assertEquals(p.x, back.x, eps)
        assertEquals(p.y, back.y, eps)
        assertEquals(p.z, back.z, eps)
    }
}
