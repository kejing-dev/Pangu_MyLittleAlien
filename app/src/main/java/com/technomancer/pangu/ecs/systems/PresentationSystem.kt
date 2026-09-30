package com.technomancer.pangu.ecs.systems

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Vector3
import com.technomancer.pangu.ecs.entities.HabitatFactory
import com.technomancer.pangu.ecs.toSdk
import com.technomancer.pangu.logic.PanguLogic
import com.technomancer.pangu.logic.config.UiConfig
import com.technomancer.pangu.logic.habitat.PadPose
import com.technomancer.pangu.logic.input.InputSnapshot
import com.technomancer.pangu.logic.math.Quat
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.sin

/**
 * Writes the logic's state onto entities: habitat pose, handle glow, orb bob and the info panel
 * turning to face the player. Stage-space poses are converted into the scene root's space.
 */
class PresentationSystem(
    private val root: Entity,
    private val habitat: Entity,
    private val infoPanel: Entity?,
    private val handleLabel: Entity?,
    private val config: UiConfig,
    habitatScale: Float,
) {
    private val habitatScaleVector = Vector3(habitatScale)
    /** Panels are children of the habitat but keep their own size, so they undo its scale. */
    private val panelScaleVector = Vector3(1f / habitatScale)

    private val habitatTransform = habitat.components[TransformComponent::class.java]
    private val handleGlow = habitat.findEntity(HabitatFactory.HANDLE_GLOW)?.components?.get(TransformComponent::class.java)
    private val orbTransform = habitat.findEntity(HabitatFactory.CARE_ORB)?.components?.get(TransformComponent::class.java)
    private val panelTransform = infoPanel?.components?.get(TransformComponent::class.java)
    private val labelTransform = handleLabel?.components?.get(TransformComponent::class.java)
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
            // Entity visibility is not exposed to Kotlin in this SDK, so the halo is hidden by scaling it to zero.
            handleGlow?.setScaleVector(if (placing) Vector3.ONE else Vector3.ZERO)
            labelTransform?.setScaleVector(if (placing) panelScaleVector else Vector3.ZERO)
        }

        orbTransform?.setPosition(
            Vector3(0f, config.orbHeight + config.orbBobAmplitude * sin(2.0 * PI * config.orbBobHz * time).toFloat(), 0f),
        )

        if (pad != null) {
            val head = if (input.headValid) habitat.convertPositionFrom(input.headPosition.toSdk(), null) else null
            billboard(panelTransform, panelOffset, head)
            if (placing) billboard(labelTransform, labelOffset, head)
        }
    }

    /** Places a panel at [offset] in pad space and yaws it (only around Y) so its front faces [head]. */
    private fun billboard(transform: TransformComponent?, offset: Vector3, head: Vector3?) {
        transform ?: return
        transform.setPosition(offset)
        if (transform === panelTransform) transform.setScaleVector(panelScaleVector)
        if (head != null) transform.setQuaternion(Quat.fromYaw(atan2(head.x - offset.x, head.z - offset.z)).toSdk())
    }

    companion object {
        /** Height of the handle label above the pad top, metres. */
        private const val LABEL_HEIGHT = 0.06f
    }
}
