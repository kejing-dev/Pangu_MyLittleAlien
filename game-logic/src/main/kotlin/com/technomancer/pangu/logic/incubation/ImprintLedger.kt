package com.technomancer.pangu.logic.incubation

import com.technomancer.pangu.logic.config.IncubationConfig

/** Egg care actions, in care-pie order. Labels are "ui.egg6.<ordinal>". */
enum class EggAction { Warm, Lamp, Blanket, Sing, Listen, Polish }

/** Personality traits the imprints feed, in imprint-array order. */
enum class Trait { Zest, Cuddle, Wonder, Calm, Neat }

/** Why a completion was not counted. */
enum class CapResult { Counted, DailyCap, Cooldown }

/**
 * Daily completion counts, cooldowns and imprint points for the egg's rituals.
 * Warm, Sing, Listen and Polish each count at most [IncubationConfig.dailyCompletionCap] times per
 * local day, with a cooldown between completions of the same action. Blanket tucks imprint Calm
 * up to their own daily cap. Counts reset when the local day changes.
 */
class ImprintLedger(private val config: IncubationConfig = IncubationConfig()) {
    /** Imprint points per trait, each capped at 255. */
    val imprint = IntArray(Trait.entries.size)

    /** Completions today, indexed by [EggAction] ordinal (Lamp and Blanket stay 0). */
    val completionsToday = IntArray(EggAction.entries.size)

    /** Last completion time per action, epoch ms. */
    val lastCompletionMs = LongArray(EggAction.entries.size)

    var blanketTucksToday = 0

    /** Local day number the counts belong to. */
    var day: Long = Long.MIN_VALUE

    /** Sing + Listen + Polish completions today, for "Rituals today n of 9". */
    val ritualsToday: Int
        get() = completionsToday[EggAction.Sing.ordinal] + completionsToday[EggAction.Listen.ordinal] + completionsToday[EggAction.Polish.ordinal]

    /** Starts a new set of daily counts when [localDay] differs from the stored day. */
    fun rollover(localDay: Long) {
        if (localDay == day) return
        day = localDay
        completionsToday.fill(0)
        blanketTucksToday = 0
    }

    fun left(action: EggAction): Int = (config.dailyCompletionCap - completionsToday[action.ordinal]).coerceAtLeast(0)

    /** True when [action] has used all of today's completions. */
    fun capped(action: EggAction, localDay: Long): Boolean {
        rollover(localDay)
        return left(action) == 0
    }

    /** Tries to count one completion of [action] at [nowMs]; adds its imprint when counted. */
    fun complete(action: EggAction, nowMs: Long, localDay: Long): CapResult {
        rollover(localDay)
        val i = action.ordinal
        if (completionsToday[i] >= config.dailyCompletionCap) return CapResult.DailyCap
        val cooldownMs = (config.actionCooldownMin * 60_000f).toLong()
        if (completionsToday[i] > 0 && nowMs - lastCompletionMs[i] < cooldownMs) return CapResult.Cooldown
        completionsToday[i]++
        lastCompletionMs[i] = nowMs
        addImprint(action.name)
        return CapResult.Counted
    }

    /** Records a blanket tuck; it imprints Calm only for the first tucks of the day. Returns true when it imprinted. */
    fun tuck(localDay: Long): Boolean {
        rollover(localDay)
        if (blanketTucksToday >= config.blanketDailyCap) return false
        blanketTucksToday++
        addImprint("Blanket")
        return true
    }

    private fun addImprint(key: String) {
        val points = config.imprintTable[key] ?: return
        for (t in imprint.indices) imprint[t] = (imprint[t] + points.getOrElse(t) { 0 }).coerceAtMost(MAX_POINTS)
    }

    fun reset() {
        imprint.fill(0)
        completionsToday.fill(0)
        lastCompletionMs.fill(0L)
        blanketTucksToday = 0
        day = Long.MIN_VALUE
    }

    companion object {
        const val MAX_POINTS = 255
    }
}
