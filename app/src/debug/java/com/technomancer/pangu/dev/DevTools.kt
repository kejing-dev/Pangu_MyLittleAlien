package com.technomancer.pangu.dev

import android.content.Context
import android.os.Process
import android.util.Log
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import com.pico.spatial.ui.design.Text
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import com.technomancer.pangu.app.DebugFlags
import com.technomancer.pangu.app.ServiceRegistry
import com.technomancer.pangu.logic.PanguLogic
import com.technomancer.pangu.logic.dev.DevCommand
import com.technomancer.pangu.logic.loc.Language
import com.technomancer.pangu.ui.LocalPanguFonts
import com.technomancer.pangu.ui.PanguTokens
import com.technomancer.pangu.ui.PanguType
import com.technomancer.pangu.ui.UiBridge
import com.technomancer.pangu.ui.UiScale
import java.io.File

/**
 * The Dev Tools menu: a small "DEV" tab beside the habitat that opens a panel of testing
 * shortcuts (jump to a stage, change the egg or the pet, speed up time, erase the save).
 *
 * This file is in the debug source set, so the menu and everything it can do exist only in debug
 * builds. Release builds compile the empty DevTools in src/release instead: no tab, no panel, and
 * the game's shortcut entry point is never switched on.
 */
object DevTools {
    /** True in debug builds: the Stage adds the Dev Tools attachment only then. */
    const val AVAILABLE = true

    private const val TAG = "PanguDev"

    /** Switches on the game's testing shortcuts. Called once when the app's services are created. */
    fun install(logic: PanguLogic) {
        logic.devTools = true
        Log.i(TAG, "Dev Tools on (debug build)")
    }

    /** The tab and, once opened, the panel. */
    @Composable
    fun Panel(scale: UiScale) {
        val logic = remember { ServiceRegistry.resolve<PanguLogic>() }
        val flags = remember { ServiceRegistry.resolve<DebugFlags>() }
        val ui = remember { ServiceRegistry.resolve<UiBridge>() }
        val context = LocalContext.current
        var open by remember { mutableStateOf(false) }
        var speed by remember { mutableFloatStateOf(flags.timeScale) }
        var confirmErase by remember { mutableStateOf(false) }
        val screen by ui.screen.collectAsState()

        fun run(command: DevCommand) {
            Log.i(TAG, "dev: $command (screen ${logic.screens.current}, flow ${logic.flow.flow})")
            logic.dev(command)
        }

        // The tools are for developers and stay in English whatever the device language is.
        PanguType(Language.En) {
            if (!open) {
                Chip("DEV", scale, filled = true) { open = true }
                return@PanguType
            }
            val shape = RoundedCornerShape(scale.px(18))
            Column(
                modifier = Modifier
                    .width(scale.px(PANEL_WIDTH))
                    .background(PanguTokens.paper, shape)
                    .border(scale.px(PanguTokens.BORDER), DEV_COLOR, shape)
                    .padding(horizontal = scale.px(18), vertical = scale.px(16)),
                verticalArrangement = Arrangement.spacedBy(scale.px(8)),
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Dev tools", color = PanguTokens.ink, fontSize = scale.sp(20f), fontWeight = FontWeight.SemiBold)
                        Text(
                            "debug build · ${logic.flow.flow} · $screen",
                            color = PanguTokens.muted,
                            fontSize = scale.sp(12f),
                            fontFamily = LocalPanguFonts.current.mono,
                        )
                    }
                    Chip("Close", scale, filled = false) {
                        open = false
                        confirmErase = false
                    }
                }

                Section("Stages", scale)
                Buttons(scale) {
                    Tool("Customize", scale) { run(DevCommand.Customize) }
                    Tool("Restart egg", scale) { run(DevCommand.RestartEgg) }
                }
                Buttons(scale) {
                    Tool("Egg ready to hatch", scale) { run(DevCommand.EggReady) }
                    Tool("Hatch now", scale) { run(DevCommand.HatchNow) }
                }
                Buttons(scale) {
                    Tool("Hatched pet", scale) { run(DevCommand.Hatched) }
                }

                Section("Egg", scale)
                Buttons(scale) {
                    Tool("Cold", scale) { run(DevCommand.EggCold) }
                    Tool("Warm", scale) { run(DevCommand.EggWarm) }
                    Tool("Dormant", scale) { run(DevCommand.EggDormant) }
                }
                Buttons(scale) {
                    Tool("Clear ritual limits", scale) { run(DevCommand.ClearRitualCaps) }
                }

                Section("Pet", scale)
                Buttons(scale) {
                    Tool("Fill needs", scale) { run(DevCommand.FillNeeds) }
                    Tool("Drain needs", scale) { run(DevCommand.DrainNeeds) }
                }
                Buttons(scale) {
                    Tool("Mopey", scale) { run(DevCommand.PetMopey) }
                    Tool("Homesick", scale) { run(DevCommand.PetHomesick) }
                    Tool("Goes home", scale) { run(DevCommand.PetWentHome) }
                }
                Buttons(scale) {
                    Tool("Cheer up", scale) { run(DevCommand.PetCheerUp) }
                    Tool("Personality card", scale) { run(DevCommand.ShowPersonality) }
                }

                Section("Time", scale)
                Buttons(scale) {
                    for ((label, value) in SPEEDS) {
                        Tool(label, scale, selected = speed == value) {
                            flags.timeScale = value
                            speed = value
                            Log.i(TAG, "dev: time ×$value")
                        }
                    }
                }

                Section("Save", scale)
                Buttons(scale) {
                    if (!confirmErase) {
                        Tool("Erase save and restart…", scale) { confirmErase = true }
                    } else {
                        Tool("Yes, erase everything", scale, danger = true) { eraseAndRestart(context) }
                        Tool("Cancel", scale) { confirmErase = false }
                    }
                }
            }
        }
    }

    /** Deletes the save files, relaunches the app and ends this process before anything can save again. */
    private fun eraseAndRestart(context: Context) {
        val app = context.applicationContext
        val deleted = (app.filesDir.listFiles() ?: emptyArray<File>()).filter { it.name.startsWith(SAVE_PREFIX) }.count { it.delete() }
        Log.i(TAG, "dev: erased $deleted save file(s), restarting")
        app.packageManager.getLaunchIntentForPackage(app.packageName)?.let {
            it.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK or android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK)
            app.startActivity(it)
        }
        Process.killProcess(Process.myPid())
    }

    @Composable
    private fun Section(title: String, scale: UiScale) {
        Text(
            title.uppercase(),
            color = PanguTokens.muted,
            fontSize = scale.sp(11f),
            fontWeight = FontWeight.Bold,
            fontFamily = LocalPanguFonts.current.mono,
            modifier = Modifier.padding(top = scale.px(4)),
        )
    }

    @Composable
    private fun Buttons(scale: UiScale, content: @Composable ButtonRow.() -> Unit) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(scale.px(8))) {
            ButtonRow(this).content()
        }
    }

    /** Lets [Tool] share a row's width equally with its neighbours. */
    private class ButtonRow(val row: androidx.compose.foundation.layout.RowScope)

    @Composable
    private fun ButtonRow.Tool(label: String, scale: UiScale, selected: Boolean = false, danger: Boolean = false, onClick: () -> Unit) {
        val shape = RoundedCornerShape(scale.px(12))
        val fill = when {
            danger -> PanguTokens.tomato
            selected -> PanguTokens.ink
            else -> PanguTokens.cream
        }
        with(row) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(scale.px(40))
                    .background(fill, shape)
                    .border(scale.px(1f), PanguTokens.ink, shape)
                    .spatialHoverEffect()
                    .clickable(onClick = onClick),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, color = if (selected || danger) PanguTokens.paper else PanguTokens.ink, fontSize = scale.sp(13f), fontWeight = FontWeight.SemiBold, maxLines = 1)
            }
        }
    }

    @Composable
    private fun Chip(label: String, scale: UiScale, filled: Boolean, onClick: () -> Unit) {
        val shape = RoundedCornerShape(scale.px(14))
        Box(
            modifier = Modifier
                .background(if (filled) DEV_COLOR else PanguTokens.paper, shape)
                .border(scale.px(1f), if (filled) DEV_COLOR else PanguTokens.ink, shape)
                .spatialHoverEffect()
                .clickable(onClick = onClick)
                .padding(horizontal = scale.px(14), vertical = scale.px(7)),
            contentAlignment = Alignment.Center,
        ) {
            Text(label, color = if (filled) PanguTokens.paper else PanguTokens.ink, fontSize = scale.sp(13f), fontWeight = FontWeight.Bold, fontFamily = LocalPanguFonts.current.mono)
        }
    }

    private const val PANEL_WIDTH = 380f
    private const val SAVE_PREFIX = "pangu_save"

    /** A colour no game panel uses, so the tools can't be mistaken for part of the game. */
    private val DEV_COLOR = Color(0xFF7A4FBF)

    /** Simulated time per real second: normal, a minute, ten minutes. */
    private val SPEEDS = listOf("×1" to 1f, "×60" to 60f, "×600" to 600f)
}
