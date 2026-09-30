package com.technomancer.pangu.logic.loc

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** One language's strings, read from `{ "entries": [ { "k": "...", "v": "..." } ] }`. */
class LocTable(private val map: Map<String, String>) {
    operator fun get(key: String): String? = map[key]
    val keys: Set<String> get() = map.keys

    @Serializable
    private data class Entry(val k: String, val v: String)

    @Serializable
    private data class File(val entries: List<Entry>)

    companion object {
        private val json = Json { ignoreUnknownKeys = true }

        fun parse(text: String): LocTable =
            LocTable(json.decodeFromString(File.serializer(), text).entries.associate { it.k to it.v })
    }
}
