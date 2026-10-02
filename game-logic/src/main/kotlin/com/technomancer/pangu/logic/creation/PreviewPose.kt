package com.technomancer.pangu.logic.creation

import com.technomancer.pangu.logic.config.LookConfig
import com.technomancer.pangu.logic.time.IRandom
import kotlin.math.PI
import kotlin.math.sin

/**
 * Motion of the studio preview on the pedestal: it turns slowly on its own, can be spun by
 * dragging, breathes and blinks, its antenna springs when booped, and a new antenna tip morphs
 * out of the old one.
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

    /**
     * The tip being morphed after a shape change (-1 when none) and how far it has melted into the
     * shared round blob (0 = its own shape, 1 = the blob). The old tip melts in, then the new tip
     * grows out of the same blob.
     */
    var morphShape = -1
        private set
    var morphBlob = 0f
        private set
    private var morphTarget = -1
    private var melting = false
    private var morphAmount = 0f

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

        if (morphShape >= 0) stepMorph(dt)
        updateBlink(dt)
    }

    /** Half of the morph each way: melt the old tip into the blob, then grow the new one out of it. */
    private fun stepMorph(dt: Float) {
        val half = config.morphSec / 2f
        if (melting) {
            morphAmount = (morphAmount + dt / half).coerceAtMost(1f)
            if (morphAmount >= 1f) {
                // The swap happens inside the blob, where both tips look the same.
                melting = false
                morphShape = morphTarget
                antennaVelocity += config.morphWobble
            }
        } else {
            morphAmount = (morphAmount - dt / half).coerceAtLeast(0f)
            if (morphAmount <= 0f) morphShape = -1
        }
        morphBlob = smooth(morphAmount)
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

    /**
     * The antenna shape changed from [fromShape] to [toShape]: the tip on show melts into the blob,
     * then [toShape] grows out of it. A change in the middle of a morph carries on from where the
     * tip on show is.
     */
    fun morph(fromShape: Int, toShape: Int) {
        if (morphShape < 0) {
            morphShape = fromShape
            morphAmount = 0f
        }
        morphTarget = toShape
        melting = morphShape != toShape
    }

    companion object {
        private const val SPRING_K = 40f
        private const val SPRING_DAMPING = 6f
        private const val BLINK_SEC = 0.08f
        private const val MAX_STEP = 1f / 60f
        private const val SPIN_PER_METRE = 12f
        private const val MAX_SPIN = 8f
        private const val BOOP_KICK = 4f

        /** Smoothstep: eases in and out between 0 and 1. */
        fun smooth(t: Float): Float = t * t * (3f - 2f * t)

        const val TAU = (2 * PI).toFloat()
    }
}
