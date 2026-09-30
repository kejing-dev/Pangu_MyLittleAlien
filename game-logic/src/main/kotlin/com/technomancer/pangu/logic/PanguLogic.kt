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
import com.technomancer.pangu.logic.pet.AwaySummary
import com.technomancer.pangu.logic.pet.Distress
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
            ScreenId.C23, ScreenId.C24, ScreenId.C25, ScreenId.C26, ScreenId.C27, ScreenId.C28, ScreenId.C29,
            ScreenId.C30, ScreenId.C31, ScreenId.C32, ScreenId.C33, ScreenId.C35 -> true
            else -> false
        }

    /** Counters shown on the Dormant and Went home cards. */
    var hatchedCount = 0
    var dormantCount = 0
    var wentHomeCount = 0

    /** What changed while away, shown on C35. */
    var welcome: AwaySummary? = null
        private set

    /** The dance loop that should be playing from the pet, or null. */
    val danceMusic: Sfx?
        get() = if (pet.actions.activity == PetActivity.Dancing) {
            if (pet.body.danceSpins) Sfx.DanceSpin else Sfx.DanceWiggle
        } else {
            null
        }

    fun setPaused(paused: Boolean) {
        isPaused = paused
        if (paused) {
            // A bath or dance past halfway finishes with its full gain; otherwise it is cancelled.
            val activity = pet.actions.activity
            if (activity == PetActivity.Cleaning || activity == PetActivity.Dancing) {
                pet.actions.finishOrCancelOnPause(pet.needs)
                pet.endActivity()
                screens.show(ScreenId.C23)
            }
            screens.pause()
        } else {
            screens.resume()
        }
    }

    /** Re-shows the Mopey or Homesick card for a pet loaded in that state (they are not blocking, so they come back on relaunch). */
    fun announceDistress() {
        if (!isPetFlow) return
        when (pet.stage()) {
            Distress.Mopey -> screens.show(ScreenId.C31)
            Distress.Homesick -> screens.show(ScreenId.C32)
            else -> Unit
        }
    }

    /** Shows the Welcome back screen with what changed while the player was away. */
    fun showWelcomeBack(summary: AwaySummary) {
        welcome = summary
        screens.show(ScreenId.C35)
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
        motionBuffer.clear()
        motionBuffer += if (input.headValid) input.headPosition else null
        for (p in input.pokes) {
            if (p.valid) pokeBuffer += toPadLocal(p.position)
            motionBuffer += if (p.valid) p.position else null
        }
        pet.update(dt, nowMs, needsSim.isNight(clock.localHour(nowMs)), head, pokeBuffer, motionBuffer)
        if (pet.stage() == Distress.WentHome && pet.body.departed && screens.current != ScreenId.C33) {
            wentHomeCount++
            saveRequested = true
            screens.show(ScreenId.C33)
        }
        if (screens.current == ScreenId.C24) {
            pieIdleSec += dt
            if (pieIdleSec >= uiConfig.pieIdleCloseSec) screens.show(ScreenId.C23)
        }
    }

    private val pokeBuffer = ArrayList<Vec3>(4)
    private val motionBuffer = ArrayList<Vec3?>(5)

    /** Recent ball drag moves (pad-local delta, time) used to work out the throw velocity. */
    private val ballMoves = ArrayDeque<Pair<Vec3, Long>>()

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
                e.target == TargetId.BALL -> {
                    pet.grabBall()
                    ballMoves.clear()
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
                e.target == TargetId.BALL -> {
                    val d = toPadLocalDirection(e.delta)
                    pet.moveBall(d)
                    ballMoves.addLast(d to e.timeNanos)
                    while (ballMoves.size > THROW_SAMPLES) ballMoves.removeFirst()
                }
            }
            InteractionType.DragEnd -> when {
                e.target == TargetId.PAD_HANDLE && isDraggingPad -> {
                    isDraggingPad = false
                    saveRequested = true
                }
                TargetId.foodIndex(e.target) >= 0 -> pet.releaseFood(TargetId.foodIndex(e.target))
                e.target == TargetId.BALL -> pet.throwBall(throwVelocity(e.timeNanos))
            }
            InteractionType.Tap -> onTap(e.target)
            InteractionType.UiPrimary -> {
                haptic = HapticCue.UiPress
                sounds += Sfx.UiConfirm
                when (screens.current) {
                    ScreenId.C31, ScreenId.C32 -> openPie()
                    ScreenId.C33 -> {
                        pet.respawn(nowMs)
                        hatchedCount++
                        saveRequested = true
                        screens.show(ScreenId.C23)
                    }
                    ScreenId.C35 -> {
                        welcome = null
                        sounds += Sfx.Happy
                        screens.show(ScreenId.C23)
                    }
                    else -> Unit
                }
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
                    ScreenId.C23, ScreenId.C31, ScreenId.C32 -> openPie()
                    ScreenId.C33 -> Unit
                    else -> backToNeeds()
                }
            }
            TargetId.PET_TIP -> pet.boop(nowMs)
            in TargetId.CRUMB_FIRST until TargetId.CRUMB_FIRST + 10 -> if (pet.sweepCrumb(TargetId.crumbIndex(target))) saveRequested = true
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
            PetAction.Clean -> {
                pet.startClean()
                screens.show(ScreenId.C27)
            }
            PetAction.Dance -> {
                pet.startDance()
                screens.show(ScreenId.C28)
            }
            PetAction.Play -> {
                pet.startPlay()
                screens.show(ScreenId.C29)
            }
            null -> Unit
        }
    }

    private fun openPie() {
        pieIdleSec = 0f
        screens.show(ScreenId.C24)
        sounds += Sfx.Question
    }

    private fun backToNeeds() {
        pet.endActivity()
        screens.show(ScreenId.C23)
    }

    /** Average velocity of the last few ball drag moves, pad-local metres per second. */
    private fun throwVelocity(endNanos: Long): Vec3 {
        if (ballMoves.size < 2) return Vec3.ZERO
        val first = ballMoves.first().second
        val seconds = ((maxOf(endNanos, ballMoves.last().second) - first) / 1e9f).coerceAtLeast(0.02f)
        var sum = Vec3.ZERO
        for ((d, _) in ballMoves) sum = sum + d
        val v = sum / seconds
        return if (v.length() > MAX_THROW) v.normalized() * MAX_THROW else v
    }

    override fun sound(sfx: Sfx) {
        sounds += sfx
    }

    override fun toast(key: String) = showToast(key)

    override fun actionFinished(activity: PetActivity) {
        saveRequested = true
        if (activity != PetActivity.Idle && activity != PetActivity.Cuddling) backToNeeds()
    }

    override fun distressChanged(stage: Distress) {
        saveRequested = true
        when (stage) {
            Distress.Mopey -> screens.show(ScreenId.C31)
            Distress.Homesick -> screens.show(ScreenId.C32)
            Distress.WentHome -> Unit
            Distress.None -> if (screens.current == ScreenId.C31 || screens.current == ScreenId.C32) screens.show(ScreenId.C23)
        }
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
        private const val THROW_SAMPLES = 5
        private const val MAX_THROW = 3f
    }
}
