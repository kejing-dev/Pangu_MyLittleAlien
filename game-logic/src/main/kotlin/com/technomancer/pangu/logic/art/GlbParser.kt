package com.technomancer.pangu.logic.art

import com.technomancer.pangu.logic.math.Vec3
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.nio.ByteBuffer
import java.nio.ByteOrder

/** The alien's parts by node name, plus the rig anchors in model units. */
class AlienParts(val parts: Map<String, MeshData>) {
    operator fun get(name: String): MeshData? = parts[name]

    companion object {
        const val BODY = "Body"
        const val EYES = "Eyes"
        const val HIGHLIGHTS = "Eye_Highlights"
        const val MOUTH = "Mouth"
        const val BLUSH = "Blush"
        const val STALK = "Antenna_Stalk"
        val TIPS = listOf("AntennaTip_Ball", "AntennaTip_Star", "AntennaTip_Heart", "AntennaTip_Spiral", "AntennaTip_Flower", "AntennaTip_Bolt")
        val ALL = listOf(BODY, EYES, HIGHLIGHTS, MOUTH, BLUSH, STALK) + TIPS

        /** Model height used to scale the alien to its real size. */
        const val MODEL_HEIGHT = 3.138f
        val ANTENNA_BASE = Vec3(0f, 2.16f, -0.02f)
        val TIP_ANCHOR = Vec3(0.12f, 2.78f, 0.02f)
    }
}

/**
 * One node of a GLB scene: its mesh (if any), where it sits under its [parent] (an index into the
 * node list, -1 for a root) and the colour of its material as 0xRRGGBB with [opacity].
 * Nodes that reuse a mesh share one [mesh] object and the same [meshName].
 */
class GlbNode(
    val name: String,
    val mesh: MeshData?,
    val meshName: String,
    val parent: Int,
    val translation: Vec3,
    val scale: Vec3,
    val color: Int,
    val opacity: Float,
)

/**
 * Minimal glTF 2.0 binary reader: each named node's single mesh primitive (float VEC3 POSITION
 * and NORMAL, uint16 or uint32 indices). [parse] reads the alien, whose nodes have no transforms.
 * [parseScene] reads a whole node tree with translations, scales, material colours and baked
 * vertex colours (the UFO); rotations are not read, as neither file has any.
 */
object GlbParser {
    private const val MAGIC = 0x46546C67 // "glTF"
    private const val CHUNK_JSON = 0x4E4F534A
    private const val CHUNK_BIN = 0x004E4942
    private const val FLOAT = 5126
    private const val USHORT = 5123
    private const val UINT = 5125

    fun parse(bytes: ByteArray): AlienParts {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(buf.getInt(0) == MAGIC) { "not a GLB file" }
        var offset = 12
        var json: JsonObject? = null
        var binStart = -1
        while (offset + 8 <= bytes.size) {
            val length = buf.getInt(offset)
            val type = buf.getInt(offset + 4)
            when (type) {
                CHUNK_JSON -> json = Json.parseToJsonElement(String(bytes, offset + 8, length, Charsets.UTF_8)).jsonObject
                CHUNK_BIN -> binStart = offset + 8
            }
            offset += 8 + length
        }
        val root = requireNotNull(json) { "GLB has no JSON chunk" }
        require(binStart >= 0) { "GLB has no BIN chunk" }

        val accessors = root.array("accessors")
        val views = root.array("bufferViews")
        val meshes = root.array("meshes")
        val parts = LinkedHashMap<String, MeshData>()
        for (node in root.array("nodes")) {
            val n = node.jsonObject
            val meshIndex = n["mesh"]?.jsonPrimitive?.int ?: continue
            val name = n["name"]?.jsonPrimitive?.content ?: continue
            val prim = meshes[meshIndex].jsonObject.array("primitives")[0].jsonObject
            val attrs = prim["attributes"]!!.jsonObject
            val reader = AccessorReader(buf, binStart, accessors, views)
            val positions = reader.floats(attrs["POSITION"]!!.jsonPrimitive.int, 3)
            val normals = reader.floats(attrs["NORMAL"]!!.jsonPrimitive.int, 3)
            val indices = reader.indices(prim["indices"]!!.jsonPrimitive.int)
            parts[name] = MeshData(positions, normals, FloatArray(0), indices)
        }
        return AlienParts(parts)
    }

    /**
     * Reads every node in file order. glTF colours are linear; they come back in display (sRGB)
     * space like the rest of the game's colours.
     */
    fun parseScene(bytes: ByteArray): List<GlbNode> {
        val buf = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        require(buf.getInt(0) == MAGIC) { "not a GLB file" }
        var offset = 12
        var json: JsonObject? = null
        var binStart = -1
        while (offset + 8 <= bytes.size) {
            val length = buf.getInt(offset)
            when (buf.getInt(offset + 4)) {
                CHUNK_JSON -> json = Json.parseToJsonElement(String(bytes, offset + 8, length, Charsets.UTF_8)).jsonObject
                CHUNK_BIN -> binStart = offset + 8
            }
            offset += 8 + length
        }
        val root = requireNotNull(json) { "GLB has no JSON chunk" }
        require(binStart >= 0) { "GLB has no BIN chunk" }

        val reader = AccessorReader(buf, binStart, root.array("accessors"), root.array("bufferViews"))
        val meshes = root.array("meshes")
        val materials = root.array("materials")
        val nodes = root.array("nodes")
        val parents = IntArray(nodes.size) { -1 }
        nodes.forEachIndexed { i, node ->
            node.jsonObject["children"]?.jsonArray?.forEach { parents[it.jsonPrimitive.int] = i }
        }
        val meshCache = HashMap<Int, MeshData>()
        return nodes.mapIndexed { i, node ->
            val n = node.jsonObject
            val meshIndex = n["mesh"]?.jsonPrimitive?.int
            var mesh: MeshData? = null
            var meshName = ""
            var color = 0xFFFFFF
            var opacity = 1f
            if (meshIndex != null) {
                val m = meshes[meshIndex].jsonObject
                meshName = m["name"]?.jsonPrimitive?.content ?: "mesh$meshIndex"
                val prim = m.array("primitives")[0].jsonObject
                mesh = meshCache.getOrPut(meshIndex) {
                    val attrs = prim["attributes"]!!.jsonObject
                    val positions = reader.floats(attrs["POSITION"]!!.jsonPrimitive.int, 3)
                    val normals = reader.floats(attrs["NORMAL"]!!.jsonPrimitive.int, 3)
                    val colors = attrs["COLOR_0"]?.jsonPrimitive?.int?.let { reader.colors(it) } ?: FloatArray(0)
                    MeshData(positions, normals, colors, reader.indices(prim["indices"]!!.jsonPrimitive.int))
                }
                val factor = prim["material"]?.jsonPrimitive?.int
                    ?.let { materials[it].jsonObject["pbrMetallicRoughness"]?.jsonObject?.get("baseColorFactor")?.jsonArray }
                if (factor != null) {
                    fun channel(k: Int) = (linearToDisplay(factor[k].jsonPrimitive.content.toFloat()) * 255f + 0.5f).toInt().coerceIn(0, 255)
                    color = (channel(0) shl 16) or (channel(1) shl 8) or channel(2)
                    opacity = factor[3].jsonPrimitive.content.toFloat()
                }
            }
            GlbNode(
                name = n["name"]?.jsonPrimitive?.content ?: "node$i",
                mesh = mesh,
                meshName = meshName,
                parent = parents[i],
                translation = n.vec3("translation", 0f),
                scale = n.vec3("scale", 1f),
                color = color,
                opacity = opacity,
            )
        }
    }

    private fun JsonObject.vec3(key: String, fallback: Float): Vec3 {
        val a = this[key]?.jsonArray ?: return Vec3(fallback, fallback, fallback)
        return Vec3(a[0].jsonPrimitive.content.toFloat(), a[1].jsonPrimitive.content.toFloat(), a[2].jsonPrimitive.content.toFloat())
    }

    /** Linear light to display (sRGB) for one channel. */
    private fun linearToDisplay(v: Float): Float {
        val c = v.coerceIn(0f, 1f)
        return if (c <= 0.0031308f) 12.92f * c else 1.055f * Math.pow(c.toDouble(), 1.0 / 2.4).toFloat() - 0.055f
    }

    private fun JsonObject.array(key: String): JsonArray = this[key]?.jsonArray ?: JsonArray(emptyList())

    private class AccessorReader(
        val buf: ByteBuffer,
        val binStart: Int,
        val accessors: JsonArray,
        val views: JsonArray,
    ) {
        private fun location(index: Int): Triple<JsonObject, Int, Int> {
            val a = accessors[index].jsonObject
            val v = views[a["bufferView"]!!.jsonPrimitive.int].jsonObject
            val start = binStart + (v["byteOffset"]?.jsonPrimitive?.int ?: 0) + (a["byteOffset"]?.jsonPrimitive?.int ?: 0)
            val stride = v["byteStride"]?.jsonPrimitive?.int ?: 0
            return Triple(a, start, stride)
        }

        fun floats(index: Int, components: Int): FloatArray {
            val (a, start, stride) = location(index)
            require(a["componentType"]!!.jsonPrimitive.int == FLOAT) { "accessor $index is not float" }
            val count = a["count"]!!.jsonPrimitive.int
            val step = if (stride > 0) stride else components * 4
            val out = FloatArray(count * components)
            for (i in 0 until count) {
                for (c in 0 until components) out[i * components + c] = buf.getFloat(start + i * step + c * 4)
            }
            return out
        }

        /** A float VEC3 or VEC4 colour accessor as RGBA in display space (alpha 1 when the file has none). */
        fun colors(index: Int): FloatArray {
            val (a, start, stride) = location(index)
            require(a["componentType"]!!.jsonPrimitive.int == FLOAT) { "colour accessor $index is not float" }
            val components = if (a["type"]!!.jsonPrimitive.content == "VEC4") 4 else 3
            val count = a["count"]!!.jsonPrimitive.int
            val step = if (stride > 0) stride else components * 4
            val out = FloatArray(count * 4)
            for (i in 0 until count) {
                for (c in 0 until 3) out[i * 4 + c] = linearToDisplay(buf.getFloat(start + i * step + c * 4))
                out[i * 4 + 3] = if (components == 4) buf.getFloat(start + i * step + 12) else 1f
            }
            return out
        }

        fun indices(index: Int): IntArray {
            val (a, start, _) = location(index)
            val count = a["count"]!!.jsonPrimitive.int
            return when (a["componentType"]!!.jsonPrimitive.int) {
                UINT -> IntArray(count) { buf.getInt(start + it * 4) }
                USHORT -> IntArray(count) { buf.getShort(start + it * 2).toInt() and 0xFFFF }
                else -> error("unsupported index type in accessor $index")
            }
        }
    }
}
