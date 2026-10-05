package com.technomancer.pangu.logic

import com.technomancer.pangu.logic.audio.Sfx
import com.technomancer.pangu.logic.config.IncubationConfig
import com.technomancer.pangu.logic.config.LookConfig
import com.technomancer.pangu.logic.config.PersonalityConfig
import com.technomancer.pangu.logic.incubation.MicChoice
import com.technomancer.pangu.logic.personality.PersonalityCalculator
import com.technomancer.pangu.logic.personality.SparkValue
import com.technomancer.pangu.logic.config.PetConfig
import com.technomancer.pangu.logic.config.UiConfig
import com.technomancer.pangu.logic.creation.KeeperIds
import com.technomancer.pangu.logic.creation.LookCatalog
import com.technomancer.pangu.logic.creation.NameWheels
import com.technomancer.pangu.logic.creation.PetLook
import com.technomancer.pangu.logic.creation.PreviewPose
import com.technomancer.pangu.logic.dev.DevCommand
import com.technomancer.pangu.logic.flow.FlowMachine
import com.technomancer.pangu.logic.flow.FlowState
import com.technomancer.pangu.logic.flow.ScreenEffect
import com.technomancer.pangu.logic.flow.ScreenId
import com.technomancer.pangu.logic.flow.ScreenMachine
import com.technomancer.pangu.logic.habitat.PadPlacement
import com.technomancer.pangu.logic.habitat.PadPose
import com.technomancer.pangu.logic.incubation.CapResult
import com.technomancer.pangu.logic.incubation.EggAction
import com.technomancer.pangu.logic.incubation.EggAway
import com.technomancer.pangu.logic.incubation.EggCondition
import com.technomancer.pangu.logic.incubation.EggEvents
import com.technomancer.pangu.logic.incubation.EggSuggestions
import com.technomancer.pangu.logic.incubation.EggWorld
import com.technomancer.pangu.logic.input.InputSnapshot
import com.technomancer.pangu.logic.input.InteractionEvent
import com.technomancer.pangu.logic.input.InteractionQueue
import com.technomancer.pangu.logic.input.InteractionType
import com.technomancer.pangu.logic.input.LookPick
import com.technomancer.pangu.logic.input.TargetId
import com.technomancer.pangu.logic.loc.Language
import com.technomancer.pangu.logic.math.Quat
import com.technomancer.pangu.logic.math.Vec3
import com.technomancer.pangu.logic.pet.AwaySummary
import com.technomancer.pangu.logic.pet.Distress
import com.technomancer.pangu.logic.pet.PetAction
import com.technomancer.pangu.logic.pet.PetActivity
import com.technomancer.pangu.logic.pet.PetEvents
import com.technomancer.pangu.logic.pet.PetWorld
import com.technomancer.pangu.logic.sim.CatchUp
import com.technomancer.pangu.logic.time.FixedClock
import com.technomancer.pangu.logic.time.IClock
import com.technomancer.pangu.logic.time.IRandom
import com.technomancer.pangu.logic.time.XorShift32

/** Controller haptic cues the logic asks the app to play. */
enum class HapticCue { UiPress, Grab, Heartbeat, Completion, Crack }

/**
 * Game logic facade, advanced once per frame by the frame System.
 * The app reads its public state after each [tick] to update entities and panels.
 */
class PanguLogic(
    private val placement: PadPlacement = PadPlacement(),
    startScreen: ScreenId = ScreenId.C02,
    afterPlacement: ScreenId = ScreenId.C03,
    petConfig: PetConfig = PetConfig(),
    private val uiConfig: UiConfig = UiConfig(),
    private val clock: IClock = FixedClock(),
    private val random: IRandom = XorShift32(1u),
    lookConfig: LookConfig = LookConfig(),
    incubationConfig: IncubationConfig = IncubationConfig(),
    private val personalityConfig: PersonalityConfig = PersonalityConfig(),
    /** Debug: placing the pad on first launch skips creation and hatches the default pet. */
    private val skipToPet: Boolean = false,
) : PetEvents, EggEvents {
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
    val catalog = LookCatalog(lookConfig)
    val wheels = NameWheels(lookConfig)
    val flow = FlowMachine(screens, catalog.default())
    val preview = PreviewPose(lookConfig, random)

    /** Private ids for the spark roll; the app loads or makes them at start. */
    var keeperIds = KeeperIds(0, 0)

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

    val pet = PetWorld(petConfig, random, PAD_RADIUS, this, placement.habitatScale, personalityConfig)
    val egg = EggWorld(incubationConfig, this, placement.habitatScale)
    private val calculator = PersonalityCalculator(personalityConfig)
    private var pieIdleSec = 0f

    /** The language of the device, set by the app at launch and whenever it resumes. */
    var language: Language = Language.En

    /** Settings: volume 0..100, sound and haptics. The app applies them each frame. */
    var volume: Int = 70
    var soundOn: Boolean = true
    var hapticsOn: Boolean = true

    /** How the player sings to the egg; asked the first time Sing is chosen. */
    var micChoice: MicChoice = MicChoice.Unknown

    /** Changes each time the app should ask for the microphone permission. */
    var micPermissionSerial: Int = 0
        private set

    /** True while the app should be reading the microphone (C12). */
    val micWanted: Boolean
        get() = screens.current == ScreenId.C12 && egg.rituals.active == EggAction.Sing && egg.rituals.singPath == MicChoice.Voice

    /** The pet's name, built from the look's syllables. */
    val petName: String get() = wheels.name(flow.look)

    val look: PetLook get() = flow.look

    private var saveRequested = false
    private var haptic: HapticCue? = null
    private val sounds = ArrayDeque<Sfx>()

    val isPetFlow: Boolean
        get() = flow.flow == FlowState.Pet || flow.flow == FlowState.WentHome

    /** Egg in the habitat: incubating, hatching or dormant. */
    val isEggFlow: Boolean
        get() = flow.flow == FlowState.Incubation || flow.flow == FlowState.Hatching || flow.flow == FlowState.Dormant

    /** The studio is open: the pad is hidden and the preview stands on the pedestal. */
    val isStudio: Boolean
        get() = when (screens.persistentScreen) {
            ScreenId.C03, ScreenId.C04, ScreenId.C05 -> true
            else -> false
        }

    /** Counters shown on the Dormant and Went home cards. */
    var hatchedCount = 0
    var dormantCount = 0
    var wentHomeCount = 0

    /** What changed while away, shown on C35: for the pet, or for the egg. */
    var welcome: AwaySummary? = null
        private set
    var eggWelcome: EggAway? = null
        private set

    /** The action named on C16 ("That's enough <action> today"). */
    var cappedAction: EggAction = EggAction.Warm
        private set

    /** The dance loop that should be playing from the pet, or null. */
    val danceMusic: Sfx?
        get() = if (isPetFlow && pet.actions.activity == PetActivity.Dancing) {
            if (pet.body.danceSpins) Sfx.DanceSpin else Sfx.DanceWiggle
        } else {
            null
        }

    fun setPaused(paused: Boolean) {
        isPaused = paused
        if (paused) {
            // A bath or dance past halfway finishes with its full gain; otherwise it is cancelled.
            val activity = pet.actions.activity
            if (isPetFlow && (activity == PetActivity.Cleaning || activity == PetActivity.Dancing)) {
                pet.actions.finishOrCancelOnPause(pet.needs)
                pet.endActivity()
                screens.show(ScreenId.C23)
            }
            if (egg.eggHeld) {
                egg.releaseEgg()
                screens.show(eggHomeScreen())
            }
            if (egg.rituals.active != null) {
                egg.rituals.stop()
                screens.show(eggHomeScreen())
            }
            screens.pause()
        } else {
            screens.resume()
        }
    }

    /** Re-shows a card for a pet or egg loaded in that state (they are not blocking, so they come back on relaunch). */
    fun announceState() {
        if (isPetFlow) {
            when (pet.stage()) {
                Distress.Mopey -> screens.show(ScreenId.C31)
                Distress.Homesick -> screens.show(ScreenId.C32)
                else -> Unit
            }
        } else if (flow.flow == FlowState.Incubation) {
            screens.show(eggHomeScreen())
        }
    }

    /** Shows the Welcome back screen with what changed for the pet while the player was away. */
    fun showWelcomeBack(summary: AwaySummary) {
        welcome = summary
        eggWelcome = null
        screens.show(ScreenId.C35)
        pet.welcomeBack()
    }

    /**
     * Replays egg time from [fromMs] to [toMs]. An egg that went quiet shows the Dormant card;
     * otherwise Welcome back after [welcomeMinMinutes] away, or the card for its condition.
     */
    fun catchUpEgg(catchUp: CatchUp, fromMs: Long, toMs: Long, welcomeMinMinutes: Float) {
        if (flow.flow != FlowState.Incubation) return
        val before = egg.sim.band(egg.egg.warmth)
        catchUp.egg(egg.egg, egg.sim, egg.ledger, fromMs, toMs)
        nowMs = toMs
        egg.syncCondition(toMs)
        if (egg.egg.dormant) {
            dormantCount++
            flow.goDormant()
            saveRequested = true
            return
        }
        val minutes = (toMs - fromMs) / 60_000L
        if (minutes >= welcomeMinMinutes) {
            showEggWelcomeBack(EggAway(before, egg.sim.band(egg.egg.warmth), egg.egg.lampHoursLeft(toMs), minutes))
        } else {
            announceState()
        }
    }

    /** Shows the egg's Welcome back screen. */
    fun showEggWelcomeBack(summary: EggAway) {
        eggWelcome = summary
        welcome = null
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
        events.drain { handle(it, input, dt) }
        if (padPose == null) return
        when {
            isStudio -> preview.update(dt)
            isEggFlow -> updateEgg(dt, input)
            isPetFlow -> updatePet(dt, input)
        }
        if (screens.current == ScreenId.C24 || screens.current == ScreenId.C08) {
            pieIdleSec += dt
            if (pieIdleSec >= uiConfig.pieIdleCloseSec) closePie()
        }
    }

    private fun updateEgg(dt: Float, input: InputSnapshot) {
        // The preview keeps breathing while it shrinks away during the seal.
        if (egg.sealSec >= 0f) preview.update(dt)
        fillSenses(input)
        val before = egg.hatchSec
        egg.update(dt, nowMs, clock.localDay(nowMs))
        val after = egg.hatchSec
        if (flow.flow == FlowState.Hatching && after >= 0f) {
            if (before < HATCH_LIFT_SEC && after >= HATCH_LIFT_SEC) sounds += Sfx.Fanfare
            if (before < HATCH_RISE_SEC && after >= HATCH_RISE_SEC) pet.body.kickAntenna(HATCH_BOING)
            if (after >= HATCH_RISE_SEC) {
                val head = if (input.headValid) toPadLocal(input.headPosition) else null
                pet.body.update(dt, PetActivity.Idle, head)
            }
        }
    }

    /** Head, fingertips and voice in metres in the pad's frame, for the rituals. */
    private fun fillSenses(input: InputSnapshot) {
        val s = egg.senses
        val scale = placement.habitatScale
        s.head = if (input.headValid) toPadLocal(input.headPosition) * scale else null
        s.headForward = if (input.headValid) toPadLocalDirection(input.headRotation.rotate(Vec3.FORWARD)).normalized() else null
        s.pokes.clear()
        for (p in input.pokes) if (p.valid) s.pokes += toPadLocal(p.position) * scale
        s.micRms = input.micRms
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
        pet.update(dt, nowMs, pet.isNight(clock.localHour(nowMs)), head, pokeBuffer, motionBuffer)
        if (pet.stage() == Distress.WentHome && pet.body.departed && flow.flow != FlowState.WentHome) {
            wentHomeCount++
            saveRequested = true
            flow.wentHome()
        }
    }

    private val pokeBuffer = ArrayList<Vec3>(4)
    private val motionBuffer = ArrayList<Vec3?>(5)

    /** Recent ball drag moves (pad-local delta, time) used to work out the throw velocity. */
    private val ballMoves = ArrayDeque<Pair<Vec3, Long>>()

    private fun handle(e: InteractionEvent, input: InputSnapshot, dt: Float) {
        when (e.type) {
            InteractionType.DragStart -> onDragStart(e.target)
            InteractionType.DragMove -> onDragMove(e, input, dt)
            InteractionType.DragEnd -> onDragEnd(e)
            InteractionType.Tap -> onTap(e.target)
            InteractionType.UiPrimary -> if (screens.current == ScreenId.C36) screens.closeSettings() else onPrimary()
            InteractionType.UiSecondary -> onSecondary()
            InteractionType.UiRecenter -> recenter(input)
            InteractionType.UiSettings -> {
                haptic = HapticCue.UiPress
                sounds += Sfx.UiTick
                screens.openSettings()
            }
            InteractionType.UiVolume -> {
                volume = e.target.coerceIn(0, 100)
                saveRequested = true
            }
            InteractionType.UiSound -> {
                soundOn = e.target == 1
                saveRequested = true
            }
            InteractionType.UiHaptics -> {
                hapticsOn = e.target == 1
                saveRequested = true
            }
            InteractionType.UiPie -> if (screens.current == ScreenId.C08) {
                chooseEgg(EggAction.entries.getOrNull(e.target))
            } else {
                choose(PetAction.entries.getOrNull(e.target))
            }
            InteractionType.UiLook -> pickLook(LookPick.row(e.target), LookPick.index(e.target))
            InteractionType.UiMic -> onMicAnswer(e.target == 1)
            InteractionType.UiAssist -> if (debugAssist) {
                if (egg.rituals.active != null) egg.rituals.completeNow() else if (screens.current == ScreenId.C09) warmCompleted(egg.ledger.complete(EggAction.Warm, nowMs, clock.localDay(nowMs)))
            }
            InteractionType.UiInfo -> if (isPetFlow && screens.current == ScreenId.C23) screens.show(ScreenId.C22)
            InteractionType.UiWheel -> if (screens.current == ScreenId.C04) {
                flow.look = wheels.cycle(flow.look, e.target.coerceIn(0, 2))
                haptic = HapticCue.UiPress
                sounds += Sfx.UiTick
            }
        }
    }

    private fun onDragStart(target: Int) {
        when {
            // The handle moves the habitat at any time; the new spot is saved when it is let go.
            target == TargetId.PAD_HANDLE && padPose != null -> {
                isDraggingPad = true
                haptic = HapticCue.Grab
            }
            target == TargetId.EGG && flow.flow == FlowState.Incubation -> if (egg.grabEgg()) {
                haptic = HapticCue.Grab
                if (screens.current != ScreenId.C34) screens.show(ScreenId.C09)
            }
            target == TargetId.BLANKET && flow.flow == FlowState.Incubation -> if (egg.grabBlanket(nowMs)) haptic = HapticCue.Grab
            target == TargetId.CRANK -> {
                egg.rituals.grabCrank()
                haptic = HapticCue.Grab
            }
            target == TargetId.PET_BODY && isStudio -> Unit
            !isPetFlow -> Unit
            TargetId.foodIndex(target) >= 0 -> {
                pet.grabFood(TargetId.foodIndex(target))
                haptic = HapticCue.Grab
            }
            target == TargetId.BALL -> {
                pet.grabBall()
                ballMoves.clear()
                haptic = HapticCue.Grab
            }
        }
    }

    private fun onDragMove(e: InteractionEvent, input: InputSnapshot, dt: Float) {
        when {
            e.target == TargetId.PAD_HANDLE && isDraggingPad -> {
                val pad = padPose ?: return
                val head = if (input.headValid) input.headPosition else pad.position
                padPose = placement.drag(pad, e.delta, head, e.viaRay)
            }
            e.target == TargetId.EGG -> egg.moveEgg(toPadLocalDirection(e.delta))
            e.target == TargetId.BLANKET -> egg.moveBlanket(toPadLocalDirection(e.delta))
            e.target == TargetId.CRANK -> egg.rituals.moveCrank(toPadLocalDirection(e.delta) * placement.habitatScale)
            e.target == TargetId.PET_BODY && isStudio -> preview.spin(toPadLocalDirection(e.delta).x * placement.habitatScale, dt)
            !isPetFlow -> Unit
            TargetId.foodIndex(e.target) >= 0 -> pet.moveFood(TargetId.foodIndex(e.target), toPadLocalDirection(e.delta))
            e.target == TargetId.BALL -> {
                val d = toPadLocalDirection(e.delta)
                pet.moveBall(d)
                ballMoves.addLast(d to e.timeNanos)
                while (ballMoves.size > THROW_SAMPLES) ballMoves.removeFirst()
            }
        }
    }

    private fun onDragEnd(e: InteractionEvent) {
        when {
            e.target == TargetId.PAD_HANDLE && isDraggingPad -> {
                isDraggingPad = false
                saveRequested = true
            }
            e.target == TargetId.EGG && egg.eggHeld -> {
                egg.releaseEgg()
                saveRequested = true
                if (screens.current == ScreenId.C09) screens.show(eggHomeScreen())
            }
            e.target == TargetId.BLANKET && egg.blanketHeld -> if (egg.releaseBlanket(nowMs, clock.localDay(nowMs))) saveRequested = true
            e.target == TargetId.CRANK -> egg.rituals.releaseCrank()
            e.target == TargetId.PET_BODY && isStudio -> preview.endSpin()
            !isPetFlow -> Unit
            TargetId.foodIndex(e.target) >= 0 -> pet.releaseFood(TargetId.foodIndex(e.target))
            e.target == TargetId.BALL -> pet.throwBall(throwVelocity(e.timeNanos))
        }
    }

    private fun onPrimary() {
        haptic = HapticCue.UiPress
        sounds += Sfx.UiConfirm
        when (screens.current) {
            ScreenId.C03 -> flow.toName()
            ScreenId.C04 -> flow.toConfirm()
            ScreenId.C05 -> setEgg()
            ScreenId.C10, ScreenId.C16 -> screens.show(eggHomeScreen())
            ScreenId.C11 -> micPermissionSerial++
            ScreenId.C22 -> screens.show(ScreenId.C23)
            ScreenId.C17, ScreenId.C18 -> {
                egg.lightLamp(nowMs)
                showToast("toast.lamp")
                saveRequested = true
                screens.show(ScreenId.C07)
            }
            ScreenId.C19, ScreenId.C33 -> {
                flow.startNewEgg()
                saveRequested = true
            }
            ScreenId.C31, ScreenId.C32 -> openPie()
            ScreenId.C35 -> {
                if (welcome != null) {
                    sounds += Sfx.Happy
                    screens.show(ScreenId.C23)
                } else {
                    screens.show(eggHomeScreen())
                }
                welcome = null
                eggWelcome = null
            }
            else -> Unit
        }
        if (screens.primary() == ScreenEffect.ConfirmPad) {
            isDraggingPad = false
            saveRequested = true
            val first = flow.flow == FlowState.Onboarding
            flow.padConfirmed()
            if (first && skipToPet) {
                flow.flow = FlowState.Pet
                pet.hatchNew(nowMs)
                screens.show(ScreenId.C23)
            }
        }
    }

    private fun onSecondary() {
        haptic = HapticCue.UiPress
        sounds += Sfx.UiTick
        when (screens.current) {
            ScreenId.C03 -> {
                val old = flow.look
                flow.look = catalog.randomize(random, wheels)
                if (flow.look.shape != old.shape) preview.morph(old.shape, flow.look.shape)
            }
            ScreenId.C04 -> flow.backToLook()
            ScreenId.C05 -> flow.backToName()
            ScreenId.C36 -> screens.closeSettings()
            ScreenId.C11 -> onMicAnswer(false)
            else -> Unit
        }
    }

    private fun pickLook(row: Int, index: Int) {
        if (screens.current != ScreenId.C03) return
        haptic = HapticCue.UiPress
        sounds += Sfx.UiTick
        val look = flow.look
        flow.look = when (row) {
            LookPick.BODY -> look.copy(body = index.coerceIn(0, catalog.swatchCount - 1))
            LookPick.TIP -> look.copy(tip = index.coerceIn(0, catalog.swatchCount - 1))
            else -> look.copy(shape = index.coerceIn(0, LookCatalog.SHAPES.size - 1))
        }
        if (flow.look.shape != look.shape) preview.morph(look.shape, flow.look.shape)
    }

    /** C05 Set egg: the egg is made with a fresh spark value and glides into the nest. */
    private fun setEgg() {
        val spark = flow.setEgg(nowMs, keeperIds)
        egg.setEgg(nowMs, spark)
        egg.startSeal()
        sounds += Sfx.Happy
        saveRequested = true
    }

    private fun onTap(target: Int) {
        when (target) {
            TargetId.CARE_ORB -> {
                sounds += Sfx.UiTick
                when {
                    isPetFlow -> when (screens.current) {
                        ScreenId.C23, ScreenId.C31, ScreenId.C32 -> openPie()
                        ScreenId.C33 -> Unit
                        else -> backToNeeds()
                    }
                    flow.flow == FlowState.Incubation -> when (screens.current) {
                        ScreenId.C07, ScreenId.C17, ScreenId.C18, ScreenId.C20 -> openEggPie()
                        ScreenId.C06, ScreenId.C09 -> Unit
                        else -> {
                            egg.rituals.stop()
                            screens.show(eggHomeScreen())
                        }
                    }
                    else -> showToast("toast.orb")
                }
            }
            TargetId.LAMP_BULB -> if (flow.flow == FlowState.Incubation) {
                egg.lightLamp(nowMs)
                sounds += Sfx.UiConfirm
                showToast("toast.lamp")
                saveRequested = true
            }
            TargetId.EGG -> if (flow.flow == FlowState.Incubation && egg.tapShell()) {
                haptic = HapticCue.Crack
                startHatch()
            } else if (flow.flow == FlowState.Incubation && egg.condition == EggCondition.Ready) {
                haptic = HapticCue.Crack
            }
            TargetId.PET_TIP, TargetId.PET_BODY -> when {
                isStudio -> {
                    preview.boop()
                    sounds += Sfx.Happy
                }
                !isPetFlow -> Unit
                target == TargetId.PET_TIP -> pet.boop(nowMs)
                pet.actions.activity == PetActivity.Napping -> {
                    pet.actions.wake()
                    sounds += Sfx.Sleepy
                    backToNeeds()
                }
                else -> sounds += Sfx.Happy
            }
            in TargetId.CRUMB_FIRST until TargetId.CRUMB_FIRST + 10 ->
                if (isPetFlow && pet.sweepCrumb(TargetId.crumbIndex(target))) saveRequested = true
        }
    }

    /** Five taps cracked the shell: the 8 s hatch begins and the designed pet is made in the nest. */
    private fun startHatch() {
        flow.startHatch()
        egg.startHatch()
        pet.hatchNew(nowMs)
        pet.applyPersonality(calculator.compute(egg.egg.sparkValue, keeperIds, egg.ledger.imprint))
        pet.body.holdStill = true
        saveRequested = true
    }

    private fun choose(action: PetAction?) {
        haptic = HapticCue.UiPress
        sounds += Sfx.UiTick
        action?.let { pet.interruptFor(it) }
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

    private fun chooseEgg(action: EggAction?) {
        haptic = HapticCue.UiPress
        sounds += Sfx.UiTick
        when (action) {
            EggAction.Warm -> if (egg.ledger.capped(EggAction.Warm, clock.localDay(nowMs))) {
                cappedAction = EggAction.Warm
                screens.show(ScreenId.C16)
            } else {
                screens.show(ScreenId.C09)
            }
            EggAction.Lamp -> {
                egg.lightLamp(nowMs)
                showToast("toast.lamp")
                saveRequested = true
                screens.show(ScreenId.C10)
            }
            EggAction.Blanket -> {
                showToast(if (egg.isTucked(nowMs)) "toast.blanketOn" else "toast.blanket")
                screens.show(eggHomeScreen())
            }
            EggAction.Sing, EggAction.Listen, EggAction.Polish -> startRitual(action)
            null -> Unit
        }
    }

    /** Puts the habitat back in front of the player and shows the recenter screen (C37). */
    private fun recenter(input: InputSnapshot) {
        haptic = HapticCue.UiPress
        padPose = if (input.headValid) {
            placement.defaultPose(input.headPosition, input.headRotation)
        } else {
            placement.fallbackPose()
        }
        screens.recenter()
    }

    /** Starts Sing, Listen or Polish, unless today's count is used up (C16). Sing asks how to sing the first time. */
    private fun startRitual(action: EggAction) {
        if (egg.ledger.capped(action, clock.localDay(nowMs))) {
            cappedAction = action
            screens.show(ScreenId.C16)
            return
        }
        when (action) {
            EggAction.Sing -> when (micChoice) {
                MicChoice.Unknown -> screens.show(ScreenId.C11)
                MicChoice.Voice -> {
                    egg.rituals.start(EggAction.Sing, MicChoice.Voice)
                    screens.show(ScreenId.C12)
                }
                MicChoice.MusicBox -> {
                    egg.rituals.start(EggAction.Sing, MicChoice.MusicBox)
                    screens.show(ScreenId.C13)
                }
            }
            EggAction.Listen -> {
                egg.rituals.start(EggAction.Listen)
                screens.show(ScreenId.C14)
            }
            else -> {
                egg.rituals.start(EggAction.Polish)
                screens.show(ScreenId.C15)
            }
        }
    }

    /** The microphone question was answered: sing with the voice, or wind the music box instead. */
    private fun onMicAnswer(granted: Boolean) {
        micChoice = if (granted) MicChoice.Voice else MicChoice.MusicBox
        saveRequested = true
        if (flow.flow == FlowState.Incubation && (screens.current == ScreenId.C11 || screens.current == ScreenId.C12)) {
            egg.rituals.start(EggAction.Sing, micChoice)
            screens.show(if (granted) ScreenId.C12 else ScreenId.C13)
        }
    }

    /** Debug builds running in the Emulator show "Complete step" on ritual panels. */
    var debugAssist = false

    /** Debug builds switch this on for their Dev Tools menu; while it is off [dev] does nothing. */
    var devTools = false

    /**
     * Runs a testing shortcut. Ignored unless [devTools] is on, and until the habitat is placed
     * (every stage needs the pad).
     */
    fun dev(command: DevCommand) {
        if (!devTools || padPose == null || screens.isPlacingPad) return
        screens.closeSettings()
        when (command) {
            DevCommand.Customize -> {
                devLeaveStage()
                flow.startNewEgg()
            }
            DevCommand.RestartEgg -> devNewEgg()
            DevCommand.EggReady -> devEggReady()
            DevCommand.HatchNow -> {
                devEggReady()
                startHatch()
            }
            DevCommand.Hatched -> devHatched()
            DevCommand.EggCold -> devEgg { egg.egg.warmth = egg.config.chillyThreshold * 0.5f }
            DevCommand.EggWarm -> devEgg {
                egg.egg.warmth = 100f
                egg.egg.coldHours = 0f
            }
            DevCommand.EggDormant -> {
                devEgg { egg.egg.dormant = true }
                dormantCount++
                flow.goDormant()
            }
            DevCommand.ClearRitualCaps -> if (flow.flow == FlowState.Incubation) {
                egg.ledger.completionsToday.fill(0)
                egg.ledger.lastCompletionMs.fill(0L)
                egg.ledger.blanketTucksToday = 0
            }
            DevCommand.FillNeeds -> devNeeds(100f)
            DevCommand.DrainNeeds -> devNeeds(8f)
            DevCommand.PetMopey -> devDistress(pet.config.mopeyHours)
            DevCommand.PetHomesick -> devDistress(pet.config.homesickHours)
            DevCommand.PetWentHome -> devDistress(pet.config.departHours)
            DevCommand.PetCheerUp -> if (isPetFlow) {
                pet.distress.hours = 0f
                pet.syncStage()
                if (flow.flow == FlowState.WentHome) flow.flow = FlowState.Pet
                screens.show(ScreenId.C23)
            }
            DevCommand.ShowPersonality -> if (flow.flow == FlowState.Pet) screens.show(ScreenId.C22)
        }
        saveRequested = true
    }

    /** Puts away whatever the current stage has out, before a shortcut jumps to another stage. */
    private fun devLeaveStage() {
        pet.endActivity()
        pet.body.holdStill = false
        egg.rituals.stop()
        if (egg.eggHeld) egg.releaseEgg()
    }

    /** A fresh egg with the current look, already in the nest. */
    private fun devNewEgg() {
        devLeaveStage()
        flow.lastLook = flow.look
        flow.flow = FlowState.Incubation
        egg.setEgg(nowMs, SparkValue.roll(nowMs, keeperIds))
        screens.show(ScreenId.C07)
    }

    /** Changes the egg (a fresh one if none is incubating) and shows the screen for its new condition. */
    private fun devEgg(change: () -> Unit) {
        if (flow.flow != FlowState.Incubation) devNewEgg()
        egg.rituals.stop()
        change()
        egg.syncCondition(nowMs)
        screens.show(eggHomeScreen())
    }

    /** The egg has had all its warm hours; an egg with no care history gets a mixed one so its pet has a personality to show. */
    private fun devEggReady() = devEgg {
        val e = egg.egg
        e.hatchProgressHours = egg.config.hatchWarmHours
        e.warmth = 100f
        e.coldHours = 0f
        e.crackTaps = 0
        val imprint = egg.ledger.imprint
        if (imprint.all { it == 0 }) DEV_IMPRINT.forEachIndexed { i, v -> if (i < imprint.size) imprint[i] = v }
    }

    /** A freshly hatched pet on the pad, as if the hatch had just finished, with its needs at their starting values. */
    private fun devHatched() {
        devEggReady()
        flow.flow = FlowState.Pet
        pet.hatchNew(nowMs)
        pet.applyPersonality(calculator.compute(egg.egg.sparkValue, keeperIds, egg.ledger.imprint))
        pet.body.settleAfterHatch()
        hatchedCount++
        screens.show(ScreenId.C23)
    }

    private fun devNeeds(value: Float) {
        if (flow.flow != FlowState.Pet) return
        pet.needs.values.fill(value)
    }

    /** Sets how long the pet has been unhappy, which decides whether it is mopey, homesick or leaving. */
    private fun devDistress(hours: Float) {
        if (flow.flow != FlowState.Pet) return
        pet.endActivity()
        pet.distress.hours = hours
        pet.syncStage()
        announceState()
    }

    /** The egg's resting screen for its condition. */
    fun eggHomeScreen(): ScreenId = when (egg.condition) {
        EggCondition.Dormant -> ScreenId.C19
        EggCondition.Ready -> ScreenId.C20
        EggCondition.Fading -> ScreenId.C18
        EggCondition.Chilly -> ScreenId.C17
        else -> ScreenId.C07
    }

    /** The egg action the pie highlights. */
    fun eggSuggestion(): EggAction? = EggSuggestions.forEgg(egg.sim.band(egg.egg.warmth), egg.ledger)

    private fun openPie() {
        pieIdleSec = 0f
        screens.show(ScreenId.C24)
        sounds += Sfx.Question
    }

    private fun openEggPie() {
        pieIdleSec = 0f
        screens.show(ScreenId.C08)
    }

    private fun closePie() {
        if (screens.current == ScreenId.C08) screens.show(eggHomeScreen()) else screens.show(ScreenId.C23)
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

    override fun heartbeat() {
        sounds += Sfx.Heartbeat
        haptic = HapticCue.Heartbeat
    }

    override fun warmCompleted(result: CapResult) {
        when (result) {
            CapResult.Counted -> {
                sounds += Sfx.Happy
                haptic = HapticCue.Completion
                showToast("toast.warmDone")
                saveRequested = true
            }
            CapResult.DailyCap -> {
                cappedAction = EggAction.Warm
                screens.show(ScreenId.C16)
            }
            CapResult.Cooldown -> showToast("toast.cooldown")
        }
    }

    override fun ritualDone(action: EggAction) {
        when (val result = egg.ledger.complete(action, nowMs, clock.localDay(nowMs))) {
            CapResult.Counted -> {
                sounds += Sfx.Sparkle
                haptic = HapticCue.Completion
                saveRequested = true
                screens.show(eggHomeScreen())
            }
            CapResult.DailyCap -> {
                cappedAction = action
                screens.show(ScreenId.C16)
            }
            CapResult.Cooldown -> {
                showToast("toast.cooldown")
                screens.show(eggHomeScreen())
            }
        }
    }

    override fun conditionChanged(condition: EggCondition) {
        saveRequested = true
        when (condition) {
            EggCondition.Dormant -> {
                if (egg.eggHeld) egg.releaseEgg()
                dormantCount++
                sounds += Sfx.DormantChime
                flow.goDormant()
                return
            }
            EggCondition.Ready -> sounds += Sfx.Happy
            EggCondition.Chilly, EggCondition.Fading -> sounds += Sfx.Sad
            else -> Unit
        }
        when (screens.current) {
            ScreenId.C07, ScreenId.C17, ScreenId.C18, ScreenId.C20 -> screens.show(eggHomeScreen())
            else -> Unit
        }
    }

    override fun sealFinished() {
        flow.sealDone()
        screens.show(eggHomeScreen())
        saveRequested = true
    }

    override fun hatchFinished() {
        pet.body.holdStill = false
        pet.body.settleAfterHatch()
        hatchedCount++
        flow.hatched()
        sounds += Sfx.Happy
        saveRequested = true
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

        /** Hatch timeline: the seam lights, the top lifts, then the hatchling rises. */
        const val HATCH_SEAM_SEC = 3f
        const val HATCH_LIFT_SEC = 4.5f
        const val HATCH_RISE_SEC = 6f
        private const val HATCH_BOING = 4f

        /** Care history a dev-made egg gets when it has none: a few points on each trait. */
        private val DEV_IMPRINT = intArrayOf(6, 3, 2, 8, 4)
    }
}
