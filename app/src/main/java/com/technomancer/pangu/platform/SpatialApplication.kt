package com.technomancer.pangu.platform

import android.app.Application
import com.pico.spatial.ui.foundation.dsl.launch
import com.technomancer.pangu.mainApp

class SpatialApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        launch(::mainApp)
    }
}
