package com.technomancer.pangu.logic.incubation

/** Warmth bands, warmest first. Labels are "band.<ordinal>". */
enum class WarmthBand { Toasty, Cozy, Cool, Chilly }

/** How the egg looks and which card it asks for. */
enum class EggCondition { Toasty, Cozy, Cool, Chilly, Fading, Dormant, Ready }

/** The bead gauge on the nest rim: how many of the 12 beads are lit for each condition. */
object EggGauge {
    const val BEADS = 12

    fun litCount(condition: EggCondition): Int = when (condition) {
        EggCondition.Toasty -> 10
        EggCondition.Cozy -> 8
        EggCondition.Cool -> 4
        EggCondition.Chilly -> 1
        EggCondition.Fading, EggCondition.Dormant -> 0
        EggCondition.Ready -> 12
    }

    /** Lit bead colour, 0xRRGGBB: accent when toasty or ready, butter when cozy, slate otherwise. */
    fun litColor(condition: EggCondition): Int = when (condition) {
        EggCondition.Toasty, EggCondition.Ready -> 0xB4533F
        EggCondition.Cozy -> 0xF2DE9B
        else -> 0x6E7680
    }
}
