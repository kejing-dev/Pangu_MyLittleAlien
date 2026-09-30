package com.technomancer.pangu.ecs.entities

import android.content.res.AssetManager
import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
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
import com.technomancer.pangu.logic.art.ToonBake
import com.technomancer.pangu.logic.art.ToonParams
import com.technomancer.pangu.logic.input.TargetId
import com.technomancer.pangu.logic.math.Vec3

/** Look choices for the alien: 0xRRGGBB body and tip colours and the antenna tip index (0–5). */
data class AlienLook(val bodyColor: Int, val tipColor: Int, val tipShape: Int)

/** Handles to the rig entities the pet animation writes to. */
class AlienRig(
    val root: Entity,
    val body: Entity,
    val eyes: Entity,
    val antenna: Entity,
)

/**
 * Builds the alien from alien_baby.glb: one mesh per part with a baked toon look, ink outline
 * hulls on the body and tip, and a small rig so body, eyes and antenna can move separately.
 * Rig: PetRoot → PetBody → (parts, EyesPivot → eyes, AntennaPivot → stalk and tip).
 */
class AlienFactory(private val meshes: MeshFactory, private val look: AlienLook) {
    private var baked: Map<String, MeshData> = emptyMap()

    /** Parses the GLB and uploads all meshes. Slow; call off the main thread. */
    fun preload(assets: AssetManager) {
        val parts = GlbParser.parse(assets.open(GLB_PATH).use { it.readBytes() })
        val out = HashMap<String, MeshData>()
        val body = ToonBake.rgb(look.bodyColor)
        val tip = ToonBake.rgb(look.tipColor)
        val tipName = AlienParts.TIPS[look.tipShape.coerceIn(0, AlienParts.TIPS.lastIndex)]
        fun add(name: String, key: String, mesh: MeshData?) {
            if (mesh != null) out[key] = mesh.also { meshes.mesh(it, key) }
        }
        add(AlienParts.BODY, BODY, parts[AlienParts.BODY]?.let { ToonBake.bake(it, ToonParams(body, BODY_SHADE, 0.10f)) })
        add(AlienParts.STALK, STALK, parts[AlienParts.STALK]?.let { ToonBake.bake(it, ToonParams(body, BODY_SHADE, 0.10f)) })
        add(tipName, TIP, parts[tipName]?.let { ToonBake.bake(it, ToonParams(tip, TIP_SHADE, 0.25f)) })
        add(AlienParts.EYES, EYES, parts[AlienParts.EYES]?.let { ToonBake.bake(it, ToonParams(EYE_BASE, EYE_SHADE, 0f, rim = 0f)) })
        add(AlienParts.HIGHLIGHTS, HIGHLIGHTS, parts[AlienParts.HIGHLIGHTS])
        add(AlienParts.MOUTH, MOUTH, parts[AlienParts.MOUTH])
        add(AlienParts.BLUSH, BLUSH, parts[AlienParts.BLUSH])
        add(AlienParts.BODY, BODY_OUTLINE, parts[AlienParts.BODY]?.let { OutlineHull.build(it, 0.012f) })
        add(tipName, TIP_OUTLINE, parts[tipName]?.let { OutlineHull.build(it, 0.008f) })
        baked = out
    }

    /** Creates the rig entities. Must run on the main thread, after [preload]. */
    fun create(): AlienRig {
        val root = Entity().apply { setName(ROOT) }
        val body = Entity().apply { setName(BODY_PIVOT) }
        root.addChild(body)

        part(BODY, WHITE)?.let { body.addChild(it) }
        part(BODY_OUTLINE, INK)?.let { body.addChild(it) }
        part(MOUTH, INK)?.let { body.addChild(it) }
        part(BLUSH, BLUSH_COLOR)?.let { body.addChild(it) }

        // Eyes blink by scaling around their own centre.
        val eyes = pivot(EYES_PIVOT, EYE_CENTRE)
        part(EYES, WHITE, -EYE_CENTRE)?.let { eyes.addChild(it) }
        part(HIGHLIGHTS, WHITE, -EYE_CENTRE)?.let { eyes.addChild(it) }
        body.addChild(eyes)

        // The antenna swings around its base.
        val antenna = pivot(ANTENNA_PIVOT, AlienParts.ANTENNA_BASE)
        part(STALK, WHITE, -AlienParts.ANTENNA_BASE)?.let { antenna.addChild(it) }
        part(TIP, WHITE, -AlienParts.ANTENNA_BASE)?.let { antenna.addChild(it) }
        part(TIP_OUTLINE, INK, -AlienParts.ANTENNA_BASE)?.let { antenna.addChild(it) }
        body.addChild(antenna)

        // Touch targets: the body capsule and the antenna tip.
        body.addChild(collider("PetBodyCollider", Vec3(0f, 1.16f, 0f), TargetId.PET_BODY) { ShapeResource.createCapsule(2.3f, 0.85f) })
        antenna.addChild(collider("PetTipCollider", AlienParts.TIP_ANCHOR - AlienParts.ANTENNA_BASE + Vec3(0f, 0.12f, 0f), TargetId.PET_TIP) { ShapeResource.createSphere(0.3f) })
        return AlienRig(root, body, eyes, antenna)
    }

    private fun part(key: String, color: Int, offset: Vec3 = Vec3.ZERO, cullFront: Boolean = false): Entity? {
        val data = baked[key] ?: return null
        return meshes.model(key, data, color, offset.toSdk(), cullFront)
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
        private const val TIP = "Pet_Tip"
        private const val TIP_OUTLINE = "Pet_TipOutline"
        private const val EYES = "Pet_Eyes"
        private const val HIGHLIGHTS = "Pet_Highlights"
        private const val MOUTH = "Pet_Mouth"
        private const val BLUSH = "Pet_Blush"

        private const val WHITE = 0xFFFFFF
        private const val INK = 0x2F2826
        private const val BLUSH_COLOR = 0xF2A8A3
        private val EYE_CENTRE = Vec3(0f, 1.455f, 0.68f)
        private val BODY_SHADE = floatArrayOf(0.8f, 0.76f, 0.86f)
        private val TIP_SHADE = floatArrayOf(0.86f, 0.82f, 0.9f)
        private val EYE_BASE = floatArrayOf(0.13f, 0.12f, 0.15f)
        private val EYE_SHADE = floatArrayOf(0.7f, 0.7f, 0.75f)

        /** Default look: Chalk pink body, Tomato tip, Heart antenna. */
        val DEFAULT_LOOK = AlienLook(0xEDB9B1, 0xD9644F, 2)
    }
}
