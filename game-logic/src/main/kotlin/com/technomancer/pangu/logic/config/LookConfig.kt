package com.technomancer.pangu.logic.config

import kotlinx.serialization.Serializable

/** Creation Studio: colour swatches, antenna shapes, name syllables and the preview's motion. */
@Serializable
data class LookConfig(
    /** White, Chalk pink, Dusk blue, Pistachio, Butter, Lilac, Tomato, Slate. */
    val swatches: List<String> = listOf("#ffffff", "#edb9b1", "#a8bfd4", "#bfd3ae", "#f2de9b", "#c9b7da", "#d9644f", "#6e7680"),
    val defaultBody: Int = 1,
    val defaultTip: Int = 6,
    /** Antenna shape index: ball, star, heart, spiral, flower, bolt. */
    val defaultShape: Int = 2,
    val wheelA: List<String> = listOf("Bo", "Mi", "Zu", "Ki", "Lu", "Pi", "No", "Ta"),
    val wheelB: List<String> = listOf("ba", "ri", "lo", "mu", "ne", "zi", "ko", "pa"),
    val wheelC: List<String> = listOf("", "o", "i", "bo", "n", "ly", "pop", "ling"),
    /** Syllable indices of the default name (Bobabo). */
    val defaultName: List<Int> = listOf(0, 0, 3),
    /** Preview turntable speed, radians per second. */
    val previewTurnSpeed: Float = 0.3f,
    /** Overshoot of the tip's grow-in when the antenna shape changes, and the antenna kick it gives. */
    val morphEaseC: Float = 1.9f,
    val morphSec: Float = 0.35f,
    val morphWobble: Float = 3.2f,
    /** Pedestal disc diameter, metres. */
    val pedestalDiameter: Float = 0.21f,
)
