package com.technomancer.pangu.logic.math

import kotlin.math.sqrt

/**
 * Immutable 3D vector in metres. Right-handed, +Y up, same handedness as glTF and the
 * PICO Spatial SDK. Kept free of SDK types; the app converts at the boundary.
 */
data class Vec3(val x: Float, val y: Float, val z: Float) {
    operator fun plus(o: Vec3) = Vec3(x + o.x, y + o.y, z + o.z)
    operator fun minus(o: Vec3) = Vec3(x - o.x, y - o.y, z - o.z)
    operator fun times(s: Float) = Vec3(x * s, y * s, z * s)
    operator fun div(s: Float) = Vec3(x / s, y / s, z / s)
    operator fun unaryMinus() = Vec3(-x, -y, -z)

    infix fun dot(o: Vec3): Float = x * o.x + y * o.y + z * o.z

    infix fun cross(o: Vec3) = Vec3(
        y * o.z - z * o.y,
        z * o.x - x * o.z,
        x * o.y - y * o.x,
    )

    fun lengthSquared(): Float = this dot this
    fun length(): Float = sqrt(lengthSquared())
    fun distanceTo(o: Vec3): Float = (this - o).length()

    /** Unit vector in the same direction, or [ZERO] if the length is ~0. */
    fun normalized(): Vec3 {
        val len = length()
        return if (len < EPSILON) ZERO else this / len
    }

    /** Copy with y = 0 (projection onto the horizontal plane). */
    fun flattened(): Vec3 = Vec3(x, 0f, z)

    fun lerp(to: Vec3, t: Float) = Vec3(x + (to.x - x) * t, y + (to.y - y) * t, z + (to.z - z) * t)

    companion object {
        const val EPSILON = 1e-6f
        val ZERO = Vec3(0f, 0f, 0f)
        val ONE = Vec3(1f, 1f, 1f)
        val UP = Vec3(0f, 1f, 0f)
        val RIGHT = Vec3(1f, 0f, 0f)

        /** Viewer/head forward. Assumed −Z (OpenXR convention); verify against HMD poses in Phase 1. */
        val FORWARD = Vec3(0f, 0f, -1f)
    }
}
