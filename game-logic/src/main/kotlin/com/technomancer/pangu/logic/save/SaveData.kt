package com.technomancer.pangu.logic.save

import com.technomancer.pangu.logic.flow.FlowState
import com.technomancer.pangu.logic.flow.ScreenId
import kotlinx.serialization.Serializable

/**
 * Save file root. Times are epoch milliseconds UTC. Later phases add fields with defaults,
 * so files written by earlier builds of the same schema version still load.
 */
@Serializable
data class SaveData(
    val version: Int = SCHEMA_VERSION,
    val flow: FlowState = FlowState.Onboarding,
    val screen: ScreenId = ScreenId.C02,
    /** Language code (en, zh, es); empty until the player has picked one. */
    val language: String = "",
    val settings: SettingsData = SettingsData(),
    val lastSimUtcMs: Long = 0L,
    val lastSeenUtcMs: Long = 0L,
    val padPose: PadPoseData = PadPoseData(),
) {
    companion object {
        const val SCHEMA_VERSION = 4
    }
}

/** Player settings. [volume] is 0..100. */
@Serializable
data class SettingsData(
    val volume: Int = 70,
    val soundOn: Boolean = true,
    val hapticsOn: Boolean = true,
)

/** Pad pose in Stage coordinates. [pos] is x, y, z of the pad top centre. */
@Serializable
data class PadPoseData(
    val has: Boolean = false,
    val pos: List<Float> = listOf(0f, 0f, 0f),
    val yaw: Float = 0f,
)
