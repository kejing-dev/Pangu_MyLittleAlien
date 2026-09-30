package com.technomancer.pangu.logic.save

/** Persistent storage for the serialized save. Implementations write atomically. */
interface ISaveStore {
    /** The saved text, or null if there is none. */
    fun read(): String?

    fun write(text: String)

    /** Moves an unreadable save aside so a fresh game can start. */
    fun quarantine()
}
