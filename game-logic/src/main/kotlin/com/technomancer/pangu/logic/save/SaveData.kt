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
    val pet: PetData = PetData(),
    val hatchedCount: Int = 0,
    val dormantCount: Int = 0,
    val wentHomeCount: Int = 0,
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

/** The hatched pet. Needs are Hunger, Energy, Hygiene, Fun, Social; Tidy comes from [crumbs]. */
@Serializable
data class PetData(
    val hatchUtcMs: Long = 0L,
    val needs: List<Float> = emptyList(),
    val crumbs: Int = 0,
    /** Crumb positions on the pad as x, z pairs. */
    val crumbPositions: List<Float> = emptyList(),
    val napEndUtcMs: Long = 0L,
    val distressHours: Float = 0f,
)
