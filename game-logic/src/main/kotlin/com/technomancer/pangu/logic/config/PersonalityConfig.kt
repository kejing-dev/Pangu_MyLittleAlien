package com.technomancer.pangu.logic.config

import kotlinx.serialization.Serializable

/** How the egg's spark value and imprints become a personality, and what each trait changes. */
@Serializable
data class PersonalityConfig(
    /** Natures with all scores closer together than this are neutral. */
    val neutralSpread: Int = 24,
    /** The favourite action of the up axis gains more and its need decays faster; the down axis the reverse. */
    val upGain: Float = 1.25f,
    val upDecay: Float = 1.15f,
    val downGain: Float = 0.8f,
    val downDecay: Float = 0.9f,
    val shimmerThreshold: Int = 16,
    val likedGain: Float = 1.3f,
    val likedFunBonus: Float = 5f,
    val dislikedGain: Float = 0.7f,

    /** Sparkle Hiccups: a small hop every so often while idle (seconds, metres). */
    val hiccupMinSec: Float = 40f,
    val hiccupMaxSec: Float = 90f,
    val hiccupHeight: Float = 0.03f,
    /** Early Riser: night sleep ends at this local hour. */
    val earlyRiserHour: Int = 6,
    /** Hand Follower: turns toward a hand closer than this, metres. */
    val handFollowDistance: Float = 0.5f,
    /** Night Watcher: looks up for [watchSec] every [watchEverySec] between [watchFromHour] and [watchToHour]. */
    val watchSec: Float = 5f,
    val watchEverySec: Float = 120f,
    val watchFromHour: Int = 22,
    val watchToHour: Int = 7,
    /** Nap Lover: dozes off below this Energy instead of 30. */
    val napLoverEnergy: Float = 50f,
    /** Slow Blinker: blink interval and duration multipliers. */
    val slowBlinkInterval: Float = 2f,
    val slowBlinkDuration: Float = 1.5f,
    /** Self-Groomer: Hygiene decay multiplier. */
    val selfGroomerDecay: Float = 0.9f,
    /** Rearranger: crumb interval multiplier. */
    val rearrangerInterval: Float = 1.25f,
)
