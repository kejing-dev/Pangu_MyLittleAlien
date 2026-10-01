package com.technomancer.pangu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import com.pico.spatial.ui.foundation.hover.spatialHoverEffect
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.pico.spatial.ui.design.SegmentControl
import com.pico.spatial.ui.design.SegmentControlDefaults
import com.pico.spatial.ui.design.SegmentItem
import com.pico.spatial.ui.design.Slider
import com.pico.spatial.ui.design.SliderDefaults
import com.pico.spatial.ui.design.Switch
import com.pico.spatial.ui.design.SwitchDefaults
import com.pico.spatial.ui.design.Text
import com.technomancer.pangu.logic.loc.Language
import com.technomancer.pangu.logic.loc.LocService

/** What Settings shows. [volume] is 0..100. */
data class SettingsUi(val language: Language, val volume: Int, val soundOn: Boolean, val hapticsOn: Boolean)

/** C01: one row per language, native name and mono code; the selected row is inverted. */
@Composable
fun LangBlock(selected: Language, loc: LocService, scale: UiScale, onPick: (Language) -> Unit) {
    val fonts = LocalPanguFonts.current
    Language.entries.forEach { lang ->
        val on = lang == selected
        val shape = RoundedCornerShape(scale.px(16))
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = scale.px(54))
                .background(if (on) PanguTokens.ink else PanguTokens.paper, shape)
                .border(scale.px(PanguTokens.BORDER), PanguTokens.ink, shape)
                .spatialHoverEffect()
                .clickable { onPick(lang) }
                .padding(horizontal = scale.px(18)),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = loc.get("ui.lang.${lang.code}"),
                color = if (on) PanguTokens.paper else PanguTokens.ink,
                fontSize = scale.sp(18f),
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Text(text = lang.bcp47, color = if (on) PanguTokens.paper else PanguTokens.muted, fontSize = scale.sp(PanguTokens.MONO), fontFamily = fonts.mono)
        }
    }
}

/** C36: volume slider, Sound and Haptics switches, language segments and the read-only input row. */
@Composable
fun SettingsBlock(settings: SettingsUi, loc: LocService, scale: UiScale, actions: PanelActions) {
    // The slider moves locally while dragged and reports once it is let go.
    var volume by remember(settings.volume) { mutableFloatStateOf(settings.volume / 100f) }
    SettingRow(loc.get("ui.volume"), scale) {
        Slider(
            value = volume,
            onValueChange = { volume = it },
            onValueChangeFinished = { actions.onVolume((volume * 100f).toInt()) },
            modifier = Modifier.fillMaxWidth(),
            colors = SliderDefaults.sliderColors(
                thumbColor = PanguTokens.paper,
                trackColor = PanguTokens.divider,
                progressColor = PanguTokens.ink,
                segmentDotColor = PanguTokens.muted,
                thumbHighColor = PanguTokens.paper,
                progressHighColor = PanguTokens.ink,
            ),
        )
    }
    SettingRow(loc.get("ui.sound"), scale) {
        Switch(checked = settings.soundOn, onCheckedChange = { actions.onSound(it) }, colors = switchColors())
    }
    SettingRow(loc.get("ui.haptics"), scale) {
        Switch(checked = settings.hapticsOn, onCheckedChange = { actions.onHaptics(it) }, colors = switchColors())
    }
    SettingRow(loc.get("ui.language"), scale) {
        SegmentControl {
            Language.entries.forEach { lang ->
                SegmentItem(
                    title = { Text(loc.get("ui.langLabel.${lang.code}")) },
                    colors = SegmentControlDefaults.colors(PanguTokens.paper, PanguTokens.ink, PanguTokens.ink, PanguTokens.paper),
                    selected = lang == settings.language,
                    onClick = { actions.onLanguage(lang) },
                )
            }
        }
    }
    SettingRow(loc.get("ui.input"), scale) {
        Text(text = loc.get("ui.inputV"), color = PanguTokens.muted, fontSize = scale.sp(16f))
    }
}

@Composable
private fun switchColors() = SwitchDefaults.switchColors(
    checkedThumbColor = PanguTokens.paper,
    checkedTrackColor = PanguTokens.ink,
    uncheckedThumbColor = PanguTokens.paper,
    uncheckedTrackColor = PanguTokens.divider,
)

@Composable
private fun SettingRow(label: String, scale: UiScale, control: @Composable () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().heightIn(min = scale.px(38)), verticalAlignment = Alignment.CenterVertically) {
        Text(text = label, color = PanguTokens.ink, fontSize = scale.sp(16f), fontWeight = FontWeight.Bold, modifier = Modifier.weight(0.42f))
        Box(modifier = Modifier.weight(0.58f), contentAlignment = Alignment.CenterEnd) { control() }
    }
    Box(modifier = Modifier.fillMaxWidth().height(scale.px(PanguTokens.BORDER)).background(PanguTokens.divider))
}
