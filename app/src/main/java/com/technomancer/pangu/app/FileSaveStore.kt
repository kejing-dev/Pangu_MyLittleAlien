package com.technomancer.pangu.app

import com.technomancer.pangu.logic.save.ISaveStore
import java.io.File

/** Save file in the app's private files dir. Writes go to a .tmp file first and are then renamed over the save. */
class FileSaveStore(dir: File) : ISaveStore {
    private val file = File(dir, FILE_NAME)
    private val tmp = File(dir, "$FILE_NAME.tmp")
    private val bak = File(dir, "$FILE_NAME.bak")

    override fun read(): String? = if (file.exists()) file.readText(Charsets.UTF_8) else null

    @Synchronized
    override fun write(text: String) {
        tmp.writeText(text, Charsets.UTF_8)
        if (!tmp.renameTo(file)) {
            // Some filesystems refuse to rename over an existing file.
            file.delete()
            check(tmp.renameTo(file)) { "Could not replace $file" }
        }
    }

    @Synchronized
    override fun quarantine() {
        if (!file.exists()) return
        bak.delete()
        file.renameTo(bak)
    }

    companion object {
        const val FILE_NAME = "pangu_save.json"
    }
}
