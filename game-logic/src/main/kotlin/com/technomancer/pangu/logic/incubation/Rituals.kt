package com.technomancer.pangu.logic.incubation

import com.technomancer.pangu.logic.art.EggMesh
import com.technomancer.pangu.logic.audio.Sfx
import com.technomancer.pangu.logic.config.IncubationConfig
import com.technomancer.pangu.logic.gestures.CrankDetector
import com.technomancer.pangu.logic.gestures.RubCircleDetector
import com.technomancer.pangu.logic.gestures.SingDetector
import com.technomancer.pangu.logic.gestures.StillnessDetector
import com.technomancer.pangu.logic.math.Vec3
import kotlinx.serialization.Serializable
import kotlin.math.sqrt

/** How the player sings to the egg: not chosen yet, their voice, or the music box. */
@Serializable
enum class MicChoice { Unknown, Voice, MusicBox }

/** What the player is doing near the egg this frame, in metres in the pad's frame (no display scale). */
class EggSenses {
    var head: Vec3? = null
    var headForward: Vec3? = null
    val pokes = ArrayList<Vec3>(4)
    var micRms = 0f
}

/**
 * The three rituals: Sing (voice or music box), Listen and Polish. One runs at a time; it reports
 * its completion through [onDone] and the caller counts it against the daily cap.
 */
class Rituals(private val config: IncubationConfig, private val events: EggEvents) {
    var active: EggAction? = null
        private set

    /** The Sing path in use: Voice listens to the microphone, MusicBox waits for the crank and the lullaby. */
    var singPath = MicChoice.Voice
        private set

    private val sing = SingDetector(config.micRmsThreshold, config.singMaxDistance, config.singRequiredSec)
    val crank = CrankDetector(config.crankTurnsRequired)
    private val listen = StillnessDetector(config.listenMaxDistance, config.listenMaxSpeed, config.listenMaxAngularSpeed, config.listenRequiredSec)
    private val polish = RubCircleDetector(config.rubMaxDistance, config.rubMinSpeed)

    /** Seconds the music box has been playing its lullaby; negative when silent. */
    var lullabySec = -1f
        private set
    private var beatSec = 0f

    /** Virtual hand position on the crank, metres from the crank axis, while it is held. */
    private var crankX = 0f
    private var crankZ = 0f
    var crankHeld = false
        private set

    /** Progress for the panels. */
    val singSec: Float get() = sing.sungSec
    val crankTurns: Float get() = crank.turns
    val listenSec: Float get() = listen.stillSec
    val listening: Boolean get() = listen.counting
    val circles: Int get() = polish.circles

    /** Polish progress 0..1, which brightens the speckles. */
    val polishProgress: Float get() = if (active == EggAction.Polish) circles.toFloat() / config.polishCircles else 0f

    fun start(action: EggAction, path: MicChoice = MicChoice.Voice) {
        active = action
        singPath = if (path == MicChoice.MusicBox) MicChoice.MusicBox else MicChoice.Voice
        sing.reset()
        crank.reset()
        listen.reset()
        polish.reset()
        lullabySec = -1f
        beatSec = 0f
        crankHeld = false
    }

    fun stop() {
        active = null
        lullabySec = -1f
        crankHeld = false
    }

    /** Finishes the running ritual at once (the Emulator assist). */
    fun completeNow() {
        val a = active ?: return
        stop()
        events.ritualDone(a)
    }

    fun grabCrank() {
        if (active != EggAction.Sing || singPath != MicChoice.MusicBox || lullabySec >= 0f) return
        crankHeld = true
        crankX = CRANK_RADIUS
        crankZ = 0f
    }

    /** Moves the hand on the crank by [deltaMetres] (pad frame). */
    fun moveCrank(deltaMetres: Vec3) {
        if (!crankHeld) return
        crankX += deltaMetres.x
        crankZ += deltaMetres.z
        // Keep the virtual hand on the crank's circle so turns follow the hand smoothly.
        val r = sqrt(crankX * crankX + crankZ * crankZ)
        if (r > 1e-5f) {
            crankX = crankX / r * CRANK_RADIUS
            crankZ = crankZ / r * CRANK_RADIUS
        }
        if (crank.update(crankX, crankZ)) {
            crankHeld = false
            lullabySec = 0f
            events.sound(Sfx.Lullaby)
        }
    }

    fun releaseCrank() {
        crankHeld = false
    }

    /** Advances the running ritual. [eggCentre] is the egg's middle, metres in the pad frame. */
    fun update(dt: Float, senses: EggSenses, eggCentre: Vec3) {
        when (active) {
            EggAction.Sing -> if (singPath == MicChoice.Voice) {
                val distance = senses.head?.distanceTo(eggCentre) ?: Float.MAX_VALUE
                if (sing.update(senses.micRms, distance, dt)) finish(EggAction.Sing)
            } else if (lullabySec >= 0f) {
                lullabySec += dt
                if (lullabySec >= config.musicBoxSec) finish(EggAction.Sing)
            }
            EggAction.Listen -> {
                val done = listen.update(senses.head, senses.headForward, eggCentre, dt)
                if (listen.counting) {
                    beatSec += dt
                    if (beatSec >= config.heartbeatIntervalSec) {
                        beatSec -= config.heartbeatIntervalSec
                        events.heartbeat()
                    }
                }
                if (done) finish(EggAction.Listen)
            }
            EggAction.Polish -> {
                var best: Vec3? = null
                var bestDistance = Float.MAX_VALUE
                for (p in senses.pokes) {
                    // The shell maths is for the model-sized egg, so points are brought into its scale and the distance back out.
                    val d = shellDistance((p - eggCentre) / config.eggScale) * config.eggScale
                    if (d < bestDistance) {
                        bestDistance = d
                        best = p
                    }
                }
                val local = best?.let { (it - eggCentre) / config.eggScale }
                val normal = local?.let { shellNormal(it) } ?: Vec3.UP
                if (polish.update(best, normal, bestDistance, dt)) {
                    events.sound(Sfx.UiTick)
                    if (polish.circles >= config.polishCircles) finish(EggAction.Polish)
                }
            }
            else -> Unit
        }
    }

    private fun finish(action: EggAction) {
        stop()
        events.ritualDone(action)
    }

    companion object {
        /** Radius of the music box crank, metres. */
        const val CRANK_RADIUS = 0.02f

        /** The shell as an ellipsoid around the egg's centre: semi-axes in metres. */
        private val SHELL_AXES = Vec3(0.0385f, EggMesh.HEIGHT / 2f, 0.0385f)

        /** Approximate distance from [p] (relative to the egg centre) to the shell, metres; negative inside. */
        fun shellDistance(p: Vec3): Float {
            val q = Vec3(p.x / SHELL_AXES.x, p.y / SHELL_AXES.y, p.z / SHELL_AXES.z)
            return (q.length() - 1f) * SHELL_AXES.x
        }

        fun shellNormal(p: Vec3): Vec3 {
            val a = SHELL_AXES
            return Vec3(p.x / (a.x * a.x), p.y / (a.y * a.y), p.z / (a.z * a.z)).normalized()
        }
    }
}
