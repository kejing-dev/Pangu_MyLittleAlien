package com.technomancer.pangu.logic.incubation

import com.technomancer.pangu.logic.audio.Sfx
import com.technomancer.pangu.logic.config.IncubationConfig
import com.technomancer.pangu.logic.gestures.HoldDetector
import com.technomancer.pangu.logic.math.Vec3
import kotlin.math.PI
import kotlin.math.sin
import kotlin.math.sqrt

/** Events the egg reports back to the game for sound, haptics, screens and toasts. */
interface EggEvents {
    fun sound(sfx: Sfx)
    fun toast(key: String)

    /** One heartbeat while the egg is held. */
    fun heartbeat()

    /** A 30 s hold finished; [result] says whether it counted. */
    fun warmCompleted(result: CapResult)
    fun conditionChanged(condition: EggCondition)
    fun sealFinished()
    fun hatchFinished()
}

/** What changed for the egg while the player was away, for Welcome back. */
data class EggAway(val bandBefore: WarmthBand, val bandAfter: WarmthBand, val lampHoursLeft: Int, val minutesAway: Long)

/**
 * The egg in the habitat: its warmth simulation, the lamp and blanket, holding it to warm it,
 * cracking it when ready, and the seal and hatch sequences. Positions are pad-local metres
 * (before the habitat's display scale) with the nest centre at the origin.
 */
class EggWorld(
    val config: IncubationConfig,
    private val events: EggEvents,
    /** The habitat is drawn this many times larger than its pad-local units. */
    private val displayScale: Float = 1f,
) {
    val egg = EggState()
    val sim = IncubationSim(config)
    val ledger = ImprintLedger(config)
    private val hold = HoldDetector(config.handWarmCompleteSec)

    var condition: EggCondition = EggCondition.Cozy
        private set

    /** Bottom centre of the egg. */
    var eggPosition: Vec3 = Vec3.ZERO
        private set
    var eggHeld = false
        private set
    private var floatFrom = Vec3.ZERO
    private var floatT = -1f

    /** Folded blanket position on the pad while it is not tucked over the egg. */
    var blanketPosition: Vec3 = BLANKET_HOME
        private set
    var blanketHeld = false
        private set
    private var blanketFrom = BLANKET_HOME
    private var blanketMoveT = -1f
    private var wasTucked = false

    /** Seconds into the seal (C06) and the hatch (C21); negative when not running. */
    var sealSec = -1f
        private set
    var hatchSec = -1f
        private set

    /** Egg scale while it forms during the seal, 0..1. */
    var formScale = 1f
        private set

    /** Side-to-side wobble, radians. */
    var wobble = 0f
        private set
    private var wobbleT = -1f
    private var readyClock = 0f
    private var beatSec = 0f
    private var time = 0f

    /** Seconds of the current continuous hold, for C09. */
    val holdSec: Float get() = hold.heldSec

    fun isTucked(nowMs: Long) = egg.blanketOn(nowMs) && blanketMoveT < 0f && !blanketHeld

    /** A fresh egg in the nest. */
    fun setEgg(nowMs: Long, spark: Long) {
        egg.reset(config.startWarmth, nowMs, spark)
        ledger.reset()
        condition = sim.condition(egg)
        eggPosition = Vec3.ZERO
        eggHeld = false
        floatT = -1f
        blanketPosition = BLANKET_HOME
        blanketHeld = false
        blanketMoveT = -1f
        wasTucked = false
        hatchSec = -1f
        hold.reset()
    }

    /** Syncs derived state after loading or catching up, without announcing anything. */
    fun syncCondition(nowMs: Long) {
        condition = sim.condition(egg)
        wasTucked = egg.blanketOn(nowMs)
    }

    fun startSeal() {
        sealSec = 0f
        formScale = 0f
        eggPosition = SEAL_START / displayScale
    }

    fun startHatch() {
        hatchSec = 0f
    }

    fun update(dt: Float, nowMs: Long, localDay: Long) {
        time += dt
        ledger.rollover(localDay)
        if (sealSec >= 0f) updateSeal(dt)
        if (hatchSec >= 0f) {
            updateHatch(dt)
            return
        }
        if (egg.dormant) {
            wobble = 0f
            return
        }

        sim.step(egg, dt / 3600f, nowMs)
        if (eggHeld) updateHold(dt, nowMs) else hold.reset()
        updateEggFloat(dt)
        updateBlanket(dt, nowMs)
        updateWobble(dt)

        val now = sim.condition(egg)
        if (now != condition) {
            condition = now
            events.conditionChanged(now)
        }
    }

    private fun updateSeal(dt: Float) {
        sealSec += dt
        val form = (sealSec / FORM_SEC).coerceIn(0f, 1f)
        formScale = form
        val glide = ((sealSec - FORM_SEC) / (config.sealSec - FORM_SEC)).coerceIn(0f, 1f)
        val t = glide * glide * (3f - 2f * glide)
        val p0 = SEAL_START / displayScale
        val p2 = Vec3.ZERO
        val control = (p0 + p2) * 0.5f + Vec3(0f, SEAL_ARC_HEIGHT / displayScale, 0f)
        val u = 1f - t
        eggPosition = p0 * (u * u) + control * (2f * u * t) + p2 * (t * t)
        if (sealSec >= config.sealSec) {
            sealSec = -1f
            formScale = 1f
            eggPosition = Vec3.ZERO
            events.sealFinished()
        }
    }

    private fun updateHatch(dt: Float) {
        hatchSec += dt
        wobble = if (hatchSec < WOBBLE_END) 0.12f * sin(2f * PI.toFloat() * 2.2f * hatchSec) else 0f
        if (hatchSec >= config.hatchSec) {
            hatchSec = -1f
            events.hatchFinished()
        }
    }

    private fun updateHold(dt: Float, nowMs: Long) {
        sim.warmByHand(egg, dt)
        beatSec += dt
        if (beatSec >= config.heartbeatIntervalSec) {
            beatSec -= config.heartbeatIntervalSec
            events.heartbeat()
        }
        if (hold.update(true, dt)) events.warmCompleted(ledger.complete(EggAction.Warm, nowMs, ledger.day))
    }

    private fun updateEggFloat(dt: Float) {
        if (floatT < 0f || eggHeld) return
        floatT = (floatT + dt / config.floatBackSec).coerceAtMost(1f)
        val t = floatT * floatT * (3f - 2f * floatT)
        eggPosition = floatFrom.lerp(Vec3.ZERO, t)
        if (floatT >= 1f) floatT = -1f
    }

    private fun updateBlanket(dt: Float, nowMs: Long) {
        val tucked = egg.blanketOn(nowMs)
        if (wasTucked && !tucked && !blanketHeld) {
            // The blanket's time is up: it slides off the egg back to its spot.
            blanketFrom = eggPosition
            blanketMoveT = 0f
        }
        wasTucked = tucked
        if (blanketMoveT >= 0f && !blanketHeld) {
            blanketMoveT = (blanketMoveT + dt / BLANKET_SLIDE_SEC).coerceAtMost(1f)
            val t = blanketMoveT * blanketMoveT * (3f - 2f * blanketMoveT)
            blanketPosition = blanketFrom.lerp(BLANKET_HOME, t)
            if (blanketMoveT >= 1f) blanketMoveT = -1f
        }
    }

    private fun updateWobble(dt: Float) {
        if (condition == EggCondition.Ready) {
            readyClock += dt
            if (readyClock >= config.readyWobbleSec) {
                readyClock = 0f
                wobbleT = 0f
            }
        }
        if (wobbleT >= 0f) {
            wobbleT += dt
            wobble = 0.1f * sin(2f * PI.toFloat() * 3f * wobbleT) * (1f - wobbleT / WOBBLE_SEC).coerceAtLeast(0f)
            if (wobbleT >= WOBBLE_SEC) {
                wobbleT = -1f
                wobble = 0f
            }
        }
    }

    fun grabEgg(): Boolean {
        if (egg.dormant || hatchSec >= 0f || sealSec >= 0f) return false
        eggHeld = true
        floatT = -1f
        beatSec = 0f
        return true
    }

    fun moveEgg(deltaLocal: Vec3) {
        if (!eggHeld) return
        val p = eggPosition + deltaLocal
        eggPosition = Vec3(p.x, p.y.coerceAtLeast(0f), p.z)
    }

    /** Lets go of the egg: it snaps into the nest when close, otherwise floats back. */
    fun releaseEgg() {
        if (!eggHeld) return
        eggHeld = false
        if (eggPosition.length() * displayScale <= config.eggSnapDistance) {
            eggPosition = Vec3.ZERO
        } else {
            floatFrom = eggPosition
            floatT = 0f
        }
    }

    fun grabBlanket(nowMs: Long): Boolean {
        if (egg.dormant || isTucked(nowMs) || hatchSec >= 0f) return false
        blanketHeld = true
        blanketMoveT = -1f
        return true
    }

    fun moveBlanket(deltaLocal: Vec3) {
        if (!blanketHeld) return
        val p = blanketPosition + deltaLocal
        blanketPosition = Vec3(p.x, p.y.coerceAtLeast(0f), p.z)
    }

    /** Lets go of the blanket: over the egg it tucks in, otherwise it floats back to its spot. Returns true when tucked. */
    fun releaseBlanket(nowMs: Long, localDay: Long): Boolean {
        if (!blanketHeld) return false
        blanketHeld = false
        val dx = blanketPosition.x - eggPosition.x
        val dz = blanketPosition.z - eggPosition.z
        if (sqrt(dx * dx + dz * dz) * displayScale <= config.blanketSnapDistance) {
            sim.tuckBlanket(egg, nowMs)
            ledger.tuck(localDay)
            wasTucked = true
            blanketPosition = BLANKET_HOME
            events.sound(Sfx.Happy)
            return true
        }
        blanketFrom = blanketPosition
        blanketMoveT = 0f
        return false
    }

    fun lightLamp(nowMs: Long) {
        sim.lightLamp(egg, nowMs)
    }

    /** A tap on the shell: cracks a Ready egg (true on the last tap), otherwise a little wobble. */
    fun tapShell(): Boolean {
        if (hatchSec >= 0f || egg.dormant) return false
        wobbleT = 0f
        if (condition != EggCondition.Ready) return false
        if (egg.crackTaps >= config.hatchTaps) return false
        egg.crackTaps++
        events.sound(Sfx.CrackTap)
        return egg.crackTaps >= config.hatchTaps
    }

    /** Crack progress, 0..1. */
    val crackFraction: Float get() = (egg.crackTaps.toFloat() / config.hatchTaps).coerceIn(0f, 1f)

    companion object {
        /** Where the egg forms during the seal: in the preview's body above the pedestal, true metres. */
        val SEAL_START = Vec3(0f, 0.07f, 0f)
        const val SEAL_ARC_HEIGHT = 0.25f
        const val FORM_SEC = 0.6f
        const val WOBBLE_SEC = 0.6f
        const val WOBBLE_END = 3f
        const val BLANKET_SLIDE_SEC = 1f
        val BLANKET_HOME = Vec3(0.13f, 0f, -0.12f)
    }
}
