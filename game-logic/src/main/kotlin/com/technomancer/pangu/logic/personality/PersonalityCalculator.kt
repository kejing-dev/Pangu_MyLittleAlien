package com.technomancer.pangu.logic.personality

import com.technomancer.pangu.logic.config.PersonalityConfig
import com.technomancer.pangu.logic.creation.KeeperIds
import kotlin.math.PI

/**
 * Works out the personality from the egg's spark value, the keeper's ids and the imprints the
 * player's care left. Pure and deterministic: the same inputs always give the same pet.
 */
class PersonalityCalculator(private val config: PersonalityConfig = PersonalityConfig()) {

    fun compute(sv: Long, ids: KeeperIds, imprint: IntArray): Personality {
        val v = sv and MASK32
        val scores = IntArray(AXES) { i -> imprint.getOrElse(i) { 0 } + baseline(v, i) }
        val (up, down) = nature(scores, v)
        val quirkAxis = if (up == down) neutralAxis(v) else up
        val neutral = up == down
        return Personality(
            scores = scores.toList(),
            up = up,
            down = down,
            natureIndex = NatureTable.index(up, down),
            quirk = Quirk.of(quirkAxis, ((v shr 16) and 1L).toInt()),
            sizeFactor = sizeFactor(v),
            freckles = freckles(v),
            shimmer = shimmer(v, ids),
            likedFood = if (neutral) -1 else up,
            dislikedFood = if (neutral) -1 else down,
            danceSpins = ((v shr 16) % 10L) > 4L,
            voicePitch = 0.9f + 0.25f * ((v shr 24) and 0xFFL) / 255f,
            sparkValue = v,
        )
    }

    /**
     * Up and down axes. Scanning starts at SV mod 5 and the first maximum (minimum) found wins.
     * When every score is within the neutral spread, both are (SV >> 28) mod 5.
     */
    fun nature(scores: IntArray, sv: Long): Pair<Int, Int> {
        val v = sv and MASK32
        val max = scores.max()
        val min = scores.min()
        if (max - min < config.neutralSpread) {
            val n = neutralAxis(v)
            return n to n
        }
        val t = (v % AXES).toInt()
        var up = -1
        var down = -1
        for (k in 0 until AXES) {
            val i = (t + k) % AXES
            if (up < 0 && scores[i] == max) up = i
            if (down < 0 && scores[i] == min) down = i
        }
        return up to down
    }

    fun shimmer(sv: Long, ids: KeeperIds): Boolean {
        val v = sv and MASK32
        val s = ids.keeperId.toLong() xor ids.secretId.toLong() xor (v shr 16) xor (v and 0xFFFFL)
        return s < config.shimmerThreshold
    }

    companion object {
        const val AXES = 5
        private const val MASK32 = 0xFFFFFFFFL

        /** Six bits of the spark value per axis, 0–63. */
        fun baseline(sv: Long, axis: Int): Int = ((sv shr (6 * axis)) and 0x3FL).toInt()

        fun neutralAxis(sv: Long): Int = ((sv shr 28) % AXES).toInt()

        fun sizeFactor(sv: Long): Float {
            val b = ((sv and 0xFFFFL) xor (sv shr 16)) and 0xFFL
            return 0.92f + 0.16f * b / 255f
        }

        /** Up to four freckles on the back, one per spark byte whose high nibble is not zero. */
        fun freckles(sv: Long): List<Freckle> = (0 until 4).mapNotNull { j ->
            val b = ((sv shr (8 * j)) and 0xFFL).toInt()
            val hi = b shr 4
            if (hi == 0) {
                null
            } else {
                val az = lerp(110f, 250f, (b and 0x0F) / 15f) * PI.toFloat() / 180f
                Freckle(az, lerp(0.45f, 1.9f, hi / 15f))
            }
        }

        private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
    }
}
