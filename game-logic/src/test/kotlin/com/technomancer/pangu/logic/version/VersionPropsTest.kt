package com.technomancer.pangu.logic.version

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows

class VersionPropsTest {
    @Test
    fun parsesCodeAndName() {
        assertEquals(VersionProps(42, "1.3.0"), VersionProps.parse("42", "1.3.0"))
        assertEquals(VersionProps(7, "2.0-rc1"), VersionProps.parse(" 7 ", " 2.0-rc1 "))
    }

    @Test
    fun missingValuesTakeDefaults() {
        assertEquals(VersionProps(VersionProps.DEFAULT_CODE, VersionProps.DEFAULT_NAME), VersionProps.parse(null, ""))
    }

    @Test
    fun malformedValuesFail() {
        assertThrows<IllegalArgumentException> { VersionProps.parse("0", "1.0") }
        assertThrows<IllegalArgumentException> { VersionProps.parse("abc", "1.0") }
        assertThrows<IllegalArgumentException> { VersionProps.parse("2100000001", "1.0") }
        assertThrows<IllegalArgumentException> { VersionProps.parse("1", "one") }
    }
}
