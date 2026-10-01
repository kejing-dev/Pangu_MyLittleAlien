package com.technomancer.pangu.ecs.systems

import android.util.Log
import com.pico.spatial.core.ecs.SceneUpdateContext
import com.pico.spatial.core.ecs.System
import com.technomancer.pangu.app.DebugFlags
import com.technomancer.pangu.app.Haptics
import com.technomancer.pangu.app.SceneHandles
import com.technomancer.pangu.app.ServiceRegistry
import com.technomancer.pangu.app.SessionController
import com.technomancer.pangu.audio.MicService
import com.technomancer.pangu.audio.SfxPlayer
import com.technomancer.pangu.logic.input.InteractionType
import com.technomancer.pangu.logic.HapticCue
import com.technomancer.pangu.logic.PanguLogic
import com.technomancer.pangu.logic.config.InputConfig
import com.technomancer.pangu.logic.input.InputSnapshot
import com.technomancer.pangu.logic.input.InteractionQueue
import com.technomancer.pangu.ui.UiBridge

/**
 * The only registered System. Each frame it runs input → logic → presentation → UI in that order,
 * so nothing depends on how the SDK orders several Systems.
 * The SDK creates it by reflection, so its dependencies come from [ServiceRegistry].
 */
class PanguFrameSystem : System() {
    private val logic: PanguLogic = ServiceRegistry.resolve()
    private val scene: SceneHandles = ServiceRegistry.resolve()
    private val snapshot: InputSnapshot = ServiceRegistry.resolve()
    private val events: InteractionQueue = ServiceRegistry.resolve()
    private val ui: UiBridge = ServiceRegistry.resolve()
    private val haptics: Haptics = ServiceRegistry.resolve()
    private val sfx: SfxPlayer = ServiceRegistry.resolve()
    private val inputConfig: InputConfig = ServiceRegistry.resolve()
    private val session: SessionController = ServiceRegistry.resolve()
    private val debug: DebugFlags = ServiceRegistry.resolve()
    private val mic: MicService = ServiceRegistry.resolve()
    private var micOn = false
    private var nextLogNanos = 0L
    private var autosaveSec = 0f

    override fun update(context: SceneUpdateContext) {
        // The SDK can run this System as rarely as once a second when the scene is still, so
        // only very long gaps are clamped; longer absences go through catch-up instead.
        val dt = context.deltaTime.coerceIn(0f, MAX_DT) * debug.timeScale

        scene.input?.collect(snapshot)
        updateMic()
        logic.tick(dt, snapshot, events)
        scene.presentation?.apply(logic, snapshot, dt)
        ui.publishIfChanged(logic)

        when (logic.consumeHaptic()) {
            HapticCue.UiPress -> haptics.play(inputConfig.uiPress)
            HapticCue.Grab -> haptics.play(inputConfig.grab)
            HapticCue.Heartbeat -> haptics.play(inputConfig.heartbeat)
            HapticCue.Completion -> haptics.play(inputConfig.completion)
            HapticCue.Crack -> haptics.play(inputConfig.tapToCrack)
            null -> Unit
        }
        sfx.voicePitch = logic.pet.personality.voicePitch
        while (true) sfx.play(logic.nextSound() ?: break)
        sfx.setLoop(if (logic.isPaused) null else logic.danceMusic)

        autosaveSec += context.deltaTime
        if (logic.consumeSaveRequest() || autosaveSec >= AUTOSAVE_SEC) {
            autosaveSec = 0f
            session.persist()
        }

        if (debug.debuggable) {
            val now = java.lang.System.nanoTime()
            if (now >= nextLogNanos) {
                nextLogNanos = now + LOG_INTERVAL_NANOS
                Log.i(TAG, "tick=${logic.tickCount} dt=${context.deltaTime} screen=${logic.screens.current}")
            }
        }
    }

    /** Reads the microphone only while the player is singing to the egg (C12). */
    private fun updateMic() {
        val want = logic.micWanted
        if (want && !micOn) {
            micOn = mic.start()
            // No permission any more: ask again through C11.
            if (!micOn) events.push(InteractionType.UiMic, 0)
        } else if (!want && micOn) {
            mic.stop()
            micOn = false
        }
        snapshot.micRms = if (micOn) mic.rms else 0f
        ui.micRms = snapshot.micRms
    }

    companion object {
        private const val TAG = "Pangu"
        private const val MAX_DT = 1.0f
        private const val AUTOSAVE_SEC = 30f
        private const val LOG_INTERVAL_NANOS = 5_000_000_000L
    }
}
