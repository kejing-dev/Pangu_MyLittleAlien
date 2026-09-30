package com.technomancer.pangu.logic.config

import kotlinx.serialization.Serializable
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class ConfigJsonTest {
    @Serializable
    data class Inner(val a: Int = 1, val b: Float = 2f)

    @Serializable
    data class Sample(
        val decayPerHour: Float = 6f,
        val threshold: Int = 70,
        val name: String = "default",
        val inner: Inner = Inner(),
    )

    @Test
    fun missingFieldKeepsDefault() {
        val cfg = ConfigJson.load("""{ "threshold": 40, "inner": { "b": 9.5 } }""", Sample())
        assertEquals(40, cfg.threshold)
        assertEquals(6f, cfg.decayPerHour)
        assertEquals("default", cfg.name)
        assertEquals(1, cfg.inner.a)
        assertEquals(9.5f, cfg.inner.b)
    }

    @Test
    fun missingFieldKeepsPassedDefaultNotClassDefault() {
        val cfg = ConfigJson.load("""{ "threshold": 40 }""", Sample(decayPerHour = 3f))
        assertEquals(3f, cfg.decayPerHour)
    }

    @Test
    fun nullJsonReturnsDefault() {
        val default = Sample(name = "x")
        assertSame(default, ConfigJson.load(null, default))
        assertSame(default, ConfigJson.load("   ", default))
    }

    @Test
    fun unknownKeysIgnored() {
        assertEquals(Sample(threshold = 1), ConfigJson.load("""{ "threshold": 1, "nope": true }""", Sample()))
    }

    @Test
    fun malformedJsonReturnsDefaultAndReports() {
        var error: Throwable? = null
        val default = Sample()
        assertSame(default, ConfigJson.load("{ not json", default) { error = it })
        assertNotNull(error)
    }
}
