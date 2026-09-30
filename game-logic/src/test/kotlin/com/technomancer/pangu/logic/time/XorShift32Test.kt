package com.technomancer.pangu.logic.time

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class XorShift32Test {
    @Test
    fun sameSeedSameSequence() {
        val a = XorShift32(12345u)
        val b = XorShift32(12345u)
        repeat(1000) { assertEquals(a.nextUInt(), b.nextUInt()) }
    }

    @Test
    fun differentSeedsDiverge() {
        assertNotEquals(XorShift32(1u).nextUInt(), XorShift32(2u).nextUInt())
    }

    @Test
    fun zeroSeedDoesNotLockAtZero() {
        val r = XorShift32(0u)
        repeat(10) { assertNotEquals(0u, r.nextUInt()) }
    }

    @Test
    fun knownFirstValueForSeed1() {
        // From state 1: x ^= x<<13 -> 8193; x ^= x>>17 -> 8193; x ^= x<<5 -> 270369.
        assertEquals(270369u, XorShift32(1u).nextUInt())
    }

    @Test
    fun rangesStayInBounds() {
        val r = XorShift32(99u)
        repeat(10_000) {
            assertTrue(r.nextInt(7) in 0 until 7)
            val f = r.nextFloat()
            assertTrue(f >= 0f && f < 1f)
        }
    }
}
