package com.technomancer.pangu.logic.config

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.encodeToJsonElement

/**
 * Loads config data classes from optional JSON overrides.
 *
 * The JSON is overlaid on [default] field by field (nested objects merge recursively), so a
 * missing file or field keeps the default value. Unknown keys are ignored. Malformed JSON keeps
 * the whole default and is reported through onError.
 */
object ConfigJson {
    val json: Json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        isLenient = true
    }

    inline fun <reified T> load(
        text: String?,
        default: T,
        noinline onError: ((Throwable) -> Unit)? = null,
    ): T {
        if (text.isNullOrBlank()) return default
        return try {
            val base = json.encodeToJsonElement(default)
            val override = json.parseToJsonElement(text)
            json.decodeFromJsonElement<T>(merge(base, override))
        } catch (e: Exception) {
            onError?.invoke(e)
            default
        }
    }

    fun merge(base: JsonElement, override: JsonElement): JsonElement {
        if (base !is JsonObject || override !is JsonObject) return override
        val out = LinkedHashMap<String, JsonElement>(base)
        for ((key, value) in override) {
            val existing = out[key]
            out[key] = if (existing != null) merge(existing, value) else value
        }
        return JsonObject(out)
    }
}
