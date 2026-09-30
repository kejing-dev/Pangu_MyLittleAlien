package com.technomancer.pangu.logic.audio

import java.io.File

/** Sounds the game plays. Pet chirps exist at several voice-pitch steps; see [chirpId]. */
enum class Sfx { Happy, Question, Sad, Sleepy, UiTick, UiConfirm, DanceWiggle, DanceSpin, WentHome }

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

    /** Loops that play from the pet while it dances, and the soft glide when it goes home. */
    private val music: Map<Sfx, List<Note>> = mapOf(
        Sfx.DanceWiggle to danceLoop(Wave.Square, Wave.Triangle, listOf(0, 4, 7, 4, 9, 7, 4, 2)),
        Sfx.DanceSpin to danceLoop(Wave.Triangle, Wave.Square, listOf(0, 7, 12, 7, 5, 9, 12, 9)),
        Sfx.WentHome to listOf(Note(Wave.Sine, listOf(2600f, 600f), 1.2f, 0.3f)),
    )

    /** Eight bars at 110 BPM: an eighth-note lead over a quarter-note bass in C major. */
    private fun danceLoop(lead: Wave, bass: Wave, steps: List<Int>): List<Note> {
        val beat = 60f / 110f
        val notes = ArrayList<Note>()
        val root = 523.25f // C5
        for (bar in 0 until 8) {
            val shift = if (bar % 4 == 3) 5 else 0 // a lift to F every fourth bar
            for (i in 0 until 8) {
                notes += Note(lead, listOf(root * semitone(steps[i] + shift)), beat / 2f * 0.9f, 0.22f, (bar * 4 + i / 2f) * beat)
            }
            for (q in 0 until 4) {
                val bassStep = shift + if (q % 2 == 1) 7 else 0
                notes += Note(bass, listOf(root / 4f * semitone(bassStep)), beat * 0.8f, 0.3f, (bar * 4 + q) * beat)
            }
        }
        return notes
    }

    private fun semitone(n: Int): Float = Math.pow(2.0, n / 12.0).toFloat()

    fun isChirp(sfx: Sfx) = sfx in chirps

    fun isLoop(sfx: Sfx) = sfx == Sfx.DanceWiggle || sfx == Sfx.DanceSpin

    fun isMusic(sfx: Sfx) = sfx in music

    fun musicId(sfx: Sfx) = "music_${sfx.name.lowercase()}"

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
        for ((sfx, notes) in music) out[musicId(sfx)] = notes
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
