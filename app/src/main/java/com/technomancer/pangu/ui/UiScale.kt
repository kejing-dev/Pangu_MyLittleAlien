package com.technomancer.pangu.ui

import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter
import com.technomancer.pangu.logic.config.UiConfig

/**
 * Converts mockup pixels to Compose units at a fixed physical size: one mockup pixel is
 * [UiConfig.metresPerMockupPx] metres in the room, whatever the panel's distance.
 */
class UiScale(private val dpPerMockupPx: Float) {
    fun px(n: Float): Dp = (n * dpPerMockupPx).dp
    fun px(n: Int): Dp = px(n.toFloat())

    /** Text size for a mockup font size in px. Panels use a 1:1 dp:sp mapping. */
    fun sp(n: Float): TextUnit = (n * dpPerMockupPx).sp

    companion object {
        @Composable
        fun remember(config: UiConfig): UiScale {
            val converter = LocalPhysicalLengthConverter.current
            return remember(converter, config) {
                val dpPerMetre = converter.lengthToDp(1f, LengthUnit.Meters).value
                Log.i("Pangu", "UiScale: $dpPerMetre dp per metre")
                UiScale(dpPerMetre * config.metresPerMockupPx)
            }
        }
    }
}
