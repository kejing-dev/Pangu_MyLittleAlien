package com.technomancer.pangu.app

import android.content.Context
import android.content.pm.ApplicationInfo
import com.technomancer.pangu.config.ConfigLoader
import com.technomancer.pangu.logic.PanguLogic
import com.technomancer.pangu.logic.config.PlacementConfig
import com.technomancer.pangu.logic.habitat.PadPlacement
import com.technomancer.pangu.logic.time.IClock

/** Build-time facts the frame loop needs without touching Android APIs every frame. */
data class DebugFlags(val debuggable: Boolean)

/** Creates the app-wide services once and registers them in [ServiceRegistry]. */
object AppServices {
    fun install(context: Context) {
        if (ServiceRegistry.tryResolve<PanguLogic>() != null) return
        val app = context.applicationContext
        val configs = ConfigLoader(app.assets)
        val placementConfig = configs.load("placement_config", PlacementConfig())
        val clock: IClock = SystemClock()
        val logic = PanguLogic()
        val session = SessionController(logic, FileSaveStore(app.filesDir), clock).apply { load() }

        ServiceRegistry.register(DebugFlags((app.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE) != 0))
        ServiceRegistry.register(configs)
        ServiceRegistry.register(placementConfig)
        ServiceRegistry.register(PadPlacement(placementConfig))
        ServiceRegistry.register(clock)
        ServiceRegistry.register(logic)
        ServiceRegistry.register(session)
    }
}
