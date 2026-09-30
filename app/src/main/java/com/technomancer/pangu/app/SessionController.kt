package com.technomancer.pangu.app

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.technomancer.pangu.logic.PanguLogic
import com.technomancer.pangu.logic.habitat.PadPose
import com.technomancer.pangu.logic.math.Vec3
import com.technomancer.pangu.logic.save.ISaveStore
import com.technomancer.pangu.logic.save.LoadResult
import com.technomancer.pangu.logic.save.PadPoseData
import com.technomancer.pangu.logic.save.SaveData
import com.technomancer.pangu.logic.save.SaveSerializer
import com.technomancer.pangu.logic.time.IClock
import com.technomancer.pangu.ui.UiBridge
import java.util.concurrent.Executors

/** Reads the save file once at startup. Unreadable files are moved aside; other schema versions are discarded. */
fun loadSave(store: ISaveStore): SaveData =
    when (val result = SaveSerializer.decode(runCatching { store.read() }.getOrNull())) {
        is LoadResult.Loaded -> result.data
        LoadResult.Empty -> SaveData()
        is LoadResult.UnknownVersion -> {
            Log.w(SessionController.TAG, "Discarding save with schema version ${result.version}")
            SaveData()
        }
        is LoadResult.Corrupt -> {
            Log.w(SessionController.TAG, "Save file is unreadable, moving it to .bak", result.error)
            store.quarantine()
            SaveData()
        }
    }

/**
 * Pauses and resumes the game with the Stage's lifecycle and writes the save.
 * The Stage's ON_PAUSE fires when the player leaves for the home space; ON_RESUME when they return.
 */
class SessionController(
    private val logic: PanguLogic,
    private val store: ISaveStore,
    private val clock: IClock,
    private val ui: UiBridge,
    initial: SaveData,
) : LifecycleEventObserver {

    private val ioThread = Executors.newSingleThreadExecutor { r -> Thread(r, "PanguSave") }

    var save: SaveData = initial
        private set

    /** The saved pad pose, if the player has confirmed one. */
    fun savedPadPose(): PadPose? {
        val p = save.padPose
        if (!p.has || p.pos.size < 3) return null
        return PadPose(Vec3(p.pos[0], p.pos[1], p.pos[2]), p.yaw)
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_PAUSE -> {
                logic.setPaused(true)
                ui.publishIfChanged(logic)
                Log.i(TAG, "paused tick=${logic.tickCount}")
                persist()
            }
            Lifecycle.Event.ON_RESUME -> {
                logic.setPaused(false)
                ui.publishIfChanged(logic)
                Log.i(TAG, "resumed tick=${logic.tickCount}")
            }
            else -> Unit
        }
    }

    /** Copies the logic's state into the save, serializes it here and writes it on a background thread. */
    fun persist() {
        val now = clock.nowUtcMillis()
        val pad = logic.padPose
        val padData = if (pad != null && !logic.screens.isPlacingPad) {
            PadPoseData(true, listOf(pad.position.x, pad.position.y, pad.position.z), pad.yaw)
        } else {
            save.padPose
        }
        save = save.copy(
            screen = logic.screens.persistentScreen,
            padPose = padData,
            lastSeenUtcMs = now,
            lastSimUtcMs = now,
        )
        val text = SaveSerializer.encode(save)
        ioThread.execute {
            try {
                store.write(text)
            } catch (e: Exception) {
                Log.e(TAG, "Save failed", e)
            }
        }
    }

    companion object {
        const val TAG = "Pangu"
    }
}
