package com.technomancer.pangu.ui

import com.technomancer.pangu.logic.PanguLogic
import com.technomancer.pangu.logic.flow.ScreenId
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
    /** Hatched, resting (dormant) and went-home counters. */
    val counters: Triple<Int, Int, Int> = Triple(0, 0, 0),
    /** Welcome back rows: need, before, after; plus new crumbs and minutes away. */
    val awayDrops: List<Triple<Need, Int, Int>> = emptyList(),
    val awayCrumbs: Int = 0,
    val awayMinutes: Long = 0,
)

/**
 * Carries logic state to the Compose panels. The frame System calls [publishIfChanged] after
 * each tick; flows only emit when a value actually changes, and pet values refresh at most
 * [PET_HZ] times a second, so panels recompose rarely.
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

    private var nextPetNanos = 0L

    fun publishIfChanged(logic: PanguLogic) {
        _screen.value = logic.screens.current
        _paused.value = logic.isPaused
        val key = logic.toastKey
        if (key != null && _toast.value?.serial != logic.toastSerial) {
            _toast.value = ToastRequest(key, logic.toastSerial)
        }
        val now = System.nanoTime()
        if (logic.isPetFlow && now >= nextPetNanos) {
            nextPetNanos = now + 1_000_000_000L / PET_HZ
            val pet = logic.pet
            _pet.value = PetUi(
                name = logic.petName,
                needs = Need.entries.map { pet.needs.value(it, pet.config).toInt() },
                mood = pet.mood(),
                lowest = Suggestions.lowestNeed(pet.needs, pet.config),
                suggested = Suggestions.forPet(pet.needs, pet.config),
                napMinutes = pet.actions.napMinutes(logic.nowMs),
                timedSec = pet.actions.timedSec.toInt(),
                counters = Triple(logic.hatchedCount, logic.dormantCount, logic.wentHomeCount),
                awayDrops = logic.welcome?.drops?.map { Triple(it.first, it.second.first, it.second.second) }.orEmpty(),
                awayCrumbs = logic.welcome?.newCrumbs ?: 0,
                awayMinutes = logic.welcome?.minutesAway ?: 0,
            )
        }
    }

    fun setLanguage(language: Language) {
        _language.value = language
    }

    companion object {
        const val PET_HZ = 4
    }
}
