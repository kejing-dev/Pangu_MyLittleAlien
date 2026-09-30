package com.technomancer.pangu.logic.input

import com.technomancer.pangu.logic.math.Quat
import com.technomancer.pangu.logic.math.Vec3

/**
 * Everything the logic reads about the player in one frame, in Stage space.
 * One instance is reused every frame; the app fills it before the logic runs.
 */
class InputSnapshot {
    var headValid: Boolean = false
    var headPosition: Vec3 = Vec3.ZERO
    var headRotation: Quat = Quat.IDENTITY

    /** Left hand tip, right hand tip, left controller tip, right controller tip. */
    val pokes: Array<PokePoint> = arrayOf(
        PokePoint(Modality.Hand, Side.Left),
        PokePoint(Modality.Hand, Side.Right),
        PokePoint(Modality.Controller, Side.Left),
        PokePoint(Modality.Controller, Side.Right),
    )

    fun setHead(position: Vec3, rotation: Quat) {
        headValid = true
        headPosition = position
        headRotation = rotation
    }

    fun setHandTip(side: Side, tip: Vec3) {
        val p = pokes[if (side == Side.Left) HAND_LEFT else HAND_RIGHT]
        p.valid = true
        p.position = tip
    }

    /** The controller's poke point sits [tipOffset] metres along the controller's forward (−Z) axis. */
    fun setControllerTip(side: Side, position: Vec3, rotation: Quat, tipOffset: Float) {
        val p = pokes[if (side == Side.Left) CONTROLLER_LEFT else CONTROLLER_RIGHT]
        p.valid = true
        p.position = position + rotation.rotate(Vec3.FORWARD) * tipOffset
    }

    fun clear() {
        headValid = false
        for (p in pokes) p.invalidate()
    }

    companion object {
        const val HAND_LEFT = 0
        const val HAND_RIGHT = 1
        const val CONTROLLER_LEFT = 2
        const val CONTROLLER_RIGHT = 3
    }
}
