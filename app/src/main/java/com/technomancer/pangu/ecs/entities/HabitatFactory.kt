package com.technomancer.pangu.ecs.entities

import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.DirectionalLightComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector3
import com.technomancer.pangu.ecs.components.TagComponent
import com.technomancer.pangu.ecs.toSdk
import com.technomancer.pangu.logic.art.MeshData
import com.technomancer.pangu.logic.art.PrimitiveMeshes
import com.technomancer.pangu.logic.input.TargetId
import com.technomancer.pangu.logic.math.Quat
import com.technomancer.pangu.logic.math.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Gray-box habitat: pad, nest, lamp, care orb and the key light, all under one root whose origin is the
 * centre of the pad's top surface. The pad's front (+Z) faces the player; the lamp is on the left.
 */
class HabitatFactory(private val meshes: MeshFactory) {

    /** Every part mesh by entity name. */
    private val parts: Map<String, MeshData> by lazy {
        mapOf(
            // The body stops 1 mm below the top disc so the two surfaces don't z-fight.
            "PH_PadBody" to PrimitiveMeshes.cylinder(PAD_RADIUS, PAD_HEIGHT - 0.001f, segments = 64, bottomY = -PAD_HEIGHT),
            "PH_PadTop" to PrimitiveMeshes.disc(PAD_RADIUS, segments = 64),
            "PH_PadRim" to PrimitiveMeshes.torus(PAD_RADIUS, 0.002f, segments = 96, sides = 8),
            HANDLE to PrimitiveMeshes.torus(HANDLE_RING_RADIUS, 0.004f, segments = 32, sides = 8),
            HANDLE_GLOW to PrimitiveMeshes.torus(HANDLE_RING_RADIUS, 0.009f, segments = 32, sides = 8),
            NEST to PrimitiveMeshes.torus(NEST_OUTER_RADIUS - NEST_TUBE_RADIUS, NEST_TUBE_RADIUS, segments = 48, sides = 12),
            "PH_LampBase" to PrimitiveMeshes.cylinder(0.035f, 0.006f),
            "PH_LampStem" to PrimitiveMeshes.cylinder(0.0045f, 0.13f, segments = 12),
            LAMP_BULB to PrimitiveMeshes.sphere(0.024f, centerY = 0.13f + 0.02f),
            CARE_ORB to PrimitiveMeshes.sphere(ORB_RADIUS),
            GHOST_DASH to PrimitiveMeshes.box(2f * PI.toFloat() * PAD_RADIUS / (2f * GHOST_DASHES), 0.003f, 0.006f),
            "PH_CareOrbRing" to PrimitiveMeshes.torus(ORB_RADIUS, 0.0012f, segments = 32, sides = 6),
        )
    }

    /**
     * Uploads every part mesh. Uploads can wait for the engine for a while, so call this off the
     * main thread; [create] then only builds entities, which must happen on the main thread.
     */
    fun preloadMeshes() {
        for ((name, data) in parts) meshes.mesh(data, name)
    }

    private fun part(name: String, color: Int, position: Vector3 = Vector3.ZERO) =
        meshes.model(name, parts.getValue(name), color, position)

    fun create(): Entity {
        val root = Entity().apply { setName(HABITAT_ROOT) }
        root.addChild(createPad())
        root.addChild(createNest())
        root.addChild(createLamp())
        root.addChild(createKeyLight())
        root.addChild(createCareOrb())
        root.addChild(createGhostRing())
        return root
    }

    private fun createPad(): Entity {
        val pad = Entity().apply { setName(PAD) }
        pad.addChild(part("PH_PadBody", PAD_SIDE))
        pad.addChild(part("PH_PadTop", PAPER))
        pad.addChild(part("PH_PadRim", INK))
        // A ring just outside the right rim that you grab to move the pad.
        val handle = part(HANDLE, INK, Vector3(HANDLE_OFFSET, -PAD_HEIGHT * 0.5f, 0f))
        makeInteractive(handle, TargetId.PAD_HANDLE, HANDLE_GRAB_RADIUS)
        // Butter halo around the ring while the pad can be moved.
        handle.addChild(part(HANDLE_GLOW, BUTTER))
        pad.addChild(handle)
        return pad
    }

    private fun createNest(): Entity = part(NEST, NEST_COLOR, Vector3(0f, NEST_TUBE_RADIUS, 0f))

    private fun createLamp(): Entity {
        val lamp = Entity().apply {
            setName(LAMP)
            components[TransformComponent::class.java]?.setPosition(Vector3(-0.17f, 0f, -0.05f))
        }
        lamp.addChild(part("PH_LampBase", INK))
        lamp.addChild(part("PH_LampStem", INK))
        // Tapping the bulb lights the heat lamp.
        lamp.addChild(part(LAMP_BULB, LAMP_OFF))
        lamp.addChild(Entity().apply {
            setName("PH_LampBulbCollider")
            components[TransformComponent::class.java]?.setPosition(Vector3(0f, 0.15f, 0f))
            makeInteractive(this, TargetId.LAMP_BULB, BULB_TAP_RADIUS)
        })
        return lamp
    }

    /** Small ball floating above the pad; tapping it opens the care menu (for now, a toast). */
    private fun createCareOrb(): Entity {
        val orb = part(CARE_ORB, ORB_COLOR)
        orb.addChild(part("PH_CareOrbRing", INK))
        makeInteractive(orb, TargetId.CARE_ORB, ORB_TAP_RADIUS)
        return orb
    }

    /** A dashed outline of the pad shown before it exists (the language screen): 24 dashes around the rim. */
    private fun createGhostRing(): Entity {
        val ring = Entity().apply {
            setName(GHOST_RING)
            components[TransformComponent::class.java]?.setScaleVector(Vector3.ZERO)
        }
        for (i in 0 until GHOST_DASHES) {
            val a = 2f * PI.toFloat() * i / GHOST_DASHES
            val dash = part(GHOST_DASH, INK, Vector3(PAD_RADIUS * sin(a), 0f, PAD_RADIUS * cos(a)))
            // Each dash runs along the rim (its long side is local X).
            dash.components[TransformComponent::class.java]?.setQuaternion(Quat.fromYaw(a + PI.toFloat() / 2f).toSdk())
            ring.addChild(dash)
        }
        return ring
    }

    private fun makeInteractive(entity: Entity, targetId: Int, radius: Float) {
        entity.components.set(InteractableComponent())
        entity.components.set(CollisionComponent(listOf(ShapeResource.createSphere(radius)), PhysicsMaterialResource()))
        entity.components.set(HoverEffectComponent())
        entity.components.set(TagComponent(targetId))
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
        const val HANDLE_GLOW = "PH_PadHandleGlow"
        const val CARE_ORB = "PH_CareOrb"
        const val NEST = "PH_Nest"
        const val LAMP = "PH_Lamp"
        const val LAMP_BULB = "PH_LampBulb"
        const val KEY_LIGHT = "PH_KeyLight"
        const val GHOST_RING = "PH_GhostRing"
        private const val GHOST_DASH = "PH_GhostDash"
        private const val GHOST_DASHES = 24

        const val PAD_RADIUS = 0.25f
        const val PAD_HEIGHT = 0.03f
        const val NEST_OUTER_RADIUS = 0.10f
        const val NEST_TUBE_RADIUS = 0.008f
        const val KEY_LIGHT_INTENSITY = 2000f
        const val HANDLE_RING_RADIUS = 0.02f
        const val HANDLE_OFFSET = PAD_RADIUS + 0.016f
        const val HANDLE_GRAB_RADIUS = 0.03f
        const val ORB_RADIUS = 0.016f
        const val ORB_TAP_RADIUS = 0.02f

        const val INK = 0x2F2826
        const val PAPER = 0xF7F3EB
        const val PAD_SIDE = 0xDDD3C4
        const val NEST_COLOR = 0xE7D6BF
        const val LAMP_OFF = 0xE7DFD2
        const val LAMP_ON = 0xF2DE9B
        const val BULB_TAP_RADIUS = 0.035f
        const val BUTTER = 0xF2DE9B
        const val ORB_COLOR = 0x5A504C
    }
}
