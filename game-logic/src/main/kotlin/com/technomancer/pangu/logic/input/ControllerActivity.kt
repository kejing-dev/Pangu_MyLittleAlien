package com.technomancer.pangu.logic.input

import kotlin.math.exp

/**
 * Works out which controller the player is using, so haptic pulses go to that hand only. The SDK's
 * gesture events do not say which controller made them, so this follows recent movement: each
 * controller's tip movement is summed and fades with a [halfLifeSec] half-life, and the busier one
 * wins. A controller that is the only one tracked is the active one; when neither has moved enough
 * the answer is null (pulse both).
 */
class ControllerActivity(private val halfLifeSec: Float = 0.6f, private val minMovement: Float = 0.01f) {
    private val lastX = FloatArray(2)
    private val lastY = FloatArray(2)
    private val lastZ = FloatArray(2)
    private val hasLast = BooleanArray(2)
    private val tracked = BooleanArray(2)
    private val movement = FloatArray(2)

    fun update(snapshot: InputSnapshot, dt: Float) {
        val fade = if (dt > 0f) exp(-LN2 * dt / halfLifeSec) else 1f
        for (i in 0..1) {
            val p = snapshot.pokes[InputSnapshot.CONTROLLER_LEFT + i]
            movement[i] *= fade
            tracked[i] = p.valid
            if (!p.valid) {
                hasLast[i] = false
                continue
            }
            val pos = p.position
            if (hasLast[i]) {
                val dx = pos.x - lastX[i]
                val dy = pos.y - lastY[i]
                val dz = pos.z - lastZ[i]
                movement[i] += kotlin.math.sqrt(dx * dx + dy * dy + dz * dz)
            }
            lastX[i] = pos.x
            lastY[i] = pos.y
            lastZ[i] = pos.z
            hasLast[i] = true
        }
    }

    /** The controller in use, or null when it cannot be told. */
    fun active(): Side? = when {
        tracked[0] && !tracked[1] -> Side.Left
        tracked[1] && !tracked[0] -> Side.Right
        !tracked[0] && !tracked[1] -> null
        maxOf(movement[0], movement[1]) < minMovement -> null
        movement[0] >= movement[1] -> Side.Left
        else -> Side.Right
    }

    private companion object {
        const val LN2 = 0.6931472f
    }
}
