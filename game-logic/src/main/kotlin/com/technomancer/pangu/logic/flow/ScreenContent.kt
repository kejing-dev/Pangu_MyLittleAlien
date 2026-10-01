package com.technomancer.pangu.logic.flow

/** What the info panel's middle block shows. */
enum class PanelKind { Lang, Hab, Look, Name, Msg, Prog, Rows, Opts, Pers, Needs, Settings, Steps }

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
            ScreenContent(ScreenId.C01, PanelKind.Lang, Hint.Poke, primaryKey = "scr.C01.p"),
            ScreenContent(ScreenId.C02, PanelKind.Hab, Hint.Grab, primaryKey = "scr.C02.p"),
            ScreenContent(ScreenId.C36, PanelKind.Settings, Hint.Poke, bodyKey = null, primaryKey = "scr.C36.s"),
            ScreenContent(ScreenId.C03, PanelKind.Look, Hint.Poke, primaryKey = "scr.C03.p", secondaryKey = "scr.C03.s"),
            ScreenContent(ScreenId.C04, PanelKind.Name, Hint.Poke, primaryKey = "scr.C04.p", secondaryKey = "scr.C04.s"),
            ScreenContent(ScreenId.C05, PanelKind.Msg, Hint.Poke, primaryKey = "scr.C05.p", secondaryKey = "scr.C05.s"),
            ScreenContent(ScreenId.C06, PanelKind.Prog, Hint.None),
            ScreenContent(ScreenId.C07, PanelKind.Rows, Hint.Poke),
            ScreenContent(ScreenId.C08, PanelKind.Msg, Hint.Poke),
            ScreenContent(ScreenId.C09, PanelKind.Prog, Hint.Grab),
            ScreenContent(ScreenId.C10, PanelKind.Msg, Hint.Poke, primaryKey = "scr.C10.p"),
            ScreenContent(ScreenId.C11, PanelKind.Opts, Hint.Poke, primaryKey = "scr.C11.p", secondaryKey = "scr.C11.s"),
            ScreenContent(ScreenId.C12, PanelKind.Prog, Hint.Voice),
            ScreenContent(ScreenId.C13, PanelKind.Prog, Hint.Crank),
            ScreenContent(ScreenId.C14, PanelKind.Prog, Hint.Lean),
            ScreenContent(ScreenId.C15, PanelKind.Prog, Hint.Rub),
            ScreenContent(ScreenId.C16, PanelKind.Msg, Hint.Poke, primaryKey = "scr.C16.p"),
            ScreenContent(ScreenId.C22, PanelKind.Pers, Hint.Poke, bodyKey = null, primaryKey = "scr.C22.p"),
            ScreenContent(ScreenId.C17, PanelKind.Steps, Hint.Poke, primaryKey = "scr.C17.p"),
            ScreenContent(ScreenId.C18, PanelKind.Steps, Hint.Poke, primaryKey = "scr.C18.p"),
            ScreenContent(ScreenId.C19, PanelKind.Msg, Hint.Poke, primaryKey = "scr.C19.p"),
            ScreenContent(ScreenId.C20, PanelKind.Steps, Hint.Poke),
            ScreenContent(ScreenId.C21, PanelKind.Steps, Hint.None),
            ScreenContent(ScreenId.C34, PanelKind.Msg, Hint.None),
            ScreenContent(ScreenId.C37, PanelKind.Msg, Hint.Grab, primaryKey = "scr.C37.p"),
            ScreenContent(ScreenId.C23, PanelKind.Needs, Hint.Poke, titleKey = null, bodyKey = null),
            ScreenContent(ScreenId.C24, PanelKind.Msg, Hint.Poke, bodyKey = null),
            ScreenContent(ScreenId.C25, PanelKind.Msg, Hint.Grab),
            ScreenContent(ScreenId.C26, PanelKind.Prog, Hint.None),
            ScreenContent(ScreenId.C30, PanelKind.Msg, Hint.Rub),
            ScreenContent(ScreenId.C27, PanelKind.Prog, Hint.Rub),
            ScreenContent(ScreenId.C28, PanelKind.Prog, Hint.Move),
            ScreenContent(ScreenId.C29, PanelKind.Msg, Hint.Grab),
            ScreenContent(ScreenId.C31, PanelKind.Steps, Hint.Poke, primaryKey = "scr.C31.p"),
            ScreenContent(ScreenId.C32, PanelKind.Steps, Hint.Poke, primaryKey = "scr.C32.p"),
            ScreenContent(ScreenId.C33, PanelKind.Msg, Hint.None, primaryKey = "scr.C33.p"),
            ScreenContent(ScreenId.C35, PanelKind.Rows, Hint.Poke, primaryKey = "scr.C35.p"),
        ).associateBy { it.id }

        /** The panel for [id], or null for screens that have no panel yet. */
        fun of(id: ScreenId): ScreenContent? = table[id]
    }
}
