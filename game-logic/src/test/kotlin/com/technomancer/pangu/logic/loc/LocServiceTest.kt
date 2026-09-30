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
