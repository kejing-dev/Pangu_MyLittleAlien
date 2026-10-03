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
import com.technomancer.pangu.logic.incubation.EggAction
import com.technomancer.pangu.logic.incubation.EggGauge
import com.technomancer.pangu.logic.incubation.MicChoice
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
    private var clearT = 0f
    private var wasTucked = false
    private val drapeT = props.drape.transform()
    private val beadT = props.beads.mapNotNull { it.transform() }
    private val beadColors = IntArray(props.beads.size) { -1 }
    private val musicBoxT = props.musicBox.transform()
    private val crankT = props.crank.transform()
    private val sparkDirs = List(EggFactory.SPARKLES) { i ->
        val a = i * 2.39996f
        val up = 0.4f + 0.6f * ((i * 37) % 11) / 10f
        Vec3(cos(a), up, sin(a)).normalized()
    }
    private val inv = 1f / habitatScale
    private val writes = TransformWrites()
    private val invVector = Vector3(inv)
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
            writes.scale(rootT, Vector3.ZERO)
            writes.scale(props.drape.transform(), Vector3.ZERO)
            writes.scale(blanketT, Vector3.ZERO)
            beadT.forEach { writes.scale(it, Vector3.ZERO) }
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
        // Speckles sit a little toward the body colour and brighten to the full tip colour while polishing.
        val shine = (world.rituals.polishProgress * SHINE_STEPS).toInt() / SHINE_STEPS.toFloat()
        val speck = when (condition) {
            EggCondition.Dormant -> DORMANT
            EggCondition.Fading -> GradientColors.desaturate(tip, 0.5f)
            else -> mix(tip, body, 0.3f * (1f - shine))
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
                writes.scale(emblemT[i], if (i == look.shape) emblemScale[i] else Vector3.ZERO)
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
            // Once the hatchling has hopped out, the empty shells shrink away instead of vanishing.
            val gone = if (hatching) ((world.hatchSec - SHELL_SHRINK_START) / SHELL_SHRINK_SEC).coerceIn(0f, 1f) else 0f
            val keep = (1f - gone * gone * (3f - 2f * gone)) * world.formScale.coerceAtMost(1f)
            writes.scale(this, if (keep >= 1f) invVector else Vector3(keep * inv))
        }

        frostT.forEachIndexed { i, t -> writes.scale(t, if (condition == EggCondition.Chilly) frostScale[i] else Vector3.ZERO) }

        val hatch = world.hatchSec
        val glow = when {
            hatching || condition == EggCondition.Ready -> tip
            condition == EggCondition.Toasty -> BUTTER
            sealing -> tip
            else -> -1
        }
        if (glow < 0) {
            writes.scale(glowT, Vector3.ZERO)
        } else {
            if (glow != glowColor) {
                glowColor = glow
                rig.glow.components[ModelComponent::class.java]?.materials?.set(0, factory.glowMaterial(glow))
            }
            val pulse = 1f + 0.04f * sin(3f * time)
            writes.scale(glowT, Vector3(EggFactory.GLOW_SCALE.x * pulse, EggFactory.GLOW_SCALE.y * pulse, EggFactory.GLOW_SCALE.z * pulse))
        }

        // The crack grows a segment per tap and disappears once the seam lights.
        val cracks = when {
            hatching -> if (hatch < PanguLogic.HATCH_SEAM_SEC) crackT.size else 0
            condition == EggCondition.Ready -> world.egg.crackTaps.coerceAtMost(crackT.size)
            else -> 0
        }
        crackT.forEachIndexed { i, t -> writes.scale(t, if (i < cracks) Vector3.ONE else Vector3.ZERO) }

        val seamOn = condition == EggCondition.Dormant || (hatching && hatch >= PanguLogic.HATCH_SEAM_SEC)
        writes.scale(seamT, if (seamOn) Vector3.ONE else Vector3.ZERO)

        // The top cap pops up and tips off to the side, so the opening is clear before the hatchling climbs out.
        val lift = if (hatching) ((hatch - PanguLogic.HATCH_LIFT_SEC) / (PanguLogic.HATCH_RISE_SEC - PanguLogic.HATCH_LIFT_SEC)).coerceIn(0f, 1f) else 0f
        val e = lift * lift * (3f - 2f * lift)
        topPivotT?.apply {
            setPosition(Vector3(CAP_SIDE * e, seamY + CAP_LIFT * 4f * e * (1f - e) + CAP_DROP * e, 0f))
            setQuaternion(Quat.fromAxisAngle(Vec3(0f, 0f, 1f), -CAP_TIP_DEG * DEG * e).toSdk())
        }

        updateSparkles(hatching, hatch, tip)
        updateMotes(condition == EggCondition.Dormant)
        // Once the egg is ready to hatch, everything on or beside it (blanket, music box) shrinks away.
        val clear = hatching || condition == EggCondition.Ready
        clearT = (clearT + (if (clear) dt else -dt) / PROPS_AWAY_SEC).coerceIn(0f, 1f)
        val keep = 1f - clearT * clearT * (3f - 2f * clearT)
        updateBlanket(logic, flow, keep)
        updateMusicBox(logic, keep)
        updateBeads(logic, condition, head, hatching || sealing || flow != FlowState.Incubation)
    }

    private var sparkleColor = -1

    private fun updateSparkles(hatching: Boolean, hatch: Float, tip: Int) {
        val t = if (hatching) hatch - PanguLogic.HATCH_LIFT_SEC else -1f
        if (t < 0f || t > SPARKLE_SEC) {
            sparkT.forEach { writes.scale(it, Vector3.ZERO) }
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
            writes.scale(tr, Vector3(s))
        }
    }

    private fun updateMotes(on: Boolean) {
        moteT.forEachIndexed { i, t ->
            if (!on) {
                writes.scale(t, Vector3.ZERO)
                return@forEachIndexed
            }
            val phase = ((time * 0.25f + i / 3f) % 1f)
            val a = i * 2.1f
            t.setPosition(Vector3(0.03f * cos(a), EggMesh.HEIGHT + 0.01f + 0.06f * phase, 0.03f * sin(a)))
            t.setQuaternion(Quat.fromYaw(time + i).toSdk())
            writes.scale(t, Vector3(sin(PI.toFloat() * phase)))
        }
    }

    /** The folded blanket beside the nest, or draped over the egg while tucked; [keep] shrinks it away (1 = full size). */
    private fun updateBlanket(logic: PanguLogic, flow: FlowState, keep: Float) {
        val world = logic.egg
        // While hatching the drape stays (shrinking) on the egg it was tucked around.
        val active = (flow == FlowState.Incubation || flow == FlowState.Hatching) && world.sealSec < 0f && keep > 0f
        val tucked = active && (world.isTucked(logic.nowMs) || (flow == FlowState.Hatching && wasTucked))
        if (flow == FlowState.Incubation) wasTucked = world.isTucked(logic.nowMs)
        if (tucked) {
            writes.scale(blanketT, Vector3.ZERO)
            drapeT?.apply {
                setPosition(world.eggPosition.toSdk())
                writes.scale(this, if (keep >= 1f) invVector else Vector3(inv * keep))
            }
        } else {
            writes.scale(drapeT, Vector3.ZERO)
            blanketT?.apply {
                writes.scale(this, if (!active) Vector3.ZERO else if (keep >= 1f) blanketScale else Vector3(blanketScale.x * keep, blanketScale.y * keep, blanketScale.z * keep))
                setPosition(world.blanketPosition.toSdk())
            }
        }
    }

    /** The music box appears beside the nest for the lullaby; its crank follows the hand. */
    private fun updateMusicBox(logic: PanguLogic, keep: Float) {
        val r = logic.egg.rituals
        val shown = r.active == EggAction.Sing && r.singPath == MicChoice.MusicBox && keep > 0f
        musicBoxT?.apply {
            writes.scale(this, if (!shown) Vector3.ZERO else if (keep >= 1f) invVector else Vector3(inv * keep))
            setPosition(MUSIC_BOX_AT)
        }
        if (shown) {
            val spin = if (r.lullabySec >= 0f) -r.lullabySec * 2f else -r.crank.angle
            crankT?.setQuaternion(Quat.fromYaw(spin).toSdk())
        }
    }

    /** Lights beads on the nest rim for the warmth band, hiding the ones behind the egg as seen from [head]. */
    private fun updateBeads(logic: PanguLogic, condition: EggCondition, head: Vector3?, hidden: Boolean) {
        if (hidden) {
            beadT.forEach { writes.scale(it, Vector3.ZERO) }
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
            writes.scale(t, invVector)
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
        /**
         * Seconds into the hatch when the empty shells start shrinking, and how long they take: they
         * are gone before the hatchling lands where the egg stood.
         */
        private const val SHELL_SHRINK_START = PanguLogic.HATCH_RISE_SEC + 0.4f
        private const val SHELL_SHRINK_SEC = 0.45f

        /** Seconds the blanket and music box take to shrink away when the egg is ready to hatch. */
        private const val PROPS_AWAY_SEC = 0.4f

        /** Where the opened cap ends up (metres): off to the side, after a small hop up, tipped right over. */
        private const val CAP_SIDE = 0.075f
        private const val CAP_LIFT = 0.04f
        private const val CAP_DROP = -0.03f
        private const val CAP_TIP_DEG = 75f
        private const val DEG = (PI / 180.0).toFloat()
        private const val DORMANT = 0xC9C3CF
        private const val DORMANT_EMBLEM = 0x8F8796
        private const val INK = 0x2F2826
        private const val BUTTER = 0xF2DE9B
        private const val SPARKLE_SEC = 2.5f
        private const val SHINE_STEPS = 8
        /** Where the music box stands, pad-local metres: front right of the nest. */
        private val MUSIC_BOX_AT = Vector3(0.12f, 0f, 0.1f)

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
