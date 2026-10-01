package com.technomancer.pangu.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import com.pico.spatial.ui.design.Text
import com.technomancer.pangu.logic.flow.PieLayout
import com.technomancer.pangu.logic.incubation.EggAction
import com.technomancer.pangu.logic.incubation.EggSuggestions
import com.technomancer.pangu.logic.loc.LocService
import com.technomancer.pangu.logic.pet.PetAction

/** One bubble: its glyph, label key, whether it can be chosen now and whether it is suggested. */
data class PieItem(val glyph: String, val labelKey: String, val enabled: Boolean, val suggested: Boolean)

/** Glyphs per action, in pie order. */
private val PET_GLYPHS = listOf("●", "☾", "✧", "♫", "◉", "♡")
private val EGG_GLYPHS = listOf("◡", "☼", "≋", "♪", "◎", "◇")

/** The pet pie: every action available, the suggested ones filled. */
fun petPieItems(suggested: List<PetAction>): List<PieItem> =
    PetAction.entries.map { PieItem(PET_GLYPHS[it.ordinal], "ui.pet6.${it.ordinal}", true, it in suggested) }

/** The egg pie: rituals not built yet are faded; the suggested action is filled. */
fun eggPieItems(suggested: EggAction?): List<PieItem> =
    EggAction.entries.map { PieItem(EGG_GLYPHS[it.ordinal], "ui.egg6.${it.ordinal}", it in EggSuggestions.AVAILABLE, it == suggested) }

/** Organic blob outline taken from the mockup's uneven corner radii. */
private val BlobShape = GenericShape { size, _ ->
    val w = size.width
    val h = size.height
    moveTo(w * 0.49f, 0f)
    cubicTo(w * 0.9f, 0f, w, h * 0.2f, w, h * 0.52f)
    cubicTo(w, h * 0.85f, w * 0.8f, h, w * 0.47f, h)
    cubicTo(w * 0.15f, h, 0f, h * 0.8f, 0f, h * 0.48f)
    cubicTo(0f, h * 0.15f, w * 0.2f, 0f, w * 0.49f, 0f)
    close()
}

/**
 * The care pie: six bubbles on a ring around the orb, starting at the top and going clockwise,
 * with a dashed guide circle. Suggested items are ink-filled; unavailable ones are faded and
 * still report the pick, so the logic can say why.
 */
@Composable
fun PieMenu(visible: Boolean, items: List<PieItem>, loc: LocService, scale: UiScale, ringRadiusPx: Float, onPick: (Int) -> Unit) {
    if (!visible) return
    val bubble = 62f
    val boxSize = (ringRadiusPx + bubble) * 2f + 40f
    Box(modifier = Modifier.size(scale.px(boxSize)), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(scale.px(ringRadiusPx * 2f))) {
            drawCircle(
                color = PanguTokens.ink.copy(alpha = 0.5f),
                radius = size.minDimension / 2f,
                style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))),
            )
        }
        val layout = PieLayout.positions(items.size, ringRadiusPx)
        items.forEachIndexed { i, item ->
            // The layout has y up; Compose offsets have y down.
            val x = layout[i].first
            val y = -layout[i].second
            val filled = item.suggested
            val enabled = item.enabled
            Column(
                modifier = Modifier
                    .offset(scale.px(x), scale.px(y + 12f))
                    .alpha(if (enabled) 1f else 0.35f)
                    .spatialHoverEffect()
                    .clickable { onPick(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(scale.px(bubble))
                        .background(if (filled) PanguTokens.ink else PanguTokens.paper, BlobShape)
                        .border(scale.px(1.5f), PanguTokens.ink, BlobShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = item.glyph, color = if (filled) PanguTokens.paper else PanguTokens.ink, fontSize = scale.sp(26f), fontFamily = LocalPanguFonts.current.glyph)
                }
                Box(
                    modifier = Modifier
                        .padding(top = scale.px(4))
                        .background(PanguTokens.paper.copy(alpha = 0.94f), RoundedCornerShape(scale.px(9)))
                        .padding(horizontal = scale.px(8), vertical = scale.px(2)),
                ) {
                    Text(text = loc.get(item.labelKey), color = PanguTokens.ink, fontSize = scale.sp(14f), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

