package com.technomancer.pangu.ecs.entities

import com.pico.spatial.core.ecs.BoundingBox
import com.pico.spatial.core.ecs.ModelEntity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.MeshModel
import com.pico.spatial.core.ecs.resource.MeshResource
import com.pico.spatial.core.ecs.resource.UnlitMaterial
import com.pico.spatial.core.math.Vector3
import com.technomancer.pangu.ecs.rgb
import com.technomancer.pangu.logic.art.MeshData

/**
 * Turns :game-logic mesh arrays into SDK mesh resources and model entities.
 * Unlit materials are shared per colour. Vertex colours are not passed to the SDK: the unlit
 * material's use of them is undocumented, so each part gets a flat material colour instead.
 */
class MeshFactory {
    private val materials = HashMap<Int, UnlitMaterial>()
    private val meshCache = HashMap<String, MeshResource>()

    fun unlit(color: Int): UnlitMaterial =
        materials.getOrPut(color) {
            UnlitMaterial.create().apply {
                setBaseColor(rgb(color))
                setApplyToneMapping(false)
            }
        }

    /** Uploads [data] once per [name]; later calls return the cached resource. */
    @Synchronized
    fun mesh(data: MeshData, name: String): MeshResource = meshCache.getOrPut(name) { upload(data, name) }

    private fun upload(data: MeshData, name: String): MeshResource {
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
        return MeshResource.createWithMeshModel(MeshModel(positions, data.indices.toList(), normals), bounds, name)
    }

    /** A named model entity at [position] in its parent's space. */
    fun model(name: String, data: MeshData, color: Int, position: Vector3 = Vector3.ZERO): ModelEntity =
        ModelEntity(mesh(data, name), unlit(color)).apply {
            setName(name)
            components[TransformComponent::class.java]?.setPosition(position)
        }
}
