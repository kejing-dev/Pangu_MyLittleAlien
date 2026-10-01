package com.technomancer.pangu.logic.creation

import com.technomancer.pangu.logic.config.LookConfig

/** The three syllable wheels that build the pet's name. Names are never translated. */
class NameWheels(config: LookConfig = LookConfig()) {
    private val wheels = listOf(config.wheelA, config.wheelB, config.wheelC)

    fun size(wheel: Int): Int = wheels[wheel].size

    fun name(look: PetLook): String = syllable(0, look.a) + syllable(1, look.b) + syllable(2, look.c)

    fun syllable(wheel: Int, index: Int): String {
        val w = wheels[wheel]
        return w[Math.floorMod(index, w.size)]
    }

    /** What a wheel shows: its syllable, or a dot when the syllable is empty. */
    fun display(wheel: Int, index: Int): String = syllable(wheel, index).ifEmpty { EMPTY }

    /** Advances one wheel by one syllable, wrapping around. */
    fun cycle(look: PetLook, wheel: Int): PetLook = when (wheel) {
        0 -> look.copy(a = (look.a + 1) % size(0))
        1 -> look.copy(b = (look.b + 1) % size(1))
        else -> look.copy(c = (look.c + 1) % size(2))
    }

    companion object {
        const val EMPTY = "·"
    }
}
