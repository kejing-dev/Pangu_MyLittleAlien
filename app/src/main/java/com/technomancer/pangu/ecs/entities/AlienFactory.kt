package com.technomancer.pangu.ecs.entities

import android.content.res.AssetManager
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.math.Vector3
import com.technomancer.pangu.ecs.components.TagComponent
import com.technomancer.pangu.ecs.toSdk
import com.technomancer.pangu.logic.art.AlienParts
import com.technomancer.pangu.logic.art.GlbParser
import com.technomancer.pangu.logic.art.MeshData
import com.technomancer.pangu.logic.art.OutlineHull
import com.technomancer.pangu.logic.art.PrimitiveMeshes
import com.technomancer.pangu.logic.art.ToonBake
import com.technomancer.pangu.logic.art.ToonParams
import com.technomancer.pangu.logic.input.TargetId
import com.technomancer.pangu.logic.math.Vec3

/** Handles to the rig entities the animation writes to, and the parts that take the look's colours. */
class AlienRig(
    val root: Entity,
    val body: Entity,
    val eyes: Entity,
    val antenna: Entity,
    /** Body and stalk models, tinted with the body colour. */
    val bodyModels: List<Entity>,
    /** One pivot per antenna shape at the tip anchor; only the chosen one is shown. */
    val tips: List<Entity>,
    /** The tip model of each shape, tinted with the tip colour. */
    val tipModels: List<Entity>,
    /** Four freckle discs on the back, placed and shown from the personality. */
    val freckles: List<Entity> = emptyList(),
    /** A small sparkle that drifts up from the tip of a shimmering pet. */
    val shimmerMote: Entity? = null,
)

/**
 * Builds the alien from alien_baby.glb: one mesh per part with a baked toon look, ink outline
 * hulls on the body and tips, and a small rig so body, eyes and antenna can move separately.
 * Rig: PetRoot → PetBody → (parts, EyesPivot → eyes, AntennaPivot → stalk and six tips).
 *
 * The body, stalk and tips are baked in white shading only and take their colour from their
 * material (vertex colour × material colour), so the look can change instantly.
 */
class AlienFactory(private val meshes: MeshFactory) {
    private var baked: Map<String, MeshData> = emptyMap()

    /** Each antenna tip mesh (white-baked) with its upload key, for the egg's emblem. */
    var tipShapes: List<Pair<String, MeshData>> = emptyList()
        private set

    /** Parses the GLB and uploads all meshes. Slow; call off the main thread. */
    fun preload(assets: AssetManager) {
        val parts = GlbParser.parse(assets.open(GLB_PATH).use { it.readBytes() })
        val out = HashMap<String, MeshData>()
        fun add(key: String, mesh: MeshData?) {
            if (mesh != null) out[key] = mesh.also { meshes.mesh(it, key) }
        }
        add(BODY, parts[AlienParts.BODY]?.let { ToonBake.bake(it, ToonParams(WHITE_RGB, BODY_SHADE, 0.10f)) })
        add(STALK, parts[AlienParts.STALK]?.let { ToonBake.bake(it, ToonParams(WHITE_RGB, BODY_SHADE, 0.10f)) })
        AlienParts.TIPS.forEachIndexed { i, name ->
            add(tipKey(i), parts[name]?.let { ToonBake.bake(it, ToonParams(WHITE_RGB, TIP_SHADE, 0.25f)) })
            add(tipOutlineKey(i), parts[name]?.let { OutlineHull.build(it, 0.008f) })
        }
        add(EYES, parts[AlienParts.EYES]?.let { ToonBake.bake(it, ToonParams(EYE_BASE, EYE_SHADE, 0f, rim = 0f)) })
        add(HIGHLIGHTS, parts[AlienParts.HIGHLIGHTS])
        add(MOUTH, parts[AlienParts.MOUTH])
        add(BLUSH, parts[AlienParts.BLUSH])
        add(BODY_OUTLINE, parts[AlienParts.BODY]?.let { OutlineHull.build(it, 0.012f) })
        add(FRECKLE, PrimitiveMeshes.disc(FRECKLE_RADIUS, segments = 16))
        add(MOTE, PrimitiveMeshes.octahedron(0.06f))
        baked = out
        tipShapes = AlienParts.TIPS.indices.mapNotNull { i -> out[tipKey(i)]?.let { tipKey(i) to it } }
    }

    /** Creates the rig entities. Must run on the main thread, after [preload]. */
    fun create(): AlienRig {
        val root = Entity().apply { setName(ROOT) }
        val body = Entity().apply { setName(BODY_PIVOT) }
        root.addChild(body)

        val bodyModels = ArrayList<Entity>()
        part(BODY, WHITE)?.let {
            body.addChild(it)
            bodyModels += it
        }
        part(BODY_OUTLINE, INK)?.let { body.addChild(it) }
        part(MOUTH, INK)?.let { body.addChild(it) }
        part(BLUSH, BLUSH_COLOR)?.let { body.addChild(it) }

        // Eyes blink by scaling around their own centre.
        val eyes = pivot(EYES_PIVOT, EYE_CENTRE)
        part(EYES, WHITE, -EYE_CENTRE)?.let { eyes.addChild(it) }
        part(HIGHLIGHTS, WHITE, -EYE_CENTRE)?.let { eyes.addChild(it) }
        body.addChild(eyes)

        // The antenna swings around its base; each tip grows in around the tip anchor.
        val antenna = pivot(ANTENNA_PIVOT, AlienParts.ANTENNA_BASE)
        part(STALK, WHITE, -AlienParts.ANTENNA_BASE)?.let {
            antenna.addChild(it)
            bodyModels += it
        }
        val tips = ArrayList<Entity>()
        val tipModels = ArrayList<Entity>()
        for (i in AlienParts.TIPS.indices) {
            val tipPivot = pivot("PetTip$i", AlienParts.TIP_ANCHOR - AlienParts.ANTENNA_BASE)
            part(tipKey(i), WHITE, -AlienParts.TIP_ANCHOR)?.let {
                it.setName("Pet_Tip$i")
                tipPivot.addChild(it)
                tipModels += it
            }
            part(tipOutlineKey(i), INK, -AlienParts.TIP_ANCHOR)?.let { tipPivot.addChild(it) }
            antenna.addChild(tipPivot)
            tips += tipPivot
        }
        body.addChild(antenna)

        val freckles = List(4) { i -> part(FRECKLE, WHITE)!!.also { it.setName("Pet_Freckle$i"); hide(it); body.addChild(it) } }
        val mote = part(MOTE, WHITE)?.also { hide(it); antenna.addChild(it) }

        // Touch targets: the body capsule and the antenna tip.
        body.addChild(collider("PetBodyCollider", Vec3(0f, 1.16f, 0f), TargetId.PET_BODY) { ShapeResource.createCapsule(2.3f, 0.85f) })
        antenna.addChild(collider("PetTipCollider", AlienParts.TIP_ANCHOR - AlienParts.ANTENNA_BASE + Vec3(0f, 0.12f, 0f), TargetId.PET_TIP) { ShapeResource.createSphere(0.3f) })
        return AlienRig(root, body, eyes, antenna, bodyModels, tips, tipModels, freckles, mote)
    }

    private fun part(key: String, color: Int, offset: Vec3 = Vec3.ZERO): Entity? {
        val data = baked[key] ?: return null
        return meshes.model(key, data, color, offset.toSdk())
    }

    private fun hide(e: Entity) {
        e.components[TransformComponent::class.java]?.setScaleVector(Vector3.ZERO)
    }

    private fun pivot(name: String, at: Vec3) = Entity().apply {
        setName(name)
        components[TransformComponent::class.java]?.setPosition(at.toSdk())
    }

    private fun collider(name: String, at: Vec3, targetId: Int, shape: () -> ShapeResource) = Entity().apply {
        setName(name)
        components[TransformComponent::class.java]?.setPosition(at.toSdk())
        components.set(InteractableComponent())
        components.set(CollisionComponent(listOf(shape()), PhysicsMaterialResource()))
        components.set(HoverEffectComponent())
        components.set(TagComponent(targetId))
    }

    companion object {
        const val GLB_PATH = "models/alien_baby.glb"
        const val ROOT = "PetRoot"
        const val BODY_PIVOT = "PetBody"
        const val EYES_PIVOT = "PetEyes"
        const val ANTENNA_PIVOT = "PetAntennaPivot"

        private const val BODY = "Pet_Body"
        private const val BODY_OUTLINE = "Pet_BodyOutline"
        private const val STALK = "Pet_Stalk"
        private const val EYES = "Pet_Eyes"
        private const val HIGHLIGHTS = "Pet_Highlights"
        private const val MOUTH = "Pet_Mouth"
        private const val BLUSH = "Pet_Blush"
        private const val FRECKLE = "Pet_Freckle"
        private const val MOTE = "Pet_ShimmerMote"

        /** Freckle radius in model units. */
        const val FRECKLE_RADIUS = 0.07f

        fun tipKey(shape: Int) = "Pet_TipShape$shape"
        private fun tipOutlineKey(shape: Int) = "Pet_TipOutline$shape"

        private const val WHITE = 0xFFFFFF
        const val INK = 0x2F2826
        private const val BLUSH_COLOR = 0xF2A8A3
        private val WHITE_RGB = floatArrayOf(1f, 1f, 1f)
        private val EYE_CENTRE = Vec3(0f, 1.455f, 0.68f)
        private val BODY_SHADE = floatArrayOf(0.8f, 0.76f, 0.86f)
        private val TIP_SHADE = floatArrayOf(0.86f, 0.82f, 0.9f)
        private val EYE_BASE = floatArrayOf(0.13f, 0.12f, 0.15f)
        private val EYE_SHADE = floatArrayOf(0.7f, 0.7f, 0.75f)
    }
}

/** Tints a model entity by swapping its first material for a shared one of [color]. */
fun MeshFactory.tint(entity: Entity, color: Int) {
    val model = entity.components[ModelComponent::class.java] ?: return
    if (model.materials.size > 0) model.materials.set(0, unlit(color))
}

/** Tints the body and stalk with [bodyColor] and every antenna tip with [tipColor]. */
fun MeshFactory.applyLook(rig: AlienRig, bodyColor: Int, tipColor: Int) {
    rig.bodyModels.forEach { tint(it, bodyColor) }
    rig.tipModels.forEach { tint(it, tipColor) }
}
