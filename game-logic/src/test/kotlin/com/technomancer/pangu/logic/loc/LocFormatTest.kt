package com.technomancer.pangu.logic.loc

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotSame
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class LocFormatTest {
    private val loc = LocService(
        mapOf(
            Language.En to LocTable(mapOf("fmt.sec" to "{a} / {b} s")),
            Language.Zh to LocTable(mapOf("fmt.sec" to "{a} / {b} 秒")),
        ),
        Language.En,
    )

    @Test
    fun progressLabelReusesBuffer() {
        val f = LocFormat(loc)
        val first = f.progress(LocFormat.Unit.Seconds, 18, 30)
        assertEquals("18 / 30 s", first)
        // Same numbers: the very same string comes back, nothing new is built.
        assertSame(first, f.progress(LocFormat.Unit.Seconds, 18, 30))
        val next = f.progress(LocFormat.Unit.Seconds, 19, 30)
        assertEquals("19 / 30 s", next)
        assertNotSame(first, next)
        // A language switch rebuilds it.
        loc.setLanguage(Language.Zh)
        assertEquals("19 / 30 秒", f.progress(LocFormat.Unit.Seconds, 19, 30))
    }
}
