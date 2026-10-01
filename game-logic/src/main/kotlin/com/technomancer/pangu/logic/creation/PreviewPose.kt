package com.technomancer.pangu.logic.creation

import com.technomancer.pangu.logic.config.LookConfig
import com.technomancer.pangu.logic.time.IRandom
import kotlin.math.PI
import kotlin.math.sin

/**
 * Motion of the studio preview on the pedestal: it turns slowly on its own, can be spun by
 * dragging, breathes and blinks, its antenna springs when booped, and a new antenna tip grows
 * in with a springy overshoot.
 */
class PreviewPose(private val config: LookConfig, private val random: IRandom) {
    var yaw = 0f
        private set
    private var spinVelocity = 0f
    private var dragging = false

    var antennaAngle = 0f
        private set
    private var antennaVelocity = 0f

    var breathScale = 1f
        private set
    var eyeOpen = 1f
        private set
    private var nextBlink = 2f
    private var blinkT = -1f
    private var time = 0f

    /** Scale of the antenna tip while it grows in after a shape change, overshooting past 1. */
    var tipScale = 1f
        private set
    private var morphT = -1f

    fun update(dt: Float) {
        if (dt <= 0f) return
        val steps = (dt / MAX_STEP).toInt().coerceIn(0, 60) + 1
        val h = dt / steps
        repeat(steps) { step(h) }
    }

    private fun step(dt: Float) {
        time += dt
        if (!dragging) {
            // Hand spins ease back into the slow turntable speed.
            spinVelocity += (config.previewTurnSpeed - spinVelocity) * (dt * 1.5f).coerceAtMost(1f)
            yaw += spinVelocity * dt
        }
        breathScale = 1f + 0.012f * sin(1.6f * time)

        val accel = -SPRING_K * antennaAngle - SPRING_DAMPING * antennaVelocity
        antennaVelocity += accel * dt
        antennaAngle = (antennaAngle + antennaVelocity * dt).coerceIn(-0.8f, 0.8f)

        if (morphT >= 0f) {
            morphT = (morphT + dt / config.morphSec).coerceAtMost(1f)
            tipScale = easeOutBack(morphT, config.morphEaseC)
            if (morphT >= 1f) {
                morphT = -1f
                tipScale = 1f
            }
        }
        updateBlink(dt)
    }

    private fun updateBlink(dt: Float) {
        if (blinkT >= 0f) {
            blinkT += dt
            val half = BLINK_SEC / 2f
            eyeOpen = if (blinkT < half) 1f - 0.9f * (blinkT / half) else 0.1f + 0.9f * ((blinkT - half) / half).coerceAtMost(1f)
            if (blinkT >= BLINK_SEC) {
                blinkT = -1f
                eyeOpen = 1f
                nextBlink = random.nextFloat(3f, 6f)
            }
        } else {
            nextBlink -= dt
            if (nextBlink <= 0f) blinkT = 0f
        }
    }

    /** A drag across the preview: [sideways] metres of hand movement turn it about 12 rad per metre. */
    fun spin(sideways: Float, dt: Float) {
        dragging = true
        val turn = sideways * SPIN_PER_METRE
        yaw += turn
        if (dt > 0f) spinVelocity = (turn / dt).coerceIn(-MAX_SPIN, MAX_SPIN)
    }

    fun endSpin() {
        dragging = false
    }

    fun boop() {
        antennaVelocity += BOOP_KICK
    }

    /** The antenna shape changed: the new tip grows in and the antenna wobbles. */
    fun morph() {
        morphT = 0f
        tipScale = 0f
        antennaVelocity += config.morphWobble
    }

    companion object {
        private const val SPRING_K = 40f
        private const val SPRING_DAMPING = 6f
        private const val BLINK_SEC = 0.08f
        private const val MAX_STEP = 1f / 60f
        private const val SPIN_PER_METRE = 12f
        private const val MAX_SPIN = 8f
        private const val BOOP_KICK = 4f

        /** Ease-out with overshoot: 0 at t = 0, 1 at t = 1, peaking past 1 in between. */
        fun easeOutBack(t: Float, c: Float): Float {
            val u = t - 1f
            return 1f + (c + 1f) * u * u * u + c * u * u
        }

        const val TAU = (2 * PI).toFloat()
    }
}
