package com.technomancer.pangu.logic.math

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class Vec3Test {
    private val eps = 1e-5f

    @Test
    fun normalizeAndDot() {
        val n = Vec3(3f, 0f, 4f).normalized()
        assertEquals(1f, n.length(), eps)
        assertEquals(0.6f, n.x, eps)
        assertEquals(0.8f, n.z, eps)
        assertEquals(0f, Vec3.RIGHT dot Vec3.UP, eps)
        assertEquals(32f, Vec3(1f, 2f, 3f) dot Vec3(4f, 5f, 6f), eps)
        assertEquals(Vec3.ZERO, Vec3.ZERO.normalized())
    }

    @Test
    fun crossIsRightHanded() {
        val z = Vec3.RIGHT cross Vec3.UP
        assertEquals(0f, z.x, eps)
        assertEquals(0f, z.y, eps)
        assertEquals(1f, z.z, eps)
    }
}
