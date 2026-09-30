package com.technomancer.pangu.logic.pet

import com.technomancer.pangu.logic.config.PetConfig
import com.technomancer.pangu.logic.math.Vec3
import com.technomancer.pangu.logic.time.IRandom
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.max
import kotlin.math.sin
import kotlin.math.sqrt

/** Picks where the pet hops next: random points on the pad, away from the rim, and how long to wait. */
class WanderPlanner(private val random: IRandom, private val config: PetConfig, private val padRadius: Float) {
    fun nextTarget(): Vec3 {
        val r = (padRadius - config.rimMargin) * sqrt(random.nextFloat())
        val a = random.nextFloat() * 2f * PI.toFloat()
        return Vec3(r * cos(a), 0f, r * sin(a))
    }

    fun nextPause(): Float = random.nextFloat(config.wanderPauseMinSec, config.wanderPauseMaxSec)
}

/**
 * Where the pet is on the pad (pad-local metres, before the habitat's display scale) and its
 * procedural animation state: hops, breathing, sway, blinking and the springy antenna.
 * All animation comes from here; nothing uses baked animation clips.
 */
class PetBody(private val config: PetConfig, private val random: IRandom, padRadius: Float) {
    private val wander = WanderPlanner(random, config, padRadius)

    var position = Vec3(0.08f, 0f, 0.1f)
        private set
    var yaw = 0f
        private set

    private var target = position
    private var pauseLeft = 1f
    private var hopFrom = position
    private var hopTo = position
    private var hopT = -1f
    private var time = 0f

    /** Height of the current hop above the pad, metres. */
    var hopHeight = 0f
        private set

    /** Squash and stretch: <1 squashed on landing, >1 stretched mid-hop. */
    var squash = 1f
        private set

    var eyeOpen = 1f
        private set
    private var nextBlink = 3f
    private var blinkT = -1f

    /** Antenna angle (radians, around the pet's forward axis) and its spring velocity. */
    var antennaAngle = 0f
        private set
    private var antennaVelocity = 0f

    /** Breathing scale on Y and side-to-side roll, radians. */
    var breathScale = 1f
        private set
    var roll = 0f
        private set

    /** Sets the pet's position directly, e.g. when restoring. */
    fun place(p: Vec3) {
        position = p
        target = p
        hopT = -1f
    }

    /** Kicks the antenna, e.g. on a boop (4) or a hop (3.2). */
    fun kickAntenna(impulse: Float) {
        antennaVelocity += impulse
    }

    /**
     * Advances one frame. While [activity] is Napping the pet heads for the nest centre and holds
     * its eyes closed; while Eating or Cuddling it stays put facing [facing] (pad-local, e.g. the head).
     */
    fun update(dt: Float, activity: PetActivity, facing: Vec3?) {
        time += dt
        breathScale = 1f + 0.012f * sin(1.6f * time)
        roll = 0.03f * sin(0.9f * time) + if (activity == PetActivity.Napping) NAP_ROLL else 0f
        updateAntenna(dt)
        updateBlink(dt, activity == PetActivity.Napping)
        updateMotion(dt, activity, facing)
    }

    private fun updateAntenna(dt: Float) {
        // Damped spring toward rest, plus a gentle idle wobble.
        val accel = -SPRING_K * antennaAngle - SPRING_DAMPING * antennaVelocity
        antennaVelocity += accel * dt
        antennaAngle += antennaVelocity * dt
        antennaAngle = antennaAngle.coerceIn(-0.8f, 0.8f)
        antennaAngle += 0.04f * sin(2.3f * time) * dt
    }

    private fun updateBlink(dt: Float, asleep: Boolean) {
        if (asleep) {
            eyeOpen = 0.1f
            return
        }
        if (blinkT >= 0f) {
            blinkT += dt
            eyeOpen = if (blinkT < BLINK_SEC / 2) 1f - 0.9f * (blinkT / (BLINK_SEC / 2)) else 0.1f + 0.9f * ((blinkT - BLINK_SEC / 2) / (BLINK_SEC / 2))
            if (blinkT >= BLINK_SEC) {
                blinkT = -1f
                eyeOpen = 1f
                nextBlink = random.nextFloat(3f, 6f)
            }
        } else {
            eyeOpen = 1f
            nextBlink -= dt
            if (nextBlink <= 0f) blinkT = 0f
        }
    }

    private fun updateMotion(dt: Float, activity: PetActivity, facing: Vec3?) {
        if (hopT >= 0f) {
            hopT += dt / config.hopSec
            val t = hopT.coerceAtMost(1f)
            position = hopFrom.lerp(hopTo, t)
            hopHeight = config.hopHeight * 4f * t * (1f - t)
            squash = 1f + 0.1f * sin(PI.toFloat() * t)
            if (hopT >= 1f) {
                hopT = -1f
                hopHeight = 0f
                squash = 0.9f
                kickAntenna(HOP_KICK)
            }
            return
        }
        squash += (1f - squash) * (dt * 8f).coerceAtMost(1f)

        val goal = if (activity == PetActivity.Napping) NEST else target
        val stay = activity == PetActivity.Eating || activity == PetActivity.Cuddling
        val away = goal - position
        val dist = sqrt(away.x * away.x + away.z * away.z)
        if (!stay && dist > ARRIVE) {
            // Hop toward the goal in short hops.
            val step = minOf(dist, MAX_HOP)
            hopFrom = position
            hopTo = Vec3(position.x + away.x / dist * step, 0f, position.z + away.z / dist * step)
            yaw = atan2(away.x, away.z)
            hopT = 0f
            return
        }
        if (facing != null && activity != PetActivity.Napping) yaw = turnToward(yaw, atan2(facing.x - position.x, facing.z - position.z), dt)
        if (activity == PetActivity.Idle) {
            pauseLeft -= dt
            if (pauseLeft <= 0f) {
                target = wander.nextTarget()
                pauseLeft = wander.nextPause()
            }
        }
    }

    private fun turnToward(current: Float, goal: Float, dt: Float): Float {
        var d = goal - current
        while (d > PI) d -= (2 * PI).toFloat()
        while (d < -PI) d += (2 * PI).toFloat()
        val maxStep = TURN_SPEED * dt
        return current + d.coerceIn(-maxStep, maxStep)
    }

    /** Mouth position in pad-local metres for a pet of the given [scale] (metres per model unit). */
    fun mouth(scale: Float): Vec3 {
        val m = MOUTH_MODEL * scale
        val c = cos(yaw)
        val s = sin(yaw)
        return Vec3(position.x + m.x * c + m.z * s, position.y + m.y + hopHeight, position.z - m.x * s + m.z * c)
    }

    /** Converts a pad-local point into the pet's model space (units of the GLB). */
    fun toModel(padLocal: Vec3, scale: Float): Vec3 {
        val d = padLocal - Vec3(position.x, position.y + hopHeight, position.z)
        val c = cos(yaw)
        val s = sin(yaw)
        return Vec3((d.x * c - d.z * s) / scale, d.y / scale, (d.x * s + d.z * c) / scale)
    }

    companion object {
        private const val SPRING_K = 40f
        private const val SPRING_DAMPING = 6f
        private const val BLINK_SEC = 0.08f
        private const val HOP_KICK = 3.2f
        private const val ARRIVE = 0.02f
        private const val MAX_HOP = 0.07f
        private const val TURN_SPEED = 3f
        private const val NAP_ROLL = -0.1f
        val NEST = Vec3(0f, 0f, 0f)
        val MOUTH_MODEL = Vec3(0f, 1.18f, 0.72f)

        /** Head proxy used for strokes: an ellipsoid around the body, in model units. */
        val HEAD_CENTRE = Vec3(0f, 1.16f, 0f)
        val HEAD_AXES = Vec3(0.9f, 1.17f, 0.78f)

        /** Approximate distance (model units) from a model-space point to the head proxy's surface. */
        fun headDistance(p: Vec3): Float {
            val q = p - HEAD_CENTRE
            val k = sqrt((q.x / HEAD_AXES.x).let { it * it } + (q.y / HEAD_AXES.y).let { it * it } + (q.z / HEAD_AXES.z).let { it * it })
            return max(0f, (k - 1f) * minOf(HEAD_AXES.x, HEAD_AXES.y, HEAD_AXES.z))
        }
    }
}
