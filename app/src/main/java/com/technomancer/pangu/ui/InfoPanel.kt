package com.technomancer.pangu.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.requiredHeightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import com.technomancer.pangu.logic.loc.Language
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

/** Everything the info panel can show besides the screen itself. */
class PanelState(
    val pet: PetUi?,
    val studio: StudioUi?,
    val egg: EggUi?,
    val counters: Triple<Int, Int, Int>,
    /** Live voice level for the C12 meter. */
    val micLevel: Float = 0f,
    /** Show the debug "Complete step" button on ritual panels. */
    val assist: Boolean = false,
    val settings: SettingsUi? = null,
)

/** What the panel's buttons and pickers report. */
class PanelActions(
    val onPrimary: () -> Unit,
    val onSecondary: () -> Unit,
    val onRecenter: () -> Unit,
    val onLook: (Int) -> Unit,
    val onWheel: (Int) -> Unit,
    val onInfo: () -> Unit = {},
    val onAssist: () -> Unit = {},
    val onSettings: () -> Unit = {},
    val onLanguage: (Language) -> Unit = {},
    val onVolume: (Int) -> Unit = {},
    val onSound: (Boolean) -> Unit = {},
    val onHaptics: (Boolean) -> Unit = {},
    val onQuit: () -> Unit = {},
)

/**
 * The info panel beside the habitat: optional illustration, title, body, a kind-specific block
 * and the primary and secondary buttons, with the gesture hint chip under its bottom-left corner.
 * [content] is null for screens that have no panel.
 */
@Composable
fun InfoPanel(
    content: ScreenContent?,
    state: PanelState,
    loc: LocService,
    scale: UiScale,
    actions: PanelActions,
) {
    if (content == null) return
    val shape = PanguTokens.panelShape(scale)
    val pet = state.pet
    val egg = state.egg
    val name = when {
        content != null && content.id.ordinal < ScreenId.C22.ordinal -> state.studio?.name ?: egg?.name ?: pet?.name ?: ""
        else -> pet?.name ?: ""
    }
    Column(verticalArrangement = Arrangement.spacedBy(scale.px(12))) {
        // The panel is weighted so the hint chip below it is measured first and never squeezed.
        Column(
            modifier = Modifier
                .weight(1f, fill = false)
                .width(scale.px(PanguTokens.PANEL_WIDTH))
                .shadow(scale.px(PanguTokens.SHADOW_Y), shape, ambientColor = PanguTokens.shadow, spotColor = PanguTokens.shadow)
                .background(PanguTokens.paper, shape)
                .border(scale.px(PanguTokens.BORDER), PanguTokens.ink, shape)
                .padding(horizontal = scale.px(PanguTokens.PANEL_PAD_X), vertical = scale.px(PanguTokens.PANEL_PAD_Y)),
            verticalArrangement = Arrangement.spacedBy(scale.px(PanguTokens.PANEL_GAP)),
        ) {
            if (content.kind == PanelKind.Hab) HabitatIllustration(scale)
            if (content.kind == PanelKind.Needs && pet != null) NeedsBlock(pet, loc, scale, actions.onInfo, actions.onSettings, actions.onQuit)
            content.titleKey?.let {
                Text(text = titleText(content, it, name, egg, pet, loc), color = PanguTokens.ink, fontSize = scale.sp(26f), fontWeight = FontWeight.SemiBold)
            }
            bodyText(content, pet, egg, name, loc)?.let {
                Text(text = it, color = PanguTokens.body, fontSize = scale.sp(18f))
            }
            if (content.kind == PanelKind.Lang) LangBlock(state.settings?.language ?: loc.language, loc, scale, actions.onLanguage)
            if (content.kind == PanelKind.Settings && state.settings != null) SettingsBlock(state.settings, loc, scale, actions)
            if (content.kind == PanelKind.Look && state.studio != null) LookBlock(state.studio, loc, scale, actions.onLook)
            if (content.kind == PanelKind.Name && state.studio != null) NameBlock(state.studio, scale, actions.onWheel)
            if (content.kind == PanelKind.Prog && pet != null) {
                when (content.id) {
                    ScreenId.C26 -> ProgressBlock(pet.napMinutes, 30, loc.format("fmt.min", "a" to pet.napMinutes, "b" to 30), scale)
                    ScreenId.C27 -> ProgressBlock(pet.timedSec, 45, loc.format("fmt.sec", "a" to pet.timedSec, "b" to 45), scale)
                    ScreenId.C28 -> ProgressBlock(pet.timedSec, 30, loc.format("fmt.sec", "a" to pet.timedSec, "b" to 30), scale)
                    else -> Unit
                }
            }
            if (content.kind == PanelKind.Prog && egg != null) {
                when (content.id) {
                    ScreenId.C06 -> ProgressBlock(egg.sealSec, egg.sealTotal, loc.format("fmt.sec", "a" to egg.sealSec, "b" to egg.sealTotal), scale)
                    ScreenId.C09 -> ProgressBlock(egg.holdSec, egg.holdTotal, loc.format("fmt.sec", "a" to egg.holdSec, "b" to egg.holdTotal), scale)
                    ScreenId.C12 -> {
                        ProgressBlock(egg.singSec, egg.singTotal, loc.format("fmt.sec", "a" to egg.singSec, "b" to egg.singTotal), scale)
                        VoiceMeter(state.micLevel, scale)
                    }
                    ScreenId.C13 -> ProgressBlock(egg.lullabySec, egg.lullabyTotal, loc.format("fmt.sec", "a" to egg.lullabySec, "b" to egg.lullabyTotal), scale)
                    ScreenId.C14 -> {
                        ProgressBlock(egg.listenSec, egg.listenTotal, loc.format("fmt.sec", "a" to egg.listenSec, "b" to egg.listenTotal), scale)
                        PulseLine(egg.listening, scale)
                    }
                    ScreenId.C15 -> ProgressBlock(egg.circles, egg.circlesTotal, loc.format("fmt.circles", "a" to egg.circles, "b" to egg.circlesTotal), scale)
                    else -> Unit
                }
                if (state.assist && content.id in ASSISTED) PillButton(loc.get("debug.complete"), scale, primary = false, onClick = actions.onAssist)
            }
            if (content.kind == PanelKind.Pers) pet?.personality?.let { PersonalityBlock(it, loc, scale) }
            stepRows(content.id, egg, loc)?.let { StepsBlock(it, loc, scale) }
            if (content.id == ScreenId.C07 && egg != null) {
                KeyValueRows(eggRows(egg, loc), scale)
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    HeaderButtons(scale, null, actions.onSettings, actions.onQuit)
                }
            }
            if (content.id == ScreenId.C35) {
                if (egg?.awayBands != null) KeyValueRows(eggAwayRows(egg, loc), scale) else if (pet != null) AwayRows(pet, loc, scale)
            }
            val primaryKey = if (content.id == ScreenId.C35 && egg?.awayBands != null) "welcome.egg.ok" else content.primaryKey
            primaryKey?.let { PillButton(loc.get(it), scale, primary = true, onClick = actions.onPrimary) }
            content.secondaryKey?.let { PillButton(loc.get(it), scale, primary = false, onClick = actions.onSecondary) }
            if (content.id == ScreenId.C22) pet?.personality?.let {
                Text(text = loc.format("fmt.eggCode", "code" to it.eggCode), color = PanguTokens.muted, fontSize = scale.sp(14f))
            }
            if (content.id == ScreenId.C33 || content.id == ScreenId.C19) {
                val (h, r, w) = state.counters
                Text(text = loc.format("fmt.counters", "h" to h, "r" to r, "w" to w), color = PanguTokens.muted, fontSize = scale.sp(15f))
            }
        }
        HintChip(HINT_GLYPHS[content.hint.key.substringAfterLast('.')] ?: "○", loc.get(content.hint.key), scale)
    }
}

/** The round ⚙ button that opens Settings. */
@Composable
private fun SettingsButton(scale: UiScale, onClick: () -> Unit) = RoundIconButton(scale, onClick) {
    // A gear drawn by hand, so it sits exactly in the middle whatever the fonts are.
    val c = center
    val r = size.minDimension / 2f
    val stroke = r * 0.16f
    for (i in 0 until 8) {
        val a = i * PI.toFloat() / 4f
        val dir = Offset(cos(a), sin(a))
        drawLine(PanguTokens.ink, c + dir * (r * 0.62f), c + dir * (r * 0.92f), strokeWidth = r * 0.24f, cap = StrokeCap.Butt)
    }
    drawCircle(PanguTokens.ink, radius = r * 0.62f, center = c, style = Stroke(width = stroke))
    drawCircle(PanguTokens.ink, radius = r * 0.2f, center = c, style = Stroke(width = stroke))
}

/** The round × button that saves and closes Pangu. */
@Composable
private fun CloseAppButton(scale: UiScale, onClick: () -> Unit) = RoundIconButton(scale, onClick, filled = true) {
    val r = size.minDimension / 2f
    val d = r * 0.62f
    val w = r * 0.2f
    drawLine(PanguTokens.paper, center + Offset(-d, -d), center + Offset(d, d), strokeWidth = w, cap = StrokeCap.Round)
    drawLine(PanguTokens.paper, center + Offset(-d, d), center + Offset(d, -d), strokeWidth = w, cap = StrokeCap.Round)
}

/** The ?, ⚙ and × buttons with even gaps between them. [onInfo] null leaves out the ?. */
@Composable
private fun HeaderButtons(scale: UiScale, onInfo: (() -> Unit)?, onSettings: () -> Unit, onQuit: () -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(scale.px(HEADER_GAP)), verticalAlignment = Alignment.CenterVertically) {
        if (onInfo != null) {
            Box(
                modifier = Modifier
                    .size(scale.px(HEADER_BUTTON))
                    .background(PanguTokens.paper, CircleShape)
                    .border(scale.px(PanguTokens.BORDER), PanguTokens.ink, CircleShape)
                    .spatialHoverEffect()
                    .clickable(onClick = onInfo),
                contentAlignment = Alignment.Center,
            ) {
                Text(text = "?", color = PanguTokens.ink, fontSize = scale.sp(16f), fontWeight = FontWeight.Bold)
            }
        }
        SettingsButton(scale, onSettings)
        CloseAppButton(scale, onQuit)
    }
}

/** Size of the round header buttons and their icons, and the gap between them (mockup px). */
private const val HEADER_BUTTON = 32
private const val HEADER_ICON = 15
private const val HEADER_GAP = 10

/** A small round button with an ink ring (ink-filled when [filled]) and a drawn icon in its middle. */
@Composable
private fun RoundIconButton(scale: UiScale, onClick: () -> Unit, filled: Boolean = false, icon: DrawScope.() -> Unit) {
    Box(
        modifier = Modifier
            .size(scale.px(HEADER_BUTTON))
            .background(if (filled) PanguTokens.ink else PanguTokens.paper, CircleShape)
            .border(scale.px(PanguTokens.BORDER), PanguTokens.ink, CircleShape)
            .spatialHoverEffect()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Canvas(modifier = Modifier.size(scale.px(HEADER_ICON)), onDraw = icon)
    }
}

/** Title text; C07 counts the egg's day, C16 names the capped action and C22 the nature. */
private fun titleText(content: ScreenContent, key: String, name: String, egg: EggUi?, pet: PetUi?, loc: LocService): String = when (content.id) {
    ScreenId.C07 -> loc.format(key, "name" to name, "day" to (egg?.day ?: 1))
    ScreenId.C16 -> loc.get(key).replace("<action>", loc.get("ui.egg6.${egg?.cappedAction?.ordinal ?: 0}"))
    ScreenId.C22 -> loc.format(key, "name" to name).replace("<nature>", loc.get("nature.${pet?.personality?.natureIndex ?: 6}"))
    else -> loc.format(key, "name" to name)
}

/**
 * Stage lists shown on the cards the product mockup draws as steps: what is happening now and
 * what comes next (Sing choice, cold egg, hatching, a sad pet).
 */
private fun stepRows(id: ScreenId, egg: EggUi?, loc: LocService): List<StepRow>? {
    val now = loc.get("steps.now")
    val next = loc.get("steps.next")
    return when (id) {
        ScreenId.C11 -> listOf(
            StepRow("♪", "steps.voice.t", "steps.voice.d", "", false),
            StepRow("♫", "steps.box.t", "steps.box.d", "", false),
        )
        ScreenId.C17 -> listOf(StepRow("❄", "steps.chilly.t", "steps.chilly.d", now, true), StepRow("◌", "steps.fading.t", "steps.fading.d", next, false))
        ScreenId.C18 -> listOf(StepRow("❄", "steps.chilly.t", "steps.chilly.d", "", false), StepRow("◌", "steps.fading.t", "steps.fading.d", now, true))
        ScreenId.C20 -> listOf(
            StepRow("☝", "steps.crack.t", "steps.crack.d", egg?.let { loc.format("fmt.taps", "a" to it.taps, "b" to it.tapsTotal) } ?: now, true),
            StepRow("✦", "steps.hatching.t", "steps.hatching.d", next, false),
        )
        ScreenId.C21 -> listOf(
            StepRow("☝", "steps.crack.t", "steps.crack.d", "✓", false),
            StepRow("✦", "steps.hatching.t", "steps.hatching.d", egg?.let { loc.format("fmt.sec", "a" to it.hatchSec, "b" to it.hatchTotal) } ?: now, true),
        )
        ScreenId.C31 -> listOf(StepRow("◡", "steps.mopey.t", "steps.mopey.d", now, true), StepRow("✦", "steps.homesick.t", "steps.homesick.d", next, false))
        ScreenId.C32 -> listOf(StepRow("◡", "steps.mopey.t", "steps.mopey.d", "", false), StepRow("✦", "steps.homesick.t", "steps.homesick.d", now, true))
        else -> null
    }
}

/** Ritual panels that get the debug "Complete step" button in the Emulator. */
private val ASSISTED = setOf(ScreenId.C09, ScreenId.C12, ScreenId.C13, ScreenId.C14, ScreenId.C15)

/** C07: warmth band, rituals today and the heat lamp. */
private fun eggRows(egg: EggUi, loc: LocService): List<Pair<String, String>> = listOf(
    loc.get("scr.C07.r.warmth") to loc.get("band.${egg.band.ordinal}"),
    loc.get("scr.C07.r.rituals") to loc.format("scr.C07.v.rituals", "n" to egg.ritualsToday),
    loc.get("scr.C07.r.lamp") to lampText(egg.lampHoursLeft, loc),
)

/** C35 for the egg: warmth band before → after, and the lamp. */
private fun eggAwayRows(egg: EggUi, loc: LocService): List<Pair<String, String>> {
    val (before, after) = egg.awayBands ?: return emptyList()
    return listOf(
        loc.get("scr.C07.r.warmth") to "${loc.get("band.${before.ordinal}")} → ${loc.get("band.${after.ordinal}")}",
        loc.get("scr.C07.r.lamp") to lampText(egg.awayLampHours, loc),
    )
}

private fun lampText(hours: Int, loc: LocService): String =
    if (hours > 0) loc.format("scr.C07.v.lampOn", "h" to hours) else loc.get("scr.C07.v.lampOff")

/** Body text: static from the string table, or built from the pet or egg state. */
private fun bodyText(content: ScreenContent, pet: PetUi?, egg: EggUi?, name: String, loc: LocService): String? = when {
    content.id == ScreenId.C24 && pet != null -> {
        val need = loc.get("ui.needs.${pet.lowest.ordinal}")
        val actions = pet.suggested.map { loc.get("ui.pet6.${it.ordinal}") }
        if (actions.size >= 2) {
            loc.format("suggest.two", "need" to need, "a" to actions[0], "b" to actions[1])
        } else {
            loc.format("suggest.one", "need" to need, "a" to actions.firstOrNull().orEmpty())
        }
    }
    content.id == ScreenId.C25 -> {
        val liked = pet?.likedFood ?: -1
        if (liked >= 0) loc.format("scr.C25.b", "food" to loc.get("food.$liked")) else loc.get("scr.C25.bAny")
    }
    content.id == ScreenId.C35 -> {
        val minutes = if (egg?.awayBands != null) egg.awayMinutes else pet?.awayMinutes ?: 0L
        if (minutes < 60) loc.format("scr.C35.bMin", "n" to minutes) else loc.format("scr.C35.b", "n" to minutes / 60)
    }
    else -> content.bodyKey?.let { loc.format(it, "name" to name) }
}

/** C35: the needs that dropped the most while away, and new crumbs. */
@Composable
private fun AwayRows(pet: PetUi, loc: LocService, scale: UiScale) {
    val rows = pet.awayDrops.map { (need, before, after) -> loc.get("ui.needs.${need.ordinal}") to "$before → $after" } +
        if (pet.awayCrumbs > 0) listOf(loc.get("scr.C35.r.crumbs") to pet.awayCrumbs.toString()) else emptyList()
    rows.forEach { (key, value) ->
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = key, color = PanguTokens.muted, fontSize = scale.sp(16f), modifier = Modifier.weight(1f))
            Text(text = value, color = PanguTokens.ink, fontSize = scale.sp(17f), fontWeight = FontWeight.Bold)
        }
        Box(modifier = Modifier.fillMaxWidth().height(scale.px(1.5f)).background(PanguTokens.divider))
    }
}

/** C23: name, mood chip, a "?" that reopens the personality card, and the six need bars. */
@Composable
private fun NeedsBlock(pet: PetUi, loc: LocService, scale: UiScale, onInfo: () -> Unit, onSettings: () -> Unit, onQuit: () -> Unit) {
    // The name and the three round buttons share one row, centred on each other; the mood tag sits under the name.
    Column {
        Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(text = pet.name, color = PanguTokens.ink, fontSize = scale.sp(29f), fontWeight = FontWeight.SemiBold, maxLines = 1, modifier = Modifier.weight(1f))
            HeaderButtons(scale, onInfo, onSettings, onQuit)
        }
        Box(
            modifier = Modifier
                .padding(top = scale.px(4))
                .background(PanguTokens.butter, RoundedCornerShape(scale.px(9)))
                .padding(horizontal = scale.px(8), vertical = scale.px(2)),
        ) {
            Text(text = loc.get("mood.${pet.mood.name.lowercase()}"), color = PanguTokens.ink, fontSize = scale.sp(13f), fontWeight = FontWeight.Bold)
        }
    }
    Need.entries.forEachIndexed { i, need ->
        val value = pet.needs.getOrElse(i) { 0 }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(text = NEED_GLYPHS[i], color = PanguTokens.ink, fontSize = scale.sp(18f), fontFamily = LocalPanguFonts.current.glyph, modifier = Modifier.width(scale.px(26)))
            Text(text = loc.get("ui.needs.${need.ordinal}"), color = PanguTokens.ink, fontSize = scale.sp(16f), fontWeight = FontWeight.Bold, modifier = Modifier.width(scale.px(84)))
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
            .border(scale.px(1.5f), PanguTokens.ink, shape),
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
    NeedBand.Low -> PanguTokens.butter
    NeedBand.Critical -> Color(0xFFD9644F)
}

/** Progress bar with a small mono label, e.g. "12 / 30 min". */
@Composable
private fun ProgressBlock(done: Int, total: Int, label: String, scale: UiScale) {
    val shape = RoundedCornerShape(scale.px(8))
    Box(modifier = Modifier.fillMaxWidth().height(scale.px(16)).border(scale.px(1.5f), PanguTokens.ink, shape)) {
        Box(modifier = Modifier.fillMaxWidth((done.toFloat() / total).coerceIn(0f, 1f)).height(scale.px(16)).background(PanguTokens.ink, shape))
    }
    Text(text = label, color = PanguTokens.muted, fontSize = scale.sp(PanguTokens.MONO), fontFamily = LocalPanguFonts.current.mono)
}

private val NEED_GLYPHS = listOf("●", "☾", "≈", "♫", "♡", "✧")

@Composable
private fun PillButton(label: String, scale: UiScale, primary: Boolean, onClick: () -> Unit) {
    val shape = RoundedCornerShape(scale.px(27))
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .requiredHeightIn(min = scale.px(54))
            .background(if (primary) PanguTokens.ink else PanguTokens.paper, shape)
            .border(scale.px(1.5f), PanguTokens.ink, shape)
            .spatialHoverEffect()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = if (primary) PanguTokens.paper else PanguTokens.ink,
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
        drawOval(PanguTokens.padSide, padTopLeft + Offset(0f, h * 0.04f), pad)
        drawOval(PanguTokens.paper, padTopLeft, pad)
        drawOval(PanguTokens.ink, padTopLeft, pad, style = stroke)
        val nest = Size(pad.width * 0.36f, pad.height * 0.36f)
        drawOval(PanguTokens.nest, Offset((w - nest.width) / 2f, (h - nest.height) / 2f), nest, style = Stroke(width = nest.width * 0.08f))
        val egg = Size(nest.width * 0.34f, nest.height * 0.5f)
        drawOval(PanguTokens.cream, Offset((w - egg.width) / 2f, h / 2f - egg.height * 0.8f), egg)
        drawOval(PanguTokens.ink, Offset((w - egg.width) / 2f, h / 2f - egg.height * 0.8f), egg, style = stroke)
        val lamp = Offset(padTopLeft.x + pad.width * 0.18f, h / 2f)
        drawCircle(PanguTokens.ink, radius = w * 0.022f, center = lamp)
        drawCircle(PanguTokens.butter, radius = w * 0.016f, center = lamp - Offset(0f, h * 0.18f))
        drawLine(PanguTokens.ink, lamp, lamp - Offset(0f, h * 0.16f), strokeWidth = stroke.width)
        val handle = Offset(padTopLeft.x + pad.width + w * 0.03f, h / 2f)
        drawCircle(PanguTokens.butter.copy(alpha = 0.6f), radius = w * 0.04f, center = handle)
        drawCircle(PanguTokens.ink, radius = w * 0.022f, center = handle, style = stroke)
    }
}
