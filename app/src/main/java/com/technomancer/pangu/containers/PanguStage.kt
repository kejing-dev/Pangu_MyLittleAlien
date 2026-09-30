package com.technomancer.pangu.containers

import android.util.Log
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Density
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.pico.spatial.core.ecs.AnchorEntity
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.anchor.AnchorTarget
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.dsl.registerSystem
import com.pico.spatial.ui.foundation.dsl.unregisterSystem
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialDragGesture
import com.pico.spatial.ui.foundation.gesture.detectSpatialTapGesture
import com.pico.spatial.ui.geometry.Offset3D
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.LocalHandControllerHaptic
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter
import com.pico.spatial.ui.platform.PhysicalLengthConverter
import com.technomancer.pangu.app.DebugFlags
import com.technomancer.pangu.app.Haptics
import com.technomancer.pangu.app.SceneHandles
import com.technomancer.pangu.app.ServiceRegistry
import com.technomancer.pangu.app.SessionController
import com.technomancer.pangu.ecs.components.TagComponent
import com.technomancer.pangu.audio.SfxPlayer
import com.technomancer.pangu.ecs.entities.AlienFactory
import com.technomancer.pangu.ecs.entities.HabitatFactory
import com.technomancer.pangu.ecs.entities.PropFactory
import com.technomancer.pangu.ecs.systems.SceneEntities
import com.technomancer.pangu.ecs.entities.MeshFactory
import com.technomancer.pangu.ecs.systems.InputSystem
import com.technomancer.pangu.ecs.systems.PanguFrameSystem
import com.technomancer.pangu.ecs.systems.PresentationSystem
import com.technomancer.pangu.ecs.toLogic
import com.technomancer.pangu.logic.PanguLogic
import com.technomancer.pangu.logic.config.InputConfig
import com.technomancer.pangu.logic.config.UiConfig
import com.technomancer.pangu.logic.flow.ScreenContent
import com.technomancer.pangu.logic.flow.ScreenId
import com.technomancer.pangu.logic.habitat.PadPlacement
import com.technomancer.pangu.logic.input.InteractionQueue
import com.technomancer.pangu.logic.input.InteractionType
import com.technomancer.pangu.logic.pet.PetWorld
import com.technomancer.pangu.logic.input.TargetId
import com.technomancer.pangu.logic.loc.LocService
import com.technomancer.pangu.logic.math.Vec3
import com.technomancer.pangu.ui.HandleLabel
import com.technomancer.pangu.ui.InfoPanel
import com.technomancer.pangu.ui.PieMenu
import com.technomancer.pangu.ui.PauseDim
import com.technomancer.pangu.ui.Toast
import com.technomancer.pangu.ui.UiBridge
import com.technomancer.pangu.ui.UiScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/**
 * Content of the default Stage. Builds the entity graph once, places the habitat, hosts the
 * panels, turns spatial gestures into logic events, and ties the frame System, tracking and
 * pause/resume to this composable's lifetime.
 */
@Composable
fun PanguStage() {
    val session = remember { ServiceRegistry.resolve<SessionController>() }
    val placement = remember { ServiceRegistry.resolve<PadPlacement>() }
    val logic = remember { ServiceRegistry.resolve<PanguLogic>() }
    val loc = remember { ServiceRegistry.resolve<LocService>() }
    val ui = remember { ServiceRegistry.resolve<UiBridge>() }
    val uiConfig = remember { ServiceRegistry.resolve<UiConfig>() }
    val events = remember { ServiceRegistry.resolve<InteractionQueue>() }
    val scene = remember { ServiceRegistry.resolve<SceneHandles>() }
    val haptics = remember { ServiceRegistry.resolve<Haptics>() }
    val sfx = remember { ServiceRegistry.resolve<SfxPlayer>() }
    val showDebug = remember { ServiceRegistry.resolve<DebugFlags>().debuggable }
    val input = remember { InputSystem(ServiceRegistry.resolve<InputConfig>()) }

    val context = LocalContext.current
    val density = LocalDensity.current
    val converter = LocalPhysicalLengthConverter.current
    val hapticDevice = LocalHandControllerHaptic.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scale = UiScale.remember(uiConfig)
    val drag = remember { DragState() }

    DisposableEffect(lifecycleOwner) {
        haptics.device = hapticDevice
        input.start()
        scene.input = input
        registerSystem<PanguFrameSystem>()
        lifecycleOwner.lifecycle.addObserver(session)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(session)
            unregisterSystem<PanguFrameSystem>()
            scene.clear()
            sfx.clear()
            input.stop()
            haptics.device = null
        }
    }

    val interactive = TargetEntity.any { it.targetId() != TargetId.NONE }

    SpatialView(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(Unit) {
                detectSpatialTapGesture(context, interactive) { tap ->
                    events.push(InteractionType.Tap, tap.targetEntity.targetId())
                }
            }
            .pointerInput(Unit) {
                detectSpatialDragGesture(
                    context,
                    interactive,
                    onDragStart = { _, value ->
                        drag.target = value.targetEntity.targetId()
                        events.push(InteractionType.DragStart, drag.target)
                    },
                    onDragEnd = { events.push(InteractionType.DragEnd, drag.target) },
                    onDragCancel = { events.push(InteractionType.DragEnd, drag.target) },
                ) { value ->
                    val root = drag.root ?: return@detectSpatialDragGesture
                    events.push(InteractionType.DragMove, drag.target, stageDelta(value.dragAmount, root, density, converter))
                }
            },
        attachments = {
            AttachmentPanel(id = PANEL_INFO) {
                val screen by ui.screen.collectAsState()
                val pet by ui.pet.collectAsState()
                InfoPanel(
                    content = ScreenContent.of(screen),
                    pet = pet,
                    loc = loc,
                    scale = scale,
                    showDebug = showDebug,
                    onPrimary = { events.push(InteractionType.UiPrimary) },
                    onRecenter = { events.push(InteractionType.UiRecenter) },
                )
            }
            AttachmentPanel(id = PANEL_HANDLE) {
                val screen by ui.screen.collectAsState()
                HandleLabel(screen == ScreenId.C02 || screen == ScreenId.C37, loc, scale)
            }
            AttachmentPanel(id = PANEL_PIE) {
                val screen by ui.screen.collectAsState()
                val pet by ui.pet.collectAsState()
                PieMenu(
                    visible = screen == ScreenId.C24,
                    suggested = pet?.suggested.orEmpty(),
                    loc = loc,
                    scale = scale,
                    ringRadiusPx = uiConfig.pieRadius / uiConfig.metresPerMockupPx,
                ) { events.push(InteractionType.UiPie, it.ordinal) }
            }
            AttachmentPanel(id = PANEL_TOAST) {
                val toast by ui.toast.collectAsState()
                Toast(toast, loc, scale, uiConfig.toastSec)
            }
            AttachmentPanel(id = PANEL_PAUSE) {
                val paused by ui.paused.collectAsState()
                PauseDim(paused, loc, scale)
            }
        },
    ) { content, attachments ->
        // Stage-space poses are converted through this root, so it goes into the scene first.
        val root = Entity().apply { setName(ROOT) }
        content.addEntity(root)
        drag.root = root

        val head = withTimeoutOrNull(HEAD_WAIT_MS) {
            input.hmd.dataFlow.first { it.hmdPose.position.y > MIN_HEAD_HEIGHT }
        }?.hmdPose
        val saved = session.savedPadPose()
        when {
            head == null -> {
                Log.w(TAG, "No head pose within $HEAD_WAIT_MS ms, using the fallback pad pose")
                if (saved != null) logic.placePad(saved) else logic.placePad(placement.fallbackPose())
            }
            saved != null && placement.shouldRestore(saved, head.position.toLogic()) -> logic.placePad(saved)
            saved != null -> logic.replacePad(placement.defaultPose(head.position.toLogic(), head.rotation.toLogic()))
            else -> logic.placePad(placement.defaultPose(head.position.toLogic(), head.rotation.toLogic()))
        }

        // Parsing and mesh uploads can wait on the engine, so they run off the main thread;
        // entities must be made on it.
        val meshes = MeshFactory()
        val habitatFactory = HabitatFactory(meshes)
        val alienFactory = AlienFactory(meshes, AlienFactory.DEFAULT_LOOK)
        val propFactory = PropFactory(meshes)
        val alienLoaded = withContext(Dispatchers.Default) {
            habitatFactory.preloadMeshes()
            propFactory.preloadMeshes()
            runCatching { alienFactory.preload(context.assets) }
                .onFailure { Log.e(TAG, "Could not load the alien model", it) }
                .isSuccess
        }
        val habitat = habitatFactory.create()
        root.addChild(habitat)
        val infoPanel = attachments.entity(PANEL_INFO)?.also { habitat.addChild(it) }
        val handleLabel = attachments.entity(PANEL_HANDLE)?.also { habitat.addChild(it) }
        val pie = attachments.entity(PANEL_PIE)?.also { habitat.addChild(it) }
        val rig = if (alienLoaded) alienFactory.create().also { habitat.addChild(it.root) } else null
        val tray = propFactory.createTray().also { habitat.addChild(it) }
        val foods = List(PetWorld.FOOD_COUNT) { i -> propFactory.createFood(i).also { habitat.addChild(it) } }
        sfx.petEmitter = rig?.root
        sfx.uiEmitter = infoPanel ?: habitat

        // Toasts and the pause veil follow the head.
        val camera = AnchorEntity(AnchorTarget.createCameraTarget())
        content.addEntity(camera)
        attachments.entity(PANEL_TOAST)?.let {
            it.components[TransformComponent::class.java]?.setPosition(Vector3(0f, 0.12f, -1.0f))
            camera.addChild(it)
        }
        attachments.entity(PANEL_PAUSE)?.let {
            it.components[TransformComponent::class.java]?.setPosition(Vector3(0f, 0f, -1.0f))
            camera.addChild(it)
        }

        scene.presentation = PresentationSystem(
            root,
            habitat,
            SceneEntities(infoPanel, handleLabel, pie, rig, tray, foods),
            uiConfig,
            placement.habitatScale,
        )
        Log.i(TAG, "Habitat placed at ${logic.padPose?.position} screen=${logic.screens.current} (head ${head?.position})")
    }
}

/** Which entity a drag started on, and the root used to convert drag movement into Stage space. */
private class DragState {
    var target: Int = TargetId.NONE
    var root: Entity? = null
}

/** Walks up from a hit entity to the nearest ancestor carrying a [TagComponent]. */
private fun Entity?.targetId(): Int {
    var e = this
    while (e != null) {
        e.components[TagComponent::class.java]?.let { return it.targetId }
        e = if (e.hasParent()) e.getParent() else null
    }
    return TargetId.NONE
}

/**
 * Drag movement arrives in view pixels (x right, y down, z toward the viewer). Converts it to
 * metres in the SpatialView's space, then to a Stage-space direction through [root].
 */
private fun stageDelta(amount: Offset3D, root: Entity, density: Density, converter: PhysicalLengthConverter): Vec3 {
    fun metres(px: Float) = with(density) { converter.dpToLength(px.toDp(), LengthUnit.Meters) }
    val local = Vector3(metres(amount.x), -metres(amount.y), metres(amount.z))
    val a = root.convertPositionTo(Vector3.ZERO, null)
    val b = root.convertPositionTo(local, null)
    return Vec3(b.x - a.x, b.y - a.y, b.z - a.z)
}

private const val TAG = "Pangu"
private const val ROOT = "PanguRoot"
private const val PANEL_INFO = "panel_info"
private const val PANEL_HANDLE = "panel_handle"
private const val PANEL_PIE = "panel_pie"
private const val PANEL_TOAST = "panel_toast"
private const val PANEL_PAUSE = "panel_pause"
private const val HEAD_WAIT_MS = 2000L

/** Poses below this height are treated as "tracking not ready yet" (the Stage origin is at the feet). */
private const val MIN_HEAD_HEIGHT = 0.2f
