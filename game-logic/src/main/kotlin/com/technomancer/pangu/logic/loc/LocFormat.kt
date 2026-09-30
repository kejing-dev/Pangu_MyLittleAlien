package com.technomancer.pangu.logic.loc

/** Progress labels such as "18 / 30 s", built from the language's template for the unit. */
class LocFormat(private val loc: LocService) {
    enum class Unit(val key: String) { Seconds("fmt.sec"), Minutes("fmt.min"), Taps("fmt.taps"), Circles("fmt.circles") }

    fun progress(unit: Unit, done: Int, total: Int): String = loc.format(unit.key, "a" to done, "b" to total)
}
