package com.technomancer.pangu.logic.sim

import com.technomancer.pangu.logic.config.SimConfig
import com.technomancer.pangu.logic.pet.NeedModifiers
import com.technomancer.pangu.logic.pet.Needs
import com.technomancer.pangu.logic.pet.NeedsSim
import com.technomancer.pangu.logic.pet.PetActions
import com.technomancer.pangu.logic.time.IClock

/**
 * Replays time spent away in fixed steps so the result does not depend on how long the gap was
 * split. Night sleep uses each step's local hour; a nap in progress keeps adding Energy.
 * At most maxCatchUpHours are replayed; anything older is dropped.
 */
class CatchUp(private val sim: SimConfig, private val needsSim: NeedsSim, private val clock: IClock) {

    /** Advances [needs] from [fromMs] to [toMs]. Returns the number of steps simulated. */
    fun pet(needs: Needs, actions: PetActions, fromMs: Long, toMs: Long, modifiers: NeedModifiers = NeedModifiers.IDENTITY): Int {
        if (toMs <= fromMs) return 0
        val stepMs = sim.simStepSec * 1000L
        val maxMs = sim.maxCatchUpHours * 3_600_000L
        var t = maxOf(fromMs, toMs - maxMs)
        val stepHours = sim.simStepSec / 3600f
        var steps = 0
        while (t + stepMs <= toMs) {
            val asleep = needsSim.isNight(clock.localHour(t)) || actions.napEndMs > t
            needsSim.step(needs, stepHours, asleep, modifiers)
            actions.update(needs, t + stepMs, sim.simStepSec.toFloat())
            t += stepMs
            steps++
        }
        return steps
    }
}
