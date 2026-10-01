package com.technomancer.pangu.logic.incubation

import com.technomancer.pangu.logic.save.EggData

/** Copies the egg and its daily counts into save data. */
fun EggWorld.toData(): EggData = EggData(
    sparkValue = egg.sparkValue,
    warmth = egg.warmth,
    hatchProgressHours = egg.hatchProgressHours,
    coldHours = egg.coldHours,
    lampOffUtcMs = egg.lampOffMs,
    blanketOffUtcMs = egg.blanketOffMs,
    imprint = ledger.imprint.toList(),
    completionsToday = ledger.completionsToday.toList(),
    completionDay = ledger.day,
    lastCompletionUtcMs = ledger.lastCompletionMs.toList(),
    blanketTucksToday = ledger.blanketTucksToday,
    crackTaps = egg.crackTaps,
    setUtcMs = egg.setMs,
    dormant = egg.dormant,
)

/** Restores the egg and its daily counts from save data. */
fun EggWorld.restore(data: EggData) {
    egg.sparkValue = data.sparkValue
    egg.warmth = data.warmth
    egg.hatchProgressHours = data.hatchProgressHours
    egg.coldHours = data.coldHours
    egg.lampOffMs = data.lampOffUtcMs
    egg.blanketOffMs = data.blanketOffUtcMs
    egg.crackTaps = data.crackTaps
    egg.setMs = data.setUtcMs
    egg.dormant = data.dormant
    data.imprint.forEachIndexed { i, v -> if (i < ledger.imprint.size) ledger.imprint[i] = v }
    data.completionsToday.forEachIndexed { i, v -> if (i < ledger.completionsToday.size) ledger.completionsToday[i] = v }
    data.lastCompletionUtcMs.forEachIndexed { i, v -> if (i < ledger.lastCompletionMs.size) ledger.lastCompletionMs[i] = v }
    ledger.blanketTucksToday = data.blanketTucksToday
    ledger.day = data.completionDay
}
