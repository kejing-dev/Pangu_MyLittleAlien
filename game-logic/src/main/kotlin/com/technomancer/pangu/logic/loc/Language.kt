package com.technomancer.pangu.logic.loc

/** Supported languages, in picker order. [code] is the string-table file suffix. */
enum class Language(val code: String, val bcp47: String) {
    En("en", "en-US"),
    Zh("zh", "zh-CN"),
    Es("es", "es-ES");

    companion object {
        fun fromCode(code: String?): Language? = entries.firstOrNull { it.code == code }

        /** Picks the language for a system locale tag such as "es-MX" or "zh-Hans-CN"; English otherwise. */
        fun forLocaleTag(tag: String?): Language {
            val lang = tag?.substringBefore('-')?.substringBefore('_')?.lowercase()
            return fromCode(lang) ?: En
        }
    }
}
