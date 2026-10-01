package com.technomancer.pangu.logic.personality

/**
 * The 25 natures: row = the up axis, column = the down axis, in axis order Zest, Cuddle, Wonder,
 * Calm, Neat. The diagonal holds the five neutral natures. Labels are "nature.<index>".
 */
object NatureTable {
    const val AXES = 5

    /** English names, also the stable ids used in tests and logs. */
    val NAMES = listOf(
        "Steady", "Bold", "Brisk", "Bouncy", "Scrappy",
        "Gentle", "Easygoing", "Loyal", "Clingy", "Snuggly",
        "Dreamy", "Aloof", "Quirky", "Nosy", "Tinkery",
        "Sleepy", "Stoic", "Placid", "Mellow", "Lazy",
        "Prim", "Fussy", "Orderly", "Finicky", "Plain",
    )

    fun index(up: Int, down: Int): Int = AXES * up + down

    fun isNeutral(index: Int): Boolean = index / AXES == index % AXES

    fun name(index: Int): String = NAMES[index]

    fun key(index: Int): String = "nature.$index"
}
