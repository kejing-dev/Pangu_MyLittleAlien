package com.technomancer.pangu.logic.loc

/**
 * Progress labels such as "18 / 30 s", built from the language's template for the unit. The last
 * label per unit is kept, so asking again with the same numbers returns the same string without
 * building a new one; new numbers are formatted through [LocService]'s shared buffer.
 */
class LocFormat(private val loc: LocService) {
    enum class Unit(val key: String) { Seconds("fmt.sec"), Minutes("fmt.min"), Taps("fmt.taps"), Circles("fmt.circles") }

    private val lastDone = IntArray(Unit.entries.size) { -1 }
    private val lastTotal = IntArray(Unit.entries.size) { -1 }
    private val lastLanguage = arrayOfNulls<Language>(Unit.entries.size)
    private val lastLabel = arrayOfNulls<String>(Unit.entries.size)

    fun progress(unit: Unit, done: Int, total: Int): String {
        val i = unit.ordinal
        val cached = lastLabel[i]
        if (cached != null && lastDone[i] == done && lastTotal[i] == total && lastLanguage[i] == loc.language) return cached
        val label = loc.format(unit.key, "a" to done, "b" to total)
        lastDone[i] = done
        lastTotal[i] = total
        lastLanguage[i] = loc.language
        lastLabel[i] = label
        return label
    }
}
