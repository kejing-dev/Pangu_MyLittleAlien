package com.technomancer.pangu.logic.config

import kotlinx.serialization.Serializable

/** Egg warmth, care props, daily caps, hatching and neglect. Rates are per hour unless the name says otherwise. */
@Serializable
data class IncubationConfig(
    /** Size of the egg on the pad, as a multiple of its 11 cm model. */
    val eggScale: Float = 1.33f,
    val startWarmth: Float = 80f,
    val sealSec: Float = 3f,
    val warmthDecayPerHour: Float = 6f,
    val blanketDecayMultiplier: Float = 0.5f,
    val toastyThreshold: Float = 70f,
    val toastyProgressMultiplier: Float = 1.25f,
    val cozyThreshold: Float = 40f,
    val chillyThreshold: Float = 15f,

    val lampDurationHours: Float = 10f,
    val lampWarmthPerHour: Float = 10f,
    /** Horizontal distance from the egg within which a released blanket tucks in, metres. */
    val blanketSnapDistance: Float = 0.08f,
    val blanketDurationHours: Float = 12f,
    val blanketDailyCap: Int = 2,
    val handWarmPerSecond: Float = 1.5f,
    val handWarmCompleteSec: Float = 30f,

    val dailyCompletionCap: Int = 3,
    val actionCooldownMin: Float = 10f,

    val hatchWarmHours: Float = 72f,
    val readyWobbleSec: Float = 5f,
    val hatchTaps: Int = 5,
    val hatchSec: Float = 8f,
    val fadingHours: Float = 24f,
    val dormantHours: Float = 48f,

    /** A released egg snaps into the nest within this distance, otherwise floats back over [floatBackSec]. */
    val eggSnapDistance: Float = 0.10f,
    val floatBackSec: Float = 0.6f,
    /** Heartbeat interval while the egg is held or listened to. */
    val heartbeatIntervalSec: Float = 0.9f,

    /** Sing: seconds of voice at or above the level threshold, with the head this close to the egg (metres). */
    val singRequiredSec: Float = 10f,
    val micRmsThreshold: Float = 0.02f,
    val singMaxDistance: Float = 0.8f,
    /** Music box: turns of the crank that start the lullaby, and how long it plays. */
    val crankTurnsRequired: Float = 2f,
    val musicBoxSec: Float = 10f,
    /** Listen: head within this distance of the egg, slower than these speeds, for this long. */
    val listenMaxDistance: Float = 0.30f,
    val listenMaxSpeed: Float = 0.05f,
    val listenMaxAngularSpeed: Float = 15f,
    val listenRequiredSec: Float = 15f,
    /** Polish: a fingertip within this distance of the shell, moving at least this fast, for this many circles. */
    val rubMaxDistance: Float = 0.02f,
    val rubMinSpeed: Float = 0.10f,
    val polishCircles: Int = 8,

    /** Imprint points per completion for Zest, Cuddle, Wonder, Calm, Neat, keyed by action. */
    val imprintTable: Map<String, List<Int>> = mapOf(
        "Warm" to listOf(0, 12, 0, 0, 0),
        "Sing" to listOf(12, 0, 0, 0, 0),
        "Listen" to listOf(0, 0, 12, 0, 0),
        "Polish" to listOf(0, 0, 0, 0, 12),
        "Blanket" to listOf(0, 0, 0, 8, 0),
    ),
)
