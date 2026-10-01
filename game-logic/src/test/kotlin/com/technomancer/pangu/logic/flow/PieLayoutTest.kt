package com.technomancer.pangu.logic.flow

import com.technomancer.pangu.logic.config.UiConfig
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.math.sqrt

class PieLayoutTest {
    @Test
    fun sixItemsStartTopClockwiseRadius0_11() {
        val radius = UiConfig().pieRadius
        assertEquals(0.11f, radius, 1e-6f)
        val p = PieLayout.positions(6, radius)
        assertEquals(6, p.size)
        // First straight up.
        assertEquals(0f, p[0].first, 1e-6f)
        assertEquals(0.11f, p[0].second, 1e-6f)
        // Clockwise: the second is to the right and below the first.
        assertTrue(p[1].first > 0f && p[1].second < p[0].second)
        // The fourth is straight down.
        assertEquals(-0.11f, p[3].second, 1e-6f)
        for ((x, y) in p) assertEquals(0.11f, sqrt(x * x + y * y), 1e-5f)
    }
}

class UiScaleTest {
    @Test
    fun pxToMetres0_0009() {
        val c = UiConfig()
        assertEquals(0.0009f, c.metresPerMockupPx, 0f)
        assertEquals(0.378f, UiMetrics.pxToMetres(420f, c.metresPerMockupPx), 1e-6f)
        assertEquals(0.0162f, UiMetrics.pxToMetres(18f, c.metresPerMockupPx), 1e-6f)
    }
}
