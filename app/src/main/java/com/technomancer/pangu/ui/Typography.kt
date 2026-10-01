package com.technomancer.pangu.ui

import android.content.Context
import android.util.Log
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import com.pico.spatial.ui.design.ProvideTextStyle
import com.technomancer.pangu.logic.loc.Language

/** The font families in use: [text] for words in the current language, [mono] for codes and numbers, [glyph] for UI symbols. */
class PanguFonts(val text: FontFamily, val mono: FontFamily, val glyph: FontFamily)

val LocalPanguFonts = staticCompositionLocalOf { PanguFonts(FontFamily.Default, FontFamily.Monospace, FontFamily.Default) }

/**
 * Noto fonts from res/font: Noto Sans for English and Spanish, Noto Sans SC for Chinese, Noto Sans
 * Mono for codes, Noto Sans Symbols 2 for the UI glyphs. The files are copied in by hand, so they are
 * looked up by name; any that are missing fall back to the system font and are logged once.
 */
object Typography {
    private var warned = false

    fun fonts(context: Context, language: Language): PanguFonts {
        val latin = family(context, "noto_sans_regular" to FontWeight.Normal, "noto_sans_medium" to FontWeight.Medium, "noto_sans_semibold" to FontWeight.SemiBold, "noto_sans_bold" to FontWeight.Bold)
        val sc = family(context, "noto_sans_sc_regular" to FontWeight.Normal, "noto_sans_sc_medium" to FontWeight.Medium, "noto_sans_sc_bold" to FontWeight.Bold)
        val mono = family(context, "noto_sans_mono_regular" to FontWeight.Normal, "noto_sans_mono_bold" to FontWeight.Bold)
        val glyph = family(context, "noto_sans_symbols2_regular" to FontWeight.Normal)
        val text = if (language == Language.Zh) sc ?: latin else latin
        return PanguFonts(text ?: FontFamily.Default, mono ?: FontFamily.Monospace, glyph ?: FontFamily.Default)
    }

    private fun family(context: Context, vararg files: Pair<String, FontWeight>): FontFamily? {
        val fonts = files.mapNotNull { (name, weight) ->
            val id = context.resources.getIdentifier(name, "font", context.packageName)
            if (id != 0) Font(id, weight) else null
        }
        if (fonts.size < files.size && !warned) {
            warned = true
            Log.w("Pangu", "Noto fonts are not in res/font yet; using the system font")
        }
        return if (fonts.isEmpty()) null else FontFamily(fonts)
    }
}

/** Gives [content] the language's fonts: the default text style and [LocalPanguFonts]. */
@Composable
fun PanguType(language: Language, content: @Composable () -> Unit) {
    val context = LocalContext.current
    val fonts = remember(language) { Typography.fonts(context, language) }
    CompositionLocalProvider(LocalPanguFonts provides fonts) {
        ProvideTextStyle(TextStyle(fontFamily = fonts.text), content)
    }
}
