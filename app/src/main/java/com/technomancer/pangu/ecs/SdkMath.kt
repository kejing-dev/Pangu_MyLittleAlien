package com.technomancer.pangu.ecs

import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Quat
import com.pico.spatial.core.math.Vector3
import com.technomancer.pangu.logic.math.Vec3
import com.technomancer.pangu.logic.math.Quat as LogicQuat

// Conversions between :game-logic math types and SDK math types. Both are right-handed, +Y up.

fun Vec3.toSdk(): Vector3 = Vector3(x, y, z)

fun Vector3.toLogic(): Vec3 = Vec3(x, y, z)

fun LogicQuat.toSdk(): Quat = Quat(x, y, z, w)

fun Quat.toLogic(): LogicQuat = LogicQuat(x, y, z, w)

/** 0xRRGGBB → opaque Color4. */
fun rgb(color: Int, alpha: Float = 1f): Color4 =
    Color4(
        ((color shr 16) and 0xFF) / 255f,
        ((color shr 8) and 0xFF) / 255f,
        (color and 0xFF) / 255f,
        alpha,
    )
