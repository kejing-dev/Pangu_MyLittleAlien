package com.technomancer.pangu.logic.personality

import com.technomancer.pangu.logic.creation.KeeperIds
import com.technomancer.pangu.logic.time.XorShift32

/** The egg's hidden 32-bit seed, rolled when the egg is set. Personality is derived from it at hatch. */
object SparkValue {
    /** Rolls a new value from the time the egg was set and the keeper's ids. Returned as an unsigned 32-bit number. */
    fun roll(nowUtcMs: Long, ids: KeeperIds): Long {
        val seed = nowUtcMs xor (ids.keeperId.toLong() shl 16) xor ids.secretId.toLong()
        return XorShift32.fromLong(seed).nextUInt().toLong()
    }

    /** Eight hex digits, as shown on the personality card. */
    fun code(value: Long): String = "%08X".format(value and 0xFFFFFFFFL)
}
