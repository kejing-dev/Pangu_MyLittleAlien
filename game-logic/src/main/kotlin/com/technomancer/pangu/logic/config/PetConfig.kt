package com.technomancer.pangu.logic.config

import kotlinx.serialization.Serializable

/** Pet needs, actions and body. Rates are per hour unless the name says otherwise. */
@Serializable
data class PetConfig(
    /** Hatch values for Hunger, Energy, Hygiene, Fun, Social. */
    val startNeeds: List<Float> = listOf(60f, 80f, 80f, 70f, 70f),
    /** Awake decay per hour for Hunger, Energy, Hygiene, Fun, Social. */
    val decayPerHour: List<Float> = listOf(5f, 4f, 3f, 6f, 5f),
    val sleepDecayMultiplier: Float = 0.3f,
    val nightSleepEnergyPerHour: Float = 10f,
    val sleepStartHour: Int = 22,
    val sleepEndHour: Int = 7,
    val tidyPerCrumb: Float = 20f,
    /** Mood weights for Hunger, Energy, Hygiene, Fun, Social, Tidy. */
    val moodWeights: List<Float> = listOf(1.5f, 1.2f, 0.8f, 1f, 1f, 0.5f),
    /** Lower bounds of Joyful, Content, Meh, Sad (anything lower is Distressed). */
    val moodThresholds: List<Float> = listOf(80f, 55f, 35f, 20f),

    val feedDistance: Float = 0.12f,
    val eatSec: Float = 3f,
    val foodGain: Float = 25f,
    val feedCooldownSec: Float = 20f,
    val refuseHungerAbove: Float = 95f,
    /** A food let go away from the pet returns to the tray after this long. */
    val foodReturnSec: Float = 10f,

    val napMaxMin: Float = 30f,
    val napEnergyPerMin: Float = 2f,

    val strokeSocialPerSec: Float = 4f,
    val strokeCapPerMin: Float = 20f,
    val boopSocial: Float = 3f,
    val boopCooldownSec: Float = 2f,
    /** Strokes only count on the top of the head, above this height in model units. */
    val strokeMinModelY: Float = 1.6f,

    val adultHeight: Float = 0.25f,
    val hatchGrowth: Float = 0.7f,
    val growthHours: Float = 72f,

    /** Wander hops: pause range, hop time and height, and how far from the rim targets stay. */
    val wanderPauseMinSec: Float = 3f,
    val wanderPauseMaxSec: Float = 8f,
    val hopSec: Float = 0.45f,
    val hopHeight: Float = 0.04f,
    val rimMargin: Float = 0.08f,
    val walkSpeed: Float = 0.12f,

    val bathSec: Float = 45f,
    val bathGain: Float = 50f,
    val danceSec: Float = 30f,
    val danceFun: Float = 30f,
    val danceEnergyCost: Float = 8f,
    val danceHygieneCost: Float = 5f,
    /** Average head-and-hands speed (m/s) that counts as dancing along. */
    val danceAlongSpeed: Float = 0.3f,
    val danceAlongSocial: Float = 10f,
    val fetchFun: Float = 8f,
    val fetchSocial: Float = 3f,
    val fetchEnergyCost: Float = 3f,
    val ballIdleSec: Float = 60f,
    val crumbIntervalHours: Float = 4f,
    val maxCrumbs: Int = 5,
    val autonomyIdleSec: Float = 20f,
    val autonomyEfficiency: Float = 0.5f,
    val mopeyHours: Float = 8f,
    val homesickHours: Float = 16f,
    val departHours: Float = 24f,
    val welcomeBackMinMinutes: Float = 60f,
)

/** Real-time simulation and catch-up. */
@Serializable
data class SimConfig(
    val simStepSec: Int = 60,
    val maxCatchUpHours: Int = 72,
)

/** Debug-only overrides; release builds ignore them. */
@Serializable
data class DebugConfig(
    /** Multiplies simulated time, e.g. 60 makes a minute pass per second. */
    val timeScale: Float = 1f,
    /** Skip straight to this flow after the pad is placed (e.g. "Pet" until creation exists). */
    val forceFlow: String = "Pet",
)
