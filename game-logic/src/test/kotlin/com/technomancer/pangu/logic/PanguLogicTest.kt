package com.technomancer.pangu.logic

import com.technomancer.pangu.logic.flow.FlowState
import com.technomancer.pangu.logic.flow.ScreenId
import com.technomancer.pangu.logic.incubation.EggAction
import com.technomancer.pangu.logic.input.LookPick
import com.technomancer.pangu.logic.habitat.PadPose
import com.technomancer.pangu.logic.input.InputSnapshot
import com.technomancer.pangu.logic.input.InteractionQueue
import com.technomancer.pangu.logic.input.InteractionType
import com.technomancer.pangu.logic.input.TargetId
import com.technomancer.pangu.logic.math.Quat
import com.technomancer.pangu.logic.math.Vec3
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class PanguLogicTest {
    private val input = InputSnapshot()
    private val events = InteractionQueue()

    @Test
    fun pausedDoesNotTick() {
        val logic = PanguLogic()
        repeat(3) { logic.tick(0.016f, input, events) }
        logic.setPaused(true)
        repeat(5) { logic.tick(0.016f, input, events) }
        assertEquals(3L, logic.tickCount)
        logic.setPaused(false)
        logic.tick(0.016f, input, events)
        assertEquals(4L, logic.tickCount)
        assertEquals(0.064, logic.elapsedSec, 1e-6)
    }

    @Test
    fun handleDragMovesPadOnlyWhilePlacing() {
        val logic = PanguLogic(startScreen = ScreenId.C02)
        logic.placePad(PadPose(Vec3(0f, 0.9f, -0.75f), 0f))
        input.setHead(Vec3(0f, 1.5f, 0f), Quat.IDENTITY)
        events.push(InteractionType.DragStart, TargetId.PAD_HANDLE)
        events.push(InteractionType.DragMove, TargetId.PAD_HANDLE, Vec3(0.1f, 0.3f, 0f))
        events.push(InteractionType.DragEnd, TargetId.PAD_HANDLE)
        logic.tick(0.016f, input, events)
        assertEquals(0.1f, logic.padPose!!.position.x, 1e-6f)
        assertEquals(0.9f, logic.padPose!!.position.y, 1e-6f)
        assertTrue(logic.consumeSaveRequest())

        events.push(InteractionType.UiPrimary)
        logic.tick(0.016f, input, events)
        assertEquals(ScreenId.C03, logic.screens.current)
        events.push(InteractionType.DragStart, TargetId.PAD_HANDLE)
        events.push(InteractionType.DragMove, TargetId.PAD_HANDLE, Vec3(0.2f, 0f, 0f))
        logic.tick(0.016f, input, events)
        assertEquals(0.1f, logic.padPose!!.position.x, 1e-6f)
        assertFalse(logic.isDraggingPad)
    }

    @Test
    fun orbTapShowsToast() {
        val logic = PanguLogic()
        events.push(InteractionType.Tap, TargetId.CARE_ORB)
        logic.tick(0.016f, input, events)
        assertEquals("toast.orb", logic.toastKey)
        assertEquals(1, logic.toastSerial)
    }

    @Test
    fun recenterPlacesPadInFrontOfHead() {
        val logic = PanguLogic(startScreen = ScreenId.C03)
        logic.placePad(PadPose(Vec3(3f, 0.9f, 3f), 1f))
        input.setHead(Vec3(0f, 1.5f, 0f), Quat.IDENTITY)
        events.push(InteractionType.UiRecenter)
        logic.tick(0.016f, input, events)
        assertEquals(ScreenId.C37, logic.screens.current)
        assertEquals(-0.75f, logic.padPose!!.position.z, 1e-5f)
        assertEquals(0.95f, logic.padPose!!.position.y, 1e-5f)
    }

    @Test
    fun studioToHatchedPet() {
        val logic = PanguLogic(startScreen = ScreenId.C03)
        logic.flow.flow = FlowState.Creation
        logic.placePad(PadPose(Vec3(0f, 0.9f, -0.75f), 0f))
        fun step(sec: Float, dt: Float = 0.1f) = repeat((sec / dt).toInt()) { logic.tick(dt, input, events) }

        events.push(InteractionType.UiLook, LookPick.encode(LookPick.SHAPE, 4))
        events.push(InteractionType.UiPrimary)
        events.push(InteractionType.UiWheel, 0)
        step(0.1f)
        assertEquals(ScreenId.C04, logic.screens.current)
        assertEquals(4, logic.look.shape)
        assertEquals("Miba" + "bo", logic.petName)

        events.push(InteractionType.UiPrimary)
        events.push(InteractionType.UiPrimary)
        step(0.1f)
        assertEquals(ScreenId.C06, logic.screens.current)
        assertEquals(FlowState.Incubation, logic.flow.flow)
        step(3.2f)
        assertEquals(ScreenId.C07, logic.screens.current)
        assertEquals(Vec3.ZERO, logic.egg.eggPosition)

        events.push(InteractionType.Tap, TargetId.CARE_ORB)
        step(0.1f)
        assertEquals(ScreenId.C08, logic.screens.current)
        events.push(InteractionType.UiPie, EggAction.Lamp.ordinal)
        step(0.1f)
        assertEquals(ScreenId.C10, logic.screens.current)
        assertTrue(logic.egg.egg.lampOn(logic.nowMs))

        events.push(InteractionType.UiPrimary)
        events.push(InteractionType.DragStart, TargetId.EGG)
        step(0.1f)
        assertEquals(ScreenId.C09, logic.screens.current)
        step(31f)
        assertEquals("toast.warmDone", logic.toastKey)
        assertEquals(1, logic.egg.ledger.completionsToday[EggAction.Warm.ordinal])
        events.push(InteractionType.DragEnd, TargetId.EGG)
        step(0.1f)
        assertEquals(ScreenId.C07, logic.screens.current)

        logic.egg.egg.hatchProgressHours = 72f
        step(0.1f)
        assertEquals(ScreenId.C20, logic.screens.current)
        repeat(5) { events.push(InteractionType.Tap, TargetId.EGG) }
        step(0.1f)
        assertEquals(ScreenId.C21, logic.screens.current)
        assertEquals(FlowState.Hatching, logic.flow.flow)
        step(8.2f)
        assertEquals(FlowState.Pet, logic.flow.flow)
        assertEquals(ScreenId.C23, logic.screens.current)
        assertEquals(1, logic.hatchedCount)
    }
}
