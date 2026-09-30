package com.technomancer.pangu.containers

import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.tracking.hmd.HMDTrackingProvider
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.dsl.registerSystem
import com.pico.spatial.ui.foundation.dsl.unregisterSystem
import com.technomancer.pangu.app.ServiceRegistry
import com.technomancer.pangu.app.SessionController
import com.technomancer.pangu.ecs.entities.HabitatFactory
import com.technomancer.pangu.ecs.entities.MeshFactory
import com.technomancer.pangu.ecs.systems.PanguFrameSystem
import com.technomancer.pangu.ecs.toLogic
import com.technomancer.pangu.ecs.toSdk
import com.technomancer.pangu.logic.habitat.PadPlacement
import com.technomancer.pangu.logic.habitat.PadPose
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Content of the default Stage. Builds the entity graph once, places the habitat in front of
 * the player, and ties the frame System, head tracking and pause/resume to this composable.
 */
@Composable
fun PanguStage() {
    val session = remember { ServiceRegistry.resolve<SessionController>() }
    val placement = remember { ServiceRegistry.resolve<PadPlacement>() }
    val hmd = remember { HMDTrackingProvider() }
    val lifecycleOwner = LocalLifecycleOwner.current

    DisposableEffect(lifecycleOwner) {
        hmd.start()
        registerSystem<PanguFrameSystem>()
        lifecycleOwner.lifecycle.addObserver(session)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(session)
            unregisterSystem<PanguFrameSystem>()
            hmd.stop()
        }
    }

    SpatialView(modifier = Modifier.fillMaxSize()) { content, _ ->
        // Stage-space poses are converted through this root, so it goes into the scene first.
        val root = Entity().apply { setName(ROOT) }
        content.addEntity(root)

        val head = withTimeoutOrNull(HEAD_WAIT_MS) {
            hmd.dataFlow.first { it.hmdPose.position.y > MIN_HEAD_HEIGHT }
        }?.hmdPose
        val pad: PadPose = when {
            head == null -> {
                Log.w(TAG, "No head pose within ${HEAD_WAIT_MS} ms, using the fallback pad pose")
                placement.fallbackPose()
            }
            else -> {
                val headPosition = head.position.toLogic()
                session.savedPadPose()?.takeIf { placement.shouldRestore(it, headPosition) }
                    ?: placement.defaultPose(headPosition, head.rotation.toLogic())
            }
        }
        session.setPadPose(pad, confirmed = session.savedPadPose() != null)

        val habitat = HabitatFactory(MeshFactory()).create()
        habitat.components[TransformComponent::class.java]?.apply {
            setPosition(root.convertPositionFrom(pad.position.toSdk(), null))
            setQuaternion(root.convertRotationFrom(pad.rotation.toSdk(), null))
        }
        root.addChild(habitat)
        Log.i(TAG, "Habitat placed at ${pad.position} yaw=${pad.yaw} (head ${head?.position})")
    }
}

private const val TAG = "Pangu"
private const val ROOT = "PanguRoot"
private const val HEAD_WAIT_MS = 2000L

/** Poses below this height are treated as "tracking not ready yet" (the Stage origin is at the feet). */
private const val MIN_HEAD_HEIGHT = 0.2f
