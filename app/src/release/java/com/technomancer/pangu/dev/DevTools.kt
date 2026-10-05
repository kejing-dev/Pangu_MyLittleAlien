package com.technomancer.pangu.dev

import androidx.compose.runtime.Composable
import com.technomancer.pangu.logic.PanguLogic
import com.technomancer.pangu.ui.UiScale

/**
 * Release stand-in for the Dev Tools menu: nothing to show and nothing to switch on. The real
 * menu lives in src/debug and is not compiled into release builds.
 */
@Suppress("UNUSED_PARAMETER")
object DevTools {
    const val AVAILABLE = false

    fun install(logic: PanguLogic) {}

    @Composable
    fun Panel(scale: UiScale) {}
}
