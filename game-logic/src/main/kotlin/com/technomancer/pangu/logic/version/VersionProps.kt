package com.technomancer.pangu.logic.version

/**
 * The app version read from the Gradle properties panguVersionCode and panguVersionName. A missing
 * property takes its default; a malformed one is an error, so a bad release version fails the build
 * instead of shipping as 1. app/build.gradle.kts applies the same rules.
 */
data class VersionProps(val code: Int, val name: String) {
    companion object {
        const val DEFAULT_CODE = 1
        const val DEFAULT_NAME = "1.0.0"

        /** The largest versionCode the Android tooling and stores accept. */
        const val MAX_CODE = 2_100_000_000

        private val NAME_PATTERN = Regex("""\d+(\.\d+){0,3}([-+][0-9A-Za-z.-]+)?""")

        fun parse(code: String?, name: String?): VersionProps {
            val c = code?.trim()?.takeIf { it.isNotEmpty() }?.let {
                val n = it.toIntOrNull()
                require(n != null && n in 1..MAX_CODE) { "panguVersionCode must be a whole number from 1 to $MAX_CODE, got \"$it\"" }
                n
            } ?: DEFAULT_CODE
            val v = name?.trim()?.takeIf { it.isNotEmpty() }?.also {
                require(NAME_PATTERN.matches(it)) { "panguVersionName must look like 1.2.3 (optionally -suffix), got \"$it\"" }
            } ?: DEFAULT_NAME
            return VersionProps(c, v)
        }
    }
}
