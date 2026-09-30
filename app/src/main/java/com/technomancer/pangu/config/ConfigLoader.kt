package com.technomancer.pangu.config

import android.content.res.AssetManager
import android.util.Log
import com.technomancer.pangu.logic.config.ConfigJson
import java.io.FileNotFoundException

/**
 * Reads optional JSON overrides from assets/config/<name>.json and overlays them on the
 * :game-logic config defaults. A missing file or field keeps the default.
 */
class ConfigLoader(private val assets: AssetManager) {

    fun readText(name: String): String? =
        try {
            assets.open("$DIR/$name.json").bufferedReader(Charsets.UTF_8).use { it.readText() }
        } catch (_: FileNotFoundException) {
            null
        }

    inline fun <reified T> load(name: String, default: T): T =
        ConfigJson.load(readText(name), default) { e ->
            Log.w(TAG, "config/$name.json is invalid, using defaults", e)
        }

    companion object {
        const val DIR = "config"
        const val TAG = "Pangu"
    }
}
