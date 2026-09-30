package com.technomancer.pangu.logic.art

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Procedural placeholder meshes. All shapes are centred on the Y axis, +Y up, metres.
 * Colours are 0xRRGGBB and are written to every vertex.
 * Triangles are oriented so their counter-clockwise face agrees with the vertex normals.
 */
object PrimitiveMeshes {

    /** Solid cylinder from y = [bottomY] to bottomY + [height], with both caps. */
    fun cylinder(radius: Float, height: Float, segments: Int = 32, color: Int = WHITE, bottomY: Float = 0f): MeshData {
        val b = Builder(color)
        val top = bottomY + height
        lathe(b, listOf(Ring(radius, bottomY, 1f, 0f), Ring(radius, top, 1f, 0f)), segments)
        cap(b, radius, top, up = true, segments = segments)
        cap(b, radius, bottomY, up = false, segments = segments)
        return b.build()
    }

    /** Flat disc facing +Y at height [y]. */
    fun disc(radius: Float, segments: Int = 32, color: Int = WHITE, y: Float = 0f): MeshData {
        val b = Builder(color)
        cap(b, radius, y, up = true, segments = segments)
        return b.build()
    }

    /** Torus lying in the XZ plane around +Y. */
    fun torus(majorRadius: Float, minorRadius: Float, segments: Int = 48, sides: Int = 16, color: Int = WHITE): MeshData {
        val b = Builder(color)
        for (i in 0..segments) {
            val theta = TAU * i / segments
            val ct = cos(theta); val st = sin(theta)
            for (j in 0..sides) {
                val phi = TAU * j / sides
                val cp = cos(phi); val sp = sin(phi)
                val r = majorRadius + minorRadius * cp
                b.vertex(r * ct, minorRadius * sp, r * st, cp * ct, sp, cp * st)
            }
        }
        grid(b, 0, segments, sides)
        return b.build()
    }

    /** UV sphere centred at y = [centerY]. */
    fun sphere(radius: Float, rings: Int = 16, segments: Int = 32, color: Int = WHITE, centerY: Float = 0f): MeshData {
        val b = Builder(color)
        val profile = (0..rings).map { k ->
            val a = PI.toFloat() * k / rings - HALF_PI // −π/2 .. π/2
            Ring(radius * cos(a), centerY + radius * sin(a), cos(a), sin(a))
        }
        lathe(b, profile, segments)
        return b.build()
    }

    /** Upper half of a sphere scaled to [height], open at the bottom, base at y = 0. */
    fun halfDome(radius: Float, height: Float, rings: Int = 12, segments: Int = 32, color: Int = WHITE): MeshData {
        val b = Builder(color)
        val profile = (0..rings).map { k ->
            val a = HALF_PI * k / rings
            // Normal of an ellipsoid with semi-axes (radius, height): (cos a / radius, sin a / height).
            val nr = cos(a) / radius; val ny = sin(a) / height
            val len = sqrt(nr * nr + ny * ny)
            Ring(radius * cos(a), height * sin(a), nr / len, ny / len)
        }
        lathe(b, profile, segments)
        return b.build()
    }

    /** Capsule along +Y, total height [height] (including the caps), base at y = 0. */
    fun capsule(radius: Float, height: Float, rings: Int = 8, segments: Int = 32, color: Int = WHITE): MeshData {
        require(height >= 2f * radius) { "capsule height must be at least 2 × radius" }
        val b = Builder(color)
        val profile = ArrayList<Ring>()
        for (k in 0..rings) { // bottom hemisphere, south pole upward
            val a = -HALF_PI + HALF_PI * k / rings
            profile += Ring(radius * cos(a), radius + radius * sin(a), cos(a), sin(a))
        }
        for (k in 0..rings) { // top hemisphere
            val a = HALF_PI * k / rings
            profile += Ring(radius * cos(a), height - radius + radius * sin(a), cos(a), sin(a))
        }
        lathe(b, profile, segments)
        return b.build()
    }

    /** Cone or frustum from [bottomRadius] at y = 0 to [topRadius] at y = [height], with caps where the radius is > 0. */
    fun cone(bottomRadius: Float, topRadius: Float, height: Float, segments: Int = 32, color: Int = WHITE): MeshData {
        val b = Builder(color)
        val dr = bottomRadius - topRadius
        val len = sqrt(dr * dr + height * height)
        val nr = height / len; val ny = dr / len
        lathe(b, listOf(Ring(bottomRadius, 0f, nr, ny), Ring(topRadius, height, nr, ny)), segments)
        if (topRadius > 0f) cap(b, topRadius, height, up = true, segments = segments)
        if (bottomRadius > 0f) cap(b, bottomRadius, 0f, up = false, segments = segments)
        return b.build()
    }

    /** Axis-aligned box centred on the origin. */
    fun box(sizeX: Float, sizeY: Float, sizeZ: Float, color: Int = WHITE): MeshData {
        val b = Builder(color)
        val hx = sizeX / 2; val hy = sizeY / 2; val hz = sizeZ / 2
        // Each face: normal, then two tangent axes spanning it.
        val faces = arrayOf(
            floatArrayOf(1f, 0f, 0f), floatArrayOf(-1f, 0f, 0f),
            floatArrayOf(0f, 1f, 0f), floatArrayOf(0f, -1f, 0f),
            floatArrayOf(0f, 0f, 1f), floatArrayOf(0f, 0f, -1f),
        )
        val half = floatArrayOf(hx, hy, hz)
        for (n in faces) {
            val axis = n.indexOfFirst { it != 0f }
            val u = (axis + 1) % 3; val v = (axis + 2) % 3
            val base = b.count
            for ((su, sv) in arrayOf(-1f to -1f, 1f to -1f, 1f to 1f, -1f to 1f)) {
                val p = FloatArray(3)
                p[axis] = n[axis] * half[axis]; p[u] = su * half[u]; p[v] = sv * half[v]
                b.vertex(p[0], p[1], p[2], n[0], n[1], n[2])
            }
            b.quad(base, base + 1, base + 2, base + 3)
        }
        return b.build()
    }

    /** Regular octahedron with flat faces; [radius] is the centre-to-tip distance, centred on the origin. */
    fun octahedron(radius: Float, color: Int = WHITE): MeshData {
        val b = Builder(color)
        val tips = arrayOf(
            floatArrayOf(radius, 0f, 0f), floatArrayOf(0f, 0f, radius),
            floatArrayOf(-radius, 0f, 0f), floatArrayOf(0f, 0f, -radius),
        )
        for (y in floatArrayOf(radius, -radius)) {
            for (i in 0..3) {
                val a = tips[i]; val c = tips[(i + 1) % 4]
                val nx = (a[0] + c[0]) / 3f; val ny = y / 3f; val nz = (a[2] + c[2]) / 3f
                val nl = sqrt(nx * nx + ny * ny + nz * nz)
                val base = b.count
                b.vertex(0f, y, 0f, nx / nl, ny / nl, nz / nl)
                b.vertex(a[0], a[1], a[2], nx / nl, ny / nl, nz / nl)
                b.vertex(c[0], c[1], c[2], nx / nl, ny / nl, nz / nl)
                b.triangle(base, base + 1, base + 2)
            }
        }
        return b.build()
    }

    /** Quad in the XY plane facing +Z, centred on the origin. */
    fun quad(width: Float, height: Float, color: Int = WHITE): MeshData {
        val b = Builder(color)
        val hw = width / 2; val hh = height / 2
        b.vertex(-hw, -hh, 0f, 0f, 0f, 1f)
        b.vertex(hw, -hh, 0f, 0f, 0f, 1f)
        b.vertex(hw, hh, 0f, 0f, 0f, 1f)
        b.vertex(-hw, hh, 0f, 0f, 0f, 1f)
        b.quad(0, 1, 2, 3)
        return b.build()
    }

    const val WHITE = 0xFFFFFF
    private const val TAU = (2 * PI).toFloat()
    private const val HALF_PI = (PI / 2).toFloat()

    /** One ring of a surface of revolution: radius, height and the (radial, vertical) normal. */
    private data class Ring(val r: Float, val y: Float, val nr: Float, val ny: Float)

    private fun lathe(b: Builder, profile: List<Ring>, segments: Int) {
        val base = b.count
        for (ring in profile) {
            for (i in 0..segments) {
                val t = TAU * i / segments
                val c = cos(t); val s = sin(t)
                b.vertex(ring.r * c, ring.y, ring.r * s, ring.nr * c, ring.ny, ring.nr * s)
            }
        }
        grid(b, base, profile.size - 1, segments)
    }

    private fun cap(b: Builder, radius: Float, y: Float, up: Boolean, segments: Int) {
        val ny = if (up) 1f else -1f
        val centre = b.count
        b.vertex(0f, y, 0f, 0f, ny, 0f)
        for (i in 0..segments) {
            val t = TAU * i / segments
            b.vertex(radius * cos(t), y, radius * sin(t), 0f, ny, 0f)
        }
        for (i in 0 until segments) b.triangle(centre, centre + 1 + i, centre + 2 + i)
    }

    /** Quads between consecutive rows of (cols + 1) vertices starting at [base]. */
    private fun grid(b: Builder, base: Int, rows: Int, cols: Int) {
        val stride = cols + 1
        for (r in 0 until rows) {
            for (c in 0 until cols) {
                val a = base + r * stride + c
                b.quad(a, a + 1, a + 1 + stride, a + stride)
            }
        }
    }

    private class Builder(color: Int) {
        private val red = ((color shr 16) and 0xFF) / 255f
        private val green = ((color shr 8) and 0xFF) / 255f
        private val blue = (color and 0xFF) / 255f
        private val pos = ArrayList<Float>()
        private val nrm = ArrayList<Float>()
        private val idx = ArrayList<Int>()
        val count: Int get() = pos.size / 3

        fun vertex(x: Float, y: Float, z: Float, nx: Float, ny: Float, nz: Float) {
            pos += x; pos += y; pos += z
            nrm += nx; nrm += ny; nrm += nz
        }

        fun quad(a: Int, b: Int, c: Int, d: Int) {
            triangle(a, b, c)
            triangle(a, c, d)
        }

        /** Adds a triangle wound so its face normal agrees with the average vertex normal. Degenerate triangles are dropped. */
        fun triangle(a: Int, b: Int, c: Int) {
            val ex = pos[b * 3] - pos[a * 3]; val ey = pos[b * 3 + 1] - pos[a * 3 + 1]; val ez = pos[b * 3 + 2] - pos[a * 3 + 2]
            val fx = pos[c * 3] - pos[a * 3]; val fy = pos[c * 3 + 1] - pos[a * 3 + 1]; val fz = pos[c * 3 + 2] - pos[a * 3 + 2]
            val cx = ey * fz - ez * fy; val cy = ez * fx - ex * fz; val cz = ex * fy - ey * fx
            if (cx * cx + cy * cy + cz * cz < 1e-18f) return
            val nx = nrm[a * 3] + nrm[b * 3] + nrm[c * 3]
            val ny = nrm[a * 3 + 1] + nrm[b * 3 + 1] + nrm[c * 3 + 1]
            val nz = nrm[a * 3 + 2] + nrm[b * 3 + 2] + nrm[c * 3 + 2]
            if (cx * nx + cy * ny + cz * nz >= 0f) {
                idx += a; idx += b; idx += c
            } else {
                idx += a; idx += c; idx += b
            }
        }

        fun build(): MeshData {
            val n = count
            val colors = FloatArray(n * 4)
            for (i in 0 until n) {
                colors[i * 4] = red; colors[i * 4 + 1] = green; colors[i * 4 + 2] = blue; colors[i * 4 + 3] = 1f
            }
            return MeshData(pos.toFloatArray(), nrm.toFloatArray(), colors, idx.toIntArray())
        }
    }
}
