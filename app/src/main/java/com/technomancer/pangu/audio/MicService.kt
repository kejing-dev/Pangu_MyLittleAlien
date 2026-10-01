package com.technomancer.pangu.audio

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import com.technomancer.pangu.app.PerfProbe
import com.technomancer.pangu.logic.gestures.MicRms

/**
 * Reads the microphone level while the player sings to the egg. 16 kHz mono PCM16 is read on its
 * own thread into one preallocated buffer, and the level (RMS, 0..1) is published every 0.1 s.
 * Nothing is stored or sent anywhere.
 */
class MicService(private val context: Context, private val perf: PerfProbe? = null) {
    @Volatile
    var rms = 0f
        private set

    @Volatile
    private var running = false
    private var thread: Thread? = null

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED

    /** Starts reading; returns false when the permission is missing or the microphone cannot open. */
    @SuppressLint("MissingPermission")
    fun start(): Boolean {
        if (running) return true
        if (!hasPermission()) return false
        val minBuffer = AudioRecord.getMinBufferSize(RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = try {
            AudioRecord(MediaRecorder.AudioSource.MIC, RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, WINDOW * 2 * 2))
        } catch (e: Exception) {
            Log.w(TAG, "Microphone unavailable", e)
            return false
        }
        if (record.state != AudioRecord.STATE_INITIALIZED) {
            record.release()
            return false
        }
        running = true
        thread = Thread({ loop(record) }, "PanguMic").apply { start() }
        return true
    }

    fun stop() {
        running = false
        thread = null
        rms = 0f
    }

    private fun loop(record: AudioRecord) {
        val buffer = ShortArray(WINDOW)
        try {
            record.startRecording()
            while (running) {
                // 1024 samples at 16 kHz is 64 ms, so the level updates more often than every 0.1 s.
                val n = record.read(buffer, 0, WINDOW)
                if (n > 0) {
                    val t = System.nanoTime()
                    rms = MicRms.rms(buffer, n)
                    perf?.micWindowMicros = (System.nanoTime() - t) / 1e3
                }
            }
        } catch (e: Exception) {
            Log.w(TAG, "Microphone read failed", e)
        } finally {
            runCatching { record.stop() }
            record.release()
            rms = 0f
        }
    }

    companion object {
        private const val TAG = "Pangu"
        private const val RATE = 16_000
        private const val WINDOW = 1024
    }
}
