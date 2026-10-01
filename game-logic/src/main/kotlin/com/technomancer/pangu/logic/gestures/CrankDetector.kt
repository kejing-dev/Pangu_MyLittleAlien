package com.technomancer.pangu.logic.gestures

import kotlin.math.PI
import kotlin.math.atan2

/**
 * Winding a crank: the hand's position around the crank's axis is tracked as an angle and the
 * turns add up in either direction. [update] returns true once when [requiredTurns] are reached.
 */
class CrankDetector(private val requiredTurns: Float) {
    private var lastAngle = Float.NaN
    private var total = 0f
    private var done = false

    /** The crank handle's angle, radians, for drawing it. */
    var angle = 0f
        private set

    val turns: Float get() = kotlin.math.abs(total) / TAU

    /** [x], [y]: the hand relative to the crank axis, in the crank's plane. */
    fun update(x: Float, y: Float): Boolean {
        if (x * x + y * y < MIN_RADIUS * MIN_RADIUS) return false
        val a = atan2(y, x)
        angle = a
        if (!lastAngle.isNaN()) {
            var d = a - lastAngle
            while (d > PI) d -= TAU
            while (d < -PI) d += TAU
            total += d
        }
        lastAngle = a
        if (!done && turns >= requiredTurns) {
            done = true
            return true
        }
        return false
    }

    fun reset() {
        lastAngle = Float.NaN
        total = 0f
        done = false
    }

    companion object {
        private const val TAU = (2 * PI).toFloat()
        private const val MIN_RADIUS = 1e-4f
    }
}
