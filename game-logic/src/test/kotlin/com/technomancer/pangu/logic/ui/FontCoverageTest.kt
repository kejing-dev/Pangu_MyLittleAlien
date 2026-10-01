package com.technomancer.pangu.logic.ui

import com.technomancer.pangu.logic.loc.Language
import com.technomancer.pangu.logic.loc.LocTable
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Assumptions.assumeTrue
import org.junit.jupiter.api.Test
import java.io.File
import java.nio.ByteBuffer

/** Reads which code points a TrueType font covers, from its cmap table (formats 4 and 12). */
object TtfCmap {
    fun codePoints(bytes: ByteArray): Set<Int> {
        val b = ByteBuffer.wrap(bytes)
        val numTables = b.getShort(4).toInt() and 0xFFFF
        var cmap = -1
        for (i in 0 until numTables) {
            val rec = 12 + i * 16
            val tag = String(bytes, rec, 4, Charsets.US_ASCII)
            if (tag == "cmap") cmap = b.getInt(rec + 8)
        }
        require(cmap >= 0) { "no cmap table" }
        val out = HashSet<Int>()
        val subtables = b.getShort(cmap + 2).toInt() and 0xFFFF
        for (i in 0 until subtables) {
            val offset = cmap + b.getInt(cmap + 4 + i * 8 + 4)
            when (b.getShort(offset).toInt() and 0xFFFF) {
                4 -> {
                    val segX2 = b.getShort(offset + 6).toInt() and 0xFFFF
                    val ends = offset + 14
                    val starts = ends + segX2 + 2
                    for (s in 0 until segX2 / 2) {
                        val end = b.getShort(ends + s * 2).toInt() and 0xFFFF
                        val start = b.getShort(starts + s * 2).toInt() and 0xFFFF
                        if (start != 0xFFFF) for (c in start..end) out += c
                    }
                }
                12 -> {
                    val groups = b.getInt(offset + 12)
                    for (g in 0 until groups) {
                        val start = b.getInt(offset + 16 + g * 12)
                        val end = b.getInt(offset + 20 + g * 12)
                        for (c in start..end) out += c
                    }
                }
            }
        }
        return out
    }
}

class FontCoverageTest {
    private val fontDir = File("../app/src/main/res/font")
    private val glyphs = "☼◡≋♪◎◇●☾✧♫◉♡∿☝○↻▲▼★♥✿ϟ≈❚✦⚙❄◌"

    private fun covered(vararg names: String): Set<Int> =
        names.flatMap { TtfCmap.codePoints(File(fontDir, it).readBytes()) }.toSet()

    @Test
    fun allLocAndGlyphCharactersCovered() {
        val latin = listOf("noto_sans_regular.ttf", "noto_sans_symbols2_regular.ttf")
        val sc = listOf("noto_sans_sc_regular.otf", "noto_sans_symbols2_regular.ttf")
        assumeTrue((latin + sc).all { File(fontDir, it).exists() }, "Noto fonts not copied into res/font yet")
        // The app chains these four fonts for UI symbols.
        val symbols = covered("noto_sans_symbols2_regular.ttf", "noto_sans_symbols_regular.ttf", "noto_sans_math_regular.ttf", "noto_sans_regular.ttf")
        val missingGlyphs = glyphs.codePoints().toArray().filter { it !in symbols }
        assertTrue(missingGlyphs.isEmpty(), "glyphs missing: ${missingGlyphs.joinToString { Character.toChars(it).concatToString() }}")
        for (lang in Language.entries) {
            val table = LocTable.parse(File("../app/src/main/assets/loc/strings_${lang.code}.json").readText(Charsets.UTF_8))
            val fonts = covered(*(if (lang == Language.Zh) sc else latin).toTypedArray())
            // Each language's own name is drawn in that language's font (the app does the same).
            val scFonts = covered(*sc.toTypedArray())
            val missing = table.keys.flatMap { k ->
                val own = if (k == "ui.lang.zh" || k == "ui.langLabel.zh") scFonts else fonts
                table[k]!!.codePoints().toArray().filter { it > 0x20 && it !in own }
            }.toSet()
            assertTrue(missing.isEmpty(), "${lang.code} missing: ${missing.joinToString("") { Character.toChars(it).concatToString() }}")
        }
    }
}
