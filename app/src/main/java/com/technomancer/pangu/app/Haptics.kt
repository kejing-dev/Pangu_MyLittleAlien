package com.technomancer.pangu.app

import com.pico.spatial.ui.platform.HandController
import com.pico.spatial.ui.platform.HandControllerHapticType
import com.pico.spatial.ui.platform.SpatialHandControllerHaptic
import com.technomancer.pangu.logic.config.HapticPulse
import com.technomancer.pangu.logic.input.Side

/**
 * Controller vibration. The SDK only vibrates when the player is using controllers.
 * [device] is provided by the Stage composable; [enabled] follows the Haptics setting.
 * [side] is the controller in use; null pulses both.
 */
class Haptics {
    var device: SpatialHandControllerHaptic? = null
    var enabled: Boolean = true

    var side: Side? = null

    fun play(pulse: HapticPulse) {
        val d = device ?: return
        if (!enabled) return
        val type = HandControllerHapticType(
            (pulse.amplitude.coerceIn(0f, 1f) * 255f).toInt(),
            FREQUENCY_HZ,
            (pulse.durationSec * 1000f).toInt().coerceIn(0, 1000),
        )
        if (side != Side.Right) d.feedback(type, HandController.Left)
        if (side != Side.Left) d.feedback(type, HandController.Right)
    }

    companion object {
        private const val FREQUENCY_HZ = 160
    }
}
