package com.technomancer.pangu.logic.flow

/** What the info panel's middle block shows. */
enum class PanelKind { Lang, Hab, Look, Name, Msg, Prog, Rows, Opts, Pers, Needs, Settings }

/** Which gesture hint the chip under the panel shows. */
enum class Hint(val key: String) {
    Crank("ui.hints.crank"),
    Lean("ui.hints.lean"),
    Poke("ui.hints.poke"),
    Grab("ui.hints.grab"),
    Gaze("ui.hints.gaze"),
    Voice("ui.hints.voice"),
    Rub("ui.hints.rub"),
    Move("ui.hints.move"),
    None("ui.hints.none"),
}

/** Static description of a screen's panel: which string keys fill it and which hint it shows. Null keys are not shown. */
data class ScreenContent(
    val id: ScreenId,
    val kind: PanelKind,
    val hint: Hint,
    val titleKey: String? = "${id.keyPrefix}.t",
    val bodyKey: String? = "${id.keyPrefix}.b",
    val primaryKey: String? = null,
    val secondaryKey: String? = null,
) {
    companion object {
        private val table: Map<ScreenId, ScreenContent> = listOf(
            ScreenContent(ScreenId.C02, PanelKind.Hab, Hint.Grab, primaryKey = "scr.C02.p"),
            ScreenContent(ScreenId.C34, PanelKind.Msg, Hint.None),
            ScreenContent(ScreenId.C37, PanelKind.Msg, Hint.Grab, primaryKey = "scr.C37.p"),
        ).associateBy { it.id }

        /** The panel for [id], or null for screens that have no panel yet. */
        fun of(id: ScreenId): ScreenContent? = table[id]
    }
}
