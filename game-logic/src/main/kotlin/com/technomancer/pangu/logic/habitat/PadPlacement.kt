package com.technomancer.pangu.logic.habitat

import com.technomancer.pangu.logic.config.PlacementConfig
import com.technomancer.pangu.logic.math.Quat
import com.technomancer.pangu.logic.math.Vec3

/** Pad pose in Stage space: the centre of the pad's top surface and its yaw (radians, 0 = pad front facing +Z). */
data class PadPose(val position: Vec3, val yaw: Float) {
    val rotation: Quat get() = Quat.fromYaw(yaw)
}

class PadPlacement(private val config: PlacementConfig = PlacementConfig()) {

    /**
     * The pad in front of the head: [PlacementConfig.padDistance] along the head's horizontal forward,
     * [PlacementConfig.padDrop] below the head, level, with its front (+Z) facing the head.
     */
    fun defaultPose(headPosition: Vec3, headRotation: Quat): PadPose {
        val yaw = headRotation.yaw()
        val forward = Quat.fromYaw(yaw).rotate(Vec3.FORWARD)
        val position = Vec3(
            headPosition.x + forward.x * config.padDistance,
            headPosition.y - config.padDrop,
            headPosition.z + forward.z * config.padDistance,
        )
        return PadPose(position, yaw)
    }

    /** Pose used when the head pose is unavailable: the head is assumed at the Stage origin, facing −Z. */
    fun fallbackPose(): PadPose = defaultPose(Vec3(0f, config.fallbackHeadHeight, 0f), Quat.IDENTITY)

    /** A saved pad is restored only if it is within [PlacementConfig.restoreMaxDistance] of the head. */
    fun shouldRestore(saved: PadPose, headPosition: Vec3): Boolean =
        saved.position.distanceTo(headPosition) <= config.restoreMaxDistance
}
