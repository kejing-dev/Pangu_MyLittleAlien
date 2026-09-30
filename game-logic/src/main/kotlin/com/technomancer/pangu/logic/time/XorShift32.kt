package com.technomancer.pangu.logic.time

/**
 * Marsaglia xorshift32 (13, 17, 5). A zero seed would lock the generator at zero,
 * so it is replaced by a fixed non-zero constant.
 */
class XorShift32(seed: UInt) : IRandom {
    private var state: UInt = if (seed == 0u) ZERO_SEED_REPLACEMENT else seed

    override fun nextUInt(): UInt {
        var x = state
        x = x xor (x shl 13)
        x = x xor (x shr 17)
        x = x xor (x shl 5)
        state = x
        return x
    }

    companion object {
        const val ZERO_SEED_REPLACEMENT: UInt = 0x9E3779B9u

        /** Folds a 64-bit seed (e.g. epoch millis) into 32 bits. */
        fun fromLong(seed: Long): XorShift32 = XorShift32((seed xor (seed ushr 32)).toUInt())
    }
}
