package com.technomancer.pangu.logic.flow

/** Side effects a screen change asks the caller to perform. */
enum class ScreenEffect { None, ConfirmPad }

/**
 * Which screen is showing. The pause screen (C34) and the recenter screen (C37) sit on top of
 * whatever was showing and return to it when dismissed.
 */
class ScreenMachine(start: ScreenId) {
    var current: ScreenId = start
        private set

    private var beforePause: ScreenId? = null
    private var beforeRecenter: ScreenId? = null

    /** True while the player is placing the pad (the handle glows and can be dragged). */
    val isPlacingPad: Boolean get() = current == ScreenId.C02 || current == ScreenId.C37

    /** The screen to save and restore on the next launch: overlays (pause, recenter) are skipped. */
    val persistentScreen: ScreenId
        get() {
            val base = if (current == ScreenId.C34) beforePause ?: current else current
            return if (base == ScreenId.C37) beforeRecenter ?: ScreenId.C03 else base
        }

    fun pause() {
        if (current == ScreenId.C34) return
        beforePause = current
        current = ScreenId.C34
    }

    fun resume() {
        beforePause?.let { current = it }
        beforePause = null
    }

    /** Re-places the pad in front of the player: show C37 until Done. */
    fun recenter() {
        if (current == ScreenId.C34) return
        if (current != ScreenId.C37) beforeRecenter = current
        current = ScreenId.C37
    }

    fun primary(): ScreenEffect = when (current) {
        ScreenId.C02 -> {
            current = ScreenId.C03
            ScreenEffect.ConfirmPad
        }
        ScreenId.C37 -> {
            current = beforeRecenter?.takeIf { it != ScreenId.C02 } ?: ScreenId.C03
            beforeRecenter = null
            ScreenEffect.ConfirmPad
        }
        else -> ScreenEffect.None
    }
}
