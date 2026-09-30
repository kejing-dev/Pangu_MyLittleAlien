package com.technomancer.pangu.app

import com.technomancer.pangu.logic.time.IClock
import java.util.TimeZone

/** Device wall clock with the device's current time zone. */
class SystemClock : IClock {
    override fun nowUtcMillis(): Long = System.currentTimeMillis()

    override fun localOffsetMillis(utcMillis: Long): Long = TimeZone.getDefault().getOffset(utcMillis).toLong()
}
