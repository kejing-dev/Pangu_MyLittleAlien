package com.technomancer.pangu.logic.save

import com.technomancer.pangu.logic.creation.PetLook
import com.technomancer.pangu.logic.flow.FlowState
import com.technomancer.pangu.logic.flow.ScreenId
import com.technomancer.pangu.logic.incubation.MicChoice
import com.technomancer.pangu.logic.personality.Personality
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
    /** Private ids made on first launch; -1 until then. */
    val keeperId: Int = -1,
    val secretId: Int = -1,
    val lastSimUtcMs: Long = 0L,
    val lastSeenUtcMs: Long = 0L,
    val padPose: PadPoseData = PadPoseData(),
    /** The look being designed, or the one sealed into the current egg or pet. */
    val look: PetLook = PetLook(),
    /** The look of the last egg that was set, preselected when starting a new egg. */
    val lastLook: PetLook = PetLook(),
    val egg: EggData = EggData(),
    /** How the player sings to the egg. */
    val micChoice: MicChoice = MicChoice.Unknown,
    val personality: Personality = Personality(),
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

/**
 * The egg. [imprint] holds Zest, Cuddle, Wonder, Calm, Neat; [completionsToday] and
 * [lastCompletionUtcMs] are indexed by egg action (Warm, Lamp, Blanket, Sing, Listen, Polish).
 */
@Serializable
data class EggData(
    val sparkValue: Long = 0L,
    val warmth: Float = 0f,
    val hatchProgressHours: Float = 0f,
    val coldHours: Float = 0f,
    val lampOffUtcMs: Long = 0L,
    val blanketOffUtcMs: Long = 0L,
    val imprint: List<Int> = emptyList(),
    val completionsToday: List<Int> = emptyList(),
    val completionDay: Long = 0L,
    val lastCompletionUtcMs: List<Long> = emptyList(),
    val blanketTucksToday: Int = 0,
    val crackTaps: Int = 0,
    val setUtcMs: Long = 0L,
    val dormant: Boolean = false,
)
