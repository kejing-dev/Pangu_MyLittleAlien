package com.technomancer.pangu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import com.pico.spatial.ui.design.Text
import com.technomancer.pangu.logic.creation.LookCatalog
import com.technomancer.pangu.logic.input.LookPick
import com.technomancer.pangu.logic.loc.LocService

/** Small uppercase section label above a row of choices. */
@Composable
private fun SectionLabel(text: String, scale: UiScale) {
    Text(text = text.uppercase(), color = PanelColors.muted, fontSize = scale.sp(13f), fontWeight = FontWeight.Bold)
}

/** C03: body swatches, tip swatches and the 3×2 antenna shape grid. */
@Composable
fun LookBlock(studio: StudioUi, loc: LocService, scale: UiScale, onPick: (Int) -> Unit) {
    SectionLabel(loc.get("ui.body"), scale)
    SwatchRow(studio.swatches, studio.look.body, scale) { onPick(LookPick.encode(LookPick.BODY, it)) }
    SectionLabel(loc.get("ui.tip"), scale)
    SwatchRow(studio.swatches, studio.look.tip, scale) { onPick(LookPick.encode(LookPick.TIP, it)) }
    SectionLabel(loc.get("ui.shape"), scale)
    Column(verticalArrangement = Arrangement.spacedBy(scale.px(8))) {
        for (row in 0 until 2) {
            Row(horizontalArrangement = Arrangement.spacedBy(scale.px(8))) {
                for (col in 0 until 3) {
                    val i = row * 3 + col
                    val selected = i == studio.look.shape
                    val shape = RoundedCornerShape(scale.px(12))
                    Box(
                        modifier = Modifier
                            .width(scale.px(114))
                            .height(scale.px(40))
                            .background(if (selected) PanelColors.ink else PanelColors.cream, shape)
                            .border(scale.px(1.5f), PanelColors.ink, shape)
                            .clickable { onPick(LookPick.encode(LookPick.SHAPE, i)) },
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = "${SHAPE_GLYPHS[i]} ${loc.get("ui.shapes.$i")}",
                            color = if (selected) PanelColors.paper else PanelColors.ink,
                            fontSize = scale.sp(15f),
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

/** Eight colour dots; the selected one gets a paper ring inside an ink ring. */
@Composable
private fun SwatchRow(swatches: List<Int>, selected: Int, scale: UiScale, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(scale.px(8)), verticalAlignment = Alignment.CenterVertically) {
        swatches.forEachIndexed { i, rgb ->
            val color = Color(0xFF000000.toInt() or rgb)
            val chosen = i == selected
            Box(
                modifier = Modifier
                    .size(scale.px(if (chosen) 42 else 34))
                    .then(if (chosen) Modifier.border(scale.px(5), PanelColors.ink, CircleShape).padding(scale.px(5)) else Modifier)
                    .then(if (chosen) Modifier.border(scale.px(3), PanelColors.paper, CircleShape).padding(scale.px(3)) else Modifier)
                    .background(color, CircleShape)
                    .border(scale.px(1.5f), PanelColors.ink, CircleShape)
                    .clickable { onPick(i) },
            )
        }
    }
}

/** C04: three syllable wheels to poke, then "= Name". */
@Composable
fun NameBlock(studio: StudioUi, scale: UiScale, onWheel: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(scale.px(10))) {
        studio.wheels.forEachIndexed { i, syllable ->
            val shape = RoundedCornerShape(scale.px(16))
            Column(
                modifier = Modifier
                    .width(scale.px(76))
                    .height(scale.px(62))
                    .background(PanelColors.cream, shape)
                    .border(scale.px(1.5f), PanelColors.ink, shape)
                    .clickable { onWheel(i) },
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(text = syllable, color = PanelColors.ink, fontSize = scale.sp(22f), fontWeight = FontWeight.SemiBold)
                Text(text = "↻", color = PanelColors.muted, fontSize = scale.sp(11f))
            }
        }
    }
    Text(text = "= ${studio.name}", color = PanelColors.ink, fontSize = scale.sp(35f), fontWeight = FontWeight.SemiBold)
}

/** Key / value rows with thin dividers, as on C07 and C35. */
@Composable
fun KeyValueRows(rows: List<Pair<String, String>>, scale: UiScale) {
    rows.forEach { (key, value) ->
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = key, color = PanelColors.muted, fontSize = scale.sp(16f), modifier = Modifier.weight(1f))
            Text(text = value, color = PanelColors.ink, fontSize = scale.sp(17f), fontWeight = FontWeight.Bold)
        }
        Box(modifier = Modifier.fillMaxWidth().height(scale.px(1.5f)).background(PanelColors.divider))
    }
}

/** Glyph per antenna shape, in [LookCatalog.SHAPES] order. */
private val SHAPE_GLYPHS = listOf("●", "★", "♥", "@", "✿", "ϟ")
