package com.technomancer.pangu.logic.time

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class FixedClockTest {
    @Test
    fun localDayAndHourUseOffset() {
        // 2026-01-01T23:30Z with a +01:00 offset is 00:30 local on 2026-01-02.
        val utc = 1_767_310_200_000L
        val clock = FixedClock(utc, offsetMillis = IClock.MILLIS_PER_HOUR)
        assertEquals(0, clock.localHour(utc))
        assertEquals(FixedClock(utc).localDay(utc) + 1, clock.localDay(utc))
    }
}
