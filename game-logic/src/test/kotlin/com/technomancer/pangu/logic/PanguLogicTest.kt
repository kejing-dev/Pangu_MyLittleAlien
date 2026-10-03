package com.technomancer.pangu.logic

import com.technomancer.pangu.logic.flow.FlowState
import com.technomancer.pangu.logic.flow.ScreenId
import com.technomancer.pangu.logic.incubation.EggAction
import com.technomancer.pangu.logic.incubation.MicChoice
import com.technomancer.pangu.logic.input.LookPick
import com.technomancer.pangu.logic.habitat.PadPlacement
import com.technomancer.pangu.logic.habitat.PadPose
import com.technomancer.pangu.logic.input.InputSnapshot
import com.technomancer.pangu.logic.input.InteractionQueue
import com.technomancer.pangu.logic.input.InteractionType
import com.technomancer.pangu.logic.input.TargetId
import com.technomancer.pangu.logic.math.Quat
import com.technomancer.pangu.logic.math.Vec3
import com.technomancer.pangu.logic.pet.PetAction
import com.technomancer.pangu.logic.pet.PetActivity
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
    fun handleDragMovesPadAnyTime() {
        val logic = PanguLogic(startScreen = ScreenId.C02)
        logic.placePad(PadPose(Vec3(0f, 0.9f, -0.75f), 0f))
        input.setHead(Vec3(0f, 1.5f, 0f), Quat.IDENTITY)
        val placement = PadPlacement()
        val handle0 = placement.handlePosition(logic.padPose!!)
        events.push(InteractionType.DragStart, TargetId.PAD_HANDLE)
        events.push(InteractionType.DragMove, TargetId.PAD_HANDLE, Vec3(0.1f, 0.3f, 0f))
        events.push(InteractionType.DragEnd, TargetId.PAD_HANDLE)
        logic.tick(0.016f, input, events)
        // The handle follows the hand sideways; the pad stays at its height.
        assertEquals(handle0.x + 0.1f, placement.handlePosition(logic.padPose!!).x, 1e-3f)
        assertEquals(0.9f, logic.padPose!!.position.y, 1e-6f)
        assertTrue(logic.consumeSaveRequest())

        events.push(InteractionType.UiPrimary)
        logic.tick(0.016f, input, events)
        assertEquals(ScreenId.C03, logic.screens.current)
        events.push(InteractionType.DragStart, TargetId.PAD_HANDLE)
        events.push(InteractionType.DragMove, TargetId.PAD_HANDLE, Vec3(0.2f, 0f, 0f))
        logic.tick(0.016f, input, events)
        // After placement the handle still moves the pad, and letting go saves it.
        assertEquals(handle0.x + 0.3f, placement.handlePosition(logic.padPose!!).x, 1e-3f)
        assertTrue(logic.isDraggingPad)
        events.push(InteractionType.DragEnd, TargetId.PAD_HANDLE)
        logic.tick(0.016f, input, events)
        assertFalse(logic.isDraggingPad)
        assertTrue(logic.consumeSaveRequest())
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
        assertEquals(ScreenId.C22, logic.screens.current)
        events.push(InteractionType.UiPrimary)
        step(0.1f)
        assertEquals(ScreenId.C23, logic.screens.current)
        assertEquals(1, logic.hatchedCount)
    }

    @Test
    fun ritualsCountCapAndMusicBox() {
        val logic = PanguLogic(startScreen = ScreenId.C05)
        logic.flow.flow = FlowState.Creation
        logic.placePad(PadPose(Vec3(0f, 0.9f, -0.75f), 0f))
        logic.debugAssist = true
        fun step(sec: Float, dt: Float = 0.1f) = repeat((sec / dt).toInt()) { logic.tick(dt, input, events) }
        events.push(InteractionType.UiPrimary)
        step(3.5f)
        assertEquals(ScreenId.C07, logic.screens.current)

        // Sing the first time asks how; the music box path needs two crank turns and a 10 s lullaby.
        events.push(InteractionType.Tap, TargetId.CARE_ORB)
        events.push(InteractionType.UiPie, EggAction.Sing.ordinal)
        step(0.1f)
        assertEquals(ScreenId.C11, logic.screens.current)
        events.push(InteractionType.UiSecondary)
        step(0.1f)
        assertEquals(ScreenId.C13, logic.screens.current)
        assertEquals(MicChoice.MusicBox, logic.micChoice)
        events.push(InteractionType.DragStart, TargetId.CRANK)
        val r = 0.02f // drag moves arrive in Stage metres
        for (i in 1..80) {
            val a = 2f * Math.PI.toFloat() * i / 36f
            val b = 2f * Math.PI.toFloat() * (i - 1) / 36f
            events.push(InteractionType.DragMove, TargetId.CRANK, Vec3(r * (kotlin.math.cos(a) - kotlin.math.cos(b)), 0f, r * (kotlin.math.sin(a) - kotlin.math.sin(b))))
            if (i % 20 == 0) step(0.1f) // the event queue holds 64 events per frame
        }
        step(0.1f)
        assertTrue(logic.egg.rituals.lullabySec >= 0f)
        step(10.5f)
        assertEquals(ScreenId.C07, logic.screens.current)
        assertEquals(1, logic.egg.ledger.ritualsToday)
        assertEquals(12, logic.egg.ledger.imprint[0])

        // Listen three times (10 minutes apart), then the fourth shows C16.
        repeat(3) {
            events.push(InteractionType.Tap, TargetId.CARE_ORB)
            events.push(InteractionType.UiPie, EggAction.Listen.ordinal)
            step(0.1f)
            assertEquals(ScreenId.C14, logic.screens.current)
            events.push(InteractionType.UiAssist)
            step(0.1f)
            assertEquals(ScreenId.C07, logic.screens.current)
            logic.nowMs += 11 * 60_000L
        }
        assertEquals(4, logic.egg.ledger.ritualsToday)
        events.push(InteractionType.Tap, TargetId.CARE_ORB)
        events.push(InteractionType.UiPie, EggAction.Listen.ordinal)
        step(0.1f)
        assertEquals(ScreenId.C16, logic.screens.current)
        assertEquals(EggAction.Listen, logic.cappedAction)
    }

    @Test
    fun tubLeavesWhenAnotherActionStarts() {
        val logic = PanguLogic()
        val pet = logic.pet
        pet.startClean()
        assertTrue(pet.tubOut)
        pet.interruptFor(PetAction.Cuddle)
        pet.actions.startCuddle()
        assertFalse(pet.tubOut)
        assertEquals(PetActivity.Cuddling, pet.actions.activity)
    }
}
