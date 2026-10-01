package com.technomancer.pangu.ecs.systems

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Vector3
import com.technomancer.pangu.ecs.entities.EggFactory
import com.technomancer.pangu.ecs.entities.EggRig
import com.technomancer.pangu.ecs.entities.MeshFactory
import com.technomancer.pangu.ecs.entities.NestProps
import com.technomancer.pangu.ecs.entities.tint
import com.technomancer.pangu.ecs.toSdk
import com.technomancer.pangu.logic.PanguLogic
import com.technomancer.pangu.logic.art.EggMesh
import com.technomancer.pangu.logic.art.GradientColors
import com.technomancer.pangu.logic.flow.FlowState
import com.technomancer.pangu.logic.incubation.EggCondition
import com.technomancer.pangu.logic.incubation.EggGauge
import com.technomancer.pangu.logic.math.Quat
import com.technomancer.pangu.logic.math.Vec3
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin

/**
 * Writes the egg's state onto its entities each frame: where it is, its shell colours for the
 * look and condition, tilt and wobble, frost, glow, crack, the dormant seam and motes, the hatch
 * sequence (seam light, lifting top, sparkles), the blanket and the bead gauge on the nest rim.
 * Pad-local positions come from the logic; the egg itself is drawn at true size.
 */
class EggPresenter(
    private val rig: EggRig,
    private val props: NestProps,
    private val factory: EggFactory,
    private val meshes: MeshFactory,
    private val habitatScale: Float,
) {
    private val rootT = rig.root.transform()
    private val topPivotT = rig.topPivot.transform()
    private val glowT = rig.glow.transform()
    private val seamT = rig.seam.transform()
    private val frostT = rig.frost.mapNotNull { it.transform() }
    private val frostScale = rig.frost.map { it.transform()?.scaleVector ?: Vector3.ONE }
    private val crackT = rig.crack.mapNotNull { it.transform() }
    private val emblemT = rig.emblems.map { it.transform() }
    private val emblemScale = rig.emblems.map { it.transform()?.scaleVector ?: Vector3.ONE }
    private val moteT = rig.motes.mapNotNull { it.transform() }
    private val sparkT = rig.sparkles.mapNotNull { it.transform() }
    private val blanketT = props.blanket.transform()
    private val blanketScale = props.blanket.transform()?.scaleVector ?: Vector3.ONE
    private val drapeT = props.drape.transform()
    private val beadT = props.beads.mapNotNull { it.transform() }
    private val beadColors = IntArray(props.beads.size) { -1 }
    private val sparkDirs = List(EggFactory.SPARKLES) { i ->
        val a = i * 2.39996f
        val up = 0.4f + 0.6f * ((i * 37) % 11) / 10f
        Vec3(cos(a), up, sin(a)).normalized()
    }
    private val inv = 1f / habitatScale
    private val seamY = EggMesh.SEAM * EggMesh.HEIGHT

    private var shellColor = -1
    private var speckleColor = -1
    private var emblemColor = -1
    private var glowColor = -1
    private var shownShape = -1
    private var time = 0f

    fun apply(logic: PanguLogic, head: Vector3?, dt: Float) {
        time += dt
        val world = logic.egg
        val flow = logic.flow.flow
        val sealing = world.sealSec >= 0f
        val hatching = flow == FlowState.Hatching
        val shown = sealing || flow == FlowState.Incubation || hatching || flow == FlowState.Dormant
        if (!shown) {
            rootT?.setScaleVector(Vector3.ZERO)
            props.drape.transform()?.setScaleVector(Vector3.ZERO)
            blanketT?.setScaleVector(Vector3.ZERO)
            beadT.forEach { it.setScaleVector(Vector3.ZERO) }
            return
        }
        val condition = world.condition
        val look = logic.look
        val body = logic.catalog.bodyColor(look)
        val tip = logic.catalog.tipColor(look)

        // Shell colours follow the condition: faded when fading, grey when dormant.
        val shell = when (condition) {
            EggCondition.Dormant -> DORMANT
            EggCondition.Fading -> GradientColors.desaturate(body, 0.5f)
            else -> body
        }
        if (shell != shellColor) factory.shell(shell)?.let {
            factory.setShell(rig, it)
            shellColor = shell
        }
        val speck = when (condition) {
            EggCondition.Dormant -> DORMANT
            EggCondition.Fading -> GradientColors.desaturate(tip, 0.5f)
            else -> mix(tip, body, 0.3f)
        }
        if (speck != speckleColor) {
            speckleColor = speck
            rig.speckles.forEach { meshes.tint(it, speck) }
        }
        val emblem = if (condition == EggCondition.Dormant) DORMANT_EMBLEM else mix(tip, INK, 0.15f)
        if (emblem != emblemColor || look.shape != shownShape) {
            emblemColor = emblem
            shownShape = look.shape
            rig.emblems.forEachIndexed { i, e ->
                e.children().forEach { meshes.tint(it, emblem) }
                emblemT[i]?.setScaleVector(if (i == look.shape) emblemScale[i] else Vector3.ZERO)
            }
        }

        // Pose: in the nest (or on its glide), leaning a little when cold, wobbling when ready.
        val tiltDeg = when (condition) {
            EggCondition.Chilly -> -4f
            EggCondition.Fading -> 3f
            EggCondition.Ready -> -6f * 0.5f
            else -> 0f
        }
        val roll = tiltDeg * DEG + world.wobble
        rootT?.apply {
            setPosition(world.eggPosition.toSdk())
            setQuaternion(Quat.fromAxisAngle(Vec3(0f, 0f, 1f), roll).toSdk())
            setScaleVector(Vector3(world.formScale * inv))
        }

        frostT.forEachIndexed { i, t -> t.setScaleVector(if (condition == EggCondition.Chilly) frostScale[i] else Vector3.ZERO) }

        val hatch = world.hatchSec
        val glow = when {
            hatching || condition == EggCondition.Ready -> tip
            condition == EggCondition.Toasty -> BUTTER
            sealing -> tip
            else -> -1
        }
        if (glow < 0) {
            glowT?.setScaleVector(Vector3.ZERO)
        } else {
            if (glow != glowColor) {
                glowColor = glow
                rig.glow.components[ModelComponent::class.java]?.materials?.set(0, factory.glowMaterial(glow))
            }
            val pulse = 1f + 0.04f * sin(3f * time)
            glowT?.setScaleVector(Vector3(EggFactory.GLOW_SCALE.x * pulse, EggFactory.GLOW_SCALE.y * pulse, EggFactory.GLOW_SCALE.z * pulse))
        }

        // The crack grows a segment per tap and disappears once the seam lights.
        val cracks = when {
            hatching -> if (hatch < PanguLogic.HATCH_SEAM_SEC) crackT.size else 0
            condition == EggCondition.Ready -> world.egg.crackTaps.coerceAtMost(crackT.size)
            else -> 0
        }
        crackT.forEachIndexed { i, t -> t.setScaleVector(if (i < cracks) Vector3.ONE else Vector3.ZERO) }

        val seamOn = condition == EggCondition.Dormant || (hatching && hatch >= PanguLogic.HATCH_SEAM_SEC)
        seamT?.setScaleVector(if (seamOn) Vector3.ONE else Vector3.ZERO)

        // The top cap lifts 5 cm and tips 22° as it opens.
        val lift = if (hatching) ((hatch - PanguLogic.HATCH_LIFT_SEC) / (PanguLogic.HATCH_RISE_SEC - PanguLogic.HATCH_LIFT_SEC)).coerceIn(0f, 1f) else 0f
        val e = lift * lift * (3f - 2f * lift)
        topPivotT?.apply {
            setPosition(Vector3(0f, seamY + 0.05f * e, 0f))
            setQuaternion(Quat.fromAxisAngle(Vec3(0f, 0f, 1f), -22f * DEG * e).toSdk())
        }

        updateSparkles(hatching, hatch, tip)
        updateMotes(condition == EggCondition.Dormant)
        updateBlanket(logic, flow)
        updateBeads(logic, condition, head, hatching || sealing || flow != FlowState.Incubation)
    }

    private var sparkleColor = -1

    private fun updateSparkles(hatching: Boolean, hatch: Float, tip: Int) {
        val t = if (hatching) hatch - PanguLogic.HATCH_LIFT_SEC else -1f
        if (t < 0f || t > SPARKLE_SEC) {
            sparkT.forEach { it.setScaleVector(Vector3.ZERO) }
            return
        }
        if (tip != sparkleColor) {
            sparkleColor = tip
            rig.sparkles.forEach { meshes.tint(it, tip) }
        }
        val s = 1f - t / SPARKLE_SEC
        sparkT.forEachIndexed { i, tr ->
            val d = sparkDirs[i]
            val speed = 0.12f + 0.04f * (i % 5)
            tr.setPosition(Vector3(d.x * speed * t, seamY + d.y * speed * t - 0.12f * t * t, d.z * speed * t))
            tr.setScaleVector(Vector3(s))
        }
    }

    private fun updateMotes(on: Boolean) {
        moteT.forEachIndexed { i, t ->
            if (!on) {
                t.setScaleVector(Vector3.ZERO)
                return@forEachIndexed
            }
            val phase = ((time * 0.25f + i / 3f) % 1f)
            val a = i * 2.1f
            t.setPosition(Vector3(0.03f * cos(a), EggMesh.HEIGHT + 0.01f + 0.06f * phase, 0.03f * sin(a)))
            t.setQuaternion(Quat.fromYaw(time + i).toSdk())
            t.setScaleVector(Vector3(sin(PI.toFloat() * phase)))
        }
    }

    private fun updateBlanket(logic: PanguLogic, flow: FlowState) {
        val world = logic.egg
        val active = flow == FlowState.Incubation && world.sealSec < 0f
        val tucked = active && world.isTucked(logic.nowMs)
        if (tucked) {
            blanketT?.setScaleVector(Vector3.ZERO)
            drapeT?.apply {
                setPosition(world.eggPosition.toSdk())
                setScaleVector(Vector3(inv))
            }
        } else {
            drapeT?.setScaleVector(Vector3.ZERO)
            blanketT?.apply {
                setScaleVector(if (active) blanketScale else Vector3.ZERO)
                setPosition(world.blanketPosition.toSdk())
            }
        }
    }

    /** Lights beads on the nest rim for the warmth band, hiding the ones behind the egg as seen from [head]. */
    private fun updateBeads(logic: PanguLogic, condition: EggCondition, head: Vector3?, hidden: Boolean) {
        if (hidden) {
            beadT.forEach { it.setScaleVector(Vector3.ZERO) }
            return
        }
        val facing = if (head != null) atan2(head.x, head.z) else 0f
        val lit = EggGauge.litCount(condition)
        val litColor = EggGauge.litColor(condition)
        var shownIndex = 0
        beadT.forEachIndexed { i, t ->
            // Beads spread around the front of the rim; the arc straight behind the egg is skipped.
            val a = facing + (i - (beadT.size - 1) / 2f) * BEAD_STEP
            t.setPosition(Vector3(NEST_RIM * sin(a), BEAD_Y, NEST_RIM * cos(a)))
            t.setScaleVector(Vector3(inv))
            val color = if (shownIndex < lit) litColor else EggFactory.PAPER
            shownIndex++
            if (beadColors[i] != color) {
                beadColors[i] = color
                meshes.tint(props.beads[i], color)
            }
        }
    }

    private fun Entity.transform() = components[TransformComponent::class.java]

    private fun Entity.children(): List<Entity> = getChildren().toList()

    companion object {
        private const val DEG = (PI / 180.0).toFloat()
        private const val DORMANT = 0xC9C3CF
        private const val DORMANT_EMBLEM = 0x8F8796
        private const val INK = 0x2F2826
        private const val BUTTER = 0xF2DE9B
        private const val SPARKLE_SEC = 2.5f

        /** Nest rim radius and bead height in pad-local metres. */
        private const val NEST_RIM = 0.092f
        private const val BEAD_Y = 0.018f

        /** Twelve beads over 300° of the rim, leaving the 60° behind the egg empty. */
        private const val BEAD_STEP = (300.0 / 11.0 * PI / 180.0).toFloat()

        fun mix(a: Int, b: Int, t: Float): Int {
            fun ch(shift: Int): Int {
                val x = (a shr shift) and 0xFF
                val y = (b shr shift) and 0xFF
                return (x + (y - x) * t + 0.5f).toInt().coerceIn(0, 255)
            }
            return (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
        }
    }
}
