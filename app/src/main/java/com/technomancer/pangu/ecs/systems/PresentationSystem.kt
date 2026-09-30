package com.technomancer.pangu.ecs.systems

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Vector3
import com.technomancer.pangu.ecs.entities.AlienRig
import com.technomancer.pangu.ecs.entities.HabitatFactory
import com.technomancer.pangu.ecs.toSdk
import com.technomancer.pangu.logic.PanguLogic
import com.technomancer.pangu.logic.art.AlienParts
import com.technomancer.pangu.logic.config.UiConfig
import com.technomancer.pangu.logic.flow.ScreenId
import com.technomancer.pangu.logic.habitat.PadPose
import com.technomancer.pangu.logic.input.InputSnapshot
import com.technomancer.pangu.logic.math.Quat
import com.technomancer.pangu.logic.math.Vec3
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
)

/**
 * Writes the logic's state onto entities each frame: habitat pose, handle glow, the pet's rig,
 * snacks, the floating orb and the panels turning to face the player. Stage-space poses are
 * converted into the scene root's space; everything else is pad-local.
 * Entity visibility is not exposed to Kotlin in this SDK, so hidden things are scaled to zero.
 */
class PresentationSystem(
    private val root: Entity,
    private val habitat: Entity,
    private val scene: SceneEntities,
    private val config: UiConfig,
    private val habitatScale: Float,
) {
    private val habitatScaleVector = Vector3(habitatScale)
    /** Panels are children of the habitat but keep their own size, so they undo its scale. */
    private val panelScaleVector = Vector3(1f / habitatScale)

    private val habitatTransform = habitat.components[TransformComponent::class.java]
    private val handleGlow = habitat.findEntity(HabitatFactory.HANDLE_GLOW)?.transform()
    private val orbTransform = habitat.findEntity(HabitatFactory.CARE_ORB)?.transform()
    private val panelTransform = scene.infoPanel?.transform()
    private val labelTransform = scene.handleLabel?.transform()
    private val pieTransform = scene.pie?.transform()
    private val trayTransform = scene.tray?.transform()
    private val foodTransforms = scene.foods.map { it.transform() }
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
        val orbBase = if (petShown) orbAbovePet(logic) else Vec3(0f, config.orbHeight, 0f)
        val bob = config.orbBobAmplitude / habitatScale * sin(2.0 * PI * config.orbBobHz * time).toFloat()
        orbTransform?.setPosition(Vector3(orbBase.x, orbBase.y + bob, orbBase.z))

        applyPet(logic, petShown)
        applyTray(logic)

        if (pad != null) {
            val head = if (input.headValid) habitat.convertPositionFrom(input.headPosition.toSdk(), null) else null
            billboard(panelTransform, panelOffset, head)
            if (placing) billboard(labelTransform, labelOffset, head)
            val pieOpen = logic.screens.current == ScreenId.C24
            if (pieOpen) {
                billboard(pieTransform, Vector3(orbBase.x, orbBase.y, orbBase.z), head)
            } else {
                pieTransform?.setScaleVector(Vector3.ZERO)
            }
        }
    }

    private fun orbAbovePet(logic: PanguLogic): Vec3 {
        val body = logic.pet.body
        val height = AlienParts.MODEL_HEIGHT * logic.pet.localScale(logic.nowMs)
        return Vec3(body.position.x, body.hopHeight + height + ORB_ABOVE_HEAD / habitatScale, body.position.z)
    }

    private fun applyPet(logic: PanguLogic, shown: Boolean) {
        val rootT = petRoot ?: return
        if (!shown) {
            rootT.setScaleVector(Vector3.ZERO)
            return
        }
        val body = logic.pet.body
        val s = logic.pet.localScale(logic.nowMs)
        rootT.setPosition(Vector3(body.position.x, body.position.y + body.hopHeight, body.position.z))
        rootT.setQuaternion(Quat.fromYaw(body.yaw).toSdk())
        rootT.setScaleVector(Vector3(s))
        petBody?.apply {
            val stretch = body.breathScale * body.squash
            setScaleVector(Vector3(1f / body.squash.coerceAtLeast(0.5f), stretch, 1f / body.squash.coerceAtLeast(0.5f)))
            setQuaternion(Quat.fromAxisAngle(Vec3(0f, 0f, 1f), body.roll).toSdk())
        }
        petEyes?.apply {
            setPosition(eyeBase)
            setScaleVector(Vector3(1f, body.eyeOpen, 1f))
        }
        petAntenna?.apply {
            setPosition(antennaBase)
            setQuaternion(Quat.fromAxisAngle(Vec3(0f, 0f, 1f), body.antennaAngle).toSdk())
        }
    }

    private fun applyTray(logic: PanguLogic) {
        val out = logic.pet.trayOut
        trayTransform?.setScaleVector(if (out) TRAY_SCALE else Vector3.ZERO)
        logic.pet.foods.forEachIndexed { i, food ->
            val t = foodTransforms.getOrNull(i) ?: return@forEachIndexed
            if (!out || food.eaten) {
                t.setScaleVector(Vector3.ZERO)
            } else {
                t.setScaleVector(Vector3.ONE)
                t.setPosition(food.position.toSdk())
            }
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
        private const val ORB_ABOVE_HEAD = 0.10f
        private val TRAY_SCALE = Vector3(1f, 1f, 0.6f)
    }
}
