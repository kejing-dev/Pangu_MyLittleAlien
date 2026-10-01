package com.technomancer.pangu.logic.creation

import com.technomancer.pangu.logic.config.LookConfig
import com.technomancer.pangu.logic.time.IRandom

/** Swatch colours, antenna shapes and the default look. Colours are 0xRRGGBB. */
class LookCatalog(private val config: LookConfig = LookConfig()) {
    val swatches: IntArray = IntArray(config.swatches.size) { parseHex(config.swatches[it]) }

    val swatchCount: Int get() = swatches.size

    fun color(index: Int): Int = swatches[index.coerceIn(0, swatches.lastIndex)]

    fun bodyColor(look: PetLook): Int = color(look.body)

    fun tipColor(look: PetLook): Int = color(look.tip)

    fun default(): PetLook {
        val n = config.defaultName
        return PetLook(config.defaultBody, config.defaultTip, config.defaultShape, n[0], n[1], n[2])
    }

    /** A random look and name. */
    fun randomize(random: IRandom, wheels: NameWheels): PetLook = PetLook(
        body = random.nextInt(swatchCount),
        tip = random.nextInt(swatchCount),
        shape = random.nextInt(SHAPES.size),
        a = random.nextInt(wheels.size(0)),
        b = random.nextInt(wheels.size(1)),
        c = random.nextInt(wheels.size(2)),
    )

    companion object {
        /** Antenna shape ids in index order; labels are "ui.shapes.<index>". */
        val SHAPES = listOf("ball", "star", "heart", "spiral", "flower", "bolt")

        fun parseHex(hex: String): Int = hex.removePrefix("#").toInt(16)
    }
}
