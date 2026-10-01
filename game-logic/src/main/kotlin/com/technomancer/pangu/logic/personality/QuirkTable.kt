package com.technomancer.pangu.logic.personality

/** A quirk: chosen by the up axis and one bit of the spark value. Labels are "quirk.<index>.name|desc". */
enum class Quirk {
    SparkleHiccups, EarlyRiser,
    HandFollower, WelcomeWave,
    AntennaRadar, NightWatcher,
    NapLover, SlowBlinker,
    SelfGroomer, Rearranger;

    companion object {
        /** The quirk for [axis] (0 Zest … 4 Neat) and bit [q]. */
        fun of(axis: Int, q: Int): Quirk = entries[axis * 2 + (q and 1)]
    }
}
