package com.technomancer.pangu.logic.incubation

/** The egg in the nest. Times are epoch ms UTC; hours are simulated hours. */
class EggState {
    var sparkValue: Long = 0L
    var warmth: Float = 0f
    var hatchProgressHours: Float = 0f

    /** Hours spent at zero warmth; reset once warmth rises above the chilly threshold. */
    var coldHours: Float = 0f
    var lampOffMs: Long = 0L
    var blanketOffMs: Long = 0L
    var setMs: Long = 0L
    var crackTaps: Int = 0

    /** Dormant is final: once set, the egg stays dormant. */
    var dormant: Boolean = false

    fun lampOn(nowMs: Long): Boolean = nowMs < lampOffMs

    fun blanketOn(nowMs: Long): Boolean = nowMs < blanketOffMs

    /** Whole hours of lamp time left, rounded up; 0 when off. */
    fun lampHoursLeft(nowMs: Long): Int =
        if (lampOn(nowMs)) ((lampOffMs - nowMs + 3_599_999L) / 3_600_000L).toInt() else 0

    /** Day of incubation, starting at 1. */
    fun day(nowMs: Long): Int = ((nowMs - setMs).coerceAtLeast(0L) / 86_400_000L).toInt() + 1

    fun reset(startWarmth: Float, nowMs: Long, spark: Long) {
        sparkValue = spark
        warmth = startWarmth
        hatchProgressHours = 0f
        coldHours = 0f
        lampOffMs = 0L
        blanketOffMs = 0L
        setMs = nowMs
        crackTaps = 0
        dormant = false
    }
}
