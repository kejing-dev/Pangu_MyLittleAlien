package com.technomancer.pangu.ecs.systems

import android.util.Log
import com.pico.spatial.core.ecs.SceneUpdateContext
import com.pico.spatial.core.ecs.System
import com.technomancer.pangu.app.DebugFlags
import com.technomancer.pangu.app.ServiceRegistry
import com.technomancer.pangu.logic.PanguLogic

/**
 * The only registered System. Each frame it runs input, logic and presentation in a fixed order,
 * so nothing depends on how the SDK orders several Systems. Today it only advances the logic.
 * The SDK creates it by reflection, so its dependencies come from [ServiceRegistry].
 */
class PanguFrameSystem : System() {
    private val logic: PanguLogic = ServiceRegistry.resolve()
    private val debugLog: Boolean = ServiceRegistry.resolve<DebugFlags>().debuggable
    private var nextLogNanos = 0L
    private var loggedFirstFrame = false

    override fun update(context: SceneUpdateContext) {
        if (debugLog && !loggedFirstFrame) {
            loggedFirstFrame = true
            Log.i(TAG, "first frame dt=${context.deltaTime}")
        }
        val dt = context.deltaTime.coerceIn(0f, MAX_DT)
        logic.tick(dt)

        if (debugLog) {
            val now = java.lang.System.nanoTime()
            if (now >= nextLogNanos) {
                nextLogNanos = now + LOG_INTERVAL_NANOS
                Log.i(TAG, "tick=${logic.tickCount} simT=${"%.1f".format(logic.elapsedSec)}s dt=${context.deltaTime}")
            }
        }
    }

    companion object {
        private const val TAG = "Pangu"
        private const val MAX_DT = 0.1f
        private const val LOG_INTERVAL_NANOS = 5_000_000_000L
    }
}
