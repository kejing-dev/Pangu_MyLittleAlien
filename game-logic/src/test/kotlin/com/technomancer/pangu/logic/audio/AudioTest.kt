package com.technomancer.pangu.logic.audio

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.abs

class WaveRendererTest {
    @Test
    fun sineLengthAndPeak() {
        val samples = WaveRenderer.render(listOf(Note(Wave.Sine, listOf(1000f), 0.5f, gain = 0.5f)))
        assertEquals(24_001, samples.size)
        val peak = samples.maxOf { abs(it.toInt()) }
        assertTrue(peak in 16_000..16_400, "peak $peak")
        assertEquals(0, samples.first().toInt())
    }

    @Test
    fun catalogHasSixPitchStepsPerChirp() {
        val ids = AudioCatalog.all().keys
        assertTrue("chirp_happy_0" in ids && "chirp_happy_5" in ids && "ui_uitick" in ids)
        assertEquals(2, AudioCatalog.nearestStep(1.0f))
        assertEquals(5, AudioCatalog.nearestStep(1.2f))
    }
}

class WavWriterTest {
    @Test
    fun headerIsValidPcm16() {
        val bytes = WavWriter.write(ShortArray(100) { it.toShort() })
        val b = ByteBuffer.wrap(bytes).order(ByteOrder.LITTLE_ENDIAN)
        assertEquals("RIFF", String(bytes, 0, 4))
        assertEquals(36 + 200, b.getInt(4))
        assertEquals("WAVE", String(bytes, 8, 4))
        assertEquals(1, b.getShort(20).toInt()) // PCM
        assertEquals(1, b.getShort(22).toInt()) // mono
        assertEquals(48_000, b.getInt(24))
        assertEquals(16, b.getShort(34).toInt())
        assertEquals("data", String(bytes, 36, 4))
        assertEquals(200, b.getInt(40))
        assertEquals(244, bytes.size)
    }
}
