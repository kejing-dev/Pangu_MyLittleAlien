package com.technomancer.pangu.logic.dev

import com.technomancer.pangu.logic.PanguLogic
import com.technomancer.pangu.logic.flow.FlowState
import com.technomancer.pangu.logic.flow.ScreenId
import com.technomancer.pangu.logic.habitat.PadPose
import com.technomancer.pangu.logic.incubation.EggCondition
import com.technomancer.pangu.logic.input.InputSnapshot
import com.technomancer.pangu.logic.input.InteractionQueue
import com.technomancer.pangu.logic.math.Vec3
import com.technomancer.pangu.logic.pet.Distress
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test

class DevCommandTest {
    private fun game(devTools: Boolean) = PanguLogic(startScreen = ScreenId.C03).apply {
        placePad(PadPose(Vec3(0f, 0.9f, -0.75f), 0f))
        this.devTools = devTools
    }

    @Test
    fun commandsDoNothingUnlessSwitchedOn() {
        val logic = game(devTools = false)
        val flow = logic.flow.flow
        for (command in DevCommand.entries) logic.dev(command)
        assertEquals(flow, logic.flow.flow)
        assertEquals(ScreenId.C03, logic.screens.current)
    }

    @Test
    fun stageShortcuts() {
        val logic = game(devTools = true)

        logic.dev(DevCommand.RestartEgg)
        assertEquals(FlowState.Incubation, logic.flow.flow)
        assertEquals(ScreenId.C07, logic.screens.current)
        assertEquals(0f, logic.egg.egg.hatchProgressHours)

        logic.dev(DevCommand.EggReady)
        assertEquals(EggCondition.Ready, logic.egg.condition)
        assertEquals(ScreenId.C20, logic.screens.current)

        logic.dev(DevCommand.HatchNow)
        assertEquals(FlowState.Hatching, logic.flow.flow)
        val input = InputSnapshot()
        val events = InteractionQueue()
        repeat(100) { logic.tick(0.1f, input, events) }
        assertEquals(FlowState.Pet, logic.flow.flow)

        logic.dev(DevCommand.Customize)
        assertEquals(FlowState.Creation, logic.flow.flow)
        assertEquals(ScreenId.C03, logic.screens.current)
    }

    @Test
    fun hatchNowWorksFromAnyStage() {
        val logic = game(devTools = true)
        logic.dev(DevCommand.HatchNow)
        assertEquals(FlowState.Hatching, logic.flow.flow)
    }

    @Test
    fun hatchedPutsAPetOnThePadFromAnyStage() {
        val logic = game(devTools = true)
        logic.dev(DevCommand.Hatched)
        assertEquals(FlowState.Pet, logic.flow.flow)
        assertEquals(ScreenId.C23, logic.screens.current)
        assertEquals(1, logic.hatchedCount)
        // It is a working pet: its needs can be changed and it can be sent back to an egg.
        logic.dev(DevCommand.DrainNeeds)
        assertTrue(logic.pet.needs.values.all { it < 10f })
        logic.dev(DevCommand.RestartEgg)
        assertEquals(FlowState.Incubation, logic.flow.flow)
    }

    @Test
    fun eggAndPetStates() {
        val logic = game(devTools = true)
        logic.dev(DevCommand.EggCold)
        assertEquals(ScreenId.C17, logic.screens.current)
        logic.dev(DevCommand.EggWarm)
        assertEquals(ScreenId.C07, logic.screens.current)
        logic.dev(DevCommand.EggDormant)
        assertEquals(FlowState.Dormant, logic.flow.flow)

        logic.dev(DevCommand.HatchNow)
        val input = InputSnapshot()
        val events = InteractionQueue()
        repeat(100) { logic.tick(0.1f, input, events) }
        logic.dev(DevCommand.DrainNeeds)
        assertTrue(logic.pet.needs.values.all { it < 10f })
        logic.dev(DevCommand.FillNeeds)
        assertTrue(logic.pet.needs.values.all { it == 100f })
        logic.dev(DevCommand.PetHomesick)
        assertEquals(Distress.Homesick, logic.pet.stage())
        assertEquals(ScreenId.C32, logic.screens.current)
        logic.dev(DevCommand.PetCheerUp)
        assertEquals(Distress.None, logic.pet.stage())
        assertEquals(ScreenId.C23, logic.screens.current)
    }
}
