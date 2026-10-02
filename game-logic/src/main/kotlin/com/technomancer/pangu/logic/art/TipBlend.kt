package com.technomancer.pangu.logic.art

import com.technomancer.pangu.logic.math.Vec3
import kotlin.math.sqrt

/**
 * Morphs an antenna tip into a round blob and back. Every vertex slides towards the point of a
 * shared sphere in its own direction, so any two tips pass through the same blob: the old tip melts
 * into it and the new one grows out of it, with no swap visible in between.
 *
 * Directions are taken inside the mesh's own bounding box, scaled to a cube, so flat tips (star,
 * heart, bolt) cover the whole sphere instead of only its equator. Tips that wrap around
 * themselves (the curl and the bolt) crease near the blob, so tips are drawn two-sided.
 */
object TipBlend {
    /**
     * [mesh] blended [t] (0 = its own shape, 1 = the sphere at [centre] with [radius]). Normals
     * turn towards the sphere's. Vertex colours, when [toon] is given, are re-shaded from the new
     * normals; otherwise they are kept.
     */
    fun blend(mesh: MeshData, centre: Vec3, radius: Float, t: Float, toon: ToonParams? = null): MeshData {
        val n = mesh.vertexCount
        val p = mesh.positions
        val nr = mesh.normals
        val b = mesh.bounds()
        val cx = (b[0] + b[3]) * 0.5f
        val cy = (b[1] + b[4]) * 0.5f
        val cz = (b[2] + b[5]) * 0.5f
        val ex = ((b[3] - b[0]) * 0.5f).coerceAtLeast(1e-4f)
        val ey = ((b[4] - b[1]) * 0.5f).coerceAtLeast(1e-4f)
        val ez = ((b[5] - b[2]) * 0.5f).coerceAtLeast(1e-4f)
        val k = t.coerceIn(0f, 1f)
        val positions = FloatArray(n * 3)
        val normals = FloatArray(n * 3)
        for (i in 0 until n) {
            var dx = (p[i * 3] - cx) / ex
            var dy = (p[i * 3 + 1] - cy) / ey
            var dz = (p[i * 3 + 2] - cz) / ez
            val len = sqrt(dx * dx + dy * dy + dz * dz)
            if (len < 1e-5f) {
                dx = nr[i * 3]; dy = nr[i * 3 + 1]; dz = nr[i * 3 + 2]
            } else {
                dx /= len; dy /= len; dz /= len
            }
            positions[i * 3] = lerp(p[i * 3], centre.x + dx * radius, k)
            positions[i * 3 + 1] = lerp(p[i * 3 + 1], centre.y + dy * radius, k)
            positions[i * 3 + 2] = lerp(p[i * 3 + 2], centre.z + dz * radius, k)
            var nx = lerp(nr[i * 3], dx, k)
            var ny = lerp(nr[i * 3 + 1], dy, k)
            var nz = lerp(nr[i * 3 + 2], dz, k)
            val nl = sqrt(nx * nx + ny * ny + nz * nz)
            if (nl > 1e-5f) { nx /= nl; ny /= nl; nz /= nl } else { nx = dx; ny = dy; nz = dz }
            normals[i * 3] = nx
            normals[i * 3 + 1] = ny
            normals[i * 3 + 2] = nz
        }
        val colors = if (toon != null && mesh.colors.size == n * 4) {
            FloatArray(n * 4).also { c ->
                for (i in 0 until n) {
                    val rgb = ToonBake.shadeOne(normals[i * 3], normals[i * 3 + 1], normals[i * 3 + 2], toon)
                    c[i * 4] = rgb[0]; c[i * 4 + 1] = rgb[1]; c[i * 4 + 2] = rgb[2]; c[i * 4 + 3] = 1f
                }
            }
        } else {
            mesh.colors
        }
        return MeshData(positions, normals, colors, mesh.indices)
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t
}
