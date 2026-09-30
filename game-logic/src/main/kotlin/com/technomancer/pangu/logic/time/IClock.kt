package com.technomancer.pangu.logic.time

/**
 * Wall-clock source for all simulation. "Day" rules use the local calendar day,
 * so the clock also reports the local UTC offset at a given instant (it can change with DST).
 */
interface IClock {
    /** Current time, epoch milliseconds UTC. */
    fun nowUtcMillis(): Long

    /** Local offset from UTC in milliseconds at [utcMillis]. */
    fun localOffsetMillis(utcMillis: Long): Long

    /** Local calendar day number (days since 1970-01-01 local) at [utcMillis]. */
    fun localDay(utcMillis: Long): Long =
        Math.floorDiv(utcMillis + localOffsetMillis(utcMillis), MILLIS_PER_DAY)

    /** Local hour of day 0..23 at [utcMillis]. */
    fun localHour(utcMillis: Long): Int =
        (Math.floorMod(utcMillis + localOffsetMillis(utcMillis), MILLIS_PER_DAY) / MILLIS_PER_HOUR).toInt()

    companion object {
        const val MILLIS_PER_HOUR: Long = 3_600_000L
        const val MILLIS_PER_DAY: Long = 86_400_000L
    }
}

/** Deterministic clock for tests and catch-up replays. */
class FixedClock(var utcMillis: Long = 0L, var offsetMillis: Long = 0L) : IClock {
    override fun nowUtcMillis(): Long = utcMillis
    override fun localOffsetMillis(utcMillis: Long): Long = offsetMillis
    fun advanceMillis(millis: Long) { utcMillis += millis }
}
