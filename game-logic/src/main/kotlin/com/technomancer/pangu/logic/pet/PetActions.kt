package com.technomancer.pangu.logic.pet

import com.technomancer.pangu.logic.config.PetConfig

/** What the pet is doing right now. */
enum class PetActivity { Idle, Eating, Napping, Cuddling }

sealed class ActionResult {
    data object Started : ActionResult()
    data class Refused(val reason: String) : ActionResult()
}

/**
 * Timed pet actions and their effect on the needs. Times are simulated epoch milliseconds, so
 * a nap keeps counting while the app is closed.
 */
class PetActions(private val config: PetConfig) {
    var activity = PetActivity.Idle
        private set

    private var eatEndMs = 0L
    private var pendingHunger = 0f
    private var lastEatEndMs = Long.MIN_VALUE / 2

    /** End of the current nap; 0 when not napping. Saved so naps survive the app closing. */
    var napEndMs = 0L
        private set

    private var strokeMinuteStartMs = 0L
    private var strokeGainedThisMinute = 0f
    private var lastBoopMs = Long.MIN_VALUE / 2

    fun startEat(needs: Needs, nowMs: Long, gainMultiplier: Float = 1f): ActionResult {
        if (activity == PetActivity.Eating) return ActionResult.Refused("busy")
        if (needs[Need.Hunger] >= config.refuseHungerAbove) return ActionResult.Refused("full")
        if (nowMs - lastEatEndMs < (config.feedCooldownSec * 1000).toLong()) return ActionResult.Refused("cooldown")
        activity = PetActivity.Eating
        eatEndMs = nowMs + (config.eatSec * 1000).toLong()
        pendingHunger = config.foodGain * gainMultiplier
        return ActionResult.Started
    }

    fun startNap(nowMs: Long) {
        activity = PetActivity.Napping
        napEndMs = nowMs + (config.napMaxMin * 60_000).toLong()
    }

    /** Resumes a nap loaded from the save, if it has not ended yet. */
    fun restoreNap(napEndMs: Long, nowMs: Long) {
        if (napEndMs > nowMs) {
            activity = PetActivity.Napping
            this.napEndMs = napEndMs
        }
    }

    fun wake() {
        if (activity == PetActivity.Napping) {
            activity = PetActivity.Idle
            napEndMs = 0L
        }
    }

    fun startCuddle() {
        if (activity == PetActivity.Idle) activity = PetActivity.Cuddling
    }

    fun stopCuddle() {
        if (activity == PetActivity.Cuddling) activity = PetActivity.Idle
    }

    /** Stroking the head for [seconds]: Social rises, capped per minute. Returns the Social gained. */
    fun stroke(needs: Needs, seconds: Float, nowMs: Long): Float {
        if (nowMs - strokeMinuteStartMs >= 60_000) {
            strokeMinuteStartMs = nowMs
            strokeGainedThisMinute = 0f
        }
        val gain = minOf(config.strokeSocialPerSec * seconds, config.strokeCapPerMin - strokeGainedThisMinute).coerceAtLeast(0f)
        strokeGainedThisMinute += gain
        needs.add(Need.Social, gain)
        return gain
    }

    /** A boop on the antenna tip: Social up, at most once every boopCooldownSec. */
    fun boop(needs: Needs, nowMs: Long): Boolean {
        if (nowMs - lastBoopMs < (config.boopCooldownSec * 1000).toLong()) return false
        lastBoopMs = nowMs
        needs.add(Need.Social, config.boopSocial)
        return true
    }

    /** Advances timed actions. Returns true when an action finished this step. */
    fun update(needs: Needs, nowMs: Long, dtSec: Float): Boolean {
        when (activity) {
            PetActivity.Eating -> if (nowMs >= eatEndMs) {
                needs.add(Need.Hunger, pendingHunger)
                lastEatEndMs = nowMs
                activity = PetActivity.Idle
                return true
            }
            PetActivity.Napping -> {
                needs.add(Need.Energy, config.napEnergyPerMin * dtSec / 60f)
                if (nowMs >= napEndMs || needs[Need.Energy] >= 100f) {
                    wake()
                    return true
                }
            }
            else -> Unit
        }
        return false
    }

    /** Nap progress in minutes, for the C26 label. */
    fun napMinutes(nowMs: Long): Int {
        if (activity != PetActivity.Napping) return 0
        val startMs = napEndMs - (config.napMaxMin * 60_000).toLong()
        return ((nowMs - startMs) / 60_000).toInt().coerceIn(0, config.napMaxMin.toInt())
    }
}
