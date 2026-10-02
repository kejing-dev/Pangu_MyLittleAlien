package com.technomancer.pangu.logic.creation

import com.technomancer.pangu.logic.config.LookConfig
import com.technomancer.pangu.logic.time.XorShift32
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PreviewMorphTest {
    private val config = LookConfig()
    private val half = config.morphSec / 2f

    @Test
    fun oldTipMeltsThenNewTipGrowsOut() {
        val pose = PreviewPose(config, XorShift32(7u))
        pose.morph(fromShape = 2, toShape = 4)
        assertEquals(2, pose.morphShape)

        // Halfway through melting the old tip is still the one shown, part way to the blob.
        pose.update(half * 0.5f)
        assertEquals(2, pose.morphShape)
        assertTrue(pose.morphBlob in 0.2f..0.8f)

        // Past the blob the new tip is shown, growing back into its own shape.
        pose.update(half * 0.75f)
        assertEquals(4, pose.morphShape)
        assertTrue(pose.morphBlob in 0.2f..0.99f)

        // At the end the new tip is fully itself and the morph is over.
        pose.update(half)
        assertEquals(-1, pose.morphShape)
        assertEquals(0f, pose.morphBlob)
    }

    @Test
    fun changeMidMorphCarriesOnFromTheShownTip() {
        val pose = PreviewPose(config, XorShift32(7u))
        pose.morph(fromShape = 0, toShape = 1)
        pose.update(half * 0.5f)
        val blob = pose.morphBlob
        // A second pick while tip 0 is still melting keeps melting it, now towards tip 3.
        pose.morph(fromShape = 1, toShape = 3)
        assertEquals(0, pose.morphShape)
        pose.update(0.001f)
        assertTrue(pose.morphBlob >= blob)
        pose.update(half)
        assertEquals(3, pose.morphShape)
    }
}
