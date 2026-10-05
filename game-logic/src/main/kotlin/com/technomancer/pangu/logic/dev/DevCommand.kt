package com.technomancer.pangu.logic.dev

/**
 * Shortcuts for testing, run through PanguLogic.dev. They only do anything while
 * PanguLogic.devTools is on, which debug builds alone switch on; release builds have no way to
 * reach them.
 */
enum class DevCommand {
    /** Back to the studio to design a new alien, starting from the last look. */
    Customize,

    /** A fresh egg in the nest with the current look, at the start of incubation. */
    RestartEgg,

    /** The egg (a fresh one if there is none) is fully incubated and waits to be cracked. */
    EggReady,

    /** The egg hatches now, skipping the taps. */
    HatchNow,

    /** The pet is already out: a freshly hatched pet on the pad, without playing the hatch. */
    Hatched,

    /** The egg's warmth drops into the chilly band, or back up to full. */
    EggCold,
    EggWarm,

    /** The egg stops glowing for good, as after long neglect. */
    EggDormant,

    /** Today's ritual counts and cooldowns are cleared, so every ritual can be done again. */
    ClearRitualCaps,

    /** The pet's needs all go to full, or all go low. */
    FillNeeds,
    DrainNeeds,

    /** The pet becomes mopey, homesick, or goes home; Cheer up clears all three. */
    PetMopey,
    PetHomesick,
    PetWentHome,
    PetCheerUp,

    /** Opens the pet's personality card. */
    ShowPersonality,
}
