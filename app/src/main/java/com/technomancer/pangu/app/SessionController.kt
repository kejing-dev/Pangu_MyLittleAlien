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
import java.util.concurrent.Executors

/**
 * Pauses and resumes the simulation with the Stage's lifecycle and owns the save.
 * The Stage's ON_PAUSE fires when the player leaves for the home space; ON_RESUME when they return.
 */
class SessionController(
    private val logic: PanguLogic,
    private val store: ISaveStore,
    private val clock: IClock,
) : LifecycleEventObserver {

    private val ioThread = Executors.newSingleThreadExecutor { r -> Thread(r, "PanguSave") }

    var save: SaveData = SaveData()
        private set

    /** Reads the save once at startup. Unreadable files are moved aside; other schema versions are discarded. */
    fun load() {
        save = when (val result = SaveSerializer.decode(runCatching { store.read() }.getOrNull())) {
            is LoadResult.Loaded -> result.data
            LoadResult.Empty -> SaveData()
            is LoadResult.UnknownVersion -> {
                Log.w(TAG, "Discarding save with schema version ${result.version}")
                SaveData()
            }
            is LoadResult.Corrupt -> {
                Log.w(TAG, "Save file is unreadable, moving it to .bak", result.error)
                store.quarantine()
                SaveData()
            }
        }
    }

    /** The saved pad pose, if the player has confirmed one. */
    fun savedPadPose(): PadPose? {
        val p = save.padPose
        if (!p.has || p.pos.size < 3) return null
        return PadPose(Vec3(p.pos[0], p.pos[1], p.pos[2]), p.yaw)
    }

    fun setPadPose(pose: PadPose, confirmed: Boolean) {
        save = save.copy(
            padPose = PadPoseData(confirmed, listOf(pose.position.x, pose.position.y, pose.position.z), pose.yaw),
        )
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        when (event) {
            Lifecycle.Event.ON_PAUSE -> {
                logic.setPaused(true)
                Log.i(TAG, "paused tick=${logic.tickCount}")
                persist()
            }
            Lifecycle.Event.ON_RESUME -> {
                logic.setPaused(false)
                Log.i(TAG, "resumed tick=${logic.tickCount}")
            }
            else -> Unit
        }
    }

    /** Serializes on the calling thread and writes on a background thread. */
    fun persist() {
        val now = clock.nowUtcMillis()
        save = save.copy(lastSeenUtcMs = now, lastSimUtcMs = now)
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
