package com.technomancer.pangu.logic.art

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class GlbSceneTest {
    private val nodes = GlbParser.parseScene(File("../app/src/main/assets/models/ufo.glb").readBytes())
    private fun node(name: String) = nodes.first { it.name == name }
    private fun isLight(node: GlbNode) = node.name.startsWith("Light_") && !node.name.endsWith("_Outline")

    @Test
    fun readsTheUfoParts() {
        for (name in listOf("Hull", "RimBand", "Dome", "BeamLens", "AntennaBall", "Eyes", "Mouth", "Blush")) {
            assertNotNull(node(name).mesh, name)
        }
        assertEquals(10, nodes.count { isLight(it) })
    }

    @Test
    fun hullKeepsItsBakedShadingAndPaperColour() {
        val hull = node("Hull")
        assertEquals(hull.mesh!!.vertexCount * 4, hull.mesh!!.colors.size)
        // The hull's material is the game's paper colour once out of linear space.
        assertEquals(0xF7F3EB, hull.color)
    }

    @Test
    fun rimLightsShareOneMeshAndSitAroundTheRim() {
        val lights = nodes.filter { isLight(it) }
        lights.forEach { assertSame(lights[0].mesh, it.mesh) }
        lights.forEach {
            val r = kotlin.math.sqrt(it.translation.x * it.translation.x + it.translation.z * it.translation.z)
            assertEquals(1.33f, r, 0.02f)
            assertEquals(0.06f, it.scale.x, 1e-4f)
        }
        // Each light's outline is its child.
        assertEquals(nodes.indexOf(lights[0]), node("Light_1_Outline").parent)
    }

    @Test
    fun beamIsSeeThroughAndHangsBelowTheHull() {
        val beam = nodes.first { it.name.startsWith("Beam (") }
        assertTrue(beam.opacity < 0.5f)
        assertTrue(beam.translation.y < -3f)
        assertEquals(3.05f, beam.scale.y, 1e-3f)
    }
}
