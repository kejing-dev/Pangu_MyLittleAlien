package com.technomancer.pangu.app

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.AssetManager
import android.util.Log
import com.technomancer.pangu.audio.SfxPlayer
import com.technomancer.pangu.config.ConfigLoader
import com.technomancer.pangu.logic.PanguLogic
import com.technomancer.pangu.logic.config.DebugConfig
import com.technomancer.pangu.logic.config.InputConfig
import com.technomancer.pangu.logic.config.PetConfig
import com.technomancer.pangu.logic.config.PlacementConfig
import com.technomancer.pangu.logic.config.SimConfig
import com.technomancer.pangu.logic.config.UiConfig
import com.technomancer.pangu.logic.flow.ScreenId
import com.technomancer.pangu.logic.habitat.PadPlacement
import com.technomancer.pangu.logic.input.InputSnapshot
import com.technomancer.pangu.logic.input.InteractionQueue
import com.technomancer.pangu.logic.loc.Language
import com.technomancer.pangu.logic.loc.LocService
import com.technomancer.pangu.logic.loc.LocTable
import com.technomancer.pangu.logic.pet.NeedsSim
import com.technomancer.pangu.logic.save.SaveData
import com.technomancer.pangu.logic.sim.CatchUp
import com.technomancer.pangu.logic.time.IClock
import com.technomancer.pangu.logic.time.XorShift32
import com.technomancer.pangu.ui.UiBridge
import java.util.Locale

/** Build-time facts the frame loop needs without touching Android APIs every frame. */
data class DebugFlags(val debuggable: Boolean, val timeScale: Float)

/** Creates the app-wide services once and registers them in [ServiceRegistry]. */
object AppServices {
    fun install(context: Context) {
        if (ServiceRegistry.tryResolve<PanguLogic>() != null) return
        val app = context.applicationContext
        val debuggable = (app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0
        val configs = ConfigLoader(app.assets)
        val placementConfig = configs.load("placement_config", PlacementConfig())
        val uiConfig = configs.load("ui_config", UiConfig())
        val inputConfig = configs.load("input_config", InputConfig())
        val petConfig = configs.load("pet_config", PetConfig())
        val simConfig = configs.load("sim_config", SimConfig())
        // Debug overrides only apply to debug builds.
        val debugConfig = if (debuggable) configs.load("debug_config", DebugConfig()) else DebugConfig(forceFlow = "Pet")
        val clock: IClock = SystemClock()
        val store = FileSaveStore(app.filesDir)
        val save = loadSave(store)

        val placement = PadPlacement(placementConfig)
        // Until creation exists, placing the pad leads straight to the pet.
        val afterPlacement = if (debugConfig.forceFlow == "Pet") ScreenId.C23 else ScreenId.C03
        val startScreen = if (save.padPose.has) {
            save.screen.takeIf { it != ScreenId.C02 && it != ScreenId.C03 } ?: afterPlacement
        } else {
            ScreenId.C02
        }
        val logic = PanguLogic(
            placement = placement,
            startScreen = startScreen,
            afterPlacement = afterPlacement,
            petConfig = petConfig,
            uiConfig = uiConfig,
            clock = clock,
            random = XorShift32.fromLong(clock.nowUtcMillis()),
        )
        restorePet(logic, save, CatchUp(simConfig, NeedsSim(petConfig), clock), clock)

        val language = Language.fromCode(save.language) ?: Language.forLocaleTag(Locale.getDefault().toLanguageTag())
        val loc = LocService(loadTables(app.assets), language) { Log.w("Pangu", "Missing string $it") }
        val ui = UiBridge().apply { setLanguage(language) }
        val haptics = Haptics().apply { enabled = save.settings.hapticsOn }
        val sfx = SfxPlayer().apply {
            volume = save.settings.volume / 100f
            soundOn = save.settings.soundOn
        }
        val session = SessionController(logic, store, clock, ui, save)

        ServiceRegistry.register(DebugFlags(debuggable, if (debuggable) debugConfig.timeScale else 1f))
        ServiceRegistry.register(configs)
        ServiceRegistry.register(placementConfig)
        ServiceRegistry.register(uiConfig)
        ServiceRegistry.register(inputConfig)
        ServiceRegistry.register(petConfig)
        ServiceRegistry.register(placement)
        ServiceRegistry.register(clock)
        ServiceRegistry.register(logic)
        ServiceRegistry.register(InputSnapshot())
        ServiceRegistry.register(InteractionQueue())
        ServiceRegistry.register(loc)
        ServiceRegistry.register(ui)
        ServiceRegistry.register(haptics)
        ServiceRegistry.register(sfx)
        ServiceRegistry.register(session)
        ServiceRegistry.register(SceneHandles())
    }

    /** Loads the saved pet and replays the time the app was closed. */
    private fun restorePet(logic: PanguLogic, save: SaveData, catchUp: CatchUp, clock: IClock) {
        val now = clock.nowUtcMillis()
        val data = save.pet
        if (data.hatchUtcMs <= 0L || data.needs.size < 5) {
            logic.nowMs = now
            return
        }
        val pet = logic.pet
        pet.hatchMs = data.hatchUtcMs
        data.needs.forEachIndexed { i, v -> if (i < pet.needs.values.size) pet.needs.values[i] = v }
        pet.needs.crumbs = data.crumbs
        val from = if (save.lastSimUtcMs > 0L) save.lastSimUtcMs else now
        pet.actions.restoreNap(data.napEndUtcMs, from)
        val steps = catchUp.pet(pet.needs, pet.actions, from, now)
        logic.nowMs = now
        Log.i("Pangu", "Caught up $steps minutes of pet time")
    }

    private fun loadTables(assets: AssetManager): Map<Language, LocTable> =
        Language.entries.associateWith { lang ->
            assets.open("loc/strings_${lang.code}.json").bufferedReader(Charsets.UTF_8).use { LocTable.parse(it.readText()) }
        }
}
