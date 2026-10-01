package com.technomancer.pangu.ui

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color

/** The mockup's style tokens: colours, borders, panel shape and spacing, in mockup pixels. */
object PanguTokens {
    val ink = Color(0xFF2F2826)
    val paper = Color(0xFFF7F3EB)
    val cream = Color(0xFFF2ECE1)
    val muted = Color(0xFF6B615B)
    val body = Color(0xFF4F4641)
    val accent = Color(0xFFB4533F)
    val divider = Color(0xFFE2D9CC)
    val padSide = Color(0xFFDDD3C4)
    val nest = Color(0xFFE7D6BF)
    val tray = Color(0xFFECE4D7)
    val butter = Color(0xFFF2DE9B)
    val pistachio = Color(0xFFBFD3AE)
    val tomato = Color(0xFFD9644F)
    val duskBlue = Color(0xFFA8BFD4)

    /** Panel shadow: 0 10 28 px rgba(47,40,38,0.12). */
    val shadow = Color(0x1F2F2826)
    const val SHADOW_BLUR = 28f
    const val SHADOW_Y = 10f

    const val BORDER = 1.5f

    /** Panel corner radii TL / TR / BR / BL and padding. */
    const val PANEL_WIDTH = 420f
    const val PANEL_PAD_X = 30f
    const val PANEL_PAD_Y = 28f
    const val PANEL_GAP = 14f

    fun panelShape(scale: UiScale) = RoundedCornerShape(scale.px(30), scale.px(26), scale.px(32), scale.px(24))

    /** Pill buttons: at least 54 px tall, radius 27. */
    const val PILL_HEIGHT = 54f
    const val PILL_RADIUS = 27f

    /** Text sizes (px): title 26/600, body 18, small 15, section label 13/700, mono 14. */
    const val TITLE = 26f
    const val BODY = 18f
    const val SMALL = 15f
    const val LABEL = 13f
    const val MONO = 14f
}
