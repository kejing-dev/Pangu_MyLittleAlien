package com.technomancer.pangu.ecs.entities

import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.GroundShadowComponent
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Vector3
import com.technomancer.pangu.ecs.components.TagComponent
import com.technomancer.pangu.ecs.rgb
import com.technomancer.pangu.ecs.toSdk
import com.technomancer.pangu.logic.art.MeshData
import com.technomancer.pangu.logic.art.PrimitiveMeshes
import com.technomancer.pangu.logic.input.TargetId
import com.technomancer.pangu.logic.pet.BallSim
import com.technomancer.pangu.logic.pet.PetWorld

/** Care props, all gray-box shapes: snack tray and snacks, tub and bubbles, ball, crumbs, UFO beam and sky ring. */
class PropFactory(private val meshes: MeshFactory) {

    private val parts: Map<String, MeshData> by lazy {
        mapOf(
            TRAY to PrimitiveMeshes.cylinder(0.085f, 0.008f, segments = 48),
            "PH_Food0" to PrimitiveMeshes.sphere(0.012f),
            "PH_Food1" to PrimitiveMeshes.capsule(0.009f, 0.026f),
            "PH_Food2" to PrimitiveMeshes.cone(0.011f, 0f, 0.024f),
            "PH_Food3" to PrimitiveMeshes.cylinder(0.009f, 0.026f, segments = 16),
            "PH_Food4" to PrimitiveMeshes.halfDome(0.013f, 0.006f),
            TUB to PrimitiveMeshes.cone(0.07f, 0.1f, 0.05f, segments = 48),
            TUB_RIM to PrimitiveMeshes.torus(0.1f, 0.006f, segments = 48, sides = 8),
            BUBBLE to PrimitiveMeshes.sphere(0.012f, rings = 8, segments = 12),
            BALL to PrimitiveMeshes.sphere(BallSim.RADIUS),
            CRUMB to PrimitiveMeshes.octahedron(0.009f),
            BEAM_DISC to PrimitiveMeshes.cylinder(0.125f, 0.02f, segments = 48),
            BEAM_CONE to PrimitiveMeshes.cone(0.215f, 0.07f, 0.5f, segments = 48),
            SKY_RING to PrimitiveMeshes.torus(0.07f, 0.004f, segments = 40, sides = 8),
        )
    }

    /** Uploads the prop meshes; call off the main thread. */
    fun preloadMeshes() {
        for ((name, data) in parts) meshes.mesh(data, name)
    }

    private fun part(name: String, color: Int, position: Vector3 = Vector3.ZERO) =
        meshes.model(name, parts.getValue(name), color, position)

    /** The tray (squashed into an oval) at its spot on the pad. Snacks are separate so they can move freely. */
    fun createTray(): Entity = part(TRAY, TRAY_COLOR, PetWorld.TRAY_CENTRE.toSdk())

    fun createFood(index: Int): Entity =
        meshes.model("PH_Food$index", parts.getValue("PH_Food$index"), FOOD_COLORS[index]).apply {
            interactive(this, TargetId.food(index), 0.022f)
        }

    /** Tub with a paper rim and five bubbles that float over it while bathing. */
    fun createTub(): Pair<Entity, List<Entity>> {
        val tub = part(TUB, TUB_COLOR, PetWorld.TUB_CENTRE.toSdk())
        tub.addChild(part(TUB_RIM, PAPER, Vector3(0f, 0.05f, 0f)))
        val bubbles = List(5) { i ->
            part(BUBBLE, 0xFFFFFF, Vector3(0f, 0.06f, 0f)).also {
                it.setName("PH_Bubble$i")
                tub.addChild(it)
            }
        }
        return tub to bubbles
    }

    fun createBall(): Entity = part(BALL, BALL_COLOR).apply {
        interactive(this, TargetId.BALL, 0.03f)
        components.set(GroundShadowComponent(true, false))
    }

    fun createCrumb(index: Int): Entity = part(CRUMB, CRUMB_COLOR).apply {
        setName("PH_Crumb$index")
        interactive(this, TargetId.crumb(index), 0.02f)
    }

    /** The UFO that takes the pet home: a butter disc with a see-through light cone below it. */
    fun createBeam(): Entity {
        val disc = part(BEAM_DISC, BUTTER)
        val cone = ModelEntity(meshes.mesh(parts.getValue(BEAM_CONE), BEAM_CONE), beamMaterial).apply {
            setName(BEAM_CONE)
            components[TransformComponent::class.java]?.setPosition(Vector3(0f, -0.5f, 0f))
        }
        disc.addChild(cone)
        return disc
    }

    /** Dashed-looking butter ring that hovers over a homesick pet. */
    fun createSkyRing(): Entity = part(SKY_RING, BUTTER)

    private val beamMaterial: UnlitMaterial by lazy {
        UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
            setBaseColor(rgb(BUTTER, 0.35f))
            setOpacity(0.35f)
            setApplyToneMapping(false)
        }
    }

    private fun interactive(entity: Entity, targetId: Int, radius: Float) {
        entity.components.set(InteractableComponent())
        entity.components.set(CollisionComponent(listOf(ShapeResource.createSphere(radius)), PhysicsMaterialResource()))
        entity.components.set(HoverEffectComponent())
        entity.components.set(TagComponent(targetId))
    }

    companion object {
        const val TRAY = "PH_FoodTray"
        const val TUB = "PH_Tub"
        private const val TUB_RIM = "PH_TubRim"
        private const val BUBBLE = "PH_Bubble"
        const val BALL = "PH_Ball"
        private const val CRUMB = "PH_Crumb"
        const val BEAM_DISC = "PH_Beam"
        private const val BEAM_CONE = "PH_BeamCone"
        const val SKY_RING = "PH_SkyRing"

        private const val TRAY_COLOR = 0xECE4D7
        private const val TUB_COLOR = 0xA8BFD4
        private const val PAPER = 0xF7F3EB
        private const val BALL_COLOR = 0xD9644F
        private const val CRUMB_COLOR = 0xC9B7DA
        private const val BUTTER = 0xF2DE9B

        /** Fizzberry, Honey bun, Comet drop, Moon milk, Crisp leaf. */
        val FOOD_COLORS = intArrayOf(0xD9644F, 0xF2DE9B, 0xC9B7DA, 0xFFFFFF, 0xBFD3AE)
    }
}
