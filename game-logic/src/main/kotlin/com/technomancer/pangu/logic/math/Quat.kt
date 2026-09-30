package com.technomancer.pangu.logic.math

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Immutable unit quaternion (x, y, z, w). Angles are radians; positive rotation is counter-clockwise (right-hand rule). */
data class Quat(val x: Float, val y: Float, val z: Float, val w: Float) {

    operator fun times(o: Quat) = Quat(
        w * o.x + x * o.w + y * o.z - z * o.y,
        w * o.y - x * o.z + y * o.w + z * o.x,
        w * o.z + x * o.y - y * o.x + z * o.w,
        w * o.w - x * o.x - y * o.y - z * o.z,
    )

    fun conjugate() = Quat(-x, -y, -z, w)

    fun normalized(): Quat {
        val len = sqrt(x * x + y * y + z * z + w * w)
        return if (len < Vec3.EPSILON) IDENTITY else Quat(x / len, y / len, z / len, w / len)
    }

    /** Rotates [v] by this quaternion (assumed unit length). */
    fun rotate(v: Vec3): Vec3 {
        // t = 2 * cross(q.xyz, v); v' = v + w * t + cross(q.xyz, t)
        val tx = 2f * (y * v.z - z * v.y)
        val ty = 2f * (z * v.x - x * v.z)
        val tz = 2f * (x * v.y - y * v.x)
        return Vec3(
            v.x + w * tx + (y * tz - z * ty),
            v.y + w * ty + (z * tx - x * tz),
            v.z + w * tz + (x * ty - y * tx),
        )
    }

    /** Heading around +Y in radians: the yaw of this rotation's forward vector, 0 when facing −Z. */
    fun yaw(): Float {
        val f = rotate(Vec3.FORWARD)
        if (abs(f.x) < Vec3.EPSILON && abs(f.z) < Vec3.EPSILON) return 0f
        return atan2(-f.x, -f.z)
    }

    companion object {
        val IDENTITY = Quat(0f, 0f, 0f, 1f)

        fun fromAxisAngle(axis: Vec3, radians: Float): Quat {
            val a = axis.normalized()
            val h = radians * 0.5f
            val s = sin(h)
            return Quat(a.x * s, a.y * s, a.z * s, cos(h))
        }

        /** Rotation of [radians] around +Y (counter-clockwise seen from above). */
        fun fromYaw(radians: Float): Quat = fromAxisAngle(Vec3.UP, radians)

        /** Shortest rotation turning direction [from] onto direction [to]. */
        fun fromTo(from: Vec3, to: Vec3): Quat {
            val a = from.normalized()
            val b = to.normalized()
            val d = a dot b
            if (d < -0.999999f) {
                // Opposite vectors: rotate 180° around any axis perpendicular to a.
                var axis = Vec3.RIGHT cross a
                if (axis.lengthSquared() < 1e-6f) axis = Vec3.UP cross a
                return fromAxisAngle(axis, PI_F)
            }
            val c = a cross b
            return Quat(c.x, c.y, c.z, 1f + d).normalized()
        }

        private const val PI_F = Math.PI.toFloat()
    }
}
