package com.technomancer.pangu.audio

import android.util.Log
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.LoadType
import com.pico.spatial.core.ecs.ObjectAudioComponent
import com.pico.spatial.core.ecs.audio.AudioPlayerController
import com.pico.spatial.core.ecs.resource.AudioResource
import com.technomancer.pangu.logic.audio.AudioCatalog
import com.technomancer.pangu.logic.audio.Sfx

/**
 * Plays the procedural clips rendered at build time (assets/audio/<id>.wav). Pet chirps come
 * from the pet, egg sounds from the egg, UI sounds from the panel side of the habitat. Clips load on first use and each
 * emitter keeps one controller per clip, so replaying a clip does not allocate.
 */
class SfxPlayer {
    private val resources = HashMap<String, AudioResource?>()
    private val controllers = HashMap<Pair<Entity, String>, AudioPlayerController>()

    var petEmitter: Entity? = null
        set(value) {
            field = value
            value?.let(::ensureAudio)
        }
    var uiEmitter: Entity? = null
        set(value) {
            field = value
            value?.let(::ensureAudio)
        }
    var eggEmitter: Entity? = null
        set(value) {
            field = value
            value?.let(::ensureAudio)
        }

    /** 0..1 volume from Settings; [soundOn] mutes everything. */
    var volume = 0.7f
    var soundOn = true

    /** Voice pitch of the current pet; picks the nearest pre-rendered chirp. */
    var voicePitch = 1f

    private var loop: Sfx? = null
    private var loopController: AudioPlayerController? = null

    /** Keeps [sfx] looping from the pet (dance music), or stops the loop when null. */
    fun setLoop(sfx: Sfx?) {
        if (sfx == loop) return
        runCatching { loopController?.stop() }
        loopController = null
        loop = sfx
        if (sfx == null || !soundOn) return
        val emitter = petEmitter ?: return
        val id = AudioCatalog.musicId(sfx)
        val resource = resources.getOrPut(id) { load(id) } ?: return
        try {
            loopController = controllers.getOrPut(emitter to id) { emitter.prepareAudio(resource) }.apply {
                setVolume(volume)
                setLoop(true)
                stop()
                play()
            }
        } catch (e: Exception) {
            Log.w(TAG, "Could not loop $id", e)
        }
    }

    fun play(sfx: Sfx) {
        if (!soundOn || AudioCatalog.isLoop(sfx)) return
        val fromPet = AudioCatalog.isChirp(sfx) || AudioCatalog.isMusic(sfx)
        val emitter = when {
            AudioCatalog.isEgg(sfx) -> eggEmitter ?: uiEmitter
            fromPet -> petEmitter
            else -> uiEmitter
        } ?: return
        val id = when {
            AudioCatalog.isEgg(sfx) -> AudioCatalog.eggId(sfx)
            AudioCatalog.isChirp(sfx) -> AudioCatalog.chirpId(sfx, AudioCatalog.nearestStep(voicePitch))
            AudioCatalog.isMusic(sfx) -> AudioCatalog.musicId(sfx)
            else -> AudioCatalog.uiId(sfx)
        }
        val resource = resources.getOrPut(id) { load(id) } ?: return
        try {
            val c = controllers.getOrPut(emitter to id) { emitter.prepareAudio(resource) }
            c.setVolume(volume)
            c.stop()
            c.play()
        } catch (e: Exception) {
            Log.w(TAG, "Could not play $id", e)
        }
    }

    fun clear() {
        loop = null
        loopController = null
        controllers.values.forEach { runCatching { it.close() } }
        controllers.clear()
        petEmitter = null
        uiEmitter = null
        eggEmitter = null
    }

    private fun load(id: String): AudioResource? = try {
        AudioResource.load(id, "asset://audio/$id.wav", LoadType.FROM_ASSETS)
    } catch (e: Exception) {
        Log.w(TAG, "Missing sound $id", e)
        null
    }

    private fun ensureAudio(e: Entity) {
        if (e.components[ObjectAudioComponent::class.java] == null) e.components.set(ObjectAudioComponent())
    }

    companion object {
        private const val TAG = "Pangu"
    }
}
