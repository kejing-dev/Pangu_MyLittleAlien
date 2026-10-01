package com.technomancer.pangu.logic.config

import kotlinx.serialization.Serializable

/** Habitat pad placement. Distances in metres. */
@Serializable
data class PlacementConfig(
    /** Horizontal distance from the head to the pad centre along the head's yaw-forward. */
    val padDistance: Float = 0.75f,
    /** How far below the head the pad top sits. */
    val padDrop: Float = 0.55f,
    /** A saved pad further than this from the head is re-placed instead of restored. */
    val restoreMaxDistance: Float = 2.5f,
    val padDiameter: Float = 0.5f,
    /** Uniform scale of the whole habitat (pad, nest, lamp, orb). 1.3 makes the 0.5 m pad 0.65 m wide. Panels keep their size. */
    val habitatScale: Float = 1.3f,
    /** Distance from the pad centre to its handle along the pad's right (+X), in pad units before [habitatScale]. */
    val handleOffset: Float = 0.266f,
    /** A ray drag never pushes the handle further than this from the head, metres. */
    val maxDragDistance: Float = 3f,
    /** Head position used when no head pose arrives in time (Stage space: origin at the feet). */
    val fallbackHeadHeight: Float = 1.0f,
)
