package com.technomancer.pangu.logic.flow

import com.technomancer.pangu.logic.creation.KeeperIds
import com.technomancer.pangu.logic.creation.PetLook
import com.technomancer.pangu.logic.personality.SparkValue

/**
 * The game's top-level flow and the screen each step shows:
 * Onboarding (C01 → C02) → Creation (C03 look, C04 name, C05 confirm, C06 seal) → Incubation (C07…)
 * → Hatching (C21) → Pet (C23…). Dormant (C19) and WentHome (C33) cards lead back to Creation
 * with the last look preselected, skipping onboarding.
 */
class FlowMachine(val screens: ScreenMachine, initialLook: PetLook = PetLook()) {
    var flow: FlowState = FlowState.Onboarding

    /** The look being edited in the studio, or the one sealed into the current egg or pet. */
    var look: PetLook = initialLook

    /** The look of the last egg that was set; a new egg starts from it. */
    var lastLook: PetLook = initialLook

    /** C01 Continue. */
    fun continueFromLanguage() {
        if (screens.current == ScreenId.C01) screens.show(ScreenId.C02)
    }

    /** The pad was confirmed on C02: the first launch moves on to the studio. */
    fun padConfirmed() {
        if (flow == FlowState.Onboarding) flow = FlowState.Creation
    }

    /** C03 → C04. */
    fun toName() = step(ScreenId.C03, ScreenId.C04)

    /** C04 → C03. */
    fun backToLook() = step(ScreenId.C04, ScreenId.C03)

    /** C04 → C05. */
    fun toConfirm() = step(ScreenId.C04, ScreenId.C05)

    /** C05 → C04. */
    fun backToName() = step(ScreenId.C05, ScreenId.C04)

    /**
     * C05 Set egg: locks the look, rolls the egg's spark value and starts the seal (C06).
     * The flow is Incubation from here on, so a restart during the seal resumes with the egg in the nest.
     */
    fun setEgg(nowMs: Long, ids: KeeperIds): Long {
        lastLook = look
        flow = FlowState.Incubation
        screens.show(ScreenId.C06)
        return SparkValue.roll(nowMs, ids)
    }

    /** The seal animation finished: C07. */
    fun sealDone() {
        if (screens.current == ScreenId.C06) screens.show(ScreenId.C07)
    }

    /** Five taps cracked the shell: C21. */
    fun startHatch() {
        flow = FlowState.Hatching
        screens.show(ScreenId.C21)
    }

    /** The hatch sequence finished: the pet is out and its personality card shows. */
    fun hatched() {
        flow = FlowState.Pet
        screens.show(ScreenId.C22)
    }

    fun goDormant() {
        flow = FlowState.Dormant
        screens.show(ScreenId.C19)
    }

    fun wentHome() {
        flow = FlowState.WentHome
        screens.show(ScreenId.C33)
    }

    /** "Start a new egg" on C19 or C33: back to the studio with the last look. */
    fun startNewEgg() {
        flow = FlowState.Creation
        look = lastLook
        screens.show(ScreenId.C03)
    }

    private fun step(from: ScreenId, to: ScreenId): Boolean {
        if (screens.current != from) return false
        screens.show(to)
        return true
    }

    companion object {
        /**
         * The screen to open on launch for a saved [flow] and [screen]. Passing moments (the seal,
         * the hatch, open menus and timed actions) resume at the screen they lead back to.
         */
        fun resumeScreen(flow: FlowState, screen: ScreenId): ScreenId = when (flow) {
            FlowState.Onboarding, FlowState.Creation -> when (screen) {
                ScreenId.C03, ScreenId.C04, ScreenId.C05 -> screen
                else -> ScreenId.C03
            }
            FlowState.Incubation -> ScreenId.C07
            FlowState.Hatching -> ScreenId.C23
            FlowState.Pet -> when (screen) {
                ScreenId.C23, ScreenId.C26, ScreenId.C31, ScreenId.C32 -> screen
                else -> ScreenId.C23
            }
            FlowState.Dormant -> ScreenId.C19
            FlowState.WentHome -> ScreenId.C33
        }
    }
}
