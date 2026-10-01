package com.technomancer.pangu.logic.personality

import kotlinx.serialization.Serializable

/** One freckle on the back of the body: azimuth (radians, 0 = face) and height in model units. */
@Serializable
data class Freckle(val azimuth: Float, val height: Float)

/**
 * The hatched pet's personality, worked out once at hatch and saved. Axis indices are 0 Zest,
 * 1 Cuddle, 2 Wonder, 3 Calm, 4 Neat. Foods use the same indices; -1 means none.
 */
@Serializable
data class Personality(
    val scores: List<Int> = listOf(0, 0, 0, 0, 0),
    val up: Int = 1,
    val down: Int = 1,
    val natureIndex: Int = 6,
    val quirk: Quirk = Quirk.HandFollower,
    val sizeFactor: Float = 1f,
    val freckles: List<Freckle> = emptyList(),
    val shimmer: Boolean = false,
    val likedFood: Int = -1,
    val dislikedFood: Int = -1,
    /** False wiggles, true spins. */
    val danceSpins: Boolean = false,
    val voicePitch: Float = 1f,
    val sparkValue: Long = 0L,
) {
    val neutral: Boolean get() = up == down

    companion object {
        /** The personality of a pet that hatched before personalities existed: neutral and plain. */
        val DEFAULT = Personality()
    }
}
