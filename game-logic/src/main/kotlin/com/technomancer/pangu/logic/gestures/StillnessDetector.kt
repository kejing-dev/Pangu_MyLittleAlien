package com.technomancer.pangu.logic.gestures

import com.technomancer.pangu.logic.math.Vec3
import kotlin.math.acos

/**
 * Leaning in to listen: time counts while the head is within [maxDistance] of the target, moving
 * slower than [maxSpeed] m/s and turning slower than [maxAngularDegPerSec]. Any movement resets it.
 */
class StillnessDetector(
    private val maxDistance: Float,
    private val maxSpeed: Float,
    private val maxAngularDegPerSec: Float,
    private val requiredSec: Float,
) {
    private var lastPosition: Vec3? = null
    private var lastForward: Vec3? = null

    var stillSec = 0f
        private set

    /** True while the last update counted. */
    var counting = false
        private set

    /** Returns true on the update that completes the listen. */
    fun update(head: Vec3?, forward: Vec3?, target: Vec3, dt: Float): Boolean {
        val prevP = lastPosition
        val prevF = lastForward
        lastPosition = head
        lastForward = forward
        if (head == null || forward == null || dt <= 0f) {
            stop()
            return false
        }
        val speed = if (prevP != null) head.distanceTo(prevP) / dt else 0f
        val turn = if (prevF != null) Math.toDegrees(acos((prevF dot forward).coerceIn(-1f, 1f)).toDouble()).toFloat() / dt else 0f
        if (head.distanceTo(target) > maxDistance || speed > maxSpeed || turn > maxAngularDegPerSec) {
            stop()
            return false
        }
        if (stillSec >= requiredSec) return false
        counting = true
        stillSec = (stillSec + dt).coerceAtMost(requiredSec)
        return stillSec >= requiredSec
    }

    private fun stop() {
        counting = false
        if (stillSec < requiredSec) stillSec = 0f
    }

    fun reset() {
        lastPosition = null
        lastForward = null
        stillSec = 0f
        counting = false
    }
}
