package com.technomancer.pangu.app

import android.os.Debug
import android.os.Process
import android.os.SystemClock
import android.util.Log
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.Material
import com.pico.spatial.core.ecs.resource.MeshResource
import java.util.IdentityHashMap
import java.util.concurrent.ConcurrentHashMap

/**
 * Debug-only performance probe. Every 5 s it logs (tag PanguPerf) the frame System's update time
 * (average and worst), objects allocated per update, the triangles and materials of everything
 * currently shown, and the last catch-up and microphone timings. Cold start is logged once, from
 * process start to the habitat being placed. Does nothing in release builds.
 */
class PerfProbe(private val enabled: Boolean) {
    /** Triangle count per uploaded mesh, filled by the mesh factory. */
    val triangles = ConcurrentHashMap<MeshResource, Int>()

    /** The scene root to measure; set when the Stage is built. */
    var root: Entity? = null

    private var frames = 0
    private var totalNanos = 0L
    private var worstNanos = 0L
    private var allocs = 0L
    private var frameStart = 0L
    private var allocStart = 0
    private var sectionStart = 0
    private val sectionAllocs = LongArray(SECTIONS.size)
    private var nextReportNanos = 0L
    private var coldStartLogged = false

    @Volatile var lastCatchUpMs = -1.0
    @Volatile var lastCatchUpHours = 0.0
    @Volatile var micWindowMicros = -1.0

    init {
        if (enabled) Debug.startAllocCounting()
    }

    fun beginFrame() {
        if (!enabled) return
        allocStart = Debug.getThreadAllocCount()
        sectionStart = allocStart
        frameStart = System.nanoTime()
    }

    /** Ends a section of the frame ([INPUT], [LOGIC], …) and charges its allocations to it. */
    fun section(index: Int) {
        if (!enabled) return
        val now = Debug.getThreadAllocCount()
        sectionAllocs[index] += now - sectionStart
        sectionStart = now
    }

    fun endFrame() {
        if (!enabled) return
        val t = System.nanoTime() - frameStart
        allocs += Debug.getThreadAllocCount() - allocStart
        frames++
        totalNanos += t
        if (t > worstNanos) worstNanos = t
        val now = System.nanoTime()
        if (now >= nextReportNanos) {
            nextReportNanos = now + REPORT_NANOS
            report()
        }
    }

    /** Times a catch-up of [hours] simulated hours. */
    inline fun <T> timeCatchUp(hours: Double, block: () -> T): T {
        val start = System.nanoTime()
        val result = block()
        lastCatchUpMs = (System.nanoTime() - start) / 1e6
        lastCatchUpHours = hours
        return result
    }

    /** Logs a mesh upload that took a while: [buildMs] to copy the arrays, [uploadMs] in the SDK. */
    fun meshUploaded(name: String, vertices: Int, buildMs: Double, uploadMs: Double) {
        if (!enabled || buildMs + uploadMs < SLOW_UPLOAD_MS) return
        Log.i(TAG, "mesh %s: %d vertices, build %.0f ms, upload %.0f ms".format(name, vertices, buildMs, uploadMs))
    }

    /** Logs [what] with the time since the process started (start-up milestones). */
    fun milestone(what: String) {
        if (!enabled) return
        Log.i(TAG, "${SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()} ms: $what")
    }

    /** Called once the habitat is placed: logs the time since the process started. */
    fun coldStartDone() {
        if (!enabled || coldStartLogged) return
        coldStartLogged = true
        val ms = SystemClock.elapsedRealtime() - Process.getStartElapsedRealtime()
        Log.i(TAG, "cold start ${ms} ms (process start to habitat placed)")
    }

    private fun report() {
        if (frames == 0) return
        val (tris, mats) = countScene()
        val split = SECTIONS.indices.joinToString(" ") { "${SECTIONS[it]} %.0f".format(sectionAllocs[it].toDouble() / frames) }
        sectionAllocs.fill(0L)
        Log.i(TAG, "allocs/update by part: $split")
        Log.i(
            TAG,
            "update avg %.2f ms max %.2f ms, allocs/update %.1f, triangles %d, materials %d, catch-up %.1f ms for %.0f h, mic %.0f µs/window"
                .format(totalNanos / frames / 1e6, worstNanos / 1e6, allocs.toDouble() / frames, tris, mats, lastCatchUpMs, lastCatchUpHours, micWindowMicros),
        )
        frames = 0
        totalNanos = 0L
        worstNanos = 0L
        allocs = 0L
    }

    /** Triangles and distinct materials of every model whose own scale and every ancestor's is non-zero. */
    private fun countScene(): Pair<Int, Int> {
        val r = root ?: return 0 to 0
        var tris = 0
        val materials = IdentityHashMap<Material, Boolean>()
        fun visit(e: Entity) {
            val s = e.components[TransformComponent::class.java]?.scaleVector
            if (s != null && (s.x == 0f || s.y == 0f || s.z == 0f)) return
            e.components[ModelComponent::class.java]?.let { m ->
                m.mesh?.let { mesh -> tris += triangles[mesh] ?: 0 }
                for (i in 0 until m.materials.size) runCatching { m.materials[i] }.getOrNull()?.let { materials[it] = true }
            }
            for (c in e.getChildren()) visit(c)
        }
        visit(r)
        return tris to materials.size
    }

    companion object {
        const val TAG = "PanguPerf"
        private const val REPORT_NANOS = 5_000_000_000L
        private const val SLOW_UPLOAD_MS = 200.0
        const val INPUT = 0
        const val LOGIC = 1
        const val PRESENTATION = 2
        const val UI = 3
        private val SECTIONS = arrayOf("input", "logic", "presentation", "ui")
    }
}
