package com.technomancer.pangu.logic.art

import com.technomancer.pangu.logic.math.Vec3
import org.junit.jupiter.api.Assertions.assertArrayEquals
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

class TipBlendTest {
    // A flat 2 × 2 × 0.2 slab, like a star or heart tip.
    private val slab = PrimitiveMeshes.box(2f, 2f, 0.2f)
    private val centre = Vec3(0f, 3f, 0f)

    @Test
    fun zeroKeepsTheShape() {
        val out = TipBlend.blend(slab, centre, 0.5f, 0f)
        assertArrayEquals(slab.positions, out.positions, 1e-5f)
    }

    @Test
    fun oneIsTheSharedSphere() {
        val out = TipBlend.blend(slab, centre, 0.5f, 1f)
        for (i in 0 until out.vertexCount) {
            val dx = out.positions[i * 3] - centre.x
            val dy = out.positions[i * 3 + 1] - centre.y
            val dz = out.positions[i * 3 + 2] - centre.z
            assertEquals(0.5f, sqrt(dx * dx + dy * dy + dz * dz), 1e-4f)
        }
    }

    @Test
    fun flatShapesInflateFrontToBack() {
        // The slab's corners must spread towards the front and back, not stay squeezed onto the equator.
        val out = TipBlend.blend(slab, centre, 0.5f, 1f)
        val maxZ = (0 until out.vertexCount).maxOf { out.positions[it * 3 + 2] }
        assertEquals(0.5f * sqrt(1f / 3f), maxZ, 0.05f)
    }
}
