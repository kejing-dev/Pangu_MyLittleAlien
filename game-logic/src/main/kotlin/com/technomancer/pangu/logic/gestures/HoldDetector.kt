package com.technomancer.pangu.logic.gestures

/**
 * Counts continuous holding. [update] returns true once when the hold reaches [requiredSec];
 * letting go resets it, and a new completion needs a new hold.
 */
class HoldDetector(private val requiredSec: Float) {
    var heldSec = 0f
        private set
    private var done = false

    fun update(holding: Boolean, dt: Float): Boolean {
        if (!holding) {
            reset()
            return false
        }
        if (done) return false
        heldSec = (heldSec + dt).coerceAtMost(requiredSec)
        if (heldSec >= requiredSec) {
            done = true
            return true
        }
        return false
    }

    fun reset() {
        heldSec = 0f
        done = false
    }
}
