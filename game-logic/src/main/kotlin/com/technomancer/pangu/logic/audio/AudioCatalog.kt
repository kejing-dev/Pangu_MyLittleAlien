package com.technomancer.pangu.logic.audio

import java.io.File

/** Sounds the game plays. Pet chirps exist at several voice-pitch steps; see [chirpId]. */
enum class Sfx { Happy, Question, Sad, Sleepy, UiTick, UiConfirm }

/** Every procedural sound, by asset id (file name without .wav). */
object AudioCatalog {
    /** Voice pitch steps; playback uses the one nearest the pet's voice pitch. */
    val PITCH_STEPS = listOf(0.90f, 0.95f, 1.00f, 1.05f, 1.10f, 1.15f)

    private fun chirp(freqs: List<Float>, sec: Float, gain: Float) = listOf(Note(Wave.Sine, freqs, sec, gain))

    private val chirps: Map<Sfx, List<Note>> = mapOf(
        Sfx.Happy to chirp(listOf(900f, 1500f), 0.16f, 0.55f),
        Sfx.Question to chirp(listOf(1200f, 1600f, 1300f), 0.22f, 0.55f),
        Sfx.Sad to chirp(listOf(900f, 600f), 0.24f, 0.5f),
        Sfx.Sleepy to chirp(listOf(600f, 450f), 0.26f, 0.3f),
    )

    private val ui: Map<Sfx, List<Note>> = mapOf(
        Sfx.UiTick to listOf(Note(Wave.Triangle, listOf(2000f), 0.03f, 0.4f)),
        Sfx.UiConfirm to listOf(
            Note(Wave.Triangle, listOf(1568f), 0.06f, 0.4f),
            Note(Wave.Triangle, listOf(2093f), 0.08f, 0.4f, startSec = 0.07f),
        ),
    )

    fun isChirp(sfx: Sfx) = sfx in chirps

    /** Asset id of a chirp at a pitch step index, e.g. "chirp_happy_2". */
    fun chirpId(sfx: Sfx, step: Int) = "chirp_${sfx.name.lowercase()}_$step"

    fun uiId(sfx: Sfx) = "ui_${sfx.name.lowercase()}"

    /** Index of the pitch step closest to [voicePitch]. */
    fun nearestStep(voicePitch: Float): Int = PITCH_STEPS.indices.minBy { kotlin.math.abs(PITCH_STEPS[it] - voicePitch) }

    /** All clips: id to notes. */
    fun all(): Map<String, List<Note>> {
        val out = LinkedHashMap<String, List<Note>>()
        for ((sfx, notes) in chirps) {
            PITCH_STEPS.forEachIndexed { i, p ->
                out[chirpId(sfx, i)] = notes.map { n -> n.copy(freqs = n.freqs.map { it * p }, durationSec = n.durationSec / p) }
            }
        }
        for ((sfx, notes) in ui) out[uiId(sfx)] = notes
        return out
    }
}

/** Build-time entry point: renders every clip in [AudioCatalog] into the directory given as the first argument. */
object RenderAudioMain {
    @JvmStatic
    fun main(args: Array<String>) {
        val dir = File(args.firstOrNull() ?: error("usage: RenderAudioMain <outputDir>"))
        dir.mkdirs()
        for ((id, notes) in AudioCatalog.all()) {
            File(dir, "$id.wav").writeBytes(WavWriter.write(WaveRenderer.render(notes)))
        }
        println("Rendered ${AudioCatalog.all().size} clips to $dir")
    }
}
