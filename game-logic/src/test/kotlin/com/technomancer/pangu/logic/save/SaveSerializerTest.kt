package com.technomancer.pangu.logic.save

import com.technomancer.pangu.logic.flow.FlowState
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertInstanceOf
import org.junit.jupiter.api.Assertions.assertSame
import org.junit.jupiter.api.Test

class SaveSerializerTest {

    @Test
    fun roundTripPadPose() {
        val data = SaveData(
            flow = FlowState.Incubation,
            lastSeenUtcMs = 1_767_310_200_000L,
            padPose = PadPoseData(has = true, pos = listOf(0.1f, 0.95f, -0.72f), yaw = 0.35f),
        )
        val result = SaveSerializer.decode(SaveSerializer.encode(data))
        assertInstanceOf(LoadResult.Loaded::class.java, result)
        assertEquals(data, (result as LoadResult.Loaded).data)
    }

    @Test
    fun unknownVersionDiscarded() {
        val result = SaveSerializer.decode("""{ "version": 3, "flow": "Pet" }""")
        assertEquals(LoadResult.UnknownVersion(3), result)
        assertEquals(LoadResult.UnknownVersion(null), SaveSerializer.decode("""{ "flow": "Pet" }"""))
    }

    @Test
    fun missingFieldsUseDefaults() {
        val result = SaveSerializer.decode("""{ "version": 4 }""") as LoadResult.Loaded
        assertEquals(SaveData(), result.data)
    }

    @Test
    fun corruptAndEmpty() {
        assertInstanceOf(LoadResult.Corrupt::class.java, SaveSerializer.decode("{ \"version\": 4, "))
        assertInstanceOf(LoadResult.Corrupt::class.java, SaveSerializer.decode("[1, 2]"))
        assertSame(LoadResult.Empty, SaveSerializer.decode(null))
        assertSame(LoadResult.Empty, SaveSerializer.decode(""))
    }
}
