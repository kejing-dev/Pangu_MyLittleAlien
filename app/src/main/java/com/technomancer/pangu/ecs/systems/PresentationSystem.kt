package com.technomancer.pangu.ecs.systems

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Vector3
import com.technomancer.pangu.ecs.entities.AlienRig
import com.technomancer.pangu.ecs.entities.HabitatFactory
import com.technomancer.pangu.ecs.entities.MeshFactory
import com.technomancer.pangu.ecs.entities.applyLook
import com.technomancer.pangu.ecs.entities.tint
import com.technomancer.pangu.ecs.toSdk
import com.technomancer.pangu.logic.PanguLogic
import com.technomancer.pangu.logic.art.AlienParts
import com.technomancer.pangu.logic.art.EggMesh
import com.technomancer.pangu.logic.creation.PetLook
import com.technomancer.pangu.logic.flow.FlowState
import com.technomancer.pangu.logic.config.UiConfig
import com.technomancer.pangu.logic.flow.ScreenId
import com.technomancer.pangu.logic.habitat.PadPose
import com.technomancer.pangu.logic.input.InputSnapshot
import com.technomancer.pangu.logic.math.Quat
import com.technomancer.pangu.logic.math.Vec3
import com.technomancer.pangu.logic.pet.Distress
import com.technomancer.pangu.logic.pet.PetActivity
import com.technomancer.pangu.logic.pet.PetWorld
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sin

/** Entities the presentation writes to besides the habitat itself. */
class SceneEntities(
    val infoPanel: Entity?,
    val handleLabel: Entity?,
    val pie: Entity?,
    val pet: AlienRig?,
    val tray: Entity?,
    val foods: List<Entity>,
    val tub: Entity? = null,
    val bubbles: List<Entity> = emptyList(),
    val ball: Entity? = null,
    val crumbs: List<Entity> = emptyList(),
    val beam: Entity? = null,
    val skyRing: Entity? = null,
    val pedestal: Entity? = null,
    val egg: EggPresenter? = null,
)

/**
 * Writes the logic's state onto entities each frame: habitat pose, handle glow, the pet's rig and
 * poses, props, the floating orb and the panels turning to face the player. Stage-space poses are
 * converted into the scene root's space; everything else is pad-local.
 * Entity visibility is not exposed to Kotlin in this SDK, so hidden things are scaled to zero.
 */
class PresentationSystem(
    private val root: Entity,
    private val habitat: Entity,
    private val scene: SceneEntities,
    private val config: UiConfig,
    private val habitatScale: Float,
    private val meshes: MeshFactory,
    /** Local scale of the pet rig at hatch size (the studio preview uses it too). */
    private val petHatchScale: Float,
) {
    private val habitatScaleVector = Vector3(habitatScale)
    /** Panels are children of the habitat but keep their own size, so they undo its scale. */
    private val panelScaleVector = Vector3(1f / habitatScale)

    private val habitatTransform = habitat.components[TransformComponent::class.java]
    private val handleGlow = habitat.findEntity(HabitatFactory.HANDLE_GLOW)?.transform()
    private val orbTransform = habitat.findEntity(HabitatFactory.CARE_ORB)?.transform()
    private val padParts = listOf(HabitatFactory.PAD, HabitatFactory.NEST, HabitatFactory.LAMP).mapNotNull { habitat.findEntity(it)?.transform() }
    private val padPartScales = padParts.map { it.scaleVector }
    private val bulb = habitat.findEntity(HabitatFactory.LAMP_BULB)
    private val pedestalTransform = scene.pedestal?.transform()
    private val pedestalScale = pedestalTransform?.scaleVector ?: Vector3.ONE
    private val tipTransforms = scene.pet?.tips?.map { it.transform() }.orEmpty()
    private val panelTransform = scene.infoPanel?.transform()
    private val labelTransform = scene.handleLabel?.transform()
    private val pieTransform = scene.pie?.transform()
    private val trayTransform = scene.tray?.transform()
    private val foodTransforms = scene.foods.map { it.transform() }
    private val tubTransform = scene.tub?.transform()
    private val bubbleTransforms = scene.bubbles.mapNotNull { it.transform() }
    private val ballTransform = scene.ball?.transform()
    private val crumbTransforms = scene.crumbs.mapNotNull { it.transform() }
    private val beamTransform = scene.beam?.transform()
    private val skyRingTransform = scene.skyRing?.transform()
    private val petRoot = scene.pet?.root?.transform()
    private val petBody = scene.pet?.body?.transform()
    private val petEyes = scene.pet?.eyes?.transform()
    private val petAntenna = scene.pet?.antenna?.transform()
    private val eyeBase = scene.pet?.eyes?.transform()?.position ?: Vector3.ZERO
    private val antennaBase = scene.pet?.antenna?.transform()?.position ?: Vector3.ZERO

    private val labelOffset = Vector3(HabitatFactory.HANDLE_OFFSET, LABEL_HEIGHT, 0f)
    private val panelOffset = Vector3(config.infoPanelOffset[0], config.infoPanelOffset[1], config.infoPanelOffset[2])

    private var shownPad: PadPose? = null
    private var glowOn: Boolean? = null
    private var time = 0.0
    private var lampLit: Boolean? = null
    private var shownLook: PetLook? = null
    private var studioShown: Boolean? = null

    fun apply(logic: PanguLogic, input: InputSnapshot, dt: Float) {
        time += dt
        val pad = logic.padPose
        if (pad != null && pad !== shownPad) {
            shownPad = pad
            habitatTransform?.apply {
                setPosition(root.convertPositionFrom(pad.position.toSdk(), null))
                setQuaternion(root.convertRotationFrom(pad.rotation.toSdk(), null))
                setScaleVector(habitatScaleVector)
            }
        }

        val placing = logic.screens.isPlacingPad
        if (placing != glowOn) {
            glowOn = placing
            handleGlow?.setScaleVector(if (placing) Vector3.ONE else Vector3.ZERO)
            labelTransform?.setScaleVector(if (placing) panelScaleVector else Vector3.ZERO)
        }

        val petShown = logic.isPetFlow
        val studio = logic.isStudio
        val sealing = logic.egg.sealSec >= 0f
        applyStudio(studio, sealing, logic.egg.formScale)

        val orbBase = when {
            petShown -> orbAbovePet(logic)
            logic.isEggFlow -> orbAboveEgg(logic)
            else -> Vec3(0f, config.orbHeight, 0f)
        }
        val bob = config.orbBobAmplitude / habitatScale * sin(2.0 * PI * config.orbBobHz * time).toFloat()
        val orbHidden = (petShown && logic.pet.stage() == Distress.WentHome) || studio || sealing ||
            logic.flow.flow == FlowState.Hatching || logic.flow.flow == FlowState.Dormant
        orbTransform?.setScaleVector(if (orbHidden) Vector3.ZERO else Vector3.ONE)
        orbTransform?.setPosition(Vector3(orbBase.x, orbBase.y + bob, orbBase.z))

        applyLook(logic)
        applyLamp(logic)
        when {
            studio || sealing -> applyPreview(logic, if (sealing) 1f - logic.egg.formScale else 1f)
            logic.flow.flow == FlowState.Hatching -> applyHatchling(logic)
            else -> applyPet(logic, petShown)
        }
        applyProps(logic.pet, petShown)
        applyDistress(logic, petShown)

        val head = if (pad != null && input.headValid) habitat.convertPositionFrom(input.headPosition.toSdk(), null) else null
        scene.egg?.apply(logic, head, dt)

        if (pad != null) {
            billboard(panelTransform, panelOffset, head)
            if (placing) billboard(labelTransform, labelOffset, head)
            if (logic.screens.current == ScreenId.C24 || logic.screens.current == ScreenId.C08) {
                billboard(pieTransform, Vector3(orbBase.x, orbBase.y, orbBase.z), head)
            } else {
                pieTransform?.setScaleVector(Vector3.ZERO)
            }
        }
    }

    /** In the studio the pad, nest and lamp make way for the pedestal, which shrinks away as the egg forms. */
    private fun applyStudio(studio: Boolean, sealing: Boolean, formScale: Float) {
        if (studio != studioShown) {
            studioShown = studio
            padParts.forEachIndexed { i, t -> t.setScaleVector(if (studio) Vector3.ZERO else padPartScales[i]) }
        }
        val p = when {
            studio -> 1f
            sealing -> 1f - formScale
            else -> 0f
        }
        pedestalTransform?.setScaleVector(Vector3(pedestalScale.x * p, pedestalScale.y * p, pedestalScale.z * p))
    }

    /** Tints the rig and shows the chosen antenna tip when the look changes. */
    private fun applyLook(logic: PanguLogic) {
        val rig = scene.pet ?: return
        val look = logic.look
        if (look == shownLook) return
        shownLook = look
        meshes.applyLook(rig, logic.catalog.bodyColor(look), logic.catalog.tipColor(look))
        tipTransforms.forEachIndexed { i, t -> t?.setScaleVector(if (i == look.shape) Vector3.ONE else Vector3.ZERO) }
    }

    private fun applyLamp(logic: PanguLogic) {
        val lit = logic.isEggFlow && logic.egg.egg.lampOn(logic.nowMs)
        if (lit == lampLit) return
        lampLit = lit
        bulb?.let { meshes.tint(it, if (lit) HabitatFactory.LAMP_ON else HabitatFactory.LAMP_OFF) }
    }

    /** The studio preview on the pedestal: turning, breathing and blinking. [fade] shrinks it into the egg. */
    private fun applyPreview(logic: PanguLogic, fade: Float) {
        val rootT = petRoot ?: return
        val pose = logic.preview
        rootT.setPosition(Vector3.ZERO)
        rootT.setQuaternion(Quat.fromYaw(pose.yaw).toSdk())
        rootT.setScaleVector(Vector3(petHatchScale * fade))
        petBody?.apply {
            setScaleVector(Vector3(1f, pose.breathScale, 1f))
            setQuaternion(Quat.IDENTITY.toSdk())
        }
        petEyes?.apply {
            setPosition(eyeBase)
            setScaleVector(Vector3(1f, pose.eyeOpen, 1f))
        }
        petAntenna?.apply {
            setPosition(antennaBase)
            setQuaternion(Quat.fromAxisAngle(Vec3(0f, 0f, 1f), pose.antennaAngle).toSdk())
        }
        tipTransforms.getOrNull(logic.look.shape)?.setScaleVector(Vector3(pose.tipScale))
    }

    /** The hatchling rises out of the opened egg during the last part of the hatch. */
    private fun applyHatchling(logic: PanguLogic) {
        val t = logic.egg.hatchSec
        if (t < PanguLogic.HATCH_RISE_SEC) {
            petRoot?.setScaleVector(Vector3.ZERO)
            return
        }
        applyPet(logic, true)
        val rise = ((t - PanguLogic.HATCH_RISE_SEC) / HATCH_RISE_TIME).coerceIn(0f, 1f)
        val body = logic.pet.body
        petRoot?.setPosition(Vector3(body.position.x, HATCH_SINK * (1f - rise), body.position.z))
    }

    private fun orbAboveEgg(logic: PanguLogic): Vec3 {
        val p = logic.egg.eggPosition
        return Vec3(p.x, p.y + (EggMesh.HEIGHT + ORB_ABOVE_EGG) / habitatScale, p.z)
    }

    private fun orbAbovePet(logic: PanguLogic): Vec3 {
        val body = logic.pet.body
        val height = AlienParts.MODEL_HEIGHT * logic.pet.localScale(logic.nowMs) * body.sizeFactor
        return Vec3(body.position.x, body.hopHeight + height * (1f + ORB_GAP), body.position.z)
    }

    private fun applyPet(logic: PanguLogic, shown: Boolean) {
        val rootT = petRoot ?: return
        if (!shown) {
            rootT.setScaleVector(Vector3.ZERO)
            return
        }
        val body = logic.pet.body
        val s = logic.pet.localScale(logic.nowMs) * body.sizeFactor
        val inTub = logic.pet.actions.activity == PetActivity.Cleaning && logic.pet.tubOut
        val bathBob = if (inTub) 0.035f + 0.004f * sin(4.0 * time).toFloat() else 0f
        rootT.setPosition(Vector3(body.position.x, body.position.y + body.hopHeight + body.lift / habitatScale + bathBob, body.position.z))
        rootT.setQuaternion(Quat.fromYaw(body.yaw + body.spin).toSdk())
        rootT.setScaleVector(Vector3(s))
        petBody?.apply {
            val squash = body.squash.coerceAtLeast(0.5f)
            setScaleVector(Vector3(1f / squash, body.breathScale * squash, 1f / squash))
            val roll = Quat.fromAxisAngle(Vec3(0f, 0f, 1f), body.roll)
            val tilt = Quat.fromAxisAngle(Vec3(1f, 0f, 0f), -body.pitch)
            setQuaternion((roll * tilt).toSdk())
        }
        petEyes?.apply {
            setPosition(eyeBase)
            setScaleVector(Vector3(1f, body.eyeOpen, 1f))
        }
        petAntenna?.apply {
            setPosition(antennaBase)
            // Sideways spring wobble, plus a forward droop when the pet is sad.
            val wobble = Quat.fromAxisAngle(Vec3(0f, 0f, 1f), body.antennaAngle)
            val droop = Quat.fromAxisAngle(Vec3(1f, 0f, 0f), body.droop)
            setQuaternion((wobble * droop).toSdk())
        }
    }

    private fun applyProps(pet: PetWorld, shown: Boolean) {
        val trayOut = shown && pet.trayOut
        trayTransform?.setScaleVector(if (trayOut) TRAY_SCALE else Vector3.ZERO)
        pet.foods.forEachIndexed { i, food ->
            val t = foodTransforms.getOrNull(i) ?: return@forEachIndexed
            if (!trayOut || food.eaten) {
                t.setScaleVector(Vector3.ZERO)
            } else {
                t.setScaleVector(Vector3.ONE)
                t.setPosition(food.position.toSdk())
            }
        }

        val tubOut = shown && pet.tubOut
        tubTransform?.setScaleVector(if (tubOut) Vector3.ONE else Vector3.ZERO)
        bubbleTransforms.forEachIndexed { i, t ->
            val phase = time * 1.3 + i * 1.7
            val r = 0.05f * ((i % 3) + 1) / 3f
            t.setPosition(Vector3(r * sin(phase).toFloat(), 0.07f + 0.02f * sin(phase * 1.9).toFloat(), r * sin(phase + 1.3).toFloat()))
        }

        val ballOut = shown && pet.ballOut
        ballTransform?.setScaleVector(if (ballOut) Vector3.ONE else Vector3.ZERO)
        if (ballOut) ballTransform?.setPosition(pet.ball.position.toSdk())

        val pulse = if (pet.sweepInvite) 1f + 0.25f * sin(6.0 * time).toFloat() else 1f
        crumbTransforms.forEachIndexed { i, t ->
            val p = pet.crumbs.positions.getOrNull(i)
            if (!shown || p == null) {
                t.setScaleVector(Vector3.ZERO)
            } else {
                t.setScaleVector(Vector3(pulse))
                t.setPosition(Vector3(p.x, 0.012f + 0.003f * sin(2.0 * time + i).toFloat(), p.z))
                t.setQuaternion(Quat.fromYaw((time * 0.8 + i).toFloat()).toSdk())
            }
        }
    }

    /** Sky ring over a homesick pet, and the UFO beam when it goes home. */
    private fun applyDistress(logic: PanguLogic, shown: Boolean) {
        val stage = if (shown) logic.pet.stage() else Distress.None
        val body = logic.pet.body
        val height = AlienParts.MODEL_HEIGHT * logic.pet.localScale(logic.nowMs) * body.sizeFactor
        if (stage == Distress.Homesick) {
            skyRingTransform?.setScaleVector(Vector3.ONE)
            skyRingTransform?.setPosition(Vector3(body.position.x, height * (1f + RING_GAP), body.position.z))
        } else {
            skyRingTransform?.setScaleVector(Vector3.ZERO)
        }
        if (stage == Distress.WentHome) {
            beamTransform?.setScaleVector(Vector3.ONE)
            beamTransform?.setPosition(Vector3(body.position.x, height * (1f + BEAM_GAP), body.position.z))
        } else {
            beamTransform?.setScaleVector(Vector3.ZERO)
        }
    }

    /** Places a panel at [offset] in pad space, keeps its real size and yaws it (only around Y) toward [head]. */
    private fun billboard(transform: TransformComponent?, offset: Vector3, head: Vector3?) {
        transform ?: return
        transform.setPosition(offset)
        transform.setScaleVector(panelScaleVector)
        if (head != null) transform.setQuaternion(Quat.fromYaw(atan2(head.x - offset.x, head.z - offset.z)).toSdk())
    }

    private fun Entity.transform() = components[TransformComponent::class.java]

    companion object {
        /** Height of the handle label above the pad top, metres. */
        private const val LABEL_HEIGHT = 0.06f
        /** Gaps above the pet's head, as a fraction of its current height, so they stay close while it is small. */
        private const val ORB_GAP = 0.3f
        private const val ORB_ABOVE_EGG = 0.10f

        /** The hatchling starts this far below the pad inside the egg (pad-local metres) and rises over [HATCH_RISE_TIME] seconds. */
        private const val HATCH_SINK = -0.05f
        private const val HATCH_RISE_TIME = 1.2f
        private const val RING_GAP = 0.12f
        private const val BEAM_GAP = 1.2f
        private val TRAY_SCALE = Vector3(1f, 1f, 0.6f)
    }
}
