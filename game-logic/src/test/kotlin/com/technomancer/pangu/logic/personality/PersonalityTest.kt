package com.technomancer.pangu.logic.personality

import com.technomancer.pangu.logic.config.PersonalityConfig
import com.technomancer.pangu.logic.creation.KeeperIds
import com.technomancer.pangu.logic.pet.Need
import com.technomancer.pangu.logic.pet.PetAction
import com.technomancer.pangu.logic.time.XorShift32
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.PI

private val calc = PersonalityCalculator()
private val ids = KeeperIds(0x1234, 0x5678)
private val noImprint = IntArray(5)

class PersonalityCalculatorTest {
    @Test
    fun baselinesSixBitFields() {
        // Axis i takes bits 6i..6i+5: Zest 1, Cuddle 2, Wonder 3, Calm 4, Neat 63.
        val sv = 1L or (2L shl 6) or (3L shl 12) or (4L shl 18) or (63L shl 24)
        assertEquals(listOf(1, 2, 3, 4, 63), (0 until 5).map { PersonalityCalculator.baseline(sv, it) })
        val p = calc.compute(sv, ids, intArrayOf(10, 0, 0, 0, 0))
        assertEquals(listOf(11, 2, 3, 4, 63), p.scores)
    }

    @Test
    fun natureTieBreakFromSVmod5() {
        val scores = intArrayOf(100, 100, 10, 10, 50)
        // SV mod 5 = 1: the scan starts at Cuddle, so Cuddle wins the tie for up; Calm is the first minimum after it.
        assertEquals(1 to 2, calc.nature(scores, 1L))
        // SV mod 5 = 3: the scan goes Calm, Neat, Zest, Cuddle, Wonder.
        assertEquals(0 to 3, calc.nature(scores, 3L))
    }

    @Test
    fun mockupExampleDreamy() {
        val (up, down) = calc.nature(intArrayOf(58, 142, 231, 96, 71), 0L)
        assertEquals(2, up)
        assertEquals(0, down)
        assertEquals("Dreamy", NatureTable.name(NatureTable.index(up, down)))
    }

    @Test
    fun spreadBelow24Neutral() {
        val sv = 3L shl 28 // neutral axis 3 (Calm)
        val (up, down) = calc.nature(intArrayOf(40, 50, 45, 63, 41), sv)
        assertEquals(3 to 3, up to down)
        assertEquals("Mellow", NatureTable.name(NatureTable.index(up, down)))
        // This spark value gives Neat a baseline of 48 and the rest 0; imprints even them out.
        val p = calc.compute(sv, ids, intArrayOf(48, 48, 48, 48, 0))
        assertTrue(p.neutral)
        assertEquals(-1, p.likedFood)
    }

    @Test
    fun sing9UpIsZestFor1000Seeds() {
        val r = XorShift32(42u)
        val imprint = intArrayOf(9 * 12, 0, 0, 0, 0)
        repeat(1000) {
            val sv = r.nextUInt().toLong()
            assertEquals(0, calc.compute(sv, ids, imprint).up, "seed $sv")
        }
    }

    @Test
    fun quirkBit16() {
        // Big Wonder imprint makes Wonder the up axis; bit 16 picks its quirk.
        val imprint = intArrayOf(0, 0, 200, 0, 0)
        assertEquals(Quirk.AntennaRadar, calc.compute(0L, ids, imprint).quirk)
        assertEquals(Quirk.NightWatcher, calc.compute(1L shl 16, ids, imprint).quirk)
    }

    @Test
    fun size0_92To1_08() {
        assertEquals(0.92f, PersonalityCalculator.sizeFactor(0L), 1e-6f)
        assertEquals(1.08f, PersonalityCalculator.sizeFactor(0xFFL), 1e-6f)
        val r = XorShift32(7u)
        repeat(500) { assertTrue(PersonalityCalculator.sizeFactor(r.nextUInt().toLong()) in 0.92f..1.08f) }
    }

    @Test
    fun frecklesFromBytes() {
        // Byte 0 = 0xF0 (top, azimuth 110°), byte 1 = 0x0F (no freckle), byte 2 = 0x1F (low, 250°).
        val f = PersonalityCalculator.freckles(0xF0L or (0x0FL shl 8) or (0x1FL shl 16))
        assertEquals(2, f.size)
        assertEquals(1.9f, f[0].height, 1e-5f)
        assertEquals((110 * PI / 180).toFloat(), f[0].azimuth, 1e-5f)
        assertEquals(0.45f + (1.9f - 0.45f) / 15f, f[1].height, 1e-5f)
        assertEquals((250 * PI / 180).toFloat(), f[1].azimuth, 1e-5f)
    }

    @Test
    fun shimmerXorBelow16() {
        // keeperId ^ secretId ^ (SV >> 16) ^ (SV & 0xFFFF)
        val k = KeeperIds(0x00F0, 0x000F)
        assertTrue(calc.shimmer(0x00FFL, k)) // 0xF0 ^ 0x0F ^ 0 ^ 0xFF = 0
        assertFalse(calc.shimmer(0x0000L, k)) // 0xFF
        assertTrue(calc.shimmer(0x00F0L, k)) // 0x0F
    }

    @Test
    fun danceStyleSplit() {
        assertFalse(calc.compute(4L shl 16, ids, noImprint).danceSpins)
        assertTrue(calc.compute(5L shl 16, ids, noImprint).danceSpins)
        assertFalse(calc.compute(10L shl 16, ids, noImprint).danceSpins)
        assertEquals(0.9f, calc.compute(0L, ids, noImprint).voicePitch, 1e-6f)
        assertEquals(1.15f, calc.compute(0xFFL shl 24, ids, noImprint).voicePitch, 1e-5f)
    }

    @Test
    fun deterministic() {
        val imprint = intArrayOf(12, 36, 0, 8, 24)
        assertEquals(calc.compute(0x7A3C18E2L, ids, imprint), calc.compute(0x7A3C18E2L, ids, imprint))
    }
}

class NatureTableTest {
    @Test
    fun twentyFiveUniqueFiveNeutralDiagonal() {
        assertEquals(25, NatureTable.NAMES.toSet().size)
        val neutral = (0 until 25).filter { NatureTable.isNeutral(it) }.map { NatureTable.name(it) }
        assertEquals(listOf("Steady", "Easygoing", "Quirky", "Mellow", "Plain"), neutral)
    }
}

class NeedModifiersTest {
    @Test
    fun upDownMultipliers() {
        val cfg = PersonalityConfig()
        // Up Zest (Dance / Fun), down Calm (Sleep / Energy), with a quirk that changes nothing here.
        val p = Personality(up = 0, down = 3, quirk = Quirk.SparkleHiccups)
        val m = PersonalityEffects.modifiers(p, cfg)
        assertEquals(1.25f, m.gain(PetAction.Dance), 1e-6f)
        assertEquals(0.8f, m.gain(PetAction.Sleep), 1e-6f)
        assertEquals(1f, m.gain(PetAction.Eat), 1e-6f)
        assertEquals(1.15f, m.decay[Need.Fun.ordinal], 1e-6f)
        assertEquals(0.9f, m.decay[Need.Energy.ordinal], 1e-6f)
        assertEquals(1f, m.decay[Need.Social.ordinal], 1e-6f)

        val neat = PersonalityEffects.modifiers(Personality(up = 4, down = 1, quirk = Quirk.SelfGroomer), cfg)
        assertEquals(1.15f * 0.9f, neat.decay[Need.Hygiene.ordinal], 1e-6f)
        assertEquals(1f / 1.15f, neat.crumbInterval, 1e-6f)

        val neutral = PersonalityEffects.modifiers(Personality(up = 2, down = 2, quirk = Quirk.AntennaRadar), cfg)
        assertTrue(neutral.gain.all { it == 1f } && neutral.decay.all { it == 1f })
    }
}
