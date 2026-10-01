package com.technomancer.pangu.logic.incubation

/** Which egg care the pie highlights. */
object EggSuggestions {
    /** Actions the player can choose. */
    val AVAILABLE: Set<EggAction> = EggAction.entries.toSet()

    /** The rituals, in pie order. */
    private val RITUALS = listOf(EggAction.Sing, EggAction.Listen, EggAction.Polish)

    /**
     * Below Cozy the lamp is suggested. Otherwise the ritual with the most completions
     * left today (ties go to the earlier one in pie order), among [available] actions.
     */
    fun forEgg(band: WarmthBand, ledger: ImprintLedger, available: Set<EggAction> = AVAILABLE): EggAction? {
        if (band == WarmthBand.Cool || band == WarmthBand.Chilly) return EggAction.Lamp
        return RITUALS.filter { it in available && ledger.left(it) > 0 }.maxByOrNull { ledger.left(it) }
    }
}
