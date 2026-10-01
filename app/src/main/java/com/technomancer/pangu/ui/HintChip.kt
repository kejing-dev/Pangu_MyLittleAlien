package com.technomancer.pangu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.pico.spatial.ui.design.Text

/** The hint chip under the panel: a paper pill with the gesture glyph and its hint. */
@Composable
fun HintChip(glyph: String, text: String, scale: UiScale) {
    val fonts = LocalPanguFonts.current
    val shape = RoundedCornerShape(scale.px(20))
    Row(
        modifier = Modifier
            .heightIn(min = scale.px(40))
            .background(PanguTokens.paper, shape)
            .border(scale.px(PanguTokens.BORDER), PanguTokens.ink, shape)
            .padding(horizontal = scale.px(16)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = glyph, color = PanguTokens.ink, fontSize = scale.sp(18f), fontFamily = fonts.glyph)
        Text(text = text, color = PanguTokens.ink, fontSize = scale.sp(15f), fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = scale.px(8)))
    }
}

/** Glyph per hint, in the order of the logic's Hint values. */
val HINT_GLYPHS = mapOf(
    "crank" to "↻", "lean" to "◎", "poke" to "☝", "grab" to "✊", "gaze" to "◉",
    "voice" to "♪", "rub" to "∿", "move" to "♫", "none" to "○",
)
