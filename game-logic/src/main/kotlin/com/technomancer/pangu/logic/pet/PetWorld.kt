package com.technomancer.pangu.logic.pet

import com.technomancer.pangu.logic.art.AlienParts
import com.technomancer.pangu.logic.audio.Sfx
import com.technomancer.pangu.logic.config.PetConfig
import com.technomancer.pangu.logic.gestures.RubDetector
import com.technomancer.pangu.logic.config.PersonalityConfig
import com.technomancer.pangu.logic.math.Vec3
import com.technomancer.pangu.logic.personality.Personality
import com.technomancer.pangu.logic.personality.PersonalityEffects
import com.technomancer.pangu.logic.personality.Quirk
import kotlin.math.atan2
import kotlin.math.sin
import com.technomancer.pangu.logic.time.IRandom
import kotlin.math.sqrt

/** One snack on the tray. Positions are pad-local metres. */
class Food(val index: Int, val home: Vec3) {
    var position: Vec3 = home
    var held = false
    var freeSec = 0f
    var eaten = false

    fun reset() {
        position = home
        held = false
        freeSec = 0f
        eaten = false
    }
}

/** Events the pet world reports back to the game for sound, screens and toasts. */
interface PetEvents {
    fun sound(sfx: Sfx)
    fun toast(key: String)
    fun actionFinished(activity: PetActivity)
    fun distressChanged(stage: Distress)
}

/**
 * The hatched pet: needs, timed actions, body motion, snacks, the tub, the ball, crumbs,
 * autonomy and distress. Everything is in pad-local coordinates (metres before the habitat's
 * display scale); the caller converts input into that space.
 */
class PetWorld(
    val config: PetConfig,
    random: IRandom,
    padRadius: Float,
    private val events: PetEvents,
    /** The habitat is drawn this many times larger than its pad-local units. */
    private val displayScale: Float = 1f,
    private val personalityConfig: PersonalityConfig = PersonalityConfig(),
) {
    val needs = Needs(config.startNeeds)
    val actions = PetActions(config)
    val body = PetBody(config, random, padRadius)
    val crumbs = CrumbSpawner(config, random, padRadius)
    val ball = BallSim(padRadius)
    val distress = DistressTracker(config)
    private val autonomy = AutonomyPlanner(config)
    private val needsSim = NeedsSim(config)
    private val mood = MoodCalculator(config)
    private val stroke = RubDetector(maxDistance = RUB_DISTANCE, minSpeed = RUB_MIN_SPEED)
    private val scrub = RubDetector(maxDistance = RUB_DISTANCE * 2f, minSpeed = RUB_MIN_SPEED)
    val motion = MotionEnergyMeter()

    /** Simulated epoch ms when the pet hatched; drives growth. */
    var hatchMs = 0L

    /** Who this pet is; set at hatch and on load. */
    var personality: Personality = Personality.DEFAULT
        private set

    /** Need decay and action gain multipliers from the personality. */
    var modifiers: NeedModifiers = NeedModifiers.IDENTITY
        private set

    /** Local hour night sleep ends (earlier for an Early Riser). */
    var sleepEndHour: Int = config.sleepEndHour
        private set

    private var watchClock = 0f

    val foods = List(FOOD_COUNT) { i -> Food(i, Vec3(TRAY_CENTRE.x - 0.05f + i * 0.025f, TRAY_CENTRE.y + 0.012f, TRAY_CENTRE.z)) }

    /** Visibility of the snack tray, the tub and the ball. */
    var trayOut = false
        private set
    var tubOut = false
        private set
    var ballOut = false
        private set

    /** True after a bath while the crumbs should pulse to invite sweeping. */
    var sweepInvite = false
        private set

    var moodScore = 0f
        private set
    private var stage = Distress.None

    /** Fetches this play session, for the panel. */
    var fetches = 0
        private set

    /** Metres per model unit right now: adult height, times growth since hatching. */
    fun scale(nowMs: Long): Float {
        val hours = ((nowMs - hatchMs) / 3_600_000f).coerceAtLeast(0f)
        val growth = config.hatchGrowth + (1f - config.hatchGrowth) * (hours / config.growthHours).coerceIn(0f, 1f)
        return config.adultHeight / AlienParts.MODEL_HEIGHT * personality.sizeFactor * growth
    }

    /** Pad-local units per model unit: the real size divided by the habitat display scale. */
    fun localScale(nowMs: Long): Float = scale(nowMs) / displayScale

    fun mood(): Mood = mood.mood(moodScore)

    /** Gives the pet its personality: need and action multipliers, dance style and quirks. */
    fun applyPersonality(p: Personality) {
        personality = p
        modifiers = PersonalityEffects.modifiers(p, personalityConfig)
        actions.modifiers = modifiers
        crumbs.intervalMultiplier = modifiers.crumbInterval
        body.danceSpins = p.danceSpins
        val c = personalityConfig
        sleepEndHour = if (p.quirk == Quirk.EarlyRiser) c.earlyRiserHour else config.sleepEndHour
        body.hiccups = p.quirk == Quirk.SparkleHiccups
        body.hiccupHeight = c.hiccupHeight / displayScale
        body.hiccupMinSec = c.hiccupMinSec
        body.hiccupMaxSec = c.hiccupMaxSec
        body.blinkIntervalScale = if (p.quirk == Quirk.SlowBlinker) c.slowBlinkInterval else 1f
        body.blinkDurationScale = if (p.quirk == Quirk.SlowBlinker) c.slowBlinkDuration else 1f
    }

    /** Night sleep, ending at this pet's wake-up hour. */
    fun isNight(localHour: Int): Boolean = needsSim.isNight(localHour, sleepEndHour)

    /** Waves hello if this pet has the Welcome Wave quirk. */
    fun welcomeBack() {
        if (personality.quirk == Quirk.WelcomeWave) body.wave()
    }

    fun stage(): Distress = stage

    fun showTray(show: Boolean) {
        trayOut = show
        if (!show) foods.forEach { it.reset() }
    }

    fun startClean() {
        tubOut = true
        sweepInvite = false
        scrub.reset()
        actions.startClean()
        body.goal = TUB_CENTRE
    }

    fun startDance() {
        motion.reset()
        actions.startDance()
        events.sound(if (body.danceSpins) Sfx.DanceSpin else Sfx.DanceWiggle)
    }

    fun startPlay(efficiency: Float = 1f) {
        if (!ballOut) ball.reset()
        ballOut = true
        fetches = 0
        actions.startPlay(efficiency)
    }

    /** Closes whatever open-ended activity is running (cuddle, play) and hides props. */
    fun endActivity() {
        actions.stopOpenEnded()
        showTray(false)
        ballOut = false
        body.goal = null
    }

    fun update(dt: Float, nowMs: Long, isNight: Boolean, headLocal: Vec3?, pokes: List<Vec3>, motionPoints: List<Vec3?>) {
        if (stage == Distress.WentHome) {
            body.update(dt, actions.activity, headLocal, stage)
            return
        }
        val activity = actions.activity
        val asleep = isNight || activity == PetActivity.Napping
        val hours = dt / 3600f
        needsSim.step(needs, hours, asleep, modifiers)
        if (crumbs.update(hours, !asleep)) needs.crumbs = crumbs.positions.size

        if (activity == PetActivity.Dancing) motion.add(motionPoints, dt)
        val scrubbing = activity == PetActivity.Cleaning && rubbing(scrub, nowMs, dt, pokes, minModelY = 0f)
        if (actions.update(needs, nowMs, dt, scrubbing) { motion.averageSpeed >= config.danceAlongSpeed }) {
            onFinished(activity)
        }

        updateAutonomy(dt, nowMs)
        if (actions.activity == PetActivity.Playing || (ballOut && !ball.held)) updateBall(dt)
        updateQuirks(dt, isNight, headLocal)
        body.update(dt, actions.activity, facingFor(headLocal, pokes), stage)
        updateFoods(dt, nowMs)
        if (actions.activity == PetActivity.Cuddling &&
            rubbing(stroke, nowMs, dt, pokes, config.strokeMinModelY) &&
            actions.stroke(needs, dt, nowMs) > 0f
        ) {
            events.sound(Sfx.Happy)
        }

        moodScore = mood.score(needs)
        distress.update(hours, moodScore)
        val newStage = distress.stage()
        if (newStage != stage) {
            stage = newStage
            if (stage == Distress.WentHome) {
                endActivity()
                events.sound(Sfx.WentHome)
            } else if (stage != Distress.None) {
                events.sound(Sfx.Sad)
            }
            events.distressChanged(stage)
        }
    }

    /** Antenna Radar points the antenna at the player; Night Watcher looks at the sky now and then at night. */
    private fun updateQuirks(dt: Float, isNight: Boolean, headLocal: Vec3?) {
        val c = personalityConfig
        body.antennaRest = if (personality.quirk == Quirk.AntennaRadar && headLocal != null) {
            val rel = atan2(headLocal.x - body.position.x, headLocal.z - body.position.z) - body.yaw
            // The antenna swings sideways, so lean it toward whichever side the player is on.
            (-sin(rel) * RADAR_LEAN).coerceIn(-RADAR_LEAN, RADAR_LEAN)
        } else {
            0f
        }
        if (personality.quirk == Quirk.NightWatcher && isNight && actions.activity != PetActivity.Napping) {
            watchClock += dt
            if (watchClock >= c.watchEverySec) watchClock -= c.watchEverySec
            body.watchingSky = watchClock < c.watchSec
        } else {
            body.watchingSky = false
        }
    }

    /** What the pet turns to face: the player's head, or for a Hand Follower the nearest hand within reach. */
    private fun facingFor(headLocal: Vec3?, pokes: List<Vec3>): Vec3? {
        if (personality.quirk != Quirk.HandFollower) return headLocal
        val reach = personalityConfig.handFollowDistance / displayScale
        var best: Vec3? = null
        var bestDistance = reach
        for (p in pokes) {
            val d = p.distanceTo(body.position)
            if (d < bestDistance) {
                bestDistance = d
                best = p
            }
        }
        return best ?: headLocal
    }

    private fun onFinished(activity: PetActivity) {
        when (activity) {
            PetActivity.Eating -> events.sound(Sfx.Happy)
            PetActivity.Cleaning -> {
                tubOut = false
                body.goal = null
                sweepInvite = crumbs.positions.isNotEmpty()
                events.sound(Sfx.Happy)
            }
            PetActivity.Dancing -> events.sound(Sfx.Happy)
            else -> Unit
        }
        events.actionFinished(activity)
    }

    private fun updateAutonomy(dt: Float, nowMs: Long) {
        val idle = actions.activity == PetActivity.Idle && body.goal == null
        val napBelow = if (personality.quirk == Quirk.NapLover) personalityConfig.napLoverEnergy else 30f
        when (autonomy.update(dt, needs, idle, ballOut, napBelow)) {
            Autonomy.Nap -> {
                actions.startNap(nowMs, config.autonomyEfficiency)
                events.sound(Sfx.Sleepy)
            }
            Autonomy.PlayAlone -> startPlay(config.autonomyEfficiency)
            Autonomy.None -> Unit
        }
    }

    /** The pet chases the ball and nudges it back toward the front of the pad; each nudge is a fetch. */
    private fun updateBall(dt: Float) {
        ball.update(dt)
        if (actions.activity != PetActivity.Playing) return
        if (ball.idleSec >= config.ballIdleSec) {
            ballOut = false
            actions.stopOpenEnded()
            body.goal = null
            events.actionFinished(PetActivity.Playing)
            return
        }
        val b = ball.position
        body.goal = Vec3(b.x, 0f, b.z)
        val dx = b.x - body.position.x
        val dz = b.z - body.position.z
        if (!ball.held && ball.isResting && sqrt(dx * dx + dz * dz) < NUDGE_REACH) {
            ball.push(Vec3(-b.x * 1.2f, 0.9f, 0.35f - b.z * 1.2f))
            actions.fetch(needs)
            fetches++
            events.sound(Sfx.Happy)
        }
    }

    /** Whether the nearest poke point is rubbing the head proxy above [minModelY]. */
    private fun rubbing(detector: RubDetector, nowMs: Long, dt: Float, pokes: List<Vec3>, minModelY: Float): Boolean {
        val scale = localScale(nowMs)
        var best: Vec3? = null
        var bestDistance = Float.MAX_VALUE
        for (p in pokes) {
            val m = body.toModel(p, scale)
            if (m.y < minModelY) continue
            val d = PetBody.headDistance(m)
            if (d < bestDistance) {
                bestDistance = d
                best = p
            }
        }
        return detector.update(best, bestDistance * scale * displayScale, dt)
    }

    private fun updateFoods(dt: Float, nowMs: Long) {
        if (!trayOut) return
        val mouth = body.mouth(localScale(nowMs))
        val reach = config.feedDistance / displayScale
        for (f in foods) {
            if (f.eaten) continue
            if (f.held && f.position.distanceTo(mouth) <= reach) {
                when (val r = actions.startEat(needs, nowMs, PersonalityEffects.foodGain(personality, f.index, personalityConfig))) {
                    ActionResult.Started -> {
                        f.eaten = true
                        f.held = false
                        when (f.index) {
                            personality.likedFood -> {
                                // A favourite: a happy wiggle and a little Fun.
                                needs.add(Need.Fun, personalityConfig.likedFunBonus)
                                body.wave()
                                events.sound(Sfx.Happy)
                            }
                            personality.dislikedFood -> events.sound(Sfx.Sad)
                            else -> events.sound(Sfx.Happy)
                        }
                    }
                    is ActionResult.Refused -> if (r.reason == "full") {
                        events.sound(Sfx.Sad)
                        events.toast("toast.full")
                        f.held = false
                    }
                }
            }
            if (!f.held && f.position != f.home) {
                f.freeSec += dt
                if (f.freeSec >= config.foodReturnSec) f.reset()
            }
        }
    }

    fun grabFood(index: Int) {
        foods.getOrNull(index)?.takeIf { trayOut && !it.eaten }?.let {
            it.held = true
            it.freeSec = 0f
        }
    }

    fun moveFood(index: Int, deltaLocal: Vec3) {
        foods.getOrNull(index)?.takeIf { it.held }?.let { it.position = it.position + deltaLocal }
    }

    fun releaseFood(index: Int) {
        foods.getOrNull(index)?.let { it.held = false }
    }

    fun grabBall() {
        if (ballOut) ball.held = true
    }

    fun moveBall(deltaLocal: Vec3) {
        if (ball.held) ball.drag(deltaLocal)
    }

    fun throwBall(velocityLocal: Vec3) {
        if (ball.held) ball.throwWith(velocityLocal)
    }

    fun sweepCrumb(index: Int): Boolean {
        if (!crumbs.sweep(index)) return false
        needs.crumbs = crumbs.positions.size
        if (crumbs.positions.isEmpty()) sweepInvite = false
        events.sound(Sfx.UiTick)
        return true
    }

    fun boop(nowMs: Long) {
        body.kickAntenna(BOOP_KICK)
        if (actions.boop(needs, nowMs)) events.sound(Sfx.Happy)
    }

    /** A freshly hatched pet in the nest, with starting needs and nothing on the pad. */
    fun hatchNew(nowMs: Long) {
        respawn(nowMs)
        body.place(PetBody.NEST)
    }

    /** Resets needs, crumbs and distress for a new pet hatched at [nowMs]. */
    fun respawn(nowMs: Long) {
        config.startNeeds.forEachIndexed { i, v -> needs.values[i] = v }
        crumbs.positions.clear()
        needs.crumbs = 0
        distress.hours = 0f
        stage = Distress.None
        hatchMs = nowMs
        endActivity()
    }

    /** Restores the distress stage after loading without re-announcing it. */
    fun syncStage() {
        stage = distress.stage()
    }

    companion object {
        const val FOOD_COUNT = 5
        val TRAY_CENTRE = Vec3(0.13f, 0f, 0.16f)
        val TUB_CENTRE = Vec3(-0.12f, 0f, 0.1f)
        private const val RUB_DISTANCE = 0.02f
        private const val RUB_MIN_SPEED = 0.10f
        private const val BOOP_KICK = 4f
        private const val NUDGE_REACH = 0.04f
        private const val RADAR_LEAN = 0.5f
    }
}
