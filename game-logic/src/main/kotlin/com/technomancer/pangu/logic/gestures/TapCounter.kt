package com.technomancer.pangu.logic.gestures

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

    /** Taps made so far as a fraction of the target, 0..1. */
    val fraction: Float get() = count.toFloat() / target

    fun reset() {
        count = 0
    }
}
