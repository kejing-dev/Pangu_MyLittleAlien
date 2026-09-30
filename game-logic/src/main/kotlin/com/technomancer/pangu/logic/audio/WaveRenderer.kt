package com.technomancer.pangu.logic.audio

import java.io.ByteArrayOutputStream
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

enum class Wave { Sine, Square, Triangle }

/**
 * One note: a waveform gliding through [freqs] (Hz, evenly spaced over the note) for [durationSec],
 * at [gain] 0..1. A single frequency plays a steady tone. [startSec] places it in the clip.
 */
data class Note(
    val wave: Wave,
    val freqs: List<Float>,
    val durationSec: Float,
    val gain: Float = 0.6f,
    val startSec: Float = 0f,
)

/** Renders notes to 16-bit mono PCM with short attack and release ramps to avoid clicks. */
object WaveRenderer {
    const val SAMPLE_RATE = 48_000
    private const val RAMP_SEC = 0.002f

    fun render(notes: List<Note>): ShortArray {
        val end = notes.maxOfOrNull { it.startSec + it.durationSec } ?: 0f
        val mix = FloatArray((end * SAMPLE_RATE).toInt() + 1)
        for (note in notes) addNote(mix, note)
        return ShortArray(mix.size) { (mix[it].coerceIn(-1f, 1f) * Short.MAX_VALUE).toInt().toShort() }
    }

    private fun addNote(mix: FloatArray, n: Note) {
        val start = (n.startSec * SAMPLE_RATE).toInt()
        val count = (n.durationSec * SAMPLE_RATE).toInt()
        val ramp = (RAMP_SEC * SAMPLE_RATE).toInt().coerceAtLeast(1)
        var phase = 0.0
        for (i in 0 until count) {
            val t = i.toFloat() / count
            val f = frequencyAt(n.freqs, t)
            phase += f / SAMPLE_RATE
            val cycle = (phase % 1.0).toFloat()
            val s = when (n.wave) {
                Wave.Sine -> sin(2.0 * PI * phase).toFloat()
                Wave.Square -> if (cycle < 0.5f) 1f else -1f
                Wave.Triangle -> 1f - 4f * abs(cycle - 0.5f)
            }
            val env = minOf(1f, i.toFloat() / ramp, (count - 1 - i).toFloat() / ramp)
            val idx = start + i
            if (idx < mix.size) mix[idx] += s * n.gain * env
        }
    }

    /** Linear glide through the frequency list. */
    private fun frequencyAt(freqs: List<Float>, t: Float): Float {
        if (freqs.size == 1) return freqs[0]
        val x = t * (freqs.size - 1)
        val i = x.toInt().coerceAtMost(freqs.size - 2)
        val f = x - i
        return freqs[i] + (freqs[i + 1] - freqs[i]) * f
    }
}

/** Writes 16-bit PCM mono samples as a WAV file. */
object WavWriter {
    fun write(samples: ShortArray, sampleRate: Int = WaveRenderer.SAMPLE_RATE): ByteArray {
        val dataBytes = samples.size * 2
        val out = ByteArrayOutputStream(44 + dataBytes)
        fun int(v: Int) = repeat(4) { out.write((v shr (8 * it)) and 0xFF) }
        fun short(v: Int) = repeat(2) { out.write((v shr (8 * it)) and 0xFF) }
        out.write("RIFF".toByteArray(Charsets.US_ASCII))
        int(36 + dataBytes)
        out.write("WAVE".toByteArray(Charsets.US_ASCII))
        out.write("fmt ".toByteArray(Charsets.US_ASCII))
        int(16)
        short(1) // PCM
        short(1) // mono
        int(sampleRate)
        int(sampleRate * 2) // byte rate
        short(2) // block align
        short(16) // bits per sample
        out.write("data".toByteArray(Charsets.US_ASCII))
        int(dataBytes)
        for (s in samples) short(s.toInt())
        return out.toByteArray()
    }
}
