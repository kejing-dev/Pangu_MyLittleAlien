package com.technomancer.pangu.ecs.systems

import com.pico.spatial.tracking.DataProvider
import com.pico.spatial.tracking.controller.ControllerPose
import com.pico.spatial.tracking.controller.ControllerTrackingProvider
import com.pico.spatial.tracking.hand.HandJoint
import com.pico.spatial.tracking.hand.HandPose
import com.pico.spatial.tracking.hand.HandTrackingProvider
import com.pico.spatial.tracking.hmd.HMDTrackingProvider
import com.technomancer.pangu.ecs.toLogic
import com.technomancer.pangu.logic.config.InputConfig
import com.technomancer.pangu.logic.input.InputSnapshot
import com.technomancer.pangu.logic.input.Side

/**
 * Owns the head, hand and controller tracking providers and copies their latest data into the
 * reused [InputSnapshot] each frame. All poses are in Stage space. Untracked devices leave their
 * poke points invalid.
 */
class InputSystem(private val config: InputConfig) {
    val hmd = HMDTrackingProvider()
    private val hands = HandTrackingProvider()
    private val controllers = ControllerTrackingProvider()

    fun start() {
        hmd.start()
        hands.start()
        controllers.start()
    }

    fun stop() {
        controllers.stop()
        hands.stop()
        hmd.stop()
    }

    fun collect(snapshot: InputSnapshot) {
        snapshot.clear()
        if (hmd.state == DataProvider.State.STARTED) {
            hmd.latestData?.hmdPose?.let { snapshot.setHead(it.position.toLogic(), it.rotation.toLogic()) }
        }
        if (hands.state == DataProvider.State.STARTED) {
            hands.latestData?.let {
                indexTip(it.left)?.let { tip -> snapshot.setHandTip(Side.Left, tip) }
                indexTip(it.right)?.let { tip -> snapshot.setHandTip(Side.Right, tip) }
            }
        }
        if (controllers.state == DataProvider.State.STARTED) {
            controllers.latestData?.let {
                setController(snapshot, Side.Left, it.left)
                setController(snapshot, Side.Right, it.right)
            }
        }
    }

    private fun indexTip(pose: HandPose?) =
        pose?.takeIf { it.handJoints.isNotEmpty() }?.joint(HandJoint.Index.INDEX_TIP)?.position?.toLogic()

    private fun setController(snapshot: InputSnapshot, side: Side, pose: ControllerPose?) {
        pose ?: return
        snapshot.setControllerTip(side, pose.position.toLogic(), pose.rotation.toLogic(), config.controllerTipOffset)
    }
}
