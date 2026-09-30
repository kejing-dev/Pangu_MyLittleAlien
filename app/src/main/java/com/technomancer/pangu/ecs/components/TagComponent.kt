package com.technomancer.pangu.ecs.components

import com.pico.spatial.core.ecs.Component
import com.technomancer.pangu.logic.input.TargetId

/** Marks an interactive entity with the id that gesture events report for it (see [TargetId]). */
class TagComponent(val targetId: Int) : Component() {
    override fun clone(): Component = TagComponent(targetId)
}
