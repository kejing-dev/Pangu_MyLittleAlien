package com.technomancer.pangu.logic.art

import com.technomancer.pangu.logic.math.Vec3
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A point on the egg's surface with its outward normal. */
data class SurfacePoint(val position: Vec3, val normal: Vec3)

/** One flat mark on the shell (a speckle or frost dot): where it sits, its size in metres and its turn in radians. */
data class ShellMark(val at: SurfacePoint, val width: Float, val height: Float, val turn: Float)

/** The egg split along its zigzag seam: the [top] cap lifts off when it hatches. */
class EggHalves(val top: MeshData, val bottom: MeshData)

/**
 * The egg as a lathe: height 0.11 m, widest radius 0.041 m (see [radius]) for y from 0
 * (bottom) to 1 (top), 32 segments around and 24 rows up. It is split into a top and a bottom
 * mesh along a zigzag seam 44 % of the way down from the top. The origin is the bottom centre;
 * +Z is the front the player sees.
 *
 * Colours are baked into the vertices: a highlight up and to the left, the body colour, and a
 * darker rim, like the mockup's radial gradient seen from the front.
 */
object EggMesh {
    const val HEIGHT = 0.11f
    const val SEGMENTS = 32
    const val ROWS = 24

    /** Seam height as a fraction of the egg's height from the bottom, and the zigzag's half-height. */
    const val SEAM = 0.56f
    const val SEAM_AMPLITUDE = 0.05f
    private const val BOTTOM_ROWS = 13
    private const val TOP_ROWS = ROWS - BOTTOM_ROWS

    /**
     * Profile radius at height fraction [y]: two half-ellipses joined at the widest point, 37 % of
     * the way up, like the mockup's egg (border-radius 50% / 63% 63% 37% 37%). The bottom is round
     * and blunt, the top taller and narrower, and neither end comes to a point.
     */
    fun radius(y: Float): Float {
        val t = y.coerceIn(0f, 1f)
        val d = if (t < WIDEST) (WIDEST - t) / WIDEST else (t - WIDEST) / (1f - WIDEST)
        return MAX_RADIUS * sqrt((1f - d * d).coerceAtLeast(0f))
    }

    private const val MAX_RADIUS = 0.041f
    private const val WIDEST = 0.37f

    /** Widest radius of the egg. */
    val maxRadius: Float by lazy { (0..200).maxOf { radius(it / 200f) } }

    /** Seam height (fraction) at column [column]: a zigzag with a tooth every four columns. */
    fun seamAt(column: Int): Float {
        val phase = Math.floorMod(column, 4)
        val tri = when (phase) {
            0 -> -1f
            2 -> 1f
            else -> 0f
        }
        return SEAM + SEAM_AMPLITUDE * tri
    }

    /**
     * Builds both halves coloured for a shell of [body] (0xRRGGBB). Deterministic: the same colour
     * always gives the same arrays.
     */
    fun build(body: Int): EggHalves {
        val colors = GradientColors.of(body)
        val bottom = half(colors, top = false)
        val top = half(colors, top = true)
        return EggHalves(top, bottom)
    }

    private fun half(colors: GradientColors, top: Boolean): MeshData {
        val rows = if (top) TOP_ROWS else BOTTOM_ROWS
        val n = (rows + 1) * SEGMENTS
        val positions = FloatArray(n * 3)
        val normals = FloatArray(n * 3)
        val vertexColors = FloatArray(n * 4)
        var v = 0
        for (r in 0..rows) {
            val f = r.toFloat() / rows
            for (j in 0 until SEGMENTS) {
                val seam = seamAt(j)
                val y = if (top) seam + (1f - seam) * f else seam * f
                val a = 2f * PI.toFloat() * j / SEGMENTS
                val p = pointAt(y, sin(a), cos(a))
                positions[v * 3] = p.position.x
                positions[v * 3 + 1] = p.position.y
                positions[v * 3 + 2] = p.position.z
                normals[v * 3] = p.normal.x
                normals[v * 3 + 1] = p.normal.y
                normals[v * 3 + 2] = p.normal.z
                colors.write(vertexColors, v, p.position.x / (2f * maxRadius) + 0.5f, 1f - y)
                v++
            }
        }
        val indices = IntArray(rows * SEGMENTS * 6)
        var k = 0
        for (r in 0 until rows) {
            for (j in 0 until SEGMENTS) {
                val a = r * SEGMENTS + j
                val b = r * SEGMENTS + (j + 1) % SEGMENTS
                val c = b + SEGMENTS
                val d = a + SEGMENTS
                // Counter-clockwise seen from outside.
                indices[k++] = a; indices[k++] = b; indices[k++] = c
                indices[k++] = a; indices[k++] = c; indices[k++] = d
            }
        }
        return MeshData(positions, normals, vertexColors, indices)
    }

    /** Surface point at height fraction [y], in the direction whose sine/cosine around Y are [sx] (x) and [cz] (z). */
    private fun pointAt(y: Float, sx: Float, cz: Float): SurfacePoint {
        val r = radius(y)
        val (nr, ny) = profileNormal(y)
        return SurfacePoint(Vec3(r * sx, y * HEIGHT, r * cz), Vec3(nr * sx, ny, nr * cz))
    }

    /** Outward normal of the profile at [y] as (radial, vertical). */
    private fun profileNormal(y: Float): Pair<Float, Float> {
        if (y <= POLE) return 0f to -1f
        if (y >= 1f - POLE) return 0f to 1f
        val e = 1e-3f
        val dr = (radius(y + e) - radius(y - e)) / (2f * e)
        val len = sqrt(HEIGHT * HEIGHT + dr * dr)
        return HEIGHT / len to -dr / len
    }

    /**
     * The surface point seen at ([xFraction], [yFromTop]) of the egg's front view, both 0..1 across
     * its bounding box, as the mockup places speckles and frost.
     */
    fun frontPoint(xFraction: Float, yFromTop: Float): SurfacePoint {
        val y = (1f - yFromTop).coerceIn(0.03f, 0.97f)
        val r = radius(y)
        val x = ((xFraction - 0.5f) * 2f * maxRadius).coerceIn(-0.92f * r, 0.92f * r)
        val z = sqrt((r * r - x * x).coerceAtLeast(0f))
        return pointAt(y, x / r, z / r)
    }

    /**
     * The mockup's 14 speckles: left and top edge, width and height (all percent of the egg's
     * front-view box) and rotation in degrees.
     */
    private val SPECKLES = listOf(
        floatArrayOf(22f, 16f, 5f, 4f, 20f), floatArrayOf(58f, 10f, 4f, 3f, -10f), floatArrayOf(70f, 30f, 6f, 4f, 35f),
        floatArrayOf(30f, 36f, 3f, 3f, 0f), floatArrayOf(14f, 52f, 5f, 3f, 60f), floatArrayOf(78f, 52f, 4f, 3f, -30f),
        floatArrayOf(48f, 26f, 3f, 2f, 10f), floatArrayOf(20f, 76f, 6f, 4f, 15f), floatArrayOf(70f, 78f, 5f, 3f, -20f),
        floatArrayOf(42f, 84f, 4f, 3f, 40f), floatArrayOf(86f, 36f, 3f, 2f, 0f), floatArrayOf(36f, 12f, 3f, 2f, 30f),
        floatArrayOf(60f, 44f, 2f, 2f, 0f), floatArrayOf(8f, 34f, 3f, 2f, 45f),
    )

    /** The mockup's seven frost dots shown on a chilly egg (left and top, percent). */
    private val FROST = listOf(14f to 20f, 60f to 10f, 76f to 36f, 24f to 48f, 50f to 58f, 70f to 72f, 20f to 78f)

    fun speckles(): List<ShellMark> = SPECKLES.map { s ->
        val w = s[2] / 100f * 2f * maxRadius
        val h = s[3] / 100f * HEIGHT
        ShellMark(frontPoint((s[0] + s[2] / 2f) / 100f, (s[1] + s[3] / 2f) / 100f), w, h, s[4] * PI.toFloat() / 180f)
    }

    fun frost(): List<ShellMark> = FROST.map { (l, t) ->
        ShellMark(frontPoint((l + 2f) / 100f, (t + 2f) / 100f), 0.005f, 0.005f, 0f)
    }

    /** The antenna-shape emblem: front centre, 60 % of the way down. */
    fun emblem(): SurfacePoint = frontPoint(0.5f, 0.6f)

    /** True when a point at [yFromTop] lies on the top cap (above the seam's middle). */
    fun onTop(yFromTop: Float): Boolean = 1f - yFromTop > SEAM

    /** Points along the zigzag seam, slightly outside the shell, closing the loop. */
    fun seamLine(lift: Float = 0.0008f): List<Vec3> = (0..SEGMENTS).map { j ->
        val a = 2f * PI.toFloat() * j / SEGMENTS
        val p = pointAt(seamAt(j), sin(a), cos(a))
        p.position + p.normal * lift
    }

    /** The crack that grows with each tap: five segments down the front, from the top. */
    fun crackLine(lift: Float = 0.0008f): List<Vec3> = CRACK.map { (x, t) ->
        val p = frontPoint(x, t)
        p.position + p.normal * lift
    }

    private val CRACK = listOf(0.54f to 0.14f, 0.46f to 0.25f, 0.57f to 0.34f, 0.45f to 0.43f, 0.56f to 0.52f, 0.49f to 0.6f)

    /** A thin four-sided tube through [points], for the seam glow and the crack. */
    fun tube(points: List<Vec3>, radius: Float): MeshData {
        val sides = 4
        val n = points.size * sides
        val positions = FloatArray(n * 3)
        val normals = FloatArray(n * 3)
        for ((i, p) in points.withIndex()) {
            val prev = points[(i - 1).coerceAtLeast(0)]
            val next = points[(i + 1).coerceAtMost(points.lastIndex)]
            val dir = (next - prev).normalized()
            val side1 = (dir cross Vec3.UP).let { if (it.lengthSquared() < 1e-8f) Vec3.RIGHT else it.normalized() }
            val side2 = (dir cross side1).normalized()
            for (s in 0 until sides) {
                val a = 2f * PI.toFloat() * s / sides
                val off = side1 * cos(a) + side2 * sin(a)
                val v = i * sides + s
                positions[v * 3] = p.x + off.x * radius
                positions[v * 3 + 1] = p.y + off.y * radius
                positions[v * 3 + 2] = p.z + off.z * radius
                normals[v * 3] = off.x
                normals[v * 3 + 1] = off.y
                normals[v * 3 + 2] = off.z
            }
        }
        val indices = ArrayList<Int>()
        for (i in 0 until points.size - 1) {
            for (s in 0 until sides) {
                val a = i * sides + s
                val b = i * sides + (s + 1) % sides
                val c = b + sides
                val d = a + sides
                // Both windings, so the thin tube shows from any side.
                indices += listOf(a, b, c, a, c, d, a, c, b, a, d, c)
            }
        }
        return MeshData(positions, normals, FloatArray(0), indices.toIntArray())
    }

    private const val POLE = 0.004f
}

/**
 * The shell gradient from the mockup: highlight (body mixed 55 % toward white) at 36 % across and
 * 28 % down, the body colour at 55 % of the way out, and body mixed 16 % toward #6b615b at the edge.
 */
class GradientColors(private val highlight: FloatArray, private val body: FloatArray, private val rim: FloatArray) {
    /** Writes the colour for front-view coordinates (x across, y down, both 0..1) into [out] at vertex [v]. */
    fun write(out: FloatArray, v: Int, x: Float, yDown: Float) {
        val dx = (x - 0.36f) / 0.8f
        val dy = (yDown - 0.28f) / 0.7f
        val d = sqrt(dx * dx + dy * dy)
        for (k in 0..2) {
            out[v * 4 + k] = if (d <= 0.55f) {
                lerp(highlight[k], body[k], d / 0.55f)
            } else {
                lerp(body[k], rim[k], ((d - 0.55f) / 0.45f).coerceAtMost(1f))
            }
        }
        out[v * 4 + 3] = 1f
    }

    private fun lerp(a: Float, b: Float, t: Float) = a + (b - a) * t

    companion object {
        fun of(body: Int): GradientColors {
            val b = ToonBake.rgb(body)
            val muted = ToonBake.rgb(0x6B615B)
            return GradientColors(
                FloatArray(3) { b[it] + (1f - b[it]) * 0.55f },
                b,
                FloatArray(3) { b[it] + (muted[it] - b[it]) * 0.16f },
            )
        }

        /** [color] with its saturation reduced by [amount] (0..1), as 0xRRGGBB. */
        fun desaturate(color: Int, amount: Float): Int {
            val c = ToonBake.rgb(color)
            val grey = 0.299f * c[0] + 0.587f * c[1] + 0.114f * c[2]
            fun ch(v: Float) = ((v + (grey - v) * amount) * 255f + 0.5f).toInt().coerceIn(0, 255)
            return (ch(c[0]) shl 16) or (ch(c[1]) shl 8) or ch(c[2])
        }
    }
}
