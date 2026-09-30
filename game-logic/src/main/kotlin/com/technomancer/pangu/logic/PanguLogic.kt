package com.technomancer.pangu.logic

import com.technomancer.pangu.logic.flow.ScreenEffect
import com.technomancer.pangu.logic.flow.ScreenId
import com.technomancer.pangu.logic.flow.ScreenMachine
import com.technomancer.pangu.logic.habitat.PadPlacement
import com.technomancer.pangu.logic.habitat.PadPose
import com.technomancer.pangu.logic.input.InputSnapshot
import com.technomancer.pangu.logic.input.InteractionEvent
import com.technomancer.pangu.logic.input.InteractionQueue
import com.technomancer.pangu.logic.input.InteractionType
import com.technomancer.pangu.logic.input.TargetId

/** Controller haptic cues the logic asks the app to play. */
enum class HapticCue { UiPress, Grab }

/**
 * Game logic facade, advanced once per frame by the frame System.
 * The app reads its public state after each [tick] to update entities and panels.
 */
class PanguLogic(
    private val placement: PadPlacement = PadPlacement(),
    startScreen: ScreenId = ScreenId.C02,
) {
    /** Frames advanced while not paused. */
    var tickCount: Long = 0L
        private set

    /** Simulated seconds advanced while not paused. */
    var elapsedSec: Double = 0.0
        private set

    @Volatile
    var isPaused: Boolean = false
        private set

    val screens = ScreenMachine(startScreen)

    /** Pad pose in Stage space; null until the app has placed the habitat. */
    var padPose: PadPose? = null
        private set

    /** True from a handle drag start until its end. */
    var isDraggingPad: Boolean = false
        private set

    /** String key of the toast to show, and a counter that changes every time a toast is requested. */
    var toastKey: String? = null
        private set
    var toastSerial: Int = 0
        private set

    /** Set when something worth saving changed; the app clears it with [consumeSaveRequest]. */
    private var saveRequested = false

    /** Latest haptic cue for the app to play; cleared with [consumeHaptic]. */
    private var haptic: HapticCue? = null

    fun setPaused(paused: Boolean) {
        isPaused = paused
        if (paused) screens.pause() else screens.resume()
    }

    fun placePad(pose: PadPose) {
        padPose = pose
    }

    /** Places the pad at [pose] and shows the recenter screen, e.g. when a saved pad is too far away. */
    fun replacePad(pose: PadPose) {
        padPose = pose
        screens.recenter()
    }

    fun tick(dt: Float, input: InputSnapshot, events: InteractionQueue) {
        if (isPaused) {
            events.drain { }
            return
        }
        tickCount++
        elapsedSec += dt
        events.drain { handle(it, input) }
    }

    private fun handle(e: InteractionEvent, input: InputSnapshot) {
        when (e.type) {
            InteractionType.DragStart -> if (e.target == TargetId.PAD_HANDLE && screens.isPlacingPad) {
                isDraggingPad = true
                haptic = HapticCue.Grab
            }
            InteractionType.DragMove -> if (e.target == TargetId.PAD_HANDLE && isDraggingPad) {
                val pad = padPose ?: return
                val head = if (input.headValid) input.headPosition else pad.position
                padPose = placement.drag(pad, e.delta, head)
            }
            InteractionType.DragEnd -> if (e.target == TargetId.PAD_HANDLE && isDraggingPad) {
                isDraggingPad = false
                saveRequested = true
            }
            InteractionType.Tap -> if (e.target == TargetId.CARE_ORB) {
                showToast("toast.orb")
            }
            InteractionType.UiPrimary -> {
                haptic = HapticCue.UiPress
                if (screens.primary() == ScreenEffect.ConfirmPad) {
                    isDraggingPad = false
                    saveRequested = true
                }
            }
            InteractionType.UiSecondary -> haptic = HapticCue.UiPress
            InteractionType.UiRecenter -> {
                haptic = HapticCue.UiPress
                padPose = if (input.headValid) {
                    placement.defaultPose(input.headPosition, input.headRotation)
                } else {
                    placement.fallbackPose()
                }
                screens.recenter()
            }
        }
    }

    private fun showToast(key: String) {
        toastKey = key
        toastSerial++
    }

    /** True once after the pad was confirmed or released. */
    fun consumeSaveRequest(): Boolean = saveRequested.also { saveRequested = false }

    fun consumeHaptic(): HapticCue? = haptic.also { haptic = null }
}
