package com.technomancer.pangu.logic.incubation

/** Which egg care the pie highlights. */
object EggSuggestions {
    /** Actions the player can choose today; the others show "Coming soon". */
    val AVAILABLE: Set<EggAction> = setOf(EggAction.Warm, EggAction.Lamp, EggAction.Blanket)

    /** Actions with a daily cap, in pie order. */
    private val CAPPED = listOf(EggAction.Warm, EggAction.Sing, EggAction.Listen, EggAction.Polish)

    /**
     * Below Cozy the lamp is suggested. Otherwise the capped action with the most completions
     * left today (ties go to the earlier one in pie order), among [available] actions.
     */
    fun forEgg(band: WarmthBand, ledger: ImprintLedger, available: Set<EggAction> = AVAILABLE): EggAction? {
        if (band == WarmthBand.Cool || band == WarmthBand.Chilly) return EggAction.Lamp
        return CAPPED.filter { it in available && ledger.left(it) > 0 }.maxByOrNull { ledger.left(it) }
    }
}
