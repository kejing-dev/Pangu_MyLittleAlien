package com.technomancer.pangu.logic.art

/**
 * Inverted-hull outline: every vertex pushed out along its normal by [width], with each triangle's
 * winding reversed, so its outer side faces away. Drawn in ink with normal back-face culling,
 * only the rim around the part shows.
 */
object OutlineHull {
    fun build(mesh: MeshData, width: Float): MeshData {
        val p = mesh.positions
        val n = mesh.normals
        val positions = FloatArray(p.size) { p[it] + n[it] * width }
        val indices = IntArray(mesh.indices.size)
        for (t in 0 until mesh.triangleCount) {
            indices[t * 3] = mesh.indices[t * 3]
            indices[t * 3 + 1] = mesh.indices[t * 3 + 2]
            indices[t * 3 + 2] = mesh.indices[t * 3 + 1]
        }
        return MeshData(positions, n.copyOf(), FloatArray(0), indices)
    }
}
