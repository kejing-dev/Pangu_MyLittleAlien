package com.technomancer.pangu.logic.loc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.io.File

class LocServiceTest {
    private fun table(lang: Language): LocTable {
        // Tests run with the module directory as the working directory.
        val file = File("../app/src/main/assets/loc/strings_${lang.code}.json")
        return LocTable.parse(file.readText(Charsets.UTF_8))
    }

    private val tables = Language.entries.associateWith { table(it) }

    @Test
    fun allMockupKeysPresentInAllThreeLanguages() {
        val en = tables.getValue(Language.En).keys
        assertTrue(en.size > 100)
        for (lang in Language.entries) {
            assertEquals(en, tables.getValue(lang).keys, "key set differs for ${lang.code}")
            for (k in en) assertTrue(tables.getValue(lang)[k]!!.isNotBlank(), "${lang.code}:$k is blank")
        }
        for (screen in listOf("C02", "C34", "C37")) assertTrue("scr.$screen.t" in en)
    }

    @Test
    fun natureQuirkFoodKeysAllLanguages() {
        val keys = (0 until 25).map { "nature.$it" } +
            (0 until 10).flatMap { listOf("quirk.$it.name", "quirk.$it.desc") } +
            (0 until 5).map { "food.$it" } + (0 until 4).map { "band.$it" } + listOf("quirk.line", "quirk.lineNone")
        for (lang in Language.entries) {
            for (k in keys) assertTrue(tables.getValue(lang)[k]?.isNotBlank() == true, "${lang.code}:$k missing")
        }
        assertEquals("Dreamy", tables.getValue(Language.En)["nature.10"])
        assertEquals("梦幻", tables.getValue(Language.Zh)["nature.10"])
        assertEquals("Soñador", tables.getValue(Language.Es)["nature.10"])
    }

    @Test
    fun switchLanguageRebuildsVisibleStrings() {
        val loc = LocService(tables, Language.En)
        val visible = listOf("scr.C02.t", "scr.C36.t", "ui.volume")
        val en = visible.map { loc.get(it) }
        loc.setLanguage(Language.Zh)
        val zh = visible.map { loc.get(it) }
        loc.setLanguage(Language.Es)
        val es = visible.map { loc.get(it) }
        assertEquals(listOf("Set up the habitat", "Settings", "Volume"), en)
        for (i in visible.indices) {
            assertTrue(zh[i] != en[i] && es[i] != en[i], "${visible[i]} did not change")
        }
        assertEquals(Language.Es, loc.language)
    }

    @Test
    fun missingKeyFallsBackToEn() {
        val missing = ArrayList<String>()
        val loc = LocService(
            mapOf(Language.En to LocTable(mapOf("a" to "A", "b" to "B")), Language.Zh to LocTable(mapOf("a" to "甲"))),
            Language.Zh,
        ) { missing += it }
        assertEquals("甲", loc.get("a"))
        assertEquals("B", loc.get("b"))
        assertEquals("B", loc.get("b"))
        assertEquals("nope", loc.get("nope"))
        assertEquals(listOf("zh:b", "zh:nope", "en:nope"), missing)
    }

    @Test
    fun nameSubstitution() {
        val loc = LocService(tables)
        assertEquals("Lock in Bobabo's look?", loc.format("scr.C05.t", "name" to "Bobabo"))
        assertEquals("That's enough Sing today", loc.format("scr.C16.t", "action" to "Sing"))
        assertEquals("18 / 30 s", LocFormat(loc).progress(LocFormat.Unit.Seconds, 18, 30))
    }

    @Test
    fun c07ZhTitleMatchesMockup() {
        val loc = LocService(tables, Language.Zh)
        assertEquals("{name} · 第 2 天", loc.format("scr.C07.t", "day" to 2))
        assertEquals("Bobabo · 第 2 天", loc.format("scr.C07.t", "name" to "Bobabo", "day" to 2))
    }

    @Test
    fun localeTagPicksLanguage() {
        assertEquals(Language.Es, Language.forLocaleTag("es-MX"))
        assertEquals(Language.Zh, Language.forLocaleTag("zh-Hans-CN"))
        assertEquals(Language.En, Language.forLocaleTag("fr-FR"))
    }
}
