package com.technomancer.pangu.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
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
 * The info panel beside the habitat: optional illustration, title, body and a primary button.
 * [content] is null for screens that have no panel yet; debug builds then show only the debug row.
 */
@Composable
fun InfoPanel(
    content: ScreenContent?,
    loc: LocService,
    scale: UiScale,
    showDebug: Boolean,
    onPrimary: () -> Unit,
    onRecenter: () -> Unit,
) {
    if (content == null && !showDebug) return
    val shape = RoundedCornerShape(scale.px(30), scale.px(26), scale.px(32), scale.px(24))
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
            content.titleKey?.let {
                Text(text = loc.get(it), color = PanelColors.ink, fontSize = scale.sp(26f), fontWeight = FontWeight.SemiBold)
            }
            content.bodyKey?.let {
                Text(text = loc.get(it), color = PanelColors.body, fontSize = scale.sp(18f))
            }
            content.primaryKey?.let { PillButton(loc.get(it), scale, primary = true, onClick = onPrimary) }
        }
        if (showDebug) PillButton(loc.get("debug.recenter"), scale, primary = false, onClick = onRecenter)
    }
}

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
