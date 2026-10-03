package com.technomancer.pangu.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import com.pico.spatial.ui.design.Slider
import com.pico.spatial.ui.design.SliderDefaults
import com.pico.spatial.ui.design.Switch
import com.pico.spatial.ui.design.SwitchDefaults
import com.pico.spatial.ui.design.Text
import com.technomancer.pangu.logic.loc.Language
import com.technomancer.pangu.logic.loc.LocService

/** What Settings shows. [volume] is 0..100. [language] is the device language, kept here so the panel redraws when it changes. */
data class SettingsUi(val language: Language, val volume: Int, val soundOn: Boolean, val hapticsOn: Boolean)

/** C36: volume slider, Sound and Haptics switches and the read-only input row. The language follows the device. */
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
