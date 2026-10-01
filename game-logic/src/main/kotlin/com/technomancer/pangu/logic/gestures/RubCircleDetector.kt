package com.technomancer.pangu.logic.gestures

import com.technomancer.pangu.logic.math.Vec3
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2

/**
 * Polishing in circles: while a point stays within [maxDistance] of a surface and moves at least
 * [minSpeed] m/s, the heading of its movement on the surface's tangent plane is followed. Each
 * full turn of heading (360°, either way) is one circle. Back-and-forth strokes flip the heading
 * and are not counted, so they never make circles.
 */
class RubCircleDetector(private val maxDistance: Float, private val minSpeed: Float) {
    private var previous: Vec3? = null
    private var lastHeading = Float.NaN
    private var turned = 0f

    var circles = 0
        private set

    /**
     * [point] in metres, [normal] the surface normal under it and [distance] its distance to the
     * surface. Returns true when this update finished a circle.
     */
    fun update(point: Vec3?, normal: Vec3, distance: Float, dt: Float): Boolean {
        val prev = previous
        previous = point
        if (point == null || distance > maxDistance) {
            lastHeading = Float.NaN
            turned = 0f
            return false
        }
        if (prev == null || dt <= 0f) return false
        val v = (point - prev) / dt
        val tangent = v - normal * (v dot normal)
        if (tangent.length() < minSpeed) return false
        val e1 = (Vec3.UP cross normal).let { if (it.lengthSquared() < 1e-6f) Vec3.RIGHT else it.normalized() }
        val e2 = normal cross e1
        val heading = atan2(tangent dot e2, tangent dot e1)
        if (!lastHeading.isNaN()) {
            var d = heading - lastHeading
            while (d > PI) d -= TAU
            while (d < -PI) d += TAU
            // A reversal is a stroke changing direction, not a turn.
            if (abs(d) < MAX_TURN_PER_SAMPLE) turned += d
        }
        lastHeading = heading
        if (abs(turned) >= TAU) {
            turned = 0f
            circles++
            return true
        }
        return false
    }

    fun reset() {
        previous = null
        lastHeading = Float.NaN
        turned = 0f
        circles = 0
    }

    companion object {
        private const val TAU = (2 * PI).toFloat()
        private const val MAX_TURN_PER_SAMPLE = (PI / 2).toFloat()
    }
}
