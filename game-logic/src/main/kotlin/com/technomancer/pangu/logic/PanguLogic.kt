package com.technomancer.pangu.logic

import com.technomancer.pangu.logic.audio.Sfx
import com.technomancer.pangu.logic.config.PetConfig
import com.technomancer.pangu.logic.config.UiConfig
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
import com.technomancer.pangu.logic.math.Quat
import com.technomancer.pangu.logic.math.Vec3
import com.technomancer.pangu.logic.pet.NeedsSim
import com.technomancer.pangu.logic.pet.PetAction
import com.technomancer.pangu.logic.pet.PetActivity
import com.technomancer.pangu.logic.pet.PetEvents
import com.technomancer.pangu.logic.pet.PetWorld
import com.technomancer.pangu.logic.time.FixedClock
import com.technomancer.pangu.logic.time.IClock
import com.technomancer.pangu.logic.time.IRandom
import com.technomancer.pangu.logic.time.XorShift32

/** Controller haptic cues the logic asks the app to play. */
enum class HapticCue { UiPress, Grab }

/**
 * Game logic facade, advanced once per frame by the frame System.
 * The app reads its public state after each [tick] to update entities and panels.
 */
class PanguLogic(
    private val placement: PadPlacement = PadPlacement(),
    startScreen: ScreenId = ScreenId.C02,
    afterPlacement: ScreenId = ScreenId.C23,
    petConfig: PetConfig = PetConfig(),
    private val uiConfig: UiConfig = UiConfig(),
    private val clock: IClock = FixedClock(),
    random: IRandom = XorShift32(1u),
) : PetEvents {
    /** Frames advanced while not paused. */
    var tickCount: Long = 0L
        private set

    /** Simulated seconds advanced while not paused. */
    var elapsedSec: Double = 0.0
        private set

    /** Simulated wall-clock time, epoch ms. Set from the device clock at start and after catch-up. */
    var nowMs: Long = clock.nowUtcMillis()

    @Volatile
    var isPaused: Boolean = false
        private set

    val screens = ScreenMachine(startScreen, afterPlacement)

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

    val pet = PetWorld(petConfig, random, PAD_RADIUS, this, placement.habitatScale)
    private val needsSim = NeedsSim(petConfig)
    private var pieIdleSec = 0f

    /** Pet name; the name wheels arrive with creation. */
    var petName: String = "Bobabo"

    private var saveRequested = false
    private var haptic: HapticCue? = null
    private val sounds = ArrayDeque<Sfx>()

    val isPetFlow: Boolean
        get() = when (screens.persistentScreen) {
            ScreenId.C23, ScreenId.C24, ScreenId.C25, ScreenId.C26, ScreenId.C30 -> true
            else -> false
        }

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
        nowMs += (dt * 1000).toLong()
        events.drain { handle(it, input) }
        if (isPetFlow && padPose != null) updatePet(dt, input)
    }

    private fun updatePet(dt: Float, input: InputSnapshot) {
        if (pet.hatchMs == 0L) pet.hatchMs = nowMs
        val head = if (input.headValid) toPadLocal(input.headPosition) else null
        pokeBuffer.clear()
        for (p in input.pokes) if (p.valid) pokeBuffer += toPadLocal(p.position)
        pet.update(dt, nowMs, needsSim.isNight(clock.localHour(nowMs)), head, pokeBuffer)
        if (screens.current == ScreenId.C24) {
            pieIdleSec += dt
            if (pieIdleSec >= uiConfig.pieIdleCloseSec) screens.show(ScreenId.C23)
        }
    }

    private val pokeBuffer = ArrayList<Vec3>(4)

    private fun handle(e: InteractionEvent, input: InputSnapshot) {
        when (e.type) {
            InteractionType.DragStart -> when {
                e.target == TargetId.PAD_HANDLE && screens.isPlacingPad -> {
                    isDraggingPad = true
                    haptic = HapticCue.Grab
                }
                TargetId.foodIndex(e.target) >= 0 -> {
                    pet.grabFood(TargetId.foodIndex(e.target))
                    haptic = HapticCue.Grab
                }
            }
            InteractionType.DragMove -> when {
                e.target == TargetId.PAD_HANDLE && isDraggingPad -> {
                    val pad = padPose ?: return
                    val head = if (input.headValid) input.headPosition else pad.position
                    padPose = placement.drag(pad, e.delta, head)
                }
                TargetId.foodIndex(e.target) >= 0 -> pet.moveFood(TargetId.foodIndex(e.target), toPadLocalDirection(e.delta))
            }
            InteractionType.DragEnd -> when {
                e.target == TargetId.PAD_HANDLE && isDraggingPad -> {
                    isDraggingPad = false
                    saveRequested = true
                }
                TargetId.foodIndex(e.target) >= 0 -> pet.releaseFood(TargetId.foodIndex(e.target))
            }
            InteractionType.Tap -> onTap(e.target)
            InteractionType.UiPrimary -> {
                haptic = HapticCue.UiPress
                sounds += Sfx.UiConfirm
                if (screens.primary() == ScreenEffect.ConfirmPad) {
                    isDraggingPad = false
                    saveRequested = true
                    if (isPetFlow && pet.hatchMs == 0L) pet.hatchMs = nowMs
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
            InteractionType.UiPie -> choose(PetAction.entries.getOrNull(e.target))
        }
    }

    private fun onTap(target: Int) {
        when (target) {
            TargetId.CARE_ORB -> {
                sounds += Sfx.UiTick
                if (!isPetFlow) {
                    showToast("toast.orb")
                    return
                }
                when (screens.current) {
                    ScreenId.C23 -> {
                        pieIdleSec = 0f
                        screens.show(ScreenId.C24)
                        sounds += Sfx.Question
                    }
                    else -> backToNeeds()
                }
            }
            TargetId.PET_TIP -> pet.boop(nowMs)
            TargetId.PET_BODY -> if (pet.actions.activity == PetActivity.Napping) {
                pet.actions.wake()
                sounds += Sfx.Sleepy
                backToNeeds()
            } else {
                sounds += Sfx.Happy
            }
        }
    }

    private fun choose(action: PetAction?) {
        haptic = HapticCue.UiPress
        sounds += Sfx.UiTick
        when (action) {
            PetAction.Eat -> {
                pet.showTray(true)
                screens.show(ScreenId.C25)
            }
            PetAction.Sleep -> {
                pet.actions.startNap(nowMs)
                sounds += Sfx.Sleepy
                screens.show(ScreenId.C26)
                saveRequested = true
            }
            PetAction.Cuddle -> {
                pet.actions.startCuddle()
                screens.show(ScreenId.C30)
            }
            PetAction.Clean, PetAction.Dance, PetAction.Play, null -> showToast("toast.soon")
        }
    }

    private fun backToNeeds() {
        pet.actions.stopCuddle()
        pet.showTray(false)
        screens.show(ScreenId.C23)
    }

    override fun sound(sfx: Sfx) {
        sounds += sfx
    }

    override fun toast(key: String) = showToast(key)

    override fun actionFinished(activity: PetActivity) {
        saveRequested = true
        if (activity == PetActivity.Eating || activity == PetActivity.Napping) backToNeeds()
    }

    /** Stage-space point to pad-local metres (undoing the pad's position, yaw and display scale). */
    fun toPadLocal(stage: Vec3): Vec3 {
        val pad = padPose ?: return stage
        return Quat.fromYaw(-pad.yaw).rotate(stage - pad.position) / placement.habitatScale
    }

    private fun toPadLocalDirection(stage: Vec3): Vec3 {
        val pad = padPose ?: return stage
        return Quat.fromYaw(-pad.yaw).rotate(stage) / placement.habitatScale
    }

    private fun showToast(key: String) {
        toastKey = key
        toastSerial++
    }

    /** True once after something worth saving changed. */
    fun consumeSaveRequest(): Boolean = saveRequested.also { saveRequested = false }

    fun consumeHaptic(): HapticCue? = haptic.also { haptic = null }

    /** Next sound to play, or null. */
    fun nextSound(): Sfx? = sounds.removeFirstOrNull()

    companion object {
        /** Pad radius in pad-local metres (before the display scale). */
        const val PAD_RADIUS = 0.25f
    }
}
