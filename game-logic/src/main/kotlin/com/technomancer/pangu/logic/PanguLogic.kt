package com.technomancer.pangu.logic

/**
 * Game logic facade, advanced once per frame by the frame System.
 * For now it only counts ticks and simulated time; later phases hang the simulation off [tick].
 */
class PanguLogic {
    /** Frames advanced while not paused. */
    var tickCount: Long = 0L
        private set

    /** Simulated seconds advanced while not paused. */
    var elapsedSec: Double = 0.0
        private set

    @Volatile
    var isPaused: Boolean = false
        private set

    fun setPaused(paused: Boolean) {
        isPaused = paused
    }

    fun tick(dt: Float) {
        if (isPaused) return
        tickCount++
        elapsedSec += dt
    }
}
