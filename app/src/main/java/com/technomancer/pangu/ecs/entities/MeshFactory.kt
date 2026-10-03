package com.technomancer.pangu.ecs.entities

import com.pico.spatial.core.ecs.BoundingBox
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Color4
import com.pico.spatial.core.math.Vector3
import com.technomancer.pangu.app.PerfProbe
import java.util.concurrent.Callable
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors
import com.technomancer.pangu.ecs.rgb
import com.technomancer.pangu.logic.art.MeshData

/**
 * Turns :game-logic mesh arrays into SDK mesh resources and model entities.
 * Unlit materials are shared per colour and culling mode. Meshes that carry vertex colours
 * (the baked toon look) pass them to the SDK and use a white material so the colours show.
 *
 * The engine frees a resource once no model uses it, and models swap these cached materials and
 * meshes in and out (tints, the lamp, egg shells), so every cached resource is made global.
 */
class MeshFactory(private val perf: PerfProbe? = null) {
    private val materials = HashMap<Long, UnlitMaterial>()

    /** The Spatial Editor toon material for the alien, when the editor bundle has one. */
    var toon: ToonMaterial? = null
    private val meshCache = ConcurrentHashMap<String, MeshResource>()
    private val uploader = Executors.newFixedThreadPool(UPLOAD_THREADS) { r -> Thread(r, "PanguMeshUpload").apply { isDaemon = true } }

    fun unlit(color: Int, cullFront: Boolean = false, doubleSided: Boolean = false): UnlitMaterial =
        materials.getOrPut(color.toLong() or (if (cullFront) 1L shl 32 else 0L) or (if (doubleSided) 1L shl 33 else 0L)) {
            UnlitMaterial.create().apply {
                setBaseColor(rgb(color))
                setApplyToneMapping(false)
                if (cullFront) setCullingMode(MaterialCullingMode.FRONT)
                if (doubleSided) setCullingMode(MaterialCullingMode.NONE)
                toGlobal()
            }
        }

    /** Uploads [data] once per [name]; later calls return the cached resource. Safe off the main thread. */
    fun mesh(data: MeshData, name: String): MeshResource = meshCache.computeIfAbsent(name) { upload(data, name) }

    /**
     * Uploads many meshes at once and waits for all of them. Each upload waits for the engine's next
     * frame, so uploading them side by side costs about one frame instead of one frame each.
     * Call off the main thread.
     */
    fun preload(items: Collection<Pair<String, MeshData>>) {
        val tasks = items.filter { !meshCache.containsKey(it.first) }.map { (name, data) -> Callable { mesh(data, name) } }
        uploader.invokeAll(tasks).forEach { it.get() }
    }

    private fun upload(data: MeshData, name: String): MeshResource {
        val started = System.nanoTime()
        val (model, bounds) = toModel(data)
        val built = System.nanoTime()
        return MeshResource.createWithMeshModel(model, bounds, name).also {
            it.toGlobal()
            perf?.meshTriangles(it, data.triangleCount)
            perf?.meshUploaded(name, data.vertexCount, (built - started) / 1e6, (System.nanoTime() - built) / 1e6)
        }
    }

    private fun toModel(data: MeshData): Pair<MeshModel, BoundingBox> {
        val n = data.vertexCount
        val positions = ArrayList<Vector3>(n)
        val normals = ArrayList<Vector3>(n)
        for (i in 0 until n) {
            positions += Vector3(data.positions[i * 3], data.positions[i * 3 + 1], data.positions[i * 3 + 2])
            normals += Vector3(data.normals[i * 3], data.normals[i * 3 + 1], data.normals[i * 3 + 2])
        }
        val b = data.bounds()
        val bounds = BoundingBox(
            Vector3((b[0] + b[3]) * 0.5f, (b[1] + b[4]) * 0.5f, (b[2] + b[5]) * 0.5f),
            Vector3((b[3] - b[0]) * 0.5f, (b[4] - b[1]) * 0.5f, (b[5] - b[2]) * 0.5f),
        )
        val model = if (data.colors.size == n * 4) {
            val colors = ArrayList<Color4>(n)
            for (i in 0 until n) colors += Color4(data.colors[i * 4], data.colors[i * 4 + 1], data.colors[i * 4 + 2], data.colors[i * 4 + 3])
            MeshModel(positions, data.indices.toList(), normals, emptyList(), emptyList(), emptyList(), colors)
        } else {
            MeshModel(positions, data.indices.toList(), normals)
        }
        return model to bounds
    }

    /** A named model entity at [position] in its parent's space. */
    fun model(
        name: String,
        data: MeshData,
        color: Int,
        position: Vector3 = Vector3.ZERO,
        cullFront: Boolean = false,
    ): ModelEntity =
        ModelEntity(mesh(data, name), unlit(color, cullFront)).apply {
            setName(name)
            components[TransformComponent::class.java]?.setPosition(position)
        }

    private companion object {
        const val UPLOAD_THREADS = 16
    }
}
