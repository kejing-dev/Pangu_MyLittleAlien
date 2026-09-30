package com.technomancer.pangu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import com.pico.spatial.ui.design.Text
import com.technomancer.pangu.logic.loc.LocService
import kotlinx.coroutines.delay

/** Head-locked toast: an ink pill shown for [durationSec] each time [request] changes. */
@Composable
fun Toast(request: ToastRequest?, loc: LocService, scale: UiScale, durationSec: Float) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(request?.serial) {
        if (request == null) return@LaunchedEffect
        visible = true
        delay((durationSec * 1000).toLong())
        visible = false
    }
    if (!visible || request == null) return
    Box(
        modifier = Modifier
            .background(PanelColors.ink, RoundedCornerShape(scale.px(22)))
            .padding(horizontal = scale.px(24), vertical = scale.px(12)),
    ) {
        Text(text = loc.get(request.key), color = PanelColors.paper, fontSize = scale.sp(18f), fontWeight = FontWeight.Bold)
    }
}

/** Head-locked pause screen: a dim veil with a pause disc and the C34 message. */
@Composable
fun PauseDim(paused: Boolean, loc: LocService, scale: UiScale) {
    if (!paused) return
    Box(
        modifier = Modifier
            .size(scale.px(1400), scale.px(900))
            .background(Color(0x6B2F2826)),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Box(
                modifier = Modifier
                    .size(scale.px(155))
                    .background(PanelColors.paper, CircleShape)
                    .border(scale.px(2), PanelColors.ink, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "❚❚", color = PanelColors.ink, fontSize = scale.sp(44f))
            }
            Text(
                text = loc.get("scr.C34.t"),
                color = PanelColors.paper,
                fontSize = scale.sp(26f),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = scale.px(20)),
            )
            Text(
                text = loc.get("scr.C34.b"),
                color = PanelColors.paper,
                fontSize = scale.sp(18f),
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(top = scale.px(8)).size(scale.px(520), scale.px(80)),
            )
        }
    }
}

/** Label floating above the pad handle while the pad can be moved, pointing down at it. Narrow, so it wraps to two lines. */
@Composable
fun HandleLabel(visible: Boolean, loc: LocService, scale: UiScale) {
    if (!visible) return
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            modifier = Modifier
                .width(scale.px(230))
                .background(PanelColors.butter, RoundedCornerShape(scale.px(16)))
                .border(scale.px(1.5f), PanelColors.ink, RoundedCornerShape(scale.px(16)))
                .padding(horizontal = scale.px(14), vertical = scale.px(8)),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = "✊ " + loc.get("hint.handle"),
                color = PanelColors.ink,
                fontSize = scale.sp(18f),
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
            )
        }
        Text(text = "▼", color = PanelColors.ink, fontSize = scale.sp(18f))
    }
}
