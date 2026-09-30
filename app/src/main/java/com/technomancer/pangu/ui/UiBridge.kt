package com.technomancer.pangu.ui

import com.technomancer.pangu.logic.PanguLogic
import com.technomancer.pangu.logic.flow.ScreenId
import com.technomancer.pangu.logic.loc.Language
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/** A toast request: the string key and a serial that changes for every new toast. */
data class ToastRequest(val key: String, val serial: Int)

/**
 * Carries logic state to the Compose panels. The frame System calls [publishIfChanged] after
 * each tick; flows only emit when a value actually changes, so panels recompose rarely.
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

    fun publishIfChanged(logic: PanguLogic) {
        _screen.value = logic.screens.current
        _paused.value = logic.isPaused
        val key = logic.toastKey
        if (key != null && _toast.value?.serial != logic.toastSerial) {
            _toast.value = ToastRequest(key, logic.toastSerial)
        }
    }

    fun setLanguage(language: Language) {
        _language.value = language
    }
}
