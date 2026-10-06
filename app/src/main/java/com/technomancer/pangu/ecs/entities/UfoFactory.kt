package com.technomancer.pangu.ecs.entities

import android.content.res.AssetManager
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Vector3
import com.technomancer.pangu.ecs.rgb
import com.technomancer.pangu.ecs.toSdk
import com.technomancer.pangu.logic.art.GlbNode
import com.technomancer.pangu.logic.art.GlbParser

/**
 * The entities of the UFO that takes the pet home.
 * [root] is placed over the pet; [craft] is the ship itself (it hovers and turns under the root);
 * [beam] is the light cone below it; [lights] are the ten rim lights, in order around the rim, and
 * [tinted] are the parts that take the pet's antenna colour (the rim lights and the antenna ball).
 */
class UfoRig(
    val root: Entity,
    val craft: Entity,
    val beam: Entity?,
    val lights: List<Entity>,
    val tinted: List<Entity>,
)

/**
 * Builds the UFO from ufo.glb: hull, rim band, glass dome, beam lens, antenna, face and ten rim
 * lights, each with the ink outline the file provides, plus its light beam. Parts keep the file's
 * colours and baked shading; the rim lights and the antenna ball are white in the file so they can
 * be tinted with the pet's antenna colour.
 */
class UfoFactory(private val meshes: MeshFactory) {
    private var nodes: List<GlbNode> = emptyList()

    /** Parses the model and uploads its meshes. Slow; call off the main thread. */
    fun preload(assets: AssetManager) {
        val parsed = GlbParser.parseScene(assets.open(GLB_PATH).use { it.readBytes() })
        meshes.preload(parsed.mapNotNull { n -> n.mesh?.let { key(n) to it } }.distinctBy { it.first })
        nodes = parsed
    }

    /** Creates the UFO, hidden. Must run on the main thread, after [preload]; null if the model didn't load. */
    fun create(): UfoRig? {
        if (nodes.isEmpty()) return null
        val root = Entity().apply {
            setName(ROOT)
            components[TransformComponent::class.java]?.setScaleVector(Vector3.ZERO)
        }
        val lights = ArrayList<Entity>()
        val tinted = ArrayList<Entity>()
        var beam: Entity? = null
        val entities = ArrayList<Entity>(nodes.size)
        for (node in nodes) {
            val mesh = node.mesh
            val isBeam = node.name.startsWith(BEAM_PREFIX)
            val entity: Entity = when {
                mesh == null -> Entity()
                isBeam -> ModelEntity(meshes.mesh(mesh, key(node)), beamMaterial(node.color, node.opacity))
                else -> ModelEntity(meshes.mesh(mesh, key(node)), meshes.unlit(node.color))
            }
            entity.setName("Ufo_${node.name}")
            entity.components[TransformComponent::class.java]?.apply {
                setPosition(node.translation.toSdk())
                setScaleVector(node.scale.toSdk())
            }
            if (isBeam) beam = entity
            if (LIGHT.matches(node.name)) lights += entity
            if (LIGHT.matches(node.name) || node.name == ANTENNA_BALL) tinted += entity
            entities += entity
        }
        // The file's root node becomes the craft: scaled to its size on the pad, under our own root.
        var craft: Entity = root
        nodes.forEachIndexed { i, node ->
            if (node.parent >= 0) {
                entities[node.parent].addChild(entities[i])
            } else {
                craft = entities[i]
                entities[i].components[TransformComponent::class.java]?.setScaleVector(Vector3(SCALE))
                root.addChild(entities[i])
            }
        }
        return UfoRig(root, craft, beam, lights, tinted)
    }

    private fun key(node: GlbNode) = "Ufo_${node.meshName}"

    /** The see-through beam; shared, so it is kept alive when nothing shows it. */
    private fun beamMaterial(color: Int, opacity: Float): UnlitMaterial =
        UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
            setBaseColor(rgb(color, opacity))
            setOpacity(opacity)
            setApplyToneMapping(false)
            toGlobal()
        }

    companion object {
        const val GLB_PATH = "models/ufo.glb"
        const val ROOT = "PH_Ufo"

        /** Pad-local metres per model unit: the hull is 2.6 units across, so about 31 cm on the pad. */
        const val SCALE = 0.12f

        /** Where the beam leaves the lens, and how wide the model's beam is drawn, in model units below the craft's origin. */
        const val LENS_Y = -0.2f

        private const val BEAM_PREFIX = "Beam ("
        private const val ANTENNA_BALL = "AntennaBall"
        private val LIGHT = Regex("Light_[0-9]+")
    }
}
