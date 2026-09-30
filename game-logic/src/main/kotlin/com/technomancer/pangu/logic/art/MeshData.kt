package com.technomancer.pangu.logic.art

/**
 * Plain mesh arrays, independent of any engine type.
 * positions and normals are xyz triples, colors are rgba quads (0..1, linear to the caller),
 * indices are triangle lists with counter-clockwise front faces.
 */
class MeshData(
    val positions: FloatArray,
    val normals: FloatArray,
    val colors: FloatArray,
    val indices: IntArray,
) {
    val vertexCount: Int get() = positions.size / 3
    val triangleCount: Int get() = indices.size / 3

    /** Axis-aligned bounds as [minX, minY, minZ, maxX, maxY, maxZ]. */
    fun bounds(): FloatArray {
        val b = floatArrayOf(
            Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY, Float.POSITIVE_INFINITY,
            Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY, Float.NEGATIVE_INFINITY,
        )
        for (i in 0 until vertexCount) {
            for (a in 0..2) {
                val v = positions[i * 3 + a]
                if (v < b[a]) b[a] = v
                if (v > b[a + 3]) b[a + 3] = v
            }
        }
        return b
    }
}
