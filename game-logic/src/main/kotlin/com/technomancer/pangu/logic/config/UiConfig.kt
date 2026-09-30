package com.technomancer.pangu.logic.config

import kotlinx.serialization.Serializable

/** Panel sizing and placement. Mockup pixels are turned into metres with [metresPerMockupPx]. */
@Serializable
data class UiConfig(
    val metresPerMockupPx: Float = 0.0009f,
    val infoPanelWidthPx: Float = 420f,
    /** Info panel top-centre in pad space (right of the pad, a little above it). */
    val infoPanelOffset: List<Float> = listOf(0.52f, 0.38f, 0f),
    val pieRadius: Float = 0.11f,
    val pieIdleCloseSec: Float = 8f,
    val toastSec: Float = 1.5f,
    /** Care orb float height above the pad top and its bob. */
    val orbHeight: Float = 0.24f,
    val orbBobAmplitude: Float = 0.005f,
    val orbBobHz: Float = 0.5f,
)
