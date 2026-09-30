package com.technomancer.pangu.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import com.technomancer.pangu.logic.loc.LocService
import com.technomancer.pangu.logic.pet.PetAction
import kotlin.math.cos
import kotlin.math.sin

/** Glyph per action, in pie order. */
private val PET_GLYPHS = listOf("●", "☾", "✧", "♫", "◉", "♡")

/** Actions that can be chosen; anything else is drawn faded. */
private val AVAILABLE = PetAction.entries.toSet()

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
 * with a dashed guide circle. Suggested actions are ink-filled; unavailable ones are faded.
 */
@Composable
fun PieMenu(visible: Boolean, suggested: List<PetAction>, loc: LocService, scale: UiScale, ringRadiusPx: Float, onPick: (PetAction) -> Unit) {
    if (!visible) return
    val bubble = 62f
    val boxSize = (ringRadiusPx + bubble) * 2f + 40f
    Box(modifier = Modifier.size(scale.px(boxSize)), contentAlignment = Alignment.Center) {
        Canvas(modifier = Modifier.size(scale.px(ringRadiusPx * 2f))) {
            drawCircle(
                color = PanelColors.ink.copy(alpha = 0.5f),
                radius = size.minDimension / 2f,
                style = Stroke(width = 2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(10f, 10f))),
            )
        }
        PetAction.entries.forEachIndexed { i, action ->
            val angle = -Math.PI / 2 + i * 2 * Math.PI / PetAction.entries.size
            val x = (ringRadiusPx * cos(angle)).toFloat()
            val y = (ringRadiusPx * sin(angle)).toFloat()
            val filled = action in suggested
            val enabled = action in AVAILABLE
            Column(
                modifier = Modifier
                    .offset(scale.px(x), scale.px(y + 12f))
                    .alpha(if (enabled) 1f else 0.35f)
                    .clickable { onPick(action) },
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(scale.px(bubble))
                        .background(if (filled) PanelColors.ink else PanelColors.paper, BlobShape)
                        .border(scale.px(1.5f), PanelColors.ink, BlobShape),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(text = PET_GLYPHS[i], color = if (filled) PanelColors.paper else PanelColors.ink, fontSize = scale.sp(26f))
                }
                Box(
                    modifier = Modifier
                        .padding(top = scale.px(4))
                        .background(PanelColors.paper.copy(alpha = 0.94f), RoundedCornerShape(scale.px(9)))
                        .padding(horizontal = scale.px(8), vertical = scale.px(2)),
                ) {
                    Text(text = loc.get("ui.pet6.${action.ordinal}"), color = PanelColors.ink, fontSize = scale.sp(14f), fontWeight = FontWeight.Bold)
                }
            }
        }
    }
}

