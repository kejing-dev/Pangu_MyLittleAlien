package com.technomancer.pangu.ecs.entities

import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.BlendingMode
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Vector3
import com.technomancer.pangu.ecs.components.TagComponent
import com.technomancer.pangu.ecs.rgb
import com.technomancer.pangu.ecs.toSdk
import com.technomancer.pangu.logic.art.EggMesh
import com.technomancer.pangu.logic.art.MeshData
import com.technomancer.pangu.logic.art.OutlineHull
import com.technomancer.pangu.logic.art.PrimitiveMeshes
import com.technomancer.pangu.logic.art.ShellMark
import com.technomancer.pangu.logic.incubation.EggGauge
import com.technomancer.pangu.logic.input.TargetId
import com.technomancer.pangu.logic.math.Quat
import com.technomancer.pangu.logic.math.Vec3
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/** A shell mark entity and where it sits on the shell. */
class MarkEntity(val entity: Entity, val mark: ShellMark)

/** Handles to the egg's entities. Everything under [root] is in true metres with the egg's bottom centre at the origin. */
class EggRig(
    val root: Entity,
    val bottom: Entity,
    /** Pivot at the seam height that lifts and turns the top cap when the egg hatches. */
    val topPivot: Entity,
    val top: Entity,
    val speckles: List<Entity>,
    /** One emblem per antenna shape; only the look's one is shown. */
    val emblems: List<Entity>,
    val frost: List<Entity>,
    val crack: List<Entity>,
    val seam: Entity,
    val glow: Entity,
    val motes: List<Entity>,
    val sparkles: List<Entity>,
)

/** The pad's props for the egg phase: the blanket (folded and tucked), the gauge beads and the studio pedestal. */
class NestProps(
    val blanket: Entity,
    val drape: Entity,
    val beads: List<Entity>,
    val pedestal: Entity,
    /** The music box (shown for the music-box lullaby) and the pivot its crank turns on. */
    val musicBox: Entity,
    val crank: Entity,
)

/**
 * Builds the egg (the two lathe halves from [EggMesh] with ink outlines, speckles, the shape
 * emblem, frost, crack, seam glow, dormant motes and hatch sparkles) and the nest props. Shell
 * colours are baked into vertex colours, so a new look or condition swaps in meshes baked on a
 * background thread by [bake].
 */
class EggFactory(private val meshes: MeshFactory) {
    private val parts: Map<String, MeshData> by lazy {
        mapOf(
            MARK to PrimitiveMeshes.disc(0.5f, segments = 16),
            CRACK_KEY to EggMesh.tube(EggMesh.crackLine(), 0.0007f),
            SEAM_KEY to EggMesh.tube(EggMesh.seamLine(), 0.0009f),
            GLOW to PrimitiveMeshes.sphere(1f, rings = 16, segments = 24),
            MOTE to PrimitiveMeshes.octahedron(0.006f),
            SPARKLE to PrimitiveMeshes.octahedron(0.004f),
            BEAD to PrimitiveMeshes.sphere(BEAD_RADIUS, rings = 8, segments = 12),
            BLANKET to PrimitiveMeshes.cylinder(0.045f, 0.012f, segments = 32),
            DRAPE to PrimitiveMeshes.halfDome(0.052f, 0.078f),
            PEDESTAL to PrimitiveMeshes.cylinder(PEDESTAL_RADIUS, PEDESTAL_HEIGHT, segments = 64, bottomY = -PEDESTAL_HEIGHT),
            PEDESTAL_RIM to PrimitiveMeshes.torus(PEDESTAL_RADIUS, 0.0015f, segments = 64, sides = 6),
            MUSIC_BOX to PrimitiveMeshes.box(0.06f, 0.04f, 0.045f),
            MUSIC_BOX_LID to PrimitiveMeshes.box(0.064f, 0.006f, 0.049f),
            CRANK_ARM to PrimitiveMeshes.box(0.022f, 0.004f, 0.005f),
            CRANK_KNOB to PrimitiveMeshes.sphere(0.006f, rings = 8, segments = 12),
        ) + crackSegments()
    }

    /** One tube per crack segment so the crack can grow a segment per tap. */
    private fun crackSegments(): Map<String, MeshData> {
        val line = EggMesh.crackLine()
        return (0 until line.size - 1).associate { i -> "$CRACK_KEY$i" to EggMesh.tube(line.subList(i, i + 2), 0.0007f) }
    }

    private val baker = Executors.newSingleThreadExecutor { r -> Thread(r, "PanguEggBake").apply { isDaemon = true } }
    private val ready = ConcurrentHashMap<Int, Pair<MeshResource, MeshResource>>()
    private val pending = ConcurrentHashMap.newKeySet<Int>()
    private var outlines: Pair<MeshData, MeshData>? = null

    /** Uploads the fixed meshes and the shell for [bodyColor]; call off the main thread. */
    fun preloadMeshes(bodyColor: Int) {
        val halves = EggMesh.build(bodyColor)
        outlines = OutlineHull.build(halves.top, OUTLINE) to OutlineHull.build(halves.bottom, OUTLINE)
        meshes.preload(
            parts.toList() + listOf(
                TOP_OUTLINE to outlines!!.first,
                BOTTOM_OUTLINE to outlines!!.second,
                shellKey(TOP, bodyColor) to halves.top,
                shellKey(BOTTOM, bodyColor) to halves.bottom,
            ),
        )
        ready[bodyColor] = meshes.mesh(halves.top, shellKey(TOP, bodyColor)) to meshes.mesh(halves.bottom, shellKey(BOTTOM, bodyColor))
    }

    /** Shell meshes (top, bottom) for [color] if they have been baked; otherwise starts baking them and returns null. */
    fun shell(color: Int): Pair<MeshResource, MeshResource>? {
        ready[color]?.let { return it }
        if (pending.add(color)) {
            baker.execute {
                val halves = EggMesh.build(color)
                ready[color] = meshes.mesh(halves.top, shellKey(TOP, color)) to meshes.mesh(halves.bottom, shellKey(BOTTOM, color))
                pending.remove(color)
            }
        }
        return null
    }

    private fun part(name: String, color: Int, position: Vector3 = Vector3.ZERO) =
        meshes.model(name, parts.getValue(name), color, position)

    /**
     * Creates the egg. [bodyColor] picks the shell baked in [preloadMeshes]; [tipShapes] are the
     * alien's antenna tip meshes (key and data) used for the emblem. Main thread only.
     */
    fun create(bodyColor: Int, tipShapes: List<Pair<String, MeshData>>): EggRig {
        val root = Entity().apply { setName(EGG_ROOT) }
        val (topMesh, bottomMesh) = ready.getValue(bodyColor)
        val seamY = EggMesh.SEAM * EggMesh.HEIGHT

        val bottom = ModelEntity(bottomMesh, meshes.unlit(WHITE)).apply { setName(BOTTOM) }
        root.addChild(bottom)
        bottom.addChild(ModelEntity(meshes.mesh(outlines!!.second, BOTTOM_OUTLINE), meshes.unlit(INK)).apply { setName(BOTTOM_OUTLINE) })

        val topPivot = Entity().apply {
            setName("PH_EggTopPivot")
            transform()?.setPosition(Vector3(0f, seamY, 0f))
        }
        root.addChild(topPivot)
        val top = ModelEntity(topMesh, meshes.unlit(WHITE)).apply {
            setName(TOP)
            transform()?.setPosition(Vector3(0f, -seamY, 0f))
        }
        topPivot.addChild(top)
        top.addChild(ModelEntity(meshes.mesh(outlines!!.first, TOP_OUTLINE), meshes.unlit(INK)).apply { setName(TOP_OUTLINE) })

        // Marks on the top cap ride along with it when it lifts.
        fun holder(mark: ShellMark) = if (mark.at.position.y > seamY + EggMesh.SEAM_AMPLITUDE * EggMesh.HEIGHT) top else bottom
        val speckles = EggMesh.speckles().mapIndexed { i, m ->
            markEntity("PH_EggSpeckle$i", m, WHITE).also { holder(m).addChild(it) }
        }
        val frost = EggMesh.frost().mapIndexed { i, m ->
            markEntity("PH_EggFrost$i", m, FROST_COLOR).also { holder(m).addChild(it) }
        }

        val emblemAt = EggMesh.emblem()
        val emblems = tipShapes.map { (key, data) ->
            val b = data.bounds()
            val centre = Vec3((b[0] + b[3]) / 2f, (b[1] + b[4]) / 2f, (b[2] + b[5]) / 2f)
            val extent = maxOf(b[3] - b[0], b[4] - b[1]).coerceAtLeast(1e-4f)
            val s = EMBLEM_SIZE / extent
            Entity().apply {
                setName("PH_EggEmblem_$key")
                transform()?.apply {
                    setPosition((emblemAt.position + emblemAt.normal * 0.0012f).toSdk())
                    setQuaternion(Quat.fromTo(Vec3(0f, 0f, 1f), emblemAt.normal).toSdk())
                    setScaleVector(Vector3(s, s, s * 0.25f))
                }
                addChild(meshes.model(key, data, WHITE, (-centre).toSdk()))
                bottom.addChild(this)
            }
        }

        val crack = (0 until EggMesh.crackLine().size - 1).map { i -> part("$CRACK_KEY$i", INK).also { root.addChild(it) } }
        val seam = part(SEAM_KEY, PAPER).also { root.addChild(it) }

        val glow = ModelEntity(meshes.mesh(parts.getValue(GLOW), GLOW), glowMaterial(WHITE)).apply {
            setName(GLOW)
            transform()?.apply {
                setPosition(Vector3(0f, EggMesh.HEIGHT * 0.5f, 0f))
                setScaleVector(GLOW_SCALE)
            }
        }
        root.addChild(glow)

        val motes = List(3) { i -> part(MOTE, PAPER).also { it.setName("PH_EggMote$i"); root.addChild(it) } }
        val sparkles = List(SPARKLES) { i -> part(SPARKLE, WHITE).also { it.setName("PH_EggSparkle$i"); root.addChild(it) } }

        // Grab and tap target around the whole egg.
        root.addChild(Entity().apply {
            setName("PH_EggCollider")
            transform()?.setPosition(Vector3(0f, EggMesh.HEIGHT * 0.5f, 0f))
            components.set(InteractableComponent())
            components.set(CollisionComponent(listOf(ShapeResource.createSphere(0.05f)), PhysicsMaterialResource()))
            components.set(HoverEffectComponent())
            components.set(TagComponent(TargetId.EGG))
        })
        return EggRig(root, bottom, topPivot, top, speckles, emblems, frost, crack, seam, glow, motes, sparkles)
    }

    /** Every speckle and frost dot shares the one disc mesh; each entity gets its own name. */
    private fun markEntity(name: String, m: ShellMark, color: Int): Entity =
        meshes.model(MARK, parts.getValue(MARK), color).apply {
            setName(name)
            transform()?.apply {
                setPosition((m.at.position + m.at.normal * 0.0006f).toSdk())
                setQuaternion((Quat.fromTo(Vec3.UP, m.at.normal) * Quat.fromYaw(m.turn)).toSdk())
                setScaleVector(Vector3(m.width, 1f, m.height))
            }
        }

    /** Blanket, gauge beads and pedestal, in pad-local units (the habitat's display scale applies). */
    fun createNestProps(displayScale: Float): NestProps {
        val inv = Vector3(1f / displayScale)
        val blanket = part(BLANKET, BLANKET_COLOR).apply {
            transform()?.setScaleVector(Vector3(1.25f / displayScale, 1f / displayScale, 0.85f / displayScale))
            components.set(InteractableComponent())
            components.set(CollisionComponent(listOf(ShapeResource.createSphere(0.05f)), PhysicsMaterialResource()))
            components.set(HoverEffectComponent())
            components.set(TagComponent(TargetId.BLANKET))
        }
        val drape = part(DRAPE, BLANKET_COLOR).apply { transform()?.setScaleVector(Vector3.ZERO) }
        val beads = List(EggGauge.BEADS) { i ->
            part(BEAD, PAPER).apply {
                setName("PH_Bead$i")
                transform()?.setScaleVector(inv)
            }
        }
        val pedestal = part(PEDESTAL, PAPER).apply {
            addChild(part(PEDESTAL_RIM, INK))
            transform()?.setScaleVector(inv)
        }
        val musicBox = Entity().apply {
            setName(MUSIC_BOX)
            transform()?.setScaleVector(Vector3.ZERO)
            addChild(part(MUSIC_BOX, MUSIC_BOX_COLOR, Vector3(0f, 0.02f, 0f)))
            addChild(part(MUSIC_BOX_LID, INK, Vector3(0f, 0.043f, 0f)))
        }
        // The crank turns on a vertical axis on the lid; the knob is what you grab.
        val crank = Entity().apply {
            setName("PH_MusicBoxCrank")
            transform()?.setPosition(Vector3(0f, 0.05f, 0f))
            addChild(part(CRANK_ARM, INK, Vector3(0.011f, 0f, 0f)))
            addChild(part(CRANK_KNOB, BUTTER, Vector3(0.022f, 0.004f, 0f)).apply {
                components.set(InteractableComponent())
                components.set(CollisionComponent(listOf(ShapeResource.createSphere(0.025f)), PhysicsMaterialResource()))
                components.set(HoverEffectComponent())
                components.set(TagComponent(TargetId.CRANK))
            })
        }
        musicBox.addChild(crank)
        return NestProps(blanket, drape, beads, pedestal, musicBox, crank)
    }

    private val glowMaterials = HashMap<Int, UnlitMaterial>()

    /** See-through glow material of [color], shared per colour. */
    fun glowMaterial(color: Int): UnlitMaterial = glowMaterials.getOrPut(color) {
        UnlitMaterial.create(BlendingMode.TRANSPARENT).apply {
            setBaseColor(rgb(color, GLOW_OPACITY))
            setOpacity(GLOW_OPACITY)
            setApplyToneMapping(false)
            // Shared and swapped between models, so kept alive when none uses it.
            toGlobal()
        }
    }

    /** Swaps the shell meshes of [rig] for [shell]. */
    fun setShell(rig: EggRig, shell: Pair<MeshResource, MeshResource>) {
        rig.top.components[ModelComponent::class.java]?.mesh = shell.first
        rig.bottom.components[ModelComponent::class.java]?.mesh = shell.second
    }

    private fun Entity.transform() = components[TransformComponent::class.java]

    companion object {
        const val EGG_ROOT = "PH_Egg"
        private const val TOP = "PH_EggTop"
        private const val BOTTOM = "PH_EggBottom"
        private const val TOP_OUTLINE = "PH_EggTopOutline"
        private const val BOTTOM_OUTLINE = "PH_EggBottomOutline"
        private const val MARK = "PH_EggMark"
        private const val CRACK_KEY = "PH_EggCrack"
        private const val SEAM_KEY = "PH_EggSeam"
        private const val GLOW = "PH_EggGlow"
        private const val MOTE = "PH_EggMote"
        private const val SPARKLE = "PH_EggSparkle"
        private const val BEAD = "PH_Bead"
        private const val BLANKET = "PH_Blanket"
        private const val DRAPE = "PH_BlanketDrape"
        private const val PEDESTAL = "PH_Pedestal"
        private const val PEDESTAL_RIM = "PH_PedestalRim"
        private const val MUSIC_BOX = "PH_MusicBox"
        private const val MUSIC_BOX_LID = "PH_MusicBoxLid"
        private const val CRANK_ARM = "PH_MusicBoxCrankArm"
        private const val CRANK_KNOB = "PH_MusicBoxCrankKnob"
        private const val MUSIC_BOX_COLOR = 0xC9B7DA
        private const val BUTTER = 0xF2DE9B

        const val SPARKLES = 40
        const val PEDESTAL_RADIUS = 0.105f
        const val PEDESTAL_HEIGHT = 0.02f
        const val BEAD_RADIUS = 0.0063f
        private const val OUTLINE = 0.0012f
        private const val EMBLEM_SIZE = 0.026f
        private const val GLOW_OPACITY = 0.35f
        val GLOW_SCALE = Vector3(0.05f, 0.066f, 0.05f)

        private const val WHITE = 0xFFFFFF
        const val INK = 0x2F2826
        const val PAPER = 0xF7F3EB
        private const val FROST_COLOR = 0xFFFFFF
        private const val BLANKET_COLOR = 0xA8BFD4

        fun shellKey(half: String, color: Int) = "${half}_%06X".format(color)
    }
}
