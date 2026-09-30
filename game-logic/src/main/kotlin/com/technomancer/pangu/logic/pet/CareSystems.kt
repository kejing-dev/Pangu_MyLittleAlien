package com.technomancer.pangu.logic.pet

import com.technomancer.pangu.logic.config.PetConfig
import com.technomancer.pangu.logic.math.Vec3
import com.technomancer.pangu.logic.time.IRandom
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** Average speed of the player's head and hands over a period, to tell whether they danced along. */
class MotionEnergyMeter {
    private val last = arrayOfNulls<Vec3>(MAX_POINTS)
    private var distance = 0f
    private var seconds = 0f

    /** Adds one frame. [points] are head then hand/controller tips; null entries are untracked. */
    fun add(points: List<Vec3?>, dt: Float) {
        if (dt <= 0f) return
        var frame = 0f
        var tracked = 0
        for (i in 0 until MAX_POINTS) {
            val p = points.getOrNull(i)
            val prev = last[i]
            if (p != null && prev != null) {
                frame += p.distanceTo(prev)
                tracked++
            }
            last[i] = p
        }
        if (tracked > 0) {
            distance += frame / tracked
            seconds += dt
        }
    }

    /** Mean speed per tracked point, m/s. */
    val averageSpeed: Float get() = if (seconds > 0f) distance / seconds else 0f

    fun reset() {
        last.fill(null)
        distance = 0f
        seconds = 0f
    }

    companion object {
        const val MAX_POINTS = 5
    }
}

/** Stardust crumbs: one appears every crumbIntervalHours while the pet is awake, up to maxCrumbs. */
class CrumbSpawner(private val config: PetConfig, private val random: IRandom, private val padRadius: Float) {
    val positions = ArrayList<Vec3>()
    private var hours = 0f

    /** Interval multiplier, e.g. 1.25 for a pet that tidies up after itself. */
    var intervalMultiplier = 1f

    /** Advances time; returns true when a crumb appeared. */
    fun update(hoursPassed: Float, awake: Boolean): Boolean {
        if (!awake) return false
        hours += hoursPassed
        val interval = config.crumbIntervalHours * intervalMultiplier
        if (hours < interval) return false
        hours -= interval
        if (positions.size >= config.maxCrumbs) return false
        val r = (padRadius - config.rimMargin) * sqrt(random.nextFloat())
        val a = random.nextFloat() * 2f * PI.toFloat()
        positions += Vec3(r * cos(a), 0f, r * sin(a))
        return true
    }

    fun sweep(index: Int): Boolean {
        if (index !in positions.indices) return false
        positions.removeAt(index)
        return true
    }
}

/** What the pet decides to do on its own after standing idle for a while. */
enum class Autonomy { None, Nap, PlayAlone }

class AutonomyPlanner(private val config: PetConfig) {
    private var idleSec = 0f

    /** Call once per frame while nothing is going on. [napBelowEnergy] is 30, or 50 for a nap lover. */
    fun update(dt: Float, needs: Needs, idle: Boolean, ballOut: Boolean, napBelowEnergy: Float = 30f): Autonomy {
        if (!idle) {
            idleSec = 0f
            return Autonomy.None
        }
        idleSec += dt
        if (idleSec < config.autonomyIdleSec) return Autonomy.None
        idleSec = 0f
        return when {
            needs[Need.Energy] < napBelowEnergy -> Autonomy.Nap
            needs[Need.Fun] < 30f && ballOut -> Autonomy.PlayAlone
            else -> Autonomy.None
        }
    }
}

/** Stages of a neglected pet. */
enum class Distress { None, Mopey, Homesick, WentHome }

/** Hours of low mood: +1/h while mood < 20, −2/h while mood ≥ 50. Crossing thresholds changes the stage. */
class DistressTracker(private val config: PetConfig) {
    var hours = 0f

    fun update(hoursPassed: Float, moodScore: Float) {
        if (stage() == Distress.WentHome) return
        when {
            moodScore < 20f -> hours += hoursPassed
            moodScore >= 50f -> hours = (hours - 2f * hoursPassed).coerceAtLeast(0f)
        }
    }

    fun stage(): Distress = when {
        hours >= config.departHours -> Distress.WentHome
        hours >= config.homesickHours -> Distress.Homesick
        hours >= config.mopeyHours -> Distress.Mopey
        else -> Distress.None
    }
}

/**
 * The fetch ball: a small hand-written simulation on the pad (pad-local metres, pad top at y = 0).
 * An invisible wall of [WALL_HEIGHT] around the rim keeps it on the pad; if it still gets off
 * (thrown over the wall), it comes back to the middle.
 */
class BallSim(private val padRadius: Float) {
    var position = Vec3(0f, RADIUS, 0.12f)
        private set
    var velocity = Vec3.ZERO
        private set
    var held = false
    var idleSec = 0f
        private set

    fun reset() {
        position = Vec3(0f, RADIUS, 0.12f)
        velocity = Vec3.ZERO
        held = false
        idleSec = 0f
    }

    /** Moves the ball while it is held. */
    fun drag(delta: Vec3) {
        position = position + delta
        velocity = Vec3.ZERO
        idleSec = 0f
    }

    fun throwWith(v: Vec3) {
        held = false
        velocity = v
        idleSec = 0f
    }

    /** Pushes the ball, e.g. the pet nudging it back toward the player. */
    fun push(v: Vec3) {
        velocity = v
        idleSec = 0f
    }

    fun update(dt: Float) {
        if (held) return
        idleSec += dt
        var steps = (dt / MAX_STEP).toInt() + 1
        val h = dt / steps
        while (steps-- > 0) step(h)
    }

    private fun step(h: Float) {
        velocity = Vec3(velocity.x, velocity.y - GRAVITY * h, velocity.z)
        position = position + velocity * h
        val r = sqrt(position.x * position.x + position.z * position.z)
        val onPad = r <= padRadius
        // Floor (only over the pad).
        if (onPad && position.y < RADIUS) {
            position = Vec3(position.x, RADIUS, position.z)
            val vy = -velocity.y * RESTITUTION
            velocity = Vec3(velocity.x * FLOOR_FRICTION, if (vy < 0.05f) 0f else vy, velocity.z * FLOOR_FRICTION)
        }
        // Wall around the rim.
        if (r > padRadius - RADIUS && position.y < WALL_HEIGHT) {
            val nx = position.x / r
            val nz = position.z / r
            val outward = velocity.x * nx + velocity.z * nz
            if (outward > 0f) {
                velocity = Vec3(velocity.x - (1f + RESTITUTION) * outward * nx, velocity.y, velocity.z - (1f + RESTITUTION) * outward * nz)
            }
            val k = (padRadius - RADIUS) / r
            position = Vec3(position.x * k, position.y, position.z * k)
        }
        if (!onPad && position.y < -RESPAWN_DROP) reset()
        if (r > padRadius * 3f) reset()
    }

    /** True when it rests on the pad. */
    val isResting: Boolean get() = !held && position.y <= RADIUS + 1e-3f && velocity.lengthSquared() < 1e-4f

    companion object {
        const val RADIUS = 0.0175f
        const val WALL_HEIGHT = 0.25f
        const val GRAVITY = 9.81f
        const val RESTITUTION = 0.6f
        private const val FLOOR_FRICTION = 0.98f
        private const val MAX_STEP = 1f / 120f
        private const val RESPAWN_DROP = 0.5f
    }
}

/** What changed while the player was away, for the Welcome back screen. */
data class AwaySummary(val minutesAway: Long, val drops: List<Pair<Need, Pair<Int, Int>>>, val newCrumbs: Int) {
    companion object {
        /** Null when the player was away less than [minMinutes]. Picks the two needs that dropped the most. */
        fun of(before: FloatArray, after: FloatArray, crumbsBefore: Int, crumbsAfter: Int, awayMs: Long, minMinutes: Float): AwaySummary? {
            val minutes = awayMs / 60_000
            if (minutes < minMinutes) return null
            val drops = (0 until Needs.STORED)
                .map { Need.entries[it] to (before[it].toInt() to after[it].toInt()) }
                .sortedByDescending { it.second.first - it.second.second }
                .take(2)
            return AwaySummary(minutes, drops, (crumbsAfter - crumbsBefore).coerceAtLeast(0))
        }
    }
}
