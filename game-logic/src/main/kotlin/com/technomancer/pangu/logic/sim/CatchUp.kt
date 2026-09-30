package com.technomancer.pangu.logic.sim

import com.technomancer.pangu.logic.config.SimConfig
import com.technomancer.pangu.logic.pet.CrumbSpawner
import com.technomancer.pangu.logic.pet.Distress
import com.technomancer.pangu.logic.pet.DistressTracker
import com.technomancer.pangu.logic.pet.MoodCalculator
import com.technomancer.pangu.logic.pet.NeedModifiers
import com.technomancer.pangu.logic.pet.Needs
import com.technomancer.pangu.logic.pet.NeedsSim
import com.technomancer.pangu.logic.pet.PetActions
import com.technomancer.pangu.logic.time.IClock

/**
 * Replays time spent away in fixed steps so the result does not depend on how long the gap was
 * split. Night sleep uses each step's local hour; a nap in progress keeps adding Energy; crumbs
 * appear and distress builds as they would have live. At most maxCatchUpHours are replayed.
 */
class CatchUp(private val sim: SimConfig, private val needsSim: NeedsSim, private val clock: IClock) {

    /** Advances the pet from [fromMs] to [toMs]. Returns the number of steps simulated. */
    fun pet(
        needs: Needs,
        actions: PetActions,
        fromMs: Long,
        toMs: Long,
        crumbs: CrumbSpawner? = null,
        distress: DistressTracker? = null,
        mood: MoodCalculator? = null,
        modifiers: NeedModifiers = NeedModifiers.IDENTITY,
    ): Int {
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
            if (crumbs != null && crumbs.update(stepHours, !asleep)) needs.crumbs = crumbs.positions.size
            if (distress != null && mood != null) {
                distress.update(stepHours, mood.score(needs))
                if (distress.stage() == Distress.WentHome) return steps + 1
            }
            t += stepMs
            steps++
        }
        return steps
    }
}
