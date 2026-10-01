package com.technomancer.pangu.logic.pet

import com.technomancer.pangu.logic.config.PetConfig

/** The six needs, in panel order. The first five are stored; Tidy comes from the crumb count. */
enum class Need { Hunger, Energy, Hygiene, Fun, Social, Tidy }

/** Pet actions, in care-pie order. */
enum class PetAction { Eat, Sleep, Clean, Dance, Play, Cuddle }

/** Current need values, 0..100. */
class Needs(initial: List<Float>) {
    val values = FloatArray(STORED) { initial.getOrElse(it) { 50f } }
    var crumbs: Int = 0

    operator fun get(need: Need): Float = values[need.ordinal]

    fun value(need: Need, config: PetConfig): Float =
        if (need == Need.Tidy) (100f - config.tidyPerCrumb * crumbs).coerceIn(0f, 100f) else values[need.ordinal]

    fun add(need: Need, amount: Float) {
        if (need == Need.Tidy) return
        values[need.ordinal] = (values[need.ordinal] + amount).coerceIn(0f, 100f)
    }

    fun copyFrom(other: Needs) {
        other.values.copyInto(values)
        crumbs = other.crumbs
    }

    companion object {
        const val STORED = 5
    }
}

/** Per-need multipliers on decay; personality fills these in later. */
class NeedModifiers {
    /** Decay multiplier per stored need. */
    val decay = FloatArray(Needs.STORED) { 1f }

    /** Gain multiplier per pet action, indexed by [PetAction] ordinal. */
    val gain = FloatArray(PetAction.entries.size) { 1f }

    /** Crumb interval multiplier: below 1 Tidy drops faster, above 1 slower. */
    var crumbInterval = 1f

    fun gain(action: PetAction): Float = gain[action.ordinal]

    companion object {
        val IDENTITY = NeedModifiers()
    }
}

/** Needs over time: awake decay, or slower decay plus Energy gain while asleep. */
class NeedsSim(private val config: PetConfig) {
    fun step(needs: Needs, hours: Float, asleep: Boolean, modifiers: NeedModifiers = NeedModifiers.IDENTITY) {
        val factor = if (asleep) config.sleepDecayMultiplier else 1f
        for (i in 0 until Needs.STORED) {
            val decay = config.decayPerHour.getOrElse(i) { 0f } * modifiers.decay[i] * factor * hours
            needs.values[i] = (needs.values[i] - decay).coerceIn(0f, 100f)
        }
        if (asleep) needs.add(Need.Energy, config.nightSleepEnergyPerHour * hours)
    }

    /** Night sleep runs from sleepStartHour to [endHour] local time (wrapping midnight). */
    fun isNight(localHour: Int, endHour: Int = config.sleepEndHour): Boolean =
        if (config.sleepStartHour > endHour) {
            localHour >= config.sleepStartHour || localHour < endHour
        } else {
            localHour >= config.sleepStartHour && localHour < endHour
        }
}

enum class Mood { Joyful, Content, Meh, Sad, Distressed }

/** Mood is a weighted mean of the six needs. */
class MoodCalculator(private val config: PetConfig) {
    fun score(needs: Needs): Float {
        var sum = 0f
        var weights = 0f
        for (need in Need.entries) {
            val w = config.moodWeights.getOrElse(need.ordinal) { 1f }
            sum += needs.value(need, config) * w
            weights += w
        }
        return if (weights > 0f) sum / weights else 0f
    }

    fun mood(score: Float): Mood {
        val t = config.moodThresholds
        return when {
            score >= t[0] -> Mood.Joyful
            score >= t[1] -> Mood.Content
            score >= t[2] -> Mood.Meh
            score >= t[3] -> Mood.Sad
            else -> Mood.Distressed
        }
    }
}

/** Colour band of a needs bar. */
enum class NeedBand { Good, Low, Critical }

object NeedBar {
    fun band(value: Float): NeedBand = when {
        value >= 60f -> NeedBand.Good
        value >= 30f -> NeedBand.Low
        else -> NeedBand.Critical
    }
}

/** Which actions the care pie highlights: the ones that help the lowest need. */
object Suggestions {
    fun lowestNeed(needs: Needs, config: PetConfig): Need = Need.entries.minBy { needs.value(it, config) }

    fun actionsFor(need: Need): List<PetAction> = when (need) {
        Need.Hunger -> listOf(PetAction.Eat)
        Need.Energy -> listOf(PetAction.Sleep)
        Need.Hygiene -> listOf(PetAction.Clean)
        Need.Fun -> listOf(PetAction.Dance, PetAction.Play)
        Need.Social -> listOf(PetAction.Cuddle, PetAction.Dance)
        Need.Tidy -> listOf(PetAction.Clean)
    }

    fun forPet(needs: Needs, config: PetConfig): List<PetAction> = actionsFor(lowestNeed(needs, config))
}
