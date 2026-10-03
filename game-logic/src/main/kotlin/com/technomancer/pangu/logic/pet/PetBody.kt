package com.technomancer.pangu.logic.pet

import com.technomancer.pangu.logic.config.PetConfig
import com.technomancer.pangu.logic.math.Vec3
import com.technomancer.pangu.logic.time.IRandom
import kotlin.math.PI
import kotlin.math.abs
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

    /** Head tilt back (positive looks up), radians. */
    var pitch = 0f
        private set

    /** Extra yaw on top of [yaw], e.g. spinning while dancing. */
    var spin = 0f
        private set

    /** Vertical offset of the whole body, metres: slumping (negative) or lifting into the beam. */
    var lift = 0f
        private set

    /** Overall size factor, e.g. shrinking into the UFO beam. */
    var sizeFactor = 1f
        private set

    /** Antenna droop added to the spring angle while sad, radians. */
    var droop = 0f
        private set

    /** Where the pet should go instead of wandering (the tub, the ball); null to wander. */
    var goal: Vec3? = null

    /** While true the pet stays where it is (hatching out of the egg), only turning to face [facing]. */
    var holdStill = false

    /**
     * Just hatched: the pet stays on the nest for a few seconds looking at the player before it
     * starts wandering, instead of hopping off the moment the hatch ends.
     */
    fun settleAfterHatch() {
        target = position
        goal = null
        pauseLeft = HATCH_SETTLE_SEC
    }

    /** Quirks that change the body's habits; set from the personality. */
    var blinkIntervalScale = 1f
    var blinkDurationScale = 1f

    /** Sparkle Hiccups: a little hop of [hiccupHeight] every [hiccupMinSec]–[hiccupMaxSec] while idle. */
    var hiccups = false
    var hiccupHeight = 0.03f
    var hiccupMinSec = 40f
    var hiccupMaxSec = 90f
    private var hiccupIn = 60f
    private var hiccupT = -1f

    /** Where the antenna leans at rest, radians (the Antenna Radar quirk points it at the player). */
    var antennaRest = 0f

    /** True while the pet gazes up at the sky (the Night Watcher quirk). */
    var watchingSky = false

    private var waveT = -1f

    /** Dance style: false wiggles, true spins. */
    var danceSpins = false

    private var poseTime = 0f

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
     * Long frames are split into small steps so the springs and hops stay stable at any frame rate.
     */
    fun update(dt: Float, activity: PetActivity, facing: Vec3?, distress: Distress = Distress.None) {
        if (dt <= 0f) return
        val steps = (dt / MAX_STEP).toInt().coerceIn(0, MAX_STEPS) + 1
        val h = dt / steps
        repeat(steps) { step(h, activity, facing, distress) }
    }

    private fun step(dt: Float, activity: PetActivity, facing: Vec3?, distress: Distress) {
        time += dt
        breathScale = 1f + 0.012f * sin(1.6f * time)
        roll = 0.03f * sin(0.9f * time) + if (activity == PetActivity.Napping) NAP_ROLL else 0f
        if (waveT >= 0f) {
            waveT += dt
            roll += WAVE_ROLL * sin(2f * PI.toFloat() * 2f * waveT) * (1f - waveT / WAVE_SEC)
            if (waveT >= WAVE_SEC) waveT = -1f
        }
        updateAntenna(dt)
        updateBlink(dt, activity == PetActivity.Napping)
        if (distress == Distress.WentHome) {
            updateDeparture(dt)
            return
        }
        poseTime = 0f
        sizeFactor = 1f
        updateMood(distress)
        if (activity == PetActivity.Dancing) updateDance() else spin = 0f
        updateMotion(dt, activity, facing)
    }

    private fun updateMood(distress: Distress) {
        val sad = distress == Distress.Mopey || distress == Distress.Homesick
        lift = if (sad) SLUMP else 0f
        droop = if (sad) DROOP else 0f
        // Homesick: a slow, small tilt back to look at the sky.
        pitch = when {
            distress == Distress.Homesick -> LOOK_UP * (0.6f + 0.4f * sin(0.5f * time))
            watchingSky -> LOOK_UP
            else -> 0f
        }
    }

    /** Dance moves on a 110 BPM beat: a roll wiggle, or a full turn every two bars with hops. */
    private fun updateDance() {
        val beat = time * BPM / 60f
        if (danceSpins) {
            spin = (beat / 8f % 1f) * 2f * PI.toFloat()
            val onBeat = (beat % 2f) < 0.25f
            if (onBeat && hopT < 0f) hopHeight = config.hopHeight * 0.5f * sin(PI.toFloat() * (beat % 2f) / 0.25f)
        } else {
            roll += 0.21f * sin(PI.toFloat() * beat)
            if ((beat % 1f) < 0.05f) kickAntenna(1.5f)
        }
    }

    /** Going home: the pet rises 10 cm and shrinks to 88 % over three seconds. */
    private fun updateDeparture(dt: Float) {
        poseTime = (poseTime + dt).coerceAtMost(DEPART_SEC)
        val t = poseTime / DEPART_SEC
        lift = 0.10f * t
        sizeFactor = 1f - 0.12f * t
        pitch = LOOK_UP
        droop = 0f
    }

    /** True once the departure animation has finished. */
    val departed: Boolean get() = poseTime >= DEPART_SEC

    private fun updateAntenna(dt: Float) {
        // Damped spring toward rest, plus a gentle idle wobble.
        val accel = -SPRING_K * (antennaAngle - antennaRest) - SPRING_DAMPING * antennaVelocity
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
            val blink = BLINK_SEC * blinkDurationScale
            eyeOpen = if (blinkT < blink / 2) 1f - 0.9f * (blinkT / (blink / 2)) else 0.1f + 0.9f * ((blinkT - blink / 2) / (blink / 2)).coerceAtMost(1f)
            if (blinkT >= blink) {
                blinkT = -1f
                eyeOpen = 1f
                nextBlink = random.nextFloat(3f, 6f) * blinkIntervalScale
            }
        } else {
            eyeOpen = 1f
            nextBlink -= dt
            if (nextBlink <= 0f) blinkT = 0f
        }
    }

    private fun updateMotion(dt: Float, activity: PetActivity, facing: Vec3?) {
        if (holdStill) {
            if (facing != null) yaw = turnToward(yaw, atan2(facing.x - position.x, facing.z - position.z), dt)
            return
        }
        if (updateHiccup(dt, activity)) return
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

        val goal = when {
            activity == PetActivity.Napping -> NEST
            else -> this.goal ?: target
        }
        val stay = activity == PetActivity.Eating || activity == PetActivity.Cuddling || activity == PetActivity.Dancing
        val away = goal - position
        val dist = sqrt(away.x * away.x + away.z * away.z)
        if (!stay && dist > ARRIVE) {
            // Turn to face the goal first, then hop toward it in short hops.
            val heading = atan2(away.x, away.z)
            if (abs(angleTo(yaw, heading)) > HOP_AIM) {
                yaw = turnToward(yaw, heading, dt)
                return
            }
            val step = minOf(dist, MAX_HOP)
            hopFrom = position
            hopTo = Vec3(position.x + away.x / dist * step, 0f, position.z + away.z / dist * step)
            hopT = 0f
            return
        }
        if (facing != null) yaw = turnToward(yaw, atan2(facing.x - position.x, facing.z - position.z), dt)
        if (activity == PetActivity.Idle) {
            pauseLeft -= dt
            if (pauseLeft <= 0f) {
                target = wander.nextTarget()
                pauseLeft = wander.nextPause()
            }
        }
    }

    /** Sparkle Hiccups: a straight-up hop now and then while the pet is idle and standing still. */
    private fun updateHiccup(dt: Float, activity: PetActivity): Boolean {
        if (hiccupT >= 0f) {
            hiccupT += dt / HICCUP_SEC
            val t = hiccupT.coerceAtMost(1f)
            hopHeight = hiccupHeight * 4f * t * (1f - t)
            if (hiccupT >= 1f) {
                hiccupT = -1f
                hopHeight = 0f
                kickAntenna(HOP_KICK)
            }
            return true
        }
        if (!hiccups || activity != PetActivity.Idle || hopT >= 0f) return false
        hiccupIn -= dt
        if (hiccupIn <= 0f) {
            hiccupIn = random.nextFloat(hiccupMinSec, hiccupMaxSec)
            hiccupT = 0f
            return true
        }
        return false
    }

    /** A happy side-to-side wave, e.g. when the player comes back. */
    fun wave() {
        waveT = 0f
        kickAntenna(HOP_KICK)
    }

    private fun turnToward(current: Float, goal: Float, dt: Float): Float {
        val d = angleTo(current, goal)
        val maxStep = TURN_SPEED * dt
        return current + d.coerceIn(-maxStep, maxStep)
    }

    /** Shortest signed turn from [current] to [goal], in -π..π. */
    private fun angleTo(current: Float, goal: Float): Float {
        var d = goal - current
        while (d > PI) d -= (2 * PI).toFloat()
        while (d < -PI) d += (2 * PI).toFloat()
        return d
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
        private const val HICCUP_SEC = 0.3f
        private const val WAVE_SEC = 2f
        private const val WAVE_ROLL = 0.25f
        private const val HOP_KICK = 3.2f
        private const val ARRIVE = 0.02f
        private const val MAX_HOP = 0.07f
        private const val TURN_SPEED = 3f

        /** Hops start once the pet faces within this many radians of where it is going. */
        private const val HOP_AIM = 0.35f

        /** Seconds a new hatchling stays on the nest before it starts wandering. */
        private const val HATCH_SETTLE_SEC = 3f
        private const val NAP_ROLL = -0.1f
        private const val BPM = 110f
        private const val SLUMP = -0.008f
        private const val DROOP = 0.3f
        private const val LOOK_UP = 0.15f
        private const val MAX_STEP = 1f / 60f
        private const val MAX_STEPS = 120
        private const val DEPART_SEC = 3f
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
