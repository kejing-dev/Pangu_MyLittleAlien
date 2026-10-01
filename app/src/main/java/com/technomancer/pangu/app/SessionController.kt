package com.technomancer.pangu.app

import android.util.Log
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.technomancer.pangu.logic.PanguLogic
import com.technomancer.pangu.logic.flow.FlowState
import com.technomancer.pangu.logic.incubation.toData
import com.technomancer.pangu.logic.habitat.PadPose
import com.technomancer.pangu.logic.math.Vec3
import com.technomancer.pangu.logic.pet.AwaySummary
import com.technomancer.pangu.logic.pet.MoodCalculator
import com.technomancer.pangu.logic.sim.CatchUp
import com.technomancer.pangu.logic.save.ISaveStore
import com.technomancer.pangu.logic.save.LoadResult
import com.technomancer.pangu.logic.save.PadPoseData
import com.technomancer.pangu.logic.save.PetData
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
    private val catchUp: CatchUp? = null,
    private val welcomeMinMinutes: Float = 60f,
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
                catchUpPause()
                logic.setPaused(false)
                ui.publishIfChanged(logic)
                Log.i(TAG, "resumed tick=${logic.tickCount}")
            }
            else -> Unit
        }
    }

    /** Replays the time the app spent in the background and shows Welcome back after a long absence. */
    private fun catchUpPause() {
        val cu = catchUp ?: return
        val now = clock.nowUtcMillis()
        val from = logic.nowMs
        if (now - from < 60_000L) return
        if (logic.flow.flow == FlowState.Incubation) {
            logic.catchUpEgg(cu, from, now, welcomeMinMinutes)
            return
        }
        if (!logic.isPetFlow || logic.pet.hatchMs == 0L) return
        val pet = logic.pet
        val before = pet.needs.values.copyOf()
        val crumbsBefore = pet.needs.crumbs
        cu.pet(pet.needs, pet.actions, from, now, pet.crumbs, pet.distress, MoodCalculator(pet.config), pet.modifiers, pet.sleepEndHour)
        pet.syncStage()
        logic.nowMs = now
        AwaySummary.of(before, pet.needs.values, crumbsBefore, pet.needs.crumbs, now - from, pet.config.welcomeBackMinMinutes)
            ?.let { logic.showWelcomeBack(it) }
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
        val pet = logic.pet
        val petData = if (pet.hatchMs > 0L) {
            PetData(
                hatchUtcMs = pet.hatchMs,
                needs = pet.needs.values.toList(),
                crumbs = pet.needs.crumbs,
                crumbPositions = pet.crumbs.positions.flatMap { listOf(it.x, it.z) },
                napEndUtcMs = pet.actions.napEndMs,
                distressHours = pet.distress.hours,
            )
        } else {
            save.pet
        }
        val eggData = if (logic.egg.egg.setMs > 0L) logic.egg.toData() else save.egg
        save = save.copy(
            flow = logic.flow.flow,
            screen = logic.screens.persistentScreen,
            keeperId = logic.keeperIds.keeperId,
            secretId = logic.keeperIds.secretId,
            look = logic.flow.look,
            lastLook = logic.flow.lastLook,
            egg = eggData,
            micChoice = logic.micChoice,
            personality = logic.pet.personality,
            padPose = padData,
            pet = petData,
            hatchedCount = logic.hatchedCount,
            dormantCount = logic.dormantCount,
            wentHomeCount = logic.wentHomeCount,
            lastSeenUtcMs = now,
            lastSimUtcMs = logic.nowMs,
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
