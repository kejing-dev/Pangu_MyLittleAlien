package com.technomancer.pangu.logic.save

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.decodeFromJsonElement
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonPrimitive

/** Outcome of reading a save file. */
sealed class LoadResult {
    data class Loaded(val data: SaveData) : LoadResult()

    /** No save file yet. */
    data object Empty : LoadResult()

    /** Valid JSON with a different schema version; the caller discards it and starts fresh. */
    data class UnknownVersion(val version: Int?) : LoadResult()

    /** Unreadable file; the caller moves it aside (.bak) and starts fresh. */
    data class Corrupt(val error: Throwable) : LoadResult()
}

object SaveSerializer {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    fun encode(data: SaveData): String = json.encodeToString(SaveData.serializer(), data)

    fun decode(text: String?): LoadResult {
        if (text.isNullOrBlank()) return LoadResult.Empty
        return try {
            val root = json.parseToJsonElement(text) as? JsonObject
                ?: return LoadResult.Corrupt(IllegalArgumentException("save root is not an object"))
            val version = root["version"]?.jsonPrimitive?.let { runCatching { it.int }.getOrNull() }
            if (version != SaveData.SCHEMA_VERSION) return LoadResult.UnknownVersion(version)
            LoadResult.Loaded(json.decodeFromJsonElement<SaveData>(root))
        } catch (e: Exception) {
            LoadResult.Corrupt(e)
        }
    }
}
