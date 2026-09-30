package com.technomancer.pangu.logic.loc

/**
 * Looks up visible strings in the current language. A key missing from that language falls back
 * to English and is reported once through [onMissing]; a key missing everywhere returns the key.
 *
 * Templates use `{name}`-style and `<name>`-style placeholders. Formatting reuses one buffer,
 * so callers should rebuild strings only when state changes.
 */
class LocService(
    private val tables: Map<Language, LocTable>,
    initial: Language = Language.En,
    private val onMissing: (String) -> Unit = {},
) {
    var language: Language = initial
        private set

    private val reported = HashSet<String>()
    private val buffer = StringBuilder(128)

    fun setLanguage(language: Language) {
        this.language = language
    }

    fun get(key: String): String {
        tables[language]?.get(key)?.let { return it }
        report("${language.code}:$key")
        return tables[Language.En]?.get(key) ?: key.also { report("en:$key") }
    }

    /** [get] with each placeholder named in [args] (pairs of name, value) replaced, e.g. "name" fills both {name} and <name>. */
    fun format(key: String, vararg args: Pair<String, Any>): String = fill(get(key), *args)

    fun fill(template: String, vararg args: Pair<String, Any>): String {
        buffer.setLength(0)
        var i = 0
        while (i < template.length) {
            val c = template[i]
            val close = if (c == '{') '}' else if (c == '<') '>' else null
            if (close != null) {
                val end = template.indexOf(close, i + 1)
                if (end > i) {
                    val name = template.substring(i + 1, end)
                    val value = args.firstOrNull { it.first == name }?.second
                    if (value != null) {
                        buffer.append(value)
                        i = end + 1
                        continue
                    }
                }
            }
            buffer.append(c)
            i++
        }
        return buffer.toString()
    }

    private fun report(id: String) {
        if (reported.add(id)) onMissing(id)
    }
}
