package com.technomancer.pangu.ecs.systems

import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.math.Vector3
import java.util.IdentityHashMap

/**
 * Skips scale writes that would not change anything. Each SDK transform write costs a few
 * allocations, and most props sit hidden (scale zero) frame after frame, so a scale is written
 * only when it is a different object from the last one written to that transform. Shared
 * constants such as [Vector3.ZERO] and [Vector3.ONE] therefore cost nothing after the first frame.
 */
class TransformWrites {
    private val lastScale = IdentityHashMap<TransformComponent, Vector3>()

    fun scale(t: TransformComponent?, s: Vector3) {
        if (t == null || lastScale[t] === s) return
        lastScale[t] = s
        t.setScaleVector(s)
    }
}
