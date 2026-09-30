package com.technomancer.pangu.ecs.entities

import com.pico.spatial.core.ecs.DirectionalLightComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector3
import com.technomancer.pangu.ecs.toSdk
import com.technomancer.pangu.logic.art.PrimitiveMeshes
import com.technomancer.pangu.logic.math.Quat
import com.technomancer.pangu.logic.math.Vec3

/**
 * Gray-box habitat: pad, nest, lamp and the key light, all under one root whose origin is the
 * centre of the pad's top surface. The pad's front (+Z) faces the player; the lamp is on the left.
 */
class HabitatFactory(private val meshes: MeshFactory) {

    fun create(): Entity {
        val root = Entity().apply { setName(HABITAT_ROOT) }
        root.addChild(createPad())
        root.addChild(createNest())
        root.addChild(createLamp())
        root.addChild(createKeyLight())
        return root
    }

    private fun createPad(): Entity {
        val pad = Entity().apply { setName(PAD) }
        // The body stops 1 mm below the top disc so the two surfaces don't z-fight.
        pad.addChild(
            meshes.model(
                "PH_PadBody",
                PrimitiveMeshes.cylinder(PAD_RADIUS, PAD_HEIGHT - 0.001f, segments = 64, bottomY = -PAD_HEIGHT),
                PAD_SIDE,
            ),
        )
        pad.addChild(meshes.model("PH_PadTop", PrimitiveMeshes.disc(PAD_RADIUS, segments = 64), PAPER))
        pad.addChild(meshes.model("PH_PadRim", PrimitiveMeshes.torus(PAD_RADIUS, 0.002f, segments = 96, sides = 8), INK))
        pad.addChild(
            meshes.model(
                HANDLE,
                PrimitiveMeshes.torus(0.02f, 0.004f, segments = 32, sides = 8),
                INK,
                Vector3(PAD_RADIUS + 0.016f, -PAD_HEIGHT * 0.5f, 0f),
            ),
        )
        return pad
    }

    private fun createNest(): Entity =
        meshes.model(
            NEST,
            PrimitiveMeshes.torus(NEST_OUTER_RADIUS - NEST_TUBE_RADIUS, NEST_TUBE_RADIUS, segments = 48, sides = 12),
            NEST_COLOR,
            Vector3(0f, NEST_TUBE_RADIUS, 0f),
        )

    private fun createLamp(): Entity {
        val lamp = Entity().apply {
            setName(LAMP)
            components[TransformComponent::class.java]?.setPosition(Vector3(-0.17f, 0f, -0.05f))
        }
        lamp.addChild(meshes.model("PH_LampBase", PrimitiveMeshes.cylinder(0.035f, 0.006f), INK))
        lamp.addChild(meshes.model("PH_LampStem", PrimitiveMeshes.cylinder(0.0045f, 0.13f, segments = 12), INK))
        lamp.addChild(
            meshes.model(
                LAMP_BULB,
                PrimitiveMeshes.sphere(0.024f, centerY = 0.13f + 0.02f),
                LAMP_OFF,
            ),
        )
        return lamp
    }

    private fun createKeyLight(): Entity =
        Entity().apply {
            setName(KEY_LIGHT)
            // A directional light shines along its entity's −Z.
            val direction = Vec3(-0.55f, -0.75f, -0.9f)
            components[TransformComponent::class.java]?.setQuaternion(Quat.fromTo(Vec3.FORWARD, direction).toSdk())
            components.set(DirectionalLightComponent(Color4.WHITE, KEY_LIGHT_INTENSITY))
        }

    companion object {
        const val HABITAT_ROOT = "HabitatRoot"
        const val PAD = "PH_Pad"
        const val HANDLE = "PH_PadHandle"
        const val NEST = "PH_Nest"
        const val LAMP = "PH_Lamp"
        const val LAMP_BULB = "PH_LampBulb"
        const val KEY_LIGHT = "PH_KeyLight"

        const val PAD_RADIUS = 0.25f
        const val PAD_HEIGHT = 0.03f
        const val NEST_OUTER_RADIUS = 0.10f
        const val NEST_TUBE_RADIUS = 0.008f
        const val KEY_LIGHT_INTENSITY = 2000f

        const val INK = 0x2F2826
        const val PAPER = 0xF7F3EB
        const val PAD_SIDE = 0xDDD3C4
        const val NEST_COLOR = 0xE7D6BF
        const val LAMP_OFF = 0xE7DFD2
    }
}
