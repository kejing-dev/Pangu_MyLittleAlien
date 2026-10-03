package com.technomancer.pangu.logic.flow

/** Side effects a screen change asks the caller to perform. */
enum class ScreenEffect { None, ConfirmPad }

/**
 * Which screen is showing. The pause screen (C34), the recenter screen (C37) and Settings (C36)
 * sit on top of whatever was showing and return to it when dismissed.
 *
 * [afterPlacement] is where the game continues once the pad is placed: C03 (creation) normally,
 * or C23 (the pet) while creation is not built yet.
 */
class ScreenMachine(start: ScreenId, private val afterPlacement: ScreenId = ScreenId.C03) {
    var current: ScreenId = start
        private set

    private var beforePause: ScreenId? = null
    private var beforeRecenter: ScreenId? = null
    private var beforeSettings: ScreenId? = null

    /** True while the player is placing the pad (the handle glows and can be dragged). */
    val isPlacingPad: Boolean get() = current == ScreenId.C02 || current == ScreenId.C37

    /** The screen to save and restore on the next launch: overlays (pause, recenter) are skipped. */
    val persistentScreen: ScreenId
        get() {
            var base = if (current == ScreenId.C34) beforePause ?: current else current
            if (base == ScreenId.C36) base = beforeSettings ?: afterPlacement
            return if (base == ScreenId.C37) beforeRecenter ?: afterPlacement else base
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

    /** Opens Settings over the current screen. */
    fun openSettings() {
        if (current == ScreenId.C34 || current == ScreenId.C37 || current == ScreenId.C36) return
        beforeSettings = current
        current = ScreenId.C36
    }

    /** Closes Settings, back to where it was opened. */
    fun closeSettings() {
        if (current != ScreenId.C36) return
        current = beforeSettings ?: afterPlacement
        beforeSettings = null
    }

    /** Moves to [id] unless an overlay is showing; behind Settings the screen underneath changes. */
    fun show(id: ScreenId) {
        if (current == ScreenId.C34 || current == ScreenId.C37) return
        if (current == ScreenId.C36) {
            beforeSettings = id
            return
        }
        current = id
    }

    fun primary(): ScreenEffect = when (current) {
        ScreenId.C02 -> {
            current = afterPlacement
            ScreenEffect.ConfirmPad
        }
        ScreenId.C37 -> {
            current = beforeRecenter?.takeIf { it != ScreenId.C02 } ?: afterPlacement
            beforeRecenter = null
            ScreenEffect.ConfirmPad
        }
        else -> ScreenEffect.None
    }
}
