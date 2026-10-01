package com.technomancer.pangu.ui

import com.technomancer.pangu.logic.PanguLogic
import com.technomancer.pangu.logic.creation.PetLook
import com.technomancer.pangu.logic.flow.ScreenId
import com.technomancer.pangu.logic.incubation.EggAction
import com.technomancer.pangu.logic.incubation.WarmthBand
import com.technomancer.pangu.logic.loc.Language
import com.technomancer.pangu.logic.pet.Mood
import com.technomancer.pangu.logic.pet.Need
import com.technomancer.pangu.logic.pet.PetAction
import com.technomancer.pangu.logic.pet.Suggestions
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A toast request: the string key and a serial that changes for every new toast. */
data class ToastRequest(val key: String, val serial: Int)

/** What the pet panels show. [needs] are whole numbers in Need order (Tidy last). */
data class PetUi(
    val name: String,
    val needs: List<Int>,
    val mood: Mood,
    val lowest: Need,
    val suggested: List<PetAction>,
    val napMinutes: Int,
    /** Seconds into the current bath or dance. */
    val timedSec: Int = 0,
    /** Welcome back rows: need, before, after; plus new crumbs and minutes away. */
    val awayDrops: List<Triple<Need, Int, Int>> = emptyList(),
    val awayCrumbs: Int = 0,
    val awayMinutes: Long = 0,
)

/** The studio panels: the look being designed, its name and each wheel's syllable. */
data class StudioUi(
    val look: PetLook,
    val swatches: List<Int>,
    val name: String,
    val wheels: List<String>,
)

/** What the egg panels show. */
data class EggUi(
    val name: String,
    val day: Int,
    val band: WarmthBand,
    val ritualsToday: Int,
    val lampHoursLeft: Int,
    val holdSec: Int,
    val holdTotal: Int,
    val taps: Int,
    val tapsTotal: Int,
    val sealSec: Int,
    val sealTotal: Int,
    val hatchSec: Int,
    val hatchTotal: Int,
    val cappedAction: EggAction,
    val suggested: EggAction?,
    /** Welcome back: warmth band before and after, lamp hours left and minutes away; null when not shown. */
    val awayBands: Pair<WarmthBand, WarmthBand>? = null,
    val awayLampHours: Int = 0,
    val awayMinutes: Long = 0,
)

/**
 * Carries logic state to the Compose panels. The frame System calls [publishIfChanged] after
 * each tick; flows only emit when a value actually changes, and pet and egg values refresh at
 * most [PANEL_HZ] times a second, so panels recompose rarely. Studio choices publish at once.
 */
class UiBridge {
    private val _screen = MutableStateFlow(ScreenId.C02)
    val screen: StateFlow<ScreenId> = _screen

    private val _toast = MutableStateFlow<ToastRequest?>(null)
    val toast: StateFlow<ToastRequest?> = _toast

    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused

    private val _language = MutableStateFlow(Language.En)
    val language: StateFlow<Language> = _language

    private val _pet = MutableStateFlow<PetUi?>(null)
    val pet: StateFlow<PetUi?> = _pet

    private val _studio = MutableStateFlow<StudioUi?>(null)
    val studio: StateFlow<StudioUi?> = _studio

    private val _egg = MutableStateFlow<EggUi?>(null)
    val egg: StateFlow<EggUi?> = _egg

    /** Hatched, resting (dormant) and went-home counters. */
    private val _counters = MutableStateFlow(Triple(0, 0, 0))
    val counters: StateFlow<Triple<Int, Int, Int>> = _counters

    private var nextPanelNanos = 0L
    private var shownLook: PetLook? = null

    fun publishIfChanged(logic: PanguLogic) {
        _screen.value = logic.screens.current
        _paused.value = logic.isPaused
        _counters.value = Triple(logic.hatchedCount, logic.dormantCount, logic.wentHomeCount)
        val key = logic.toastKey
        if (key != null && _toast.value?.serial != logic.toastSerial) {
            _toast.value = ToastRequest(key, logic.toastSerial)
        }
        if (logic.isStudio && logic.look != shownLook) {
            val look = logic.look
            shownLook = look
            _studio.value = StudioUi(
                look = look,
                swatches = logic.catalog.swatches.toList(),
                name = logic.petName,
                wheels = listOf(logic.wheels.display(0, look.a), logic.wheels.display(1, look.b), logic.wheels.display(2, look.c)),
            )
        }
        val now = System.nanoTime()
        if (now < nextPanelNanos) return
        nextPanelNanos = now + 1_000_000_000L / PANEL_HZ
        if (logic.isPetFlow) publishPet(logic)
        if (logic.isEggFlow || logic.egg.sealSec >= 0f) publishEgg(logic)
    }

    private fun publishPet(logic: PanguLogic) {
        val pet = logic.pet
        _pet.value = PetUi(
            name = logic.petName,
            needs = Need.entries.map { pet.needs.value(it, pet.config).toInt() },
            mood = pet.mood(),
            lowest = Suggestions.lowestNeed(pet.needs, pet.config),
            suggested = Suggestions.forPet(pet.needs, pet.config),
            napMinutes = pet.actions.napMinutes(logic.nowMs),
            timedSec = pet.actions.timedSec.toInt(),
            awayDrops = logic.welcome?.drops?.map { Triple(it.first, it.second.first, it.second.second) }.orEmpty(),
            awayCrumbs = logic.welcome?.newCrumbs ?: 0,
            awayMinutes = logic.welcome?.minutesAway ?: 0,
        )
    }

    private fun publishEgg(logic: PanguLogic) {
        val world = logic.egg
        val config = world.config
        val egg = world.egg
        val away = logic.eggWelcome
        _egg.value = EggUi(
            name = logic.petName,
            day = egg.day(logic.nowMs),
            band = world.sim.band(egg.warmth),
            ritualsToday = world.ledger.ritualsToday,
            lampHoursLeft = egg.lampHoursLeft(logic.nowMs),
            holdSec = world.holdSec.toInt(),
            holdTotal = config.handWarmCompleteSec.toInt(),
            taps = egg.crackTaps,
            tapsTotal = config.hatchTaps,
            sealSec = world.sealSec.coerceAtLeast(0f).toInt(),
            sealTotal = config.sealSec.toInt(),
            hatchSec = world.hatchSec.coerceAtLeast(0f).toInt(),
            hatchTotal = config.hatchSec.toInt(),
            cappedAction = logic.cappedAction,
            suggested = logic.eggSuggestion(),
            awayBands = away?.let { it.bandBefore to it.bandAfter },
            awayLampHours = away?.lampHoursLeft ?: 0,
            awayMinutes = away?.minutesAway ?: 0,
        )
    }

    fun setLanguage(language: Language) {
        _language.value = language
    }

    companion object {
        const val PANEL_HZ = 4
    }
}
