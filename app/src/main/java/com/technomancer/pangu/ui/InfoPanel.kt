package com.technomancer.pangu.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import com.pico.spatial.ui.design.Text
import com.technomancer.pangu.logic.flow.PanelKind
import com.technomancer.pangu.logic.flow.ScreenContent
import com.technomancer.pangu.logic.flow.ScreenId
import com.technomancer.pangu.logic.pet.Need
import com.technomancer.pangu.logic.pet.NeedBand
import com.technomancer.pangu.logic.pet.NeedBar
import com.technomancer.pangu.logic.loc.LocService

/** Colours from the mockup. The full panel styling arrives with the mockup look pass. */
object PanelColors {
    val ink = Color(0xFF2F2826)
    val paper = Color(0xFFF7F3EB)
    val cream = Color(0xFFF2ECE1)
    val body = Color(0xFF4F4641)
    val muted = Color(0xFF6B615B)
    val divider = Color(0xFFE2D9CC)
    val padSide = Color(0xFFDDD3C4)
    val nest = Color(0xFFE7D6BF)
    val butter = Color(0xFFF2DE9B)
}

/**
 * The info panel beside the habitat: optional illustration, title, body, a kind-specific block
 * and a primary button. [content] is null for screens that have no panel yet; debug builds then
 * show only the debug row. [pet] fills the pet screens.
 */
@Composable
fun InfoPanel(
    content: ScreenContent?,
    pet: PetUi?,
    loc: LocService,
    scale: UiScale,
    showDebug: Boolean,
    onPrimary: () -> Unit,
    onRecenter: () -> Unit,
) {
    if (content == null && !showDebug) return
    val shape = RoundedCornerShape(scale.px(30), scale.px(26), scale.px(32), scale.px(24))
    val name = pet?.name ?: ""
    Column(
        modifier = Modifier
            .width(scale.px(420))
            .background(PanelColors.paper, shape)
            .border(scale.px(1.5f), PanelColors.ink, shape)
            .padding(horizontal = scale.px(30), vertical = scale.px(28)),
        verticalArrangement = Arrangement.spacedBy(scale.px(14)),
    ) {
        if (content != null) {
            if (content.kind == PanelKind.Hab) HabitatIllustration(scale)
            if (content.kind == PanelKind.Needs && pet != null) NeedsBlock(pet, loc, scale)
            content.titleKey?.let {
                Text(text = loc.format(it, "name" to name), color = PanelColors.ink, fontSize = scale.sp(26f), fontWeight = FontWeight.SemiBold)
            }
            bodyText(content, pet, loc)?.let {
                Text(text = it, color = PanelColors.body, fontSize = scale.sp(18f))
            }
            if (content.kind == PanelKind.Prog && content.id == ScreenId.C26 && pet != null) {
                ProgressBlock(pet.napMinutes, 30, loc.format("fmt.min", "a" to pet.napMinutes, "b" to 30), scale)
            }
            content.primaryKey?.let { PillButton(loc.get(it), scale, primary = true, onClick = onPrimary) }
        }
        if (showDebug) PillButton(loc.get("debug.recenter"), scale, primary = false, onClick = onRecenter)
    }
}

/** Body text: static from the string table, or built from the pet state for C24 and C25. */
private fun bodyText(content: ScreenContent, pet: PetUi?, loc: LocService): String? = when {
    content.id == ScreenId.C24 && pet != null -> {
        val need = loc.get("ui.needs.${pet.lowest.ordinal}")
        val actions = pet.suggested.map { loc.get("ui.pet6.${it.ordinal}") }
        if (actions.size >= 2) {
            loc.format("suggest.two", "need" to need, "a" to actions[0], "b" to actions[1])
        } else {
            loc.format("suggest.one", "need" to need, "a" to actions.firstOrNull().orEmpty())
        }
    }
    content.id == ScreenId.C25 -> loc.format("scr.C25.b", "food" to loc.get("food.2"))
    else -> content.bodyKey?.let { loc.format(it, "name" to (pet?.name ?: "")) }
}

/** C23: name, mood chip and the six need bars. */
@Composable
private fun NeedsBlock(pet: PetUi, loc: LocService, scale: UiScale) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(text = pet.name, color = PanelColors.ink, fontSize = scale.sp(29f), fontWeight = FontWeight.SemiBold)
        Box(
            modifier = Modifier
                .padding(start = scale.px(12))
                .background(PanelColors.butter, RoundedCornerShape(scale.px(12)))
                .padding(horizontal = scale.px(10), vertical = scale.px(4)),
        ) {
            Text(text = loc.get("mood.${pet.mood.name.lowercase()}"), color = PanelColors.ink, fontSize = scale.sp(17f), fontWeight = FontWeight.Bold)
        }
    }
    Need.entries.forEachIndexed { i, need ->
        val value = pet.needs.getOrElse(i) { 0 }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = NEED_GLYPHS[i], color = PanelColors.ink, fontSize = scale.sp(18f), modifier = Modifier.width(scale.px(26)))
            Text(text = loc.get("ui.needs.${need.ordinal}"), color = PanelColors.ink, fontSize = scale.sp(16f), fontWeight = FontWeight.Bold, modifier = Modifier.width(scale.px(84)))
            NeedBarView(value, scale)
        }
    }
}

@Composable
private fun NeedBarView(value: Int, scale: UiScale) {
    val animated by animateFloatAsState(targetValue = value / 100f, animationSpec = tween(400), label = "need")
    val shape = RoundedCornerShape(scale.px(10))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(scale.px(20))
            .background(Color.White, shape)
            .border(scale.px(1.5f), PanelColors.ink, shape),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth(animated.coerceIn(0f, 1f))
                .height(scale.px(20))
                .background(bandColor(value), shape),
        )
    }
}

private fun bandColor(value: Int): Color = when (NeedBar.band(value.toFloat())) {
    NeedBand.Good -> Color(0xFFBFD3AE)
    NeedBand.Low -> PanelColors.butter
    NeedBand.Critical -> Color(0xFFD9644F)
}

/** Progress bar with a small mono label, e.g. "12 / 30 min". */
@Composable
private fun ProgressBlock(done: Int, total: Int, label: String, scale: UiScale) {
    val shape = RoundedCornerShape(scale.px(8))
    Box(modifier = Modifier.fillMaxWidth().height(scale.px(16)).border(scale.px(1.5f), PanelColors.ink, shape)) {
        Box(modifier = Modifier.fillMaxWidth((done.toFloat() / total).coerceIn(0f, 1f)).height(scale.px(16)).background(PanelColors.ink, shape))
    }
    Text(text = label, color = PanelColors.muted, fontSize = scale.sp(14f))
}

private val NEED_GLYPHS = listOf("●", "☾", "≈", "♫", "♡", "✧")

@Composable
private fun PillButton(label: String, scale: UiScale, primary: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(scale.px(27))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = scale.px(54))
            .background(if (primary) PanelColors.ink else PanelColors.paper, shape)
            .border(scale.px(1.5f), PanelColors.ink, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (primary) PanelColors.paper else PanelColors.ink,
            fontSize = scale.sp(18f),
            fontWeight = FontWeight.SemiBold,
        )
    }
}

/** Top-down sketch of the habitat: pad, nest, egg, lamp and the glowing handle. */
@Composable
private fun HabitatIllustration(scale: UiScale) {
    Canvas(modifier = Modifier.size(scale.px(358), scale.px(170))) {
        val w = size.width
        val h = size.height
        val stroke = Stroke(width = 1.5f * w / 358f)
        val pad = Size(w * 0.62f, h * 0.62f)
        val padTopLeft = Offset((w - pad.width) / 2f, (h - pad.height) / 2f)
        drawOval(PanelColors.padSide, padTopLeft + Offset(0f, h * 0.04f), pad)
        drawOval(PanelColors.paper, padTopLeft, pad)
        drawOval(PanelColors.ink, padTopLeft, pad, style = stroke)
        val nest = Size(pad.width * 0.36f, pad.height * 0.36f)
        drawOval(PanelColors.nest, Offset((w - nest.width) / 2f, (h - nest.height) / 2f), nest, style = Stroke(width = nest.width * 0.08f))
        val egg = Size(nest.width * 0.34f, nest.height * 0.5f)
        drawOval(PanelColors.cream, Offset((w - egg.width) / 2f, h / 2f - egg.height * 0.8f), egg)
        drawOval(PanelColors.ink, Offset((w - egg.width) / 2f, h / 2f - egg.height * 0.8f), egg, style = stroke)
        val lamp = Offset(padTopLeft.x + pad.width * 0.18f, h / 2f)
        drawCircle(PanelColors.ink, radius = w * 0.022f, center = lamp)
        drawCircle(PanelColors.butter, radius = w * 0.016f, center = lamp - Offset(0f, h * 0.18f))
        drawLine(PanelColors.ink, lamp, lamp - Offset(0f, h * 0.16f), strokeWidth = stroke.width)
        val handle = Offset(padTopLeft.x + pad.width + w * 0.03f, h / 2f)
        drawCircle(PanelColors.butter.copy(alpha = 0.6f), radius = w * 0.04f, center = handle)
        drawCircle(PanelColors.ink, radius = w * 0.022f, center = handle, style = stroke)
    }
}
