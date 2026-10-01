package com.technomancer.pangu.logic.flow

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** Where the care pie's bubbles go: evenly on a ring, starting at the top and going clockwise. */
object PieLayout {
    /**
     * Offsets (x right, y up) of [count] bubbles on a ring of [radius], the first straight up and
     * the rest clockwise as the player sees them.
     */
    fun positions(count: Int, radius: Float): List<Pair<Float, Float>> = (0 until count).map { i ->
        val a = PI / 2 - i * 2 * PI / count
        (radius * cos(a)).toFloat() to (radius * sin(a)).toFloat()
    }
}

/** Mockup pixels to room metres, as the panels are sized. */
object UiMetrics {
    fun pxToMetres(px: Float, metresPerMockupPx: Float): Float = px * metresPerMockupPx
}
