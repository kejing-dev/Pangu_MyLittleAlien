package com.technomancer.pangu.ui

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import com.pico.spatial.ui.design.Text
import com.technomancer.pangu.logic.loc.LocService

private val Accent = Color(0xFFB4533F)
private val DuskBlue = Color(0xFFA8BFD4)

/** C12: fourteen bars that rise with the live voice level; lit bars are ink, the rest divider grey. */
@Composable
fun VoiceMeter(level: Float, scale: UiScale) {
    val lit = (level / FULL_LEVEL * BARS).toInt().coerceIn(0, BARS)
    Row(
        modifier = Modifier.fillMaxWidth().height(scale.px(44)),
        horizontalArrangement = Arrangement.spacedBy(scale.px(6)),
        verticalAlignment = Alignment.Bottom,
    ) {
        for (i in 0 until BARS) {
            val h = 12 + 32 * (i + 1) / BARS
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(scale.px(h))
                    .background(if (i < lit) PanelColors.ink else PanelColors.divider, RoundedCornerShape(scale.px(3))),
            )
        }
    }
}

/** C14: a heartbeat line scrolling across while the player holds still. */
@Composable
fun PulseLine(active: Boolean, scale: UiScale) {
    val transition = rememberInfiniteTransition(label = "pulse")
    val shift by transition.animateFloat(0f, 1f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Restart), label = "shift")
    Canvas(modifier = Modifier.fillMaxWidth().height(scale.px(40))) {
        val w = size.width
        val mid = size.height / 2f
        val path = Path()
        val beat = w / 3f
        path.moveTo(0f, mid)
        var x = 0f
        while (x <= w) {
            val phase = ((x / beat) + (if (active) shift else 0f)) % 1f
            // A flat line with one sharp spike per beat.
            val y = when {
                !active -> mid
                phase in 0.40f..0.45f -> mid - size.height * 0.45f * ((phase - 0.40f) / 0.05f)
                phase in 0.45f..0.52f -> mid - size.height * 0.45f + size.height * 0.85f * ((phase - 0.45f) / 0.07f)
                phase in 0.52f..0.56f -> mid + size.height * 0.4f * (1f - (phase - 0.52f) / 0.04f)
                else -> mid
            }
            path.lineTo(x, y)
            x += 2f
        }
        drawPath(path, Accent, style = Stroke(width = 1.8f * w / 360f))
        drawLine(PanelColors.divider, Offset(0f, mid), Offset(w, mid), strokeWidth = 1f)
    }
}

/** C22: quirk box, then one bar per axis with ▲ on the up axis and ▼ on the down axis. */
@Composable
fun PersonalityBlock(p: PersonalityUi, loc: LocService, scale: UiScale) {
    val quirk = loc.get("quirk.${p.quirk}.name")
    val desc = loc.get("quirk.${p.quirk}.desc")
    val line = if (p.likedFood >= 0) {
        loc.format("quirk.line", "quirk" to quirk, "desc" to desc, "food" to loc.get("food.${p.likedFood}"))
    } else {
        loc.format("quirk.lineNone", "quirk" to quirk, "desc" to desc)
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(PanelColors.cream, RoundedCornerShape(scale.px(14)))
            .padding(horizontal = scale.px(16), vertical = scale.px(12)),
    ) {
        Text(text = "${loc.get("ui.quirk")} · $line", color = PanelColors.body, fontSize = scale.sp(16f))
    }
    p.scores.forEachIndexed { i, score ->
        val up = !p.neutral && i == p.up
        val down = !p.neutral && i == p.down
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = loc.get("ui.axes.$i"), color = PanelColors.ink, fontSize = scale.sp(15f), fontWeight = FontWeight.Bold, modifier = Modifier.width(scale.px(70)))
            val shape = RoundedCornerShape(scale.px(7))
            Box(modifier = Modifier.weight(1f).height(scale.px(14)).background(Color.White, shape)) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth((score / MAX_SCORE).coerceIn(0.03f, 1f))
                        .height(scale.px(14))
                        .background(if (up) PanelColors.ink else DuskBlue, shape),
                )
            }
            Text(
                text = when {
                    up -> "▲"
                    down -> "▼"
                    else -> ""
                },
                color = PanelColors.ink,
                fontSize = scale.sp(14f),
                modifier = Modifier.width(scale.px(26)).padding(start = scale.px(8)),
            )
        }
    }
}

private const val BARS = 14

/** Voice level that fills the whole meter. */
private const val FULL_LEVEL = 0.12f

/** Highest possible axis score: 255 imprint + 63 baseline. */
private const val MAX_SCORE = 318f
