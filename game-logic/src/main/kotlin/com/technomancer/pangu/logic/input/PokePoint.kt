package com.technomancer.pangu.logic.input

import com.technomancer.pangu.logic.math.Vec3

/**
 * A point that can touch things: an index fingertip or a controller tip, in Stage space.
 * Invalid when that hand or controller is not tracked this frame.
 */
class PokePoint(val modality: Modality, val side: Side) {
    var valid: Boolean = false
    var position: Vec3 = Vec3.ZERO

    fun invalidate() {
        valid = false
    }
}
