package com.technomancer.pangu.logic.personality

import com.technomancer.pangu.logic.config.PersonalityConfig
import com.technomancer.pangu.logic.pet.Need
import com.technomancer.pangu.logic.pet.NeedModifiers
import com.technomancer.pangu.logic.pet.PetAction

/** How a personality changes the pet's needs and actions. */
object PersonalityEffects {
    /** Each axis's favourite action, in axis order Zest, Cuddle, Wonder, Calm, Neat. */
    val AXIS_ACTION = listOf(PetAction.Dance, PetAction.Cuddle, PetAction.Play, PetAction.Sleep, PetAction.Clean)

    /** Each axis's need. Neat also covers Tidy, through the crumb interval. */
    val AXIS_NEED = listOf(Need.Fun, Need.Social, Need.Fun, Need.Energy, Need.Hygiene)

    private const val NEAT = 4

    /**
     * The up axis's action gains more and its need decays faster; the down axis's the reverse.
     * Neutral natures change nothing. Self-Groomer and Rearranger quirks add their own tweaks.
     */
    fun modifiers(p: Personality, config: PersonalityConfig): NeedModifiers {
        val m = NeedModifiers()
        if (!p.neutral) {
            apply(m, p.up, config.upGain, config.upDecay)
            apply(m, p.down, config.downGain, config.downDecay)
        }
        when (p.quirk) {
            Quirk.SelfGroomer -> m.decay[Need.Hygiene.ordinal] *= config.selfGroomerDecay
            Quirk.Rearranger -> m.crumbInterval *= config.rearrangerInterval
            else -> Unit
        }
        return m
    }

    private fun apply(m: NeedModifiers, axis: Int, gain: Float, decay: Float) {
        m.gain[AXIS_ACTION[axis].ordinal] *= gain
        m.decay[AXIS_NEED[axis].ordinal] *= decay
        // Faster Tidy decay means crumbs come sooner.
        if (axis == NEAT) m.crumbInterval /= decay
    }

    /** Hunger gain multiplier for [food]: more for the liked one, less for the disliked one. */
    fun foodGain(p: Personality, food: Int, config: PersonalityConfig): Float = when (food) {
        p.likedFood -> config.likedGain
        p.dislikedFood -> config.dislikedGain
        else -> 1f
    }
}
