package com.technomancer.pangu.logic.art

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File

class GlbParserTest {
    private val file = File("../app/src/main/assets/models/alien_baby.glb")

    private fun parts(): AlienParts {
        assumeTrue(file.exists(), "alien_baby.glb is not in the app assets")
        return GlbParser.parse(file.readBytes())
    }

    @Test
    fun partCountsMatchReference() {
        val expected = mapOf(
            "Body" to (36532 to 73060), "Eyes" to (12622 to 24884), "Eye_Highlights" to (4310 to 8436),
            "Mouth" to (2077 to 4056), "Blush" to (4544 to 8936), "Antenna_Stalk" to (3816 to 7628),
            "AntennaTip_Ball" to (4786 to 9568), "AntennaTip_Star" to (5502 to 11000), "AntennaTip_Heart" to (6062 to 12120),
            "AntennaTip_Spiral" to (4784 to 9604), "AntennaTip_Flower" to (4594 to 9184), "AntennaTip_Bolt" to (2594 to 5184),
        )
        val p = parts()
        for ((name, counts) in expected) {
            val mesh = p[name] ?: error("missing part $name")
            assertEquals(counts.first, mesh.vertexCount, "$name vertices")
            assertEquals(counts.second, mesh.triangleCount, "$name triangles")
        }
    }

    @Test
    fun windingMatchesNormals() {
        val body = parts()[AlienParts.BODY]!!
        var agree = 0
        var sampled = 0
        for (t in 0 until body.triangleCount step 7) {
            val a = body.indices[t * 3]; val b = body.indices[t * 3 + 1]; val c = body.indices[t * 3 + 2]
            val p = body.positions; val n = body.normals
            val ex = p[b * 3] - p[a * 3]; val ey = p[b * 3 + 1] - p[a * 3 + 1]; val ez = p[b * 3 + 2] - p[a * 3 + 2]
            val fx = p[c * 3] - p[a * 3]; val fy = p[c * 3 + 1] - p[a * 3 + 1]; val fz = p[c * 3 + 2] - p[a * 3 + 2]
            val cx = ey * fz - ez * fy; val cy = ez * fx - ex * fz; val cz = ex * fy - ey * fx
            val dot = cx * (n[a * 3] + n[b * 3] + n[c * 3]) + cy * (n[a * 3 + 1] + n[b * 3 + 1] + n[c * 3 + 1]) + cz * (n[a * 3 + 2] + n[b * 3 + 2] + n[c * 3 + 2])
            if (dot > 0f) agree++
            sampled++
        }
        assertTrue(agree >= sampled * 0.99, "only $agree of $sampled triangles agree with their normals")
    }
}

class OutlineHullTest {
    @Test
    fun reversedWindingAndOffset() {
        val quad = PrimitiveMeshes.quad(1f, 1f)
        val hull = OutlineHull.build(quad, 0.01f)
        assertEquals(quad.positions[2] + 0.01f, hull.positions[2], 1e-6f)
        for (t in 0 until quad.triangleCount) {
            assertEquals(quad.indices[t * 3], hull.indices[t * 3])
            assertEquals(quad.indices[t * 3 + 1], hull.indices[t * 3 + 2])
            assertEquals(quad.indices[t * 3 + 2], hull.indices[t * 3 + 1])
        }
    }
}
