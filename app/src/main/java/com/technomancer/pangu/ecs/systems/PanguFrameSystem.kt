package com.technomancer.pangu.ecs.systems

import android.util.Log
import com.pico.spatial.core.ecs.SceneUpdateContext
import com.pico.spatial.core.ecs.System
import com.technomancer.pangu.app.DebugFlags
import com.technomancer.pangu.app.Haptics
import com.technomancer.pangu.app.SceneHandles
import com.technomancer.pangu.app.ServiceRegistry
import com.technomancer.pangu.app.SessionController
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
    private val inputConfig: InputConfig = ServiceRegistry.resolve()
    private val session: SessionController = ServiceRegistry.resolve()
    private val debugLog: Boolean = ServiceRegistry.resolve<DebugFlags>().debuggable
    private var nextLogNanos = 0L

    override fun update(context: SceneUpdateContext) {
        val dt = context.deltaTime.coerceIn(0f, MAX_DT)

        scene.input?.collect(snapshot)
        logic.tick(dt, snapshot, events)
        scene.presentation?.apply(logic, snapshot, dt)
        ui.publishIfChanged(logic)

        when (logic.consumeHaptic()) {
            HapticCue.UiPress -> haptics.play(inputConfig.uiPress)
            HapticCue.Grab -> haptics.play(inputConfig.grab)
            null -> Unit
        }
        if (logic.consumeSaveRequest()) session.persist()

        if (debugLog) {
            val now = java.lang.System.nanoTime()
            if (now >= nextLogNanos) {
                nextLogNanos = now + LOG_INTERVAL_NANOS
                Log.i(TAG, "tick=${logic.tickCount} dt=${context.deltaTime} screen=${logic.screens.current}")
            }
        }
    }

    companion object {
        private const val TAG = "Pangu"
        private const val MAX_DT = 0.1f
        private const val LOG_INTERVAL_NANOS = 5_000_000_000L
    }
}
