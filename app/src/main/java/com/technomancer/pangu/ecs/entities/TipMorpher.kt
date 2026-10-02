package com.technomancer.pangu.ecs.entities

import android.util.Log
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.math.Vector3
import com.technomancer.pangu.logic.art.MeshData
import com.technomancer.pangu.logic.art.TipBlend
import com.technomancer.pangu.logic.math.Vec3
import kotlin.concurrent.thread
import kotlin.math.roundToInt

/**
 * Shows the antenna tip morphing between shapes. Every tip's in-between shapes, from itself to the
 * shared round blob, are built and uploaded once in the background (an upload takes a few hundred
 * milliseconds, longer than the morph itself). While a tip morphs, [update] only switches its model
 * to the step nearest the blob amount, which costs nothing and never lags behind.
 *
 * The tip's ink outline is hidden while it morphs: an inverted hull turns inside out wherever the
 * melting surface creases and shows as black patches.
 *
 * [update] runs on the main thread every frame.
 */
class TipMorpher(
    private val rig: AlienRig,
    sources: List<Pair<MeshData?, MeshData?>>,
    meshes: MeshFactory,
) {
    private val count = sources.size

    /** Per tip, its in-between meshes for blob amounts 1/STEPS .. 1; set once they are all uploaded. */
    @Volatile private var steps: List<List<MeshResource>?>? = null

    // The engine releases a mesh once no model uses it, so every mesh swapped in and out here is
    // made global to keep it alive.
    private val tipOriginal = rig.tipModels.map { it.mesh()?.apply { toGlobal() } }
    private val outlineTransforms = rig.tipOutlines.map { it?.components?.get(TransformComponent::class.java) }
    private val outlineHidden = BooleanArray(count)

    init {
        thread(name = "PanguTipMorph", isDaemon = true) {
            try {
                val items = ArrayList<Pair<String, MeshData>>()
                sources.forEachIndexed { i, (tip, _) ->
                    if (tip != null) for (s in 1..STEPS) items += key(i, s) to TipBlend.blend(tip, CENTRE, RADIUS, s.toFloat() / STEPS, AlienFactory.TIP_TOON)
                }
                meshes.preload(items)
                val byKey = items.associate { (k, d) -> k to meshes.mesh(d, k).apply { toGlobal() } }
                steps = List(count) { i -> (1..STEPS).map { s -> byKey[key(i, s)] ?: return@List null } }
            } catch (e: Exception) {
                Log.w("Pangu", "Antenna morph meshes failed; tips will switch without morphing: ${e.message}")
            }
        }
    }

    /** [shape] is the tip on show while morphing (-1 when not) at [blob] (0..1). */
    fun update(shape: Int, blob: Float) {
        val ready = steps
        for (i in 0 until count) {
            val step = if (i == shape && ready != null) (blob * STEPS).roundToInt().coerceIn(0, STEPS) else 0
            val mesh = if (step == 0) tipOriginal.getOrNull(i) else ready?.getOrNull(i)?.getOrNull(step - 1)
            rig.tipModels.getOrNull(i)?.setMesh(mesh ?: tipOriginal.getOrNull(i))
            val hide = i == shape
            if (hide != outlineHidden[i]) {
                outlineHidden[i] = hide
                outlineTransforms.getOrNull(i)?.setScaleVector(if (hide) Vector3.ZERO else Vector3.ONE)
            }
        }
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

        /** In-between shapes per tip on the way to the blob. */
        const val STEPS = 8

        private fun key(tip: Int, step: Int) = "PH_TipMorph${tip}_$step"
    }
}
