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

    /** Where the handle is for [pose], Stage space. */
    fun handlePosition(pose: PadPose): Vec3 = pose.position + pose.rotation.rotate(handleArm)

    private val handleArm: Vec3 get() = Vec3(config.handleOffset * config.habitatScale, 0f, 0f)

    /**
     * Moves the pad so its handle follows the hand, keeping the pad level at its height and its
     * front toward the head. [delta] is the hand movement this event, Stage space.
     *
     * A direct grab moves the handle by the horizontal part of [delta]. A ray, gaze or mouse drag
     * ([viaRay]) moves the point under the pointer on a plane facing the player, so the handle
     * is put where the line from the head through that point meets the pad's level: moving the
     * pointer up pushes the pad away, down pulls it closer, and the handle stays under the pointer.
     */
    fun drag(current: PadPose, delta: Vec3, headPosition: Vec3, viaRay: Boolean = false): PadPose {
        val handle = handlePosition(current)
        val target = if (viaRay) {
            rayTarget(handle, delta, headPosition)
        } else {
            Vec3(handle.x + delta.x, handle.y, handle.z + delta.z)
        }
        // The yaw depends on where the centre ends up, so settle both together.
        var yaw = current.yaw
        var centre = target - Quat.fromYaw(yaw).rotate(handleArm)
        repeat(SOLVE_STEPS) {
            yaw = facingYaw(centre, headPosition, yaw)
            centre = target - Quat.fromYaw(yaw).rotate(handleArm)
        }
        return PadPose(Vec3(centre.x, current.position.y, centre.z), yaw)
    }

    /** Where the line from the head through the moved pointer point meets the handle's level. */
    private fun rayTarget(handle: Vec3, delta: Vec3, head: Vec3): Vec3 {
        val pointer = handle + delta
        val dir = pointer - head
        val flat = Vec3(handle.x + delta.x, handle.y, handle.z + delta.z)
        // Looking level or up at the handle level: there is no floor-side hit, so slide horizontally.
        if (dir.y > -MIN_DOWN) return flat
        val t = (handle.y - head.y) / dir.y
        val hit = head + dir * t
        val away = Vec3(hit.x - head.x, 0f, hit.z - head.z)
        val reach = away.length()
        return if (reach > config.maxDragDistance) {
            val k = config.maxDragDistance / reach
            Vec3(head.x + away.x * k, handle.y, head.z + away.z * k)
        } else {
            Vec3(hit.x, handle.y, hit.z)
        }
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

    private companion object {
        const val SOLVE_STEPS = 4
        const val MIN_DOWN = 0.02f
    }
}
