package com.technomancer.pangu.app

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.AssetManager
import android.util.Log
import com.technomancer.pangu.config.ConfigLoader
import com.technomancer.pangu.logic.PanguLogic
import com.technomancer.pangu.logic.config.InputConfig
import com.technomancer.pangu.logic.config.PlacementConfig
import com.technomancer.pangu.logic.config.UiConfig
import com.technomancer.pangu.logic.flow.ScreenId
import com.technomancer.pangu.logic.habitat.PadPlacement
import com.technomancer.pangu.logic.input.InputSnapshot
import com.technomancer.pangu.logic.input.InteractionQueue
import com.technomancer.pangu.logic.loc.Language
import com.technomancer.pangu.logic.loc.LocService
import com.technomancer.pangu.logic.loc.LocTable
import com.technomancer.pangu.logic.time.IClock
import com.technomancer.pangu.ui.UiBridge
import java.util.Locale

/** Build-time facts the frame loop needs without touching Android APIs every frame. */
data class DebugFlags(val debuggable: Boolean)

/** Creates the app-wide services once and registers them in [ServiceRegistry]. */
object AppServices {
    fun install(context: Context) {
        if (ServiceRegistry.tryResolve<PanguLogic>() != null) return
        val app = context.applicationContext
        val configs = ConfigLoader(app.assets)
        val placementConfig = configs.load("placement_config", PlacementConfig())
        val uiConfig = configs.load("ui_config", UiConfig())
        val inputConfig = configs.load("input_config", InputConfig())
        val clock: IClock = SystemClock()
        val store = FileSaveStore(app.filesDir)
        val save = loadSave(store)

        val placement = PadPlacement(placementConfig)
        // A confirmed pad skips placement; its screen comes back from the save.
        val startScreen = if (save.padPose.has) save.screen.takeIf { it != ScreenId.C02 } ?: ScreenId.C03 else ScreenId.C02
        val logic = PanguLogic(placement, startScreen)

        val language = Language.fromCode(save.language) ?: Language.forLocaleTag(Locale.getDefault().toLanguageTag())
        val loc = LocService(loadTables(app.assets), language) { Log.w("Pangu", "Missing string $it") }
        val ui = UiBridge().apply { setLanguage(language) }
        val haptics = Haptics().apply { enabled = save.settings.hapticsOn }
        val session = SessionController(logic, store, clock, ui, save)

        ServiceRegistry.register(DebugFlags((app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0))
        ServiceRegistry.register(configs)
        ServiceRegistry.register(placementConfig)
        ServiceRegistry.register(uiConfig)
        ServiceRegistry.register(inputConfig)
        ServiceRegistry.register(placement)
        ServiceRegistry.register(clock)
        ServiceRegistry.register(logic)
        ServiceRegistry.register(InputSnapshot())
        ServiceRegistry.register(InteractionQueue())
        ServiceRegistry.register(loc)
        ServiceRegistry.register(ui)
        ServiceRegistry.register(haptics)
        ServiceRegistry.register(session)
        ServiceRegistry.register(SceneHandles())
    }

    private fun loadTables(assets: AssetManager): Map<Language, LocTable> =
        Language.entries.associateWith { lang ->
            assets.open("loc/strings_${lang.code}.json").bufferedReader(Charsets.UTF_8).use { LocTable.parse(it.readText()) }
        }
}
