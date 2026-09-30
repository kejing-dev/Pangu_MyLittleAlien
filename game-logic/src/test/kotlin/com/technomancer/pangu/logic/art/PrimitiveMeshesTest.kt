package com.technomancer.pangu.logic.art

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PrimitiveMeshesTest {

    @Test
    fun cylinderCountsAndOutwardNormals() {
        val s = 24
        val m = PrimitiveMeshes.cylinder(radius = 0.25f, height = 0.03f, segments = s)
        // Side: 2 rings of s+1; each cap: centre + ring of s+1.
        assertEquals(4 * s + 6, m.vertexCount)
        assertEquals(4 * s, m.triangleCount)
        for (i in 0 until m.vertexCount) {
            val x = m.positions[i * 3]; val y = m.positions[i * 3 + 1]; val z = m.positions[i * 3 + 2]
            val nx = m.normals[i * 3]; val ny = m.normals[i * 3 + 1]; val nz = m.normals[i * 3 + 2]
            // Side normals point away from the axis; cap normals point away from the mid-height.
            val outward = nx * x + nz * z + ny * (y - 0.015f)
            assertTrue(outward > 0f, "normal $i points inward")
        }
        assertWindingMatchesNormals(m)
        val b = m.bounds()
        assertEquals(0f, b[1], 1e-6f)
        assertEquals(0.03f, b[4], 1e-6f)
    }

    @Test
    fun torusIndicesInRange() {
        val m = PrimitiveMeshes.torus(majorRadius = 0.092f, minorRadius = 0.008f, segments = 40, sides = 12)
        assertEquals(41 * 13, m.vertexCount)
        assertEquals(40 * 12 * 2, m.triangleCount)
        assertTrue(m.indices.all { it in 0 until m.vertexCount })
        assertWindingMatchesNormals(m)
    }

    @Test
    fun allShapesAreWoundOutward() {
        listOf(
            PrimitiveMeshes.sphere(0.024f),
            PrimitiveMeshes.halfDome(0.05f, 0.047f),
            PrimitiveMeshes.capsule(0.01f, 0.03f),
            PrimitiveMeshes.cone(0.07f, 0.215f, 0.3f),
            PrimitiveMeshes.box(0.088f, 0.054f, 0.06f),
            PrimitiveMeshes.octahedron(0.009f),
            PrimitiveMeshes.quad(0.1f, 0.1f),
            PrimitiveMeshes.disc(0.1f),
        ).forEach { m ->
            assertTrue(m.indices.all { it in 0 until m.vertexCount })
            assertEquals(m.vertexCount * 4, m.colors.size)
            assertWindingMatchesNormals(m)
        }
    }

    @Test
    fun colorIsWrittenToEveryVertex() {
        val m = PrimitiveMeshes.quad(1f, 1f, color = 0xDDD3C4)
        assertEquals(0xDD / 255f, m.colors[0], 1e-6f)
        assertEquals(0xD3 / 255f, m.colors[1], 1e-6f)
        assertEquals(0xC4 / 255f, m.colors[2], 1e-6f)
        assertEquals(1f, m.colors[3], 0f)
    }

    private fun assertWindingMatchesNormals(m: MeshData) {
        for (t in 0 until m.triangleCount) {
            val a = m.indices[t * 3]; val b = m.indices[t * 3 + 1]; val c = m.indices[t * 3 + 2]
            val p = m.positions; val n = m.normals
            val ex = p[b * 3] - p[a * 3]; val ey = p[b * 3 + 1] - p[a * 3 + 1]; val ez = p[b * 3 + 2] - p[a * 3 + 2]
            val fx = p[c * 3] - p[a * 3]; val fy = p[c * 3 + 1] - p[a * 3 + 1]; val fz = p[c * 3 + 2] - p[a * 3 + 2]
            val cx = ey * fz - ez * fy; val cy = ez * fx - ex * fz; val cz = ex * fy - ey * fx
            val dot = cx * (n[a * 3] + n[b * 3] + n[c * 3]) +
                cy * (n[a * 3 + 1] + n[b * 3 + 1] + n[c * 3 + 1]) +
                cz * (n[a * 3 + 2] + n[b * 3 + 2] + n[c * 3 + 2])
            assertTrue(dot > 0f, "triangle $t is wound against its normals")
        }
    }
}
