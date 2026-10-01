package com.technomancer.pangu.logic.creation

import kotlinx.serialization.Serializable

/**
 * What the player designed: body and tip swatch indices (0–7), antenna shape (0–5) and the three
 * name syllable indices [a], [b], [c].
 */
@Serializable
data class PetLook(
    val body: Int = 1,
    val tip: Int = 6,
    val shape: Int = 2,
    val a: Int = 0,
    val b: Int = 0,
    val c: Int = 3,
)
