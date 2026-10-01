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
import androidx.compose.ui.text.font.Typeface
import com.pico.spatial.ui.design.ProvideTextStyle
import com.technomancer.pangu.logic.loc.Language

/**
 * The font families in use: [text] for words in the current language, [mono] for codes and
 * numbers, [glyph] for UI symbols, and [nativeName] for each language's own name (中文 needs the
 * Chinese font even while the rest of the panel is in English or Spanish).
 */
class PanguFonts(val text: FontFamily, val mono: FontFamily, val glyph: FontFamily, val chinese: FontFamily = text) {
    fun nativeName(language: Language): FontFamily = if (language == Language.Zh) chinese else text
}

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
        val glyph = glyphFamily(context)
        val text = if (language == Language.Zh) sc ?: latin else latin
        return PanguFonts(text ?: FontFamily.Default, mono ?: FontFamily.Monospace, glyph ?: FontFamily.Default, sc ?: text ?: FontFamily.Default)
    }

    /**
     * UI symbols: Noto Sans Symbols 2 first, then Noto Sans Symbols, Noto Sans Math and Noto Sans,
     * chained as one typeface so each symbol comes from the first font that has it.
     */
    private fun glyphFamily(context: Context): FontFamily? {
        val chain = listOf("noto_sans_symbols2_regular", "noto_sans_symbols_regular", "noto_sans_math_regular", "noto_sans_regular")
            .mapNotNull { name ->
                val id = context.resources.getIdentifier(name, "font", context.packageName)
                if (id == 0) null else runCatching { android.graphics.fonts.FontFamily.Builder(android.graphics.fonts.Font.Builder(context.resources, id).build()).build() }.getOrNull()
            }
        if (chain.isEmpty()) return null
        val builder = android.graphics.Typeface.CustomFallbackBuilder(chain.first())
        chain.drop(1).forEach { builder.addCustomFallback(it) }
        return FontFamily(Typeface(builder.build()))
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
