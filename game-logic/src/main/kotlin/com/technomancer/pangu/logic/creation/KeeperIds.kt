package com.technomancer.pangu.logic.creation

import com.technomancer.pangu.logic.time.IClock
import com.technomancer.pangu.logic.time.XorShift32

/** Two private 16-bit ids made once on first launch and kept forever. They are never shown. */
data class KeeperIds(val keeperId: Int, val secretId: Int) {
    companion object {
        /** Returns [saved] when it exists, otherwise makes new ids seeded from the clock. */
        fun ensure(saved: KeeperIds?, clock: IClock): KeeperIds {
            if (saved != null && saved.keeperId >= 0 && saved.secretId >= 0) return saved
            val random = XorShift32.fromLong(clock.nowUtcMillis())
            return KeeperIds(random.nextUShort().toInt(), random.nextUShort().toInt())
        }
    }
}
