package com.technomancer.pangu.logic.pet

import com.technomancer.pangu.logic.config.PetConfig

/** What the pet is doing right now. */
enum class PetActivity { Idle, Eating, Napping, Cuddling, Cleaning, Dancing, Playing }

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

    /** Nap gain multiplier: 1 when the player chose it, lower when the pet dozes off by itself. */
    private var napEfficiency = 1f

    private var strokeMinuteStartMs = 0L
    private var strokeGainedThisMinute = 0f
    private var lastBoopMs = Long.MIN_VALUE / 2

    /** Seconds into the current bath or dance. */
    var timedSec = 0f
        private set

    /** Play started by the pet itself earns less. */
    var playEfficiency = 1f
        private set

    fun startEat(needs: Needs, nowMs: Long, gainMultiplier: Float = 1f): ActionResult {
        if (activity == PetActivity.Eating) return ActionResult.Refused("busy")
        if (needs[Need.Hunger] >= config.refuseHungerAbove) return ActionResult.Refused("full")
        if (nowMs - lastEatEndMs < (config.feedCooldownSec * 1000).toLong()) return ActionResult.Refused("cooldown")
        activity = PetActivity.Eating
        eatEndMs = nowMs + (config.eatSec * 1000).toLong()
        pendingHunger = config.foodGain * gainMultiplier
        return ActionResult.Started
    }

    fun startNap(nowMs: Long, efficiency: Float = 1f) {
        activity = PetActivity.Napping
        napEfficiency = efficiency
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

    fun startClean() = startTimed(PetActivity.Cleaning)

    fun startDance() = startTimed(PetActivity.Dancing)

    fun startPlay(efficiency: Float = 1f) {
        activity = PetActivity.Playing
        playEfficiency = efficiency
    }

    private fun startTimed(kind: PetActivity) {
        activity = kind
        timedSec = 0f
    }

    /** Ends cuddling or play (the player closed it); other actions keep running. */
    fun stopOpenEnded() {
        if (activity == PetActivity.Cuddling || activity == PetActivity.Playing) activity = PetActivity.Idle
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

    /** One fetch during play: Fun, Social and a little Energy. */
    fun fetch(needs: Needs) {
        needs.add(Need.Fun, config.fetchFun * playEfficiency)
        needs.add(Need.Social, config.fetchSocial * playEfficiency)
        needs.add(Need.Energy, -config.fetchEnergyCost)
    }

    /**
     * Advances timed actions. [scrubbing] doubles the bath rate. [dancedAlong] is checked when a
     * dance ends. Returns true when an action finished this step.
     */
    fun update(needs: Needs, nowMs: Long, dtSec: Float, scrubbing: Boolean = false, dancedAlong: () -> Boolean = { false }): Boolean {
        when (activity) {
            PetActivity.Eating -> if (nowMs >= eatEndMs) {
                needs.add(Need.Hunger, pendingHunger)
                lastEatEndMs = nowMs
                activity = PetActivity.Idle
                return true
            }
            PetActivity.Napping -> {
                needs.add(Need.Energy, config.napEnergyPerMin * napEfficiency * dtSec / 60f)
                if (nowMs >= napEndMs || needs[Need.Energy] >= 100f) {
                    wake()
                    return true
                }
            }
            PetActivity.Cleaning -> {
                val rate = if (scrubbing) 2f else 1f
                val step = minOf(dtSec * rate, config.bathSec - timedSec)
                timedSec += step
                needs.add(Need.Hygiene, config.bathGain * step / config.bathSec)
                if (timedSec >= config.bathSec) {
                    activity = PetActivity.Idle
                    return true
                }
            }
            PetActivity.Dancing -> {
                val step = minOf(dtSec, config.danceSec - timedSec)
                timedSec += step
                val f = step / config.danceSec
                needs.add(Need.Fun, config.danceFun * f)
                needs.add(Need.Energy, -config.danceEnergyCost * f)
                needs.add(Need.Hygiene, -config.danceHygieneCost * f)
                if (timedSec >= config.danceSec) {
                    if (dancedAlong()) needs.add(Need.Social, config.danceAlongSocial)
                    activity = PetActivity.Idle
                    return true
                }
            }
            else -> Unit
        }
        return false
    }

    /**
     * The app went to the background: a bath or dance past halfway finishes with its full gain,
     * one before halfway is cancelled. Returns true if something finished.
     */
    fun finishOrCancelOnPause(needs: Needs): Boolean {
        val total = when (activity) {
            PetActivity.Cleaning -> config.bathSec
            PetActivity.Dancing -> config.danceSec
            else -> return false
        }
        if (timedSec / total > 0.5f) {
            val rest = total - timedSec
            if (activity == PetActivity.Cleaning) {
                needs.add(Need.Hygiene, config.bathGain * rest / total)
            } else {
                val f = rest / total
                needs.add(Need.Fun, config.danceFun * f)
                needs.add(Need.Energy, -config.danceEnergyCost * f)
                needs.add(Need.Hygiene, -config.danceHygieneCost * f)
            }
            activity = PetActivity.Idle
            return true
        }
        activity = PetActivity.Idle
        return false
    }

    /** Nap progress in minutes, for the C26 label. */
    fun napMinutes(nowMs: Long): Int {
        if (activity != PetActivity.Napping) return 0
        val startMs = napEndMs - (config.napMaxMin * 60_000).toLong()
        return ((nowMs - startMs) / 60_000).toInt().coerceIn(0, config.napMaxMin.toInt())
    }
}
