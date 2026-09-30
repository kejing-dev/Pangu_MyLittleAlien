package com.technomancer.pangu.logic.config

import kotlinx.serialization.Serializable

/** A controller haptic pulse: amplitude 0..1 and duration in seconds. */
@Serializable
data class HapticPulse(val amplitude: Float, val durationSec: Float)

@Serializable
data class InputConfig(
    /** Distance from the controller origin to its poke point along its forward axis, metres. */
    val controllerTipOffset: Float = 0.06f,
    val uiPress: HapticPulse = HapticPulse(0.25f, 0.03f),
    val grab: HapticPulse = HapticPulse(0.15f, 0.02f),
    val completion: HapticPulse = HapticPulse(0.4f, 0.08f),
    val heartbeat: HapticPulse = HapticPulse(0.12f, 0.02f),
    val tapToCrack: HapticPulse = HapticPulse(0.3f, 0.03f),
    val refusal: HapticPulse = HapticPulse(0.2f, 0.06f),
)
