package com.technomancer.pangu

import com.technomancer.pangu.content.HomeVolume
import com.pico.spatial.ui.design.PicoTheme
import com.pico.spatial.ui.foundation.dsl.DefaultWindowContainer
import com.pico.spatial.ui.foundation.dsl.SpatialAppScope

fun mainApp(scope: SpatialAppScope) =
    with(scope) { DefaultWindowContainer { PicoTheme { HomeVolume() } } }

