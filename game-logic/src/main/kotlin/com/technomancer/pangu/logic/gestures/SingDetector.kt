package com.technomancer.pangu.logic.gestures

import kotlin.math.sqrt

/**
 * Singing to the egg: time is counted while the microphone level is at least [threshold] and
 * the head is within [maxDistance] metres of the egg. Quiet moments pause it; they do not reset it.
 */
class SingDetector(private val threshold: Float, private val maxDistance: Float, private val requiredSec: Float) {
    var sungSec = 0f
        private set

    /** True on the update that completes the song. */
    fun update(rms: Float, headDistance: Float, dt: Float): Boolean {
        if (sungSec >= requiredSec) return false
        if (rms >= threshold && headDistance <= maxDistance) {
            sungSec = (sungSec + dt).coerceAtMost(requiredSec)
            return sungSec >= requiredSec
        }
        return false
    }

    fun reset() {
        sungSec = 0f
    }
}

/** Microphone level helpers. */
object MicRms {
    /** Root mean square of the first [count] 16-bit samples, scaled to 0..1 of full scale. */
    fun rms(samples: ShortArray, count: Int = samples.size): Float {
        if (count <= 0) return 0f
        var sum = 0.0
        for (i in 0 until count) {
            val s = samples[i] / 32768.0
            sum += s * s
        }
        return sqrt(sum / count).toFloat()
    }
}
