package com.technomancer.pangu.app

import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.resource.MeshResource

/**
 * Release stand-in for the debug performance probe: same members, no measuring and no logging,
 * so release builds carry none of the probe's work.
 */
@Suppress("UNUSED_PARAMETER")
class PerfProbe(enabled: Boolean) {
    var root: Entity? = null
    var lastCatchUpMs = -1.0
    var lastCatchUpHours = 0.0
    var micWindowMicros = -1.0

    fun beginFrame() {}
    fun section(index: Int) {}
    fun endFrame() {}
    inline fun <T> timeCatchUp(hours: Double, block: () -> T): T = block()
    fun meshTriangles(mesh: MeshResource, count: Int) {}
    fun meshUploaded(name: String, vertices: Int, buildMs: Double, uploadMs: Double) {}
    fun milestone(what: String) {}
    fun coldStartDone() {}

    companion object {
        const val TAG = "PanguPerf"
        const val INPUT = 0
        const val LOGIC = 1
        const val PRESENTATION = 2
        const val UI = 3
    }
}
