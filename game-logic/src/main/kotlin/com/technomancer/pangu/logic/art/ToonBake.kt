package com.technomancer.pangu.logic.art

import kotlin.math.max
import kotlin.math.sqrt

/** Toon shading settings for one part. Colours are linear 0..1 RGB. */
data class ToonParams(
    val base: FloatArray,
    val shade: FloatArray,
    val gloss: Float,
    val rim: Float = 0.25f,
)

/**
 * Bakes a three-band toon look into vertex colours, seen from the front (+Z), lit from the
 * upper left front. Used while no shader-graph toon material is available.
 */
object ToonBake {
    private val LIGHT = normalize(-0.55f, 0.75f, 0.9f)
    private val VIEW = floatArrayOf(0f, 0f, 1f)

    fun bake(mesh: MeshData, p: ToonParams): MeshData {
        val n = mesh.vertexCount
        val colors = FloatArray(n * 4)
        for (i in 0 until n) {
            val nx = mesh.normals[i * 3]
            val ny = mesh.normals[i * 3 + 1]
            val nz = mesh.normals[i * 3 + 2]
            val c = shadeOne(nx, ny, nz, p)
            colors[i * 4] = c[0]
            colors[i * 4 + 1] = c[1]
            colors[i * 4 + 2] = c[2]
            colors[i * 4 + 3] = 1f
        }
        return MeshData(mesh.positions, mesh.normals, colors, mesh.indices)
    }

    /** Colour for one normal. The specular term is left out in the baked version. */
    fun shadeOne(nx: Float, ny: Float, nz: Float, p: ToonParams): FloatArray {
        val ndl = nx * LIGHT[0] + ny * LIGHT[1] + nz * LIGHT[2]
        val ndv = nx * VIEW[0] + ny * VIEW[1] + nz * VIEW[2]
        val lit = smoothstep(-0.05f, 0.08f, ndl)
        val deep = smoothstep(-0.55f, -0.35f, ndl)
        val rimTerm = smoothstep(0.62f, 0.9f, 1f - max(ndv, 0f)) * p.rim * lit
        return FloatArray(3) { k ->
            val shaded = p.base[k] * p.shade[k]
            val dark = lerp(shaded * 0.86f, shaded, deep)
            (lerp(dark, p.base[k], lit) + rimTerm).coerceIn(0f, 1f)
        }
    }

    fun smoothstep(e0: Float, e1: Float, x: Float): Float {
        val t = ((x - e0) / (e1 - e0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    private fun normalize(x: Float, y: Float, z: Float): FloatArray {
        val l = sqrt(x * x + y * y + z * z)
        return floatArrayOf(x / l, y / l, z / l)
    }

    /** 0xRRGGBB → linear-ish 0..1 floats (no gamma conversion; colours are used as authored). */
    fun rgb(color: Int) = floatArrayOf(((color shr 16) and 0xFF) / 255f, ((color shr 8) and 0xFF) / 255f, (color and 0xFF) / 255f)
}
