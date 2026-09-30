package com.technomancer.pangu.app

import com.technomancer.pangu.ecs.systems.InputSystem
import com.technomancer.pangu.ecs.systems.PresentationSystem

/**
 * The per-Stage objects the frame System drives. The Stage composable fills these when its
 * scene is built and clears them when it goes away; until then the frame only advances the logic.
 */
class SceneHandles {
    var input: InputSystem? = null
    var presentation: PresentationSystem? = null

    fun clear() {
        input = null
        presentation = null
    }
}
