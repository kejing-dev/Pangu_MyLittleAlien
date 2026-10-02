package com.technomancer.pangu.ecs.entities

import android.util.Log
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.resource.MeshResource
import com.technomancer.pangu.logic.art.MeshData
import com.technomancer.pangu.logic.art.TipBlend
import com.technomancer.pangu.logic.math.Vec3
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicIntegerArray

/**
 * Shows the antenna tip morphing between shapes. While a tip morphs, it and its outline show a
 * mesh of their own that a worker thread rewrites for the latest blob amount (each rewrite waits
 * for an engine frame, so it stays off the frame System). The worker also readies the next tip at
 * full blob, so it can take over inside the blob without a jump. Once the morph ends the tips go
 * back to their usual meshes.
 *
 * [update] runs on the main thread every frame.
 */
class TipMorpher(
    private val rig: AlienRig,
    private val sources: List<Pair<MeshData?, MeshData?>>,
    private val meshes: MeshFactory,
) {
    private val count = sources.size
    private val worker = Executors.newSingleThreadExecutor { r -> Thread(r, "PanguTipMorph").apply { isDaemon = true } }
    private val running = AtomicBoolean(false)

    @Volatile private var wantShape = -1
    @Volatile private var wantBlob = 0f
    @Volatile private var nextShape = -1

    // Written by the worker: each tip's morph meshes and the blob amount they hold (as float bits, -1 = none).
    private val tipMesh = arrayOfNulls<MeshResource>(count)
    private val outlineMesh = arrayOfNulls<MeshResource>(count)
    private val applied = AtomicIntegerArray(count).also { a -> for (i in 0 until count) a.set(i, NONE) }

    // Main thread: the usual meshes, captured once. The engine releases a mesh once no model uses
    // it, so every mesh that is swapped in and out here is made global to keep it alive.
    private val tipOriginal = rig.tipModels.map { it.mesh()?.apply { toGlobal() } }
    private val outlineOriginal = rig.tipOutlines.map { it?.mesh()?.apply { toGlobal() } }

    /**
     * [shape] is the tip on show while morphing (-1 when not) at [blob] (0..1); [next] is the tip
     * it turns into.
     */
    fun update(shape: Int, blob: Float, next: Int) {
        if (shape >= 0) {
            wantShape = shape
            wantBlob = blob
            nextShape = next
            if (running.compareAndSet(false, true)) worker.execute(::work)
        }
        for (i in 0 until count) {
            val ready = i == shape && Float.fromBits(applied.get(i)) >= 0f && tipMesh[i] != null
            rig.tipModels.getOrNull(i)?.setMesh(if (ready) tipMesh[i] else tipOriginal.getOrNull(i))
            rig.tipOutlines.getOrNull(i)?.setMesh(if (ready && outlineMesh[i] != null) outlineMesh[i] else outlineOriginal.getOrNull(i))
        }
    }

    private fun work() {
        try {
            while (true) {
                val s = wantShape
                val b = wantBlob
                val next = nextShape
                when {
                    s >= 0 && differs(s, b) -> build(s, b)
                    // Ready the tip that will take over, already melted into the blob.
                    next >= 0 && next != s && differs(next, 1f) -> build(next, 1f)
                    else -> break
                }
            }
        } catch (e: Exception) {
            Log.w("Pangu", "Antenna morph mesh failed: ${e.message}")
        } finally {
            running.set(false)
        }
    }

    private fun differs(i: Int, blob: Float): Boolean {
        val a = Float.fromBits(applied.get(i))
        return a < 0f || kotlin.math.abs(a - blob) > STEP
    }

    private fun build(i: Int, blob: Float) {
        val (tip, outline) = sources.getOrNull(i) ?: return
        if (tip == null) return
        val tipData = TipBlend.blend(tip, CENTRE, RADIUS, blob, AlienFactory.TIP_TOON)
        tipMesh[i]?.let { meshes.replace(it, tipData) } ?: run { tipMesh[i] = meshes.createDynamic(tipData, "PH_TipMorph$i").apply { toGlobal() } }
        if (outline != null) {
            val outlineData = TipBlend.blend(outline, CENTRE, RADIUS + AlienFactory.TIP_OUTLINE, blob)
            outlineMesh[i]?.let { meshes.replace(it, outlineData) } ?: run { outlineMesh[i] = meshes.createDynamic(outlineData, "PH_TipMorphOutline$i").apply { toGlobal() } }
        }
        applied.set(i, blob.toRawBits())
    }

    private fun Entity.mesh(): MeshResource? = components[ModelComponent::class.java]?.mesh

    private fun Entity.setMesh(mesh: MeshResource?) {
        val model = components[ModelComponent::class.java] ?: return
        if (mesh != null && model.mesh !== mesh) model.mesh = mesh
    }

    companion object {
        /** The shared blob: the round "bobble" tip's centre and radius, in the alien model's space. */
        private val CENTRE = Vec3(0.12f, 2.88f, 0.02f)
        private const val RADIUS = 0.12f

        /** Smallest blob change worth a new mesh. */
        private const val STEP = 0.02f
        private val NONE = (-1f).toRawBits()
    }
}
