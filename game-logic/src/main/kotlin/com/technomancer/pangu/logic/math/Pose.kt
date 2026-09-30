package com.technomancer.pangu.logic.math

/** Rigid transform: rotation then translation. */
data class Pose(val position: Vec3 = Vec3.ZERO, val rotation: Quat = Quat.IDENTITY) {

    /** Local point → parent space. */
    fun transformPoint(local: Vec3): Vec3 = position + rotation.rotate(local)

    /** Local direction → parent space (no translation). */
    fun transformDirection(local: Vec3): Vec3 = rotation.rotate(local)

    /** Parent-space point → local space. */
    fun inverseTransformPoint(world: Vec3): Vec3 = rotation.conjugate().rotate(world - position)

    fun forward(): Vec3 = rotation.rotate(Vec3.FORWARD)

    /** This pose followed by [child] expressed in this pose's space. */
    operator fun times(child: Pose) = Pose(transformPoint(child.position), (rotation * child.rotation).normalized())

    companion object {
        val IDENTITY = Pose()
    }
}
