package com.technomancer.pangu.logic.incubation

import com.technomancer.pangu.logic.config.IncubationConfig

/**
 * Advances the egg's warmth, hatch progress and cold time. Warmth decays every hour (half as
 * fast under the blanket) and the lamp adds warmth while lit. A Ready egg no longer changes.
 */
class IncubationSim(private val config: IncubationConfig = IncubationConfig()) {

    fun band(warmth: Float): WarmthBand = when {
        warmth >= config.toastyThreshold -> WarmthBand.Toasty
        warmth >= config.cozyThreshold -> WarmthBand.Cozy
        warmth >= config.chillyThreshold -> WarmthBand.Cool
        else -> WarmthBand.Chilly
    }

    fun isReady(egg: EggState): Boolean = egg.hatchProgressHours >= config.hatchWarmHours

    fun condition(egg: EggState): EggCondition = when {
        egg.dormant || egg.coldHours >= config.dormantHours -> EggCondition.Dormant
        isReady(egg) -> EggCondition.Ready
        egg.coldHours >= config.fadingHours -> EggCondition.Fading
        else -> when (band(egg.warmth)) {
            WarmthBand.Toasty -> EggCondition.Toasty
            WarmthBand.Cozy -> EggCondition.Cozy
            WarmthBand.Cool -> EggCondition.Cool
            WarmthBand.Chilly -> EggCondition.Chilly
        }
    }

    /** Advances [hours] of simulated time ending after [nowMs]; the lamp and blanket are read at [nowMs]. */
    fun step(egg: EggState, hours: Float, nowMs: Long) {
        if (egg.dormant || isReady(egg) || hours <= 0f) return
        val decay = config.warmthDecayPerHour * if (egg.blanketOn(nowMs)) config.blanketDecayMultiplier else 1f
        val lamp = if (egg.lampOn(nowMs)) config.lampWarmthPerHour else 0f
        egg.warmth = (egg.warmth + (lamp - decay) * hours).coerceIn(0f, MAX_WARMTH)

        egg.hatchProgressHours += hours * when (band(egg.warmth)) {
            WarmthBand.Toasty -> config.toastyProgressMultiplier
            WarmthBand.Cozy -> 1f
            else -> 0f
        }

        if (egg.warmth <= 0f) {
            egg.coldHours += hours
        } else if (egg.warmth > config.chillyThreshold) {
            egg.coldHours = 0f
        }
        if (egg.coldHours >= config.dormantHours) egg.dormant = true
    }

    /** Warmth from the player's hands over [sec] seconds of holding. */
    fun warmByHand(egg: EggState, sec: Float) {
        if (egg.dormant) return
        egg.warmth = (egg.warmth + config.handWarmPerSecond * sec).coerceAtMost(MAX_WARMTH)
        if (egg.warmth > config.chillyThreshold) egg.coldHours = 0f
    }

    fun lightLamp(egg: EggState, nowMs: Long) {
        egg.lampOffMs = nowMs + (config.lampDurationHours * 3_600_000L).toLong()
    }

    fun tuckBlanket(egg: EggState, nowMs: Long) {
        egg.blanketOffMs = nowMs + (config.blanketDurationHours * 3_600_000L).toLong()
    }

    companion object {
        const val MAX_WARMTH = 100f
    }
}
