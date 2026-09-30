package com.technomancer.pangu.logic.gestures

import com.technomancer.pangu.logic.math.Vec3

/**
 * Detects rubbing: a poke point moving at least [minSpeed] m/s while within [maxDistance] m of a
 * surface. The caller supplies the point's distance to the surface each frame.
 */
class RubDetector(private val maxDistance: Float, private val minSpeed: Float) {
    private var previous: Vec3? = null

    /** Total seconds spent rubbing since the last [reset]. */
    var rubSeconds = 0f
        private set

    /** Returns true when this frame counts as rubbing. */
    fun update(point: Vec3?, distanceToSurface: Float, dt: Float): Boolean {
        val prev = previous
        previous = point
        if (point == null || prev == null || dt <= 0f) return false
        val speed = point.distanceTo(prev) / dt
        val rubbing = distanceToSurface <= maxDistance && speed >= minSpeed
        if (rubbing) rubSeconds += dt
        return rubbing
    }

    fun reset() {
        previous = null
        rubSeconds = 0f
    }
}

/** Counts taps toward a target number, e.g. taps to crack the egg. */
class TapCounter(val target: Int) {
    var count = 0
        private set

    /** Adds a tap; returns true on the tap that reaches the target. */
    fun tap(): Boolean {
        if (count >= target) return false
        count++
        return count == target
    }

    fun reset() {
        count = 0
    }
}
