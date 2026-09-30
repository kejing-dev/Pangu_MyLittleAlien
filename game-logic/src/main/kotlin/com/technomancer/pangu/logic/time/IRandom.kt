package com.technomancer.pangu.logic.time

/** Randomness source for all game logic. Implementations must be deterministic per seed. */
interface IRandom {
    /** Next raw 32-bit value. */
    fun nextUInt(): UInt

    /** Uniform integer in [0, bound). [bound] must be > 0. */
    fun nextInt(bound: Int): Int {
        require(bound > 0) { "bound must be positive: $bound" }
        return (nextUInt().toULong() * bound.toULong() shr 32).toInt()
    }

    /** Uniform integer in [from, until). */
    fun nextInt(from: Int, until: Int): Int = from + nextInt(until - from)

    /** Uniform float in [0, 1). */
    fun nextFloat(): Float = (nextUInt() shr 8).toFloat() / (1 shl 24).toFloat()

    /** Uniform float in [from, until). */
    fun nextFloat(from: Float, until: Float): Float = from + (until - from) * nextFloat()

    /** Next raw 16-bit value. */
    fun nextUShort(): UShort = (nextUInt() shr 16).toUShort()
}
