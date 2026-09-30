package com.technomancer.pangu

import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultStage
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope
import com.technomancer.pangu.app.AppServices
import com.technomancer.pangu.containers.PanguStage

fun mainApp(scope: SpatialAppScope) =
    with(scope) {
        AppServices.install(context)
        DefaultStage { PicoTheme { PanguStage() } }
    }
