package com.technomancer.pangu.app

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.res.AssetManager
import android.util.Log
import com.technomancer.pangu.audio.MicService
import com.technomancer.pangu.audio.SfxPlayer
import com.technomancer.pangu.config.ConfigLoader
import com.technomancer.pangu.logic.PanguLogic
import com.technomancer.pangu.logic.config.DebugConfig
import com.technomancer.pangu.logic.config.InputConfig
import com.technomancer.pangu.logic.config.PetConfig
import com.technomancer.pangu.logic.config.PlacementConfig
import com.technomancer.pangu.logic.config.SimConfig
import com.technomancer.pangu.logic.config.UiConfig
import com.technomancer.pangu.logic.config.IncubationConfig
import com.technomancer.pangu.logic.config.LookConfig
import com.technomancer.pangu.logic.config.PersonalityConfig
import com.technomancer.pangu.logic.creation.KeeperIds
import com.technomancer.pangu.logic.flow.FlowMachine
import com.technomancer.pangu.logic.flow.FlowState
import com.technomancer.pangu.logic.flow.ScreenId
import com.technomancer.pangu.logic.incubation.restore
import com.technomancer.pangu.logic.habitat.PadPlacement
import com.technomancer.pangu.logic.input.InputSnapshot
import com.technomancer.pangu.logic.input.InteractionQueue
import com.technomancer.pangu.logic.loc.Language
import com.technomancer.pangu.logic.loc.LocService
import com.technomancer.pangu.logic.loc.LocTable
import com.technomancer.pangu.logic.math.Vec3
import com.technomancer.pangu.logic.pet.AwaySummary
import com.technomancer.pangu.logic.pet.MoodCalculator
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
        val lookConfig = configs.load("look_config", LookConfig())
        val incubationConfig = configs.load("incubation_config", IncubationConfig())
        val personalityConfig = configs.load("personality_config", PersonalityConfig())
        // Debug overrides only apply to debug builds.
        val debugConfig = if (debuggable) configs.load("debug_config", DebugConfig()) else DebugConfig()
        val clock: IClock = SystemClock()
        val store = FileSaveStore(app.filesDir)
        val save = loadSave(store)

        val placement = PadPlacement(placementConfig)
        // Saves from before creation existed have a hatched pet but no flow yet.
        val flow = if (save.flow == FlowState.Onboarding && save.pet.hatchUtcMs > 0L) FlowState.Pet else save.flow
        // First launch picks a language (C01); after that a missing pad is placed again (C02).
        val startScreen = when {
            save.padPose.has -> FlowMachine.resumeScreen(flow, save.screen)
            save.language.isEmpty() -> ScreenId.C01
            else -> ScreenId.C02
        }
        val logic = PanguLogic(
            placement = placement,
            startScreen = startScreen,
            afterPlacement = ScreenId.C03,
            petConfig = petConfig,
            uiConfig = uiConfig,
            clock = clock,
            random = XorShift32.fromLong(clock.nowUtcMillis()),
            lookConfig = lookConfig,
            incubationConfig = incubationConfig,
            personalityConfig = personalityConfig,
            skipToPet = debugConfig.forceFlow == "Pet",
        )
        logic.keeperIds = KeeperIds.ensure(KeeperIds(save.keeperId, save.secretId).takeIf { save.keeperId >= 0 }, clock)
        logic.hatchedCount = save.hatchedCount
        logic.dormantCount = save.dormantCount
        logic.wentHomeCount = save.wentHomeCount
        logic.flow.flow = flow
        logic.flow.look = save.look
        logic.flow.lastLook = save.lastLook
        logic.micChoice = save.micChoice
        logic.language = Language.fromCode(save.language) ?: Language.forLocaleTag(Locale.getDefault().toLanguageTag())
        logic.volume = save.settings.volume
        logic.soundOn = save.settings.soundOn
        logic.hapticsOn = save.settings.hapticsOn
        logic.debugAssist = debuggable && isEmulator()
        val catchUp = CatchUp(simConfig, NeedsSim(petConfig), clock)
        val perf = PerfProbe(debuggable)
        perf.milestone("services starting")
        val awayHours = if (save.lastSimUtcMs > 0L) ((clock.nowUtcMillis() - save.lastSimUtcMs) / 3.6e6).coerceIn(0.0, simConfig.maxCatchUpHours.toDouble()) else 0.0
        perf.timeCatchUp(awayHours) { restore(flow, logic, save, catchUp, clock, petConfig) }


        val language = Language.fromCode(save.language) ?: Language.forLocaleTag(Locale.getDefault().toLanguageTag())
        val loc = LocService(loadTables(app.assets), language) { Log.w("Pangu", "Missing string $it") }
        val ui = UiBridge().apply { setLanguage(language) }
        val haptics = Haptics().apply { enabled = save.settings.hapticsOn }
        val sfx = SfxPlayer().apply {
            volume = save.settings.volume / 100f
            soundOn = save.settings.soundOn
        }
        val session = SessionController(logic, store, clock, ui, save, catchUp, petConfig.welcomeBackMinMinutes)

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
        ServiceRegistry.register(MicService(app, perf))
        ServiceRegistry.register(perf)
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
        pet.applyPersonality(save.personality)
        data.needs.forEachIndexed { i, v -> if (i < pet.needs.values.size) pet.needs.values[i] = v }
        for (i in 0 until data.crumbPositions.size / 2) {
            pet.crumbs.positions += Vec3(data.crumbPositions[i * 2], 0f, data.crumbPositions[i * 2 + 1])
        }
        pet.needs.crumbs = pet.crumbs.positions.size
        pet.distress.hours = data.distressHours

        val from = if (save.lastSimUtcMs > 0L) save.lastSimUtcMs else now
        pet.actions.restoreNap(data.napEndUtcMs, from)
        val before = pet.needs.values.copyOf()
        val crumbsBefore = pet.needs.crumbs
        val steps = catchUp.pet(pet.needs, pet.actions, from, now, pet.crumbs, pet.distress, MoodCalculator(pet.config), pet.modifiers, pet.sleepEndHour)
        pet.syncStage()
        logic.nowMs = now
        logic.announceState()
        Log.i("Pangu", "Caught up $steps minutes of pet time")

        val lastSeen = if (save.lastSeenUtcMs > 0L) save.lastSeenUtcMs else now
        AwaySummary.of(before, pet.needs.values, crumbsBefore, pet.needs.crumbs, now - lastSeen, pet.config.welcomeBackMinMinutes)
            ?.takeIf { logic.isPetFlow }
            ?.let { logic.showWelcomeBack(it) }
    }

    /** Restores the pet or the egg for the saved [flow] and replays the time the app was closed. */
    private fun restore(flow: FlowState, logic: PanguLogic, save: SaveData, catchUp: CatchUp, clock: IClock, petConfig: PetConfig) {
        when (flow) {
            FlowState.Hatching -> {
                // The app closed mid-hatch: the pet is out.
                logic.flow.flow = FlowState.Pet
                logic.hatchedCount++
                restorePet(logic, save, catchUp, clock)
            }
            FlowState.Pet, FlowState.WentHome -> restorePet(logic, save, catchUp, clock)
            FlowState.Incubation, FlowState.Dormant -> restoreEgg(logic, save, catchUp, clock, petConfig.welcomeBackMinMinutes)
            else -> logic.nowMs = clock.nowUtcMillis()
        }
    }

    /** Loads the egg and replays the time the app was closed. */
    private fun restoreEgg(logic: PanguLogic, save: SaveData, catchUp: CatchUp, clock: IClock, welcomeMinMinutes: Float) {
        val now = clock.nowUtcMillis()
        logic.egg.restore(save.egg)
        val from = if (save.lastSimUtcMs > 0L) save.lastSimUtcMs else now
        logic.egg.syncCondition(from)
        logic.nowMs = from
        logic.catchUpEgg(catchUp, from, now, welcomeMinMinutes)
        logic.nowMs = now
        Log.i("Pangu", "Caught up the egg to ${logic.egg.condition}, warmth ${logic.egg.egg.warmth}")
    }

    /**
     * True in the PICO Emulator. The SDK's SpatialBuild only reports isSpatialPlatform(), so this
     * reads Android's build properties (the Emulator runs on "ranchu" hardware).
     */
    private fun isEmulator(): Boolean =
        android.os.Build.HARDWARE == "ranchu" || android.os.Build.FINGERPRINT.contains("emulator")

    private fun loadTables(assets: AssetManager): Map<Language, LocTable> =
        Language.entries.associateWith { lang ->
            assets.open("loc/strings_${lang.code}.json").bufferedReader(Charsets.UTF_8).use { LocTable.parse(it.readText()) }
        }
}
