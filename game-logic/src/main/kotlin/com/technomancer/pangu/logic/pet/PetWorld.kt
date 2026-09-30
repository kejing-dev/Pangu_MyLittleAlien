package com.technomancer.pangu.logic.pet

import com.technomancer.pangu.logic.art.AlienParts
import com.technomancer.pangu.logic.audio.Sfx
import com.technomancer.pangu.logic.config.PetConfig
import com.technomancer.pangu.logic.gestures.RubDetector
import com.technomancer.pangu.logic.input.InputSnapshot
import com.technomancer.pangu.logic.math.Vec3
import com.technomancer.pangu.logic.time.IRandom

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
}

/**
 * The hatched pet: needs, timed actions, body motion, snacks and stroking. Everything is in
 * pad-local coordinates (metres before the habitat's display scale). The caller converts input
 * into that space with [toPadLocal].
 */
class PetWorld(
    val config: PetConfig,
    random: IRandom,
    padRadius: Float,
    private val events: PetEvents,
    /** The habitat is drawn this many times larger than its pad-local units. */
    private val displayScale: Float = 1f,
) {
    val needs = Needs(config.startNeeds)
    val actions = PetActions(config)
    val body = PetBody(config, random, padRadius)
    private val needsSim = NeedsSim(config)
    private val mood = MoodCalculator(config)
    private val stroke = RubDetector(maxDistance = STROKE_DISTANCE, minSpeed = STROKE_MIN_SPEED)

    /** Simulated epoch ms when the pet hatched; drives growth. */
    var hatchMs = 0L

    val foods = List(FOOD_COUNT) { i -> Food(i, Vec3(TRAY_CENTRE.x - 0.05f + i * 0.025f, TRAY_CENTRE.y + 0.012f, TRAY_CENTRE.z)) }

    /** True while the tray and snacks should be visible (Eat screen). */
    var trayOut = false
        private set

    var moodScore = 0f
        private set

    /** Metres per model unit right now: adult height, times growth since hatching. */
    fun scale(nowMs: Long): Float {
        val hours = ((nowMs - hatchMs) / 3_600_000f).coerceAtLeast(0f)
        val growth = config.hatchGrowth + (1f - config.hatchGrowth) * (hours / config.growthHours).coerceIn(0f, 1f)
        return config.adultHeight / AlienParts.MODEL_HEIGHT * growth
    }

    /** Pad-local units per model unit: the real size divided by the habitat display scale. */
    fun localScale(nowMs: Long): Float = scale(nowMs) / displayScale

    fun mood(): Mood = mood.mood(moodScore)

    fun showTray(show: Boolean) {
        trayOut = show
        if (!show) foods.forEach { it.reset() }
    }

    fun update(dt: Float, nowMs: Long, isNight: Boolean, headLocal: Vec3?, pokes: List<Vec3>) {
        val asleep = isNight || actions.activity == PetActivity.Napping
        needsSim.step(needs, dt / 3600f, asleep)
        val before = actions.activity
        if (actions.update(needs, nowMs, dt)) {
            if (before == PetActivity.Eating) events.sound(Sfx.Happy)
            events.actionFinished(before)
        }
        body.update(dt, actions.activity, headLocal)
        updateFoods(dt, nowMs)
        if (actions.activity == PetActivity.Cuddling) updateStroke(dt, nowMs, pokes)
        moodScore = mood.score(needs)
    }

    private fun updateFoods(dt: Float, nowMs: Long) {
        if (!trayOut) return
        val mouth = body.mouth(localScale(nowMs))
        val reach = config.feedDistance / displayScale
        for (f in foods) {
            if (f.eaten) continue
            if (f.held && f.position.distanceTo(mouth) <= reach) {
                when (val r = actions.startEat(needs, nowMs)) {
                    ActionResult.Started -> {
                        f.eaten = true
                        f.held = false
                        events.sound(Sfx.Happy)
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

    private fun updateStroke(dt: Float, nowMs: Long, pokes: List<Vec3>) {
        val scale = localScale(nowMs)
        // The poke point closest to the top of the head, in model units.
        var best: Vec3? = null
        var bestDistance = Float.MAX_VALUE
        for (p in pokes) {
            val m = body.toModel(p, scale)
            if (m.y < config.strokeMinModelY) continue
            val d = PetBody.headDistance(m)
            if (d < bestDistance) {
                bestDistance = d
                best = p
            }
        }
        if (stroke.update(best, bestDistance * scale * displayScale, dt) && actions.stroke(needs, dt, nowMs) > 0f) {
            events.sound(Sfx.Happy)
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

    fun boop(nowMs: Long) {
        body.kickAntenna(BOOP_KICK)
        if (actions.boop(needs, nowMs)) events.sound(Sfx.Happy)
    }

    companion object {
        const val FOOD_COUNT = 5
        val TRAY_CENTRE = Vec3(0.13f, 0f, 0.16f)
        private const val STROKE_DISTANCE = 0.02f
        private const val STROKE_MIN_SPEED = 0.10f
        private const val BOOP_KICK = 4f
    }
}
