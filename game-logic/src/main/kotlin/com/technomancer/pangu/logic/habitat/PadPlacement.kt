package com.technomancer.pangu.logic.habitat

import com.technomancer.pangu.logic.config.PlacementConfig
import com.technomancer.pangu.logic.math.Quat
import com.technomancer.pangu.logic.math.Vec3
import kotlin.math.atan2

/** Pad pose in Stage space: the centre of the pad's top surface and its yaw (radians, 0 = pad front facing +Z). */
data class PadPose(val position: Vec3, val yaw: Float) {
    val rotation: Quat get() = Quat.fromYaw(yaw)
}

class PadPlacement(private val config: PlacementConfig = PlacementConfig()) {
    val habitatScale: Float get() = config.habitatScale


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

    /**
     * Moves the pad by the horizontal part of [delta] (height, pitch and roll never change)
     * and turns its front toward the head.
     */
    fun drag(current: PadPose, delta: Vec3, headPosition: Vec3): PadPose {
        val position = Vec3(current.position.x + delta.x, current.position.y, current.position.z + delta.z)
        return PadPose(position, facingYaw(position, headPosition, current.yaw))
    }

    /** Yaw that points the pad's front (+Z) at the head, or [fallback] when the head is straight above. */
    fun facingYaw(padPosition: Vec3, headPosition: Vec3, fallback: Float): Float {
        val dx = headPosition.x - padPosition.x
        val dz = headPosition.z - padPosition.z
        if (dx * dx + dz * dz < 1e-8f) return fallback
        return atan2(dx, dz)
    }

    /** A saved pad is restored only if it is within [PlacementConfig.restoreMaxDistance] of the head. */
    fun shouldRestore(saved: PadPose, headPosition: Vec3): Boolean =
        saved.position.distanceTo(headPosition) <= config.restoreMaxDistance
}
