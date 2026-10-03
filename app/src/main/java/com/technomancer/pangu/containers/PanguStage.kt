package com.technomancer.pangu.containers

import android.Manifest
import android.util.Log
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import kotlinx.coroutines.delay
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.rememberCoroutineScope
import com.pico.spatial.ui.platform.containers.LocalSpatialNavigator
import kotlinx.coroutines.launch
import com.technomancer.pangu.audio.MicService
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
import com.pico.spatial.core.ecs.GroundShadowComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.anchor.AnchorTarget
import com.pico.spatial.core.math.Vector3
import com.pico.spatial.ui.foundation.content.SpatialView
import com.pico.spatial.ui.foundation.dsl.registerSystem
import com.pico.spatial.ui.foundation.dsl.unregisterSystem
import com.pico.spatial.ui.foundation.gesture.TargetEntity
import com.pico.spatial.ui.foundation.gesture.detectSpatialDragGesture
import com.pico.spatial.ui.foundation.gesture.data.InteractionKind
import com.pico.spatial.ui.foundation.gesture.detectSpatialTapGesture
import com.pico.spatial.ui.geometry.Offset3D
import com.pico.spatial.ui.platform.LengthUnit
import com.pico.spatial.ui.platform.LocalHandControllerHaptic
import com.pico.spatial.ui.platform.LocalPhysicalLengthConverter
import com.pico.spatial.ui.platform.PhysicalLengthConverter
import com.technomancer.pangu.app.Haptics
import com.technomancer.pangu.app.PerfProbe
import com.technomancer.pangu.app.SceneHandles
import com.technomancer.pangu.app.ServiceRegistry
import com.technomancer.pangu.app.SessionController
import com.technomancer.pangu.ecs.components.TagComponent
import com.technomancer.pangu.audio.SfxPlayer
import com.technomancer.pangu.ecs.entities.AlienFactory
import com.technomancer.pangu.ecs.entities.EggFactory
import com.technomancer.pangu.ecs.systems.EggPresenter
import com.technomancer.pangu.logic.art.AlienParts
import com.technomancer.pangu.logic.config.PetConfig
import com.technomancer.pangu.ui.PanelActions
import com.technomancer.pangu.ui.PanelState
import com.technomancer.pangu.ui.PanguType
import androidx.compose.runtime.key
import com.technomancer.pangu.ui.eggPieItems
import com.technomancer.pangu.ui.petPieItems
import com.technomancer.pangu.ecs.entities.HabitatFactory
import com.technomancer.pangu.ecs.entities.PropFactory
import com.technomancer.pangu.ecs.entities.TipMorpher
import com.technomancer.pangu.ecs.entities.ToonMaterial
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
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
    val petConfig = remember { ServiceRegistry.resolve<PetConfig>() }
    val events = remember { ServiceRegistry.resolve<InteractionQueue>() }
    val scene = remember { ServiceRegistry.resolve<SceneHandles>() }
    val haptics = remember { ServiceRegistry.resolve<Haptics>() }
    val sfx = remember { ServiceRegistry.resolve<SfxPlayer>() }
    val input = remember { InputSystem(ServiceRegistry.resolve<InputConfig>()) }
    val mic = remember { ServiceRegistry.resolve<MicService>() }
    val perf = remember { ServiceRegistry.resolve<PerfProbe>() }

    val context = LocalContext.current
    val density = LocalDensity.current
    val converter = LocalPhysicalLengthConverter.current
    val hapticDevice = LocalHandControllerHaptic.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val scale = UiScale.remember(uiConfig)
    val drag = remember { DragState() }
    val navigator = LocalSpatialNavigator.current
    val quitScope = rememberCoroutineScope()
    val panelActions = remember {
        PanelActions(
            onPrimary = { events.push(InteractionType.UiPrimary) },
            onSecondary = { events.push(InteractionType.UiSecondary) },
            onRecenter = { events.push(InteractionType.UiRecenter) },
            onLook = { events.push(InteractionType.UiLook, it) },
            onWheel = { events.push(InteractionType.UiWheel, it) },
            onInfo = { events.push(InteractionType.UiInfo) },
            onAssist = { events.push(InteractionType.UiAssist) },
            onSettings = { events.push(InteractionType.UiSettings) },
            onVolume = { events.push(InteractionType.UiVolume, it) },
            onSound = { events.push(InteractionType.UiSound, if (it) 1 else 0) },
            onHaptics = { events.push(InteractionType.UiHaptics, if (it) 1 else 0) },
            onQuit = {
                // Save first, then close the Stage; the Activity goes too so nothing is left running.
                session.persist()
                quitScope.launch {
                    runCatching { navigator.closeStage() }.onFailure { Log.w(TAG, "closeStage failed", it) }
                    context.findActivity()?.finishAndRemoveTask()
                }
            },
        )
    }

    // "Allow microphone" on C11 asks Android for the permission. The launch Activity is the
    // template's and has no result callback, so the answer is read back once the system dialog closes.
    val micRequest by ui.micRequest.collectAsState()
    LaunchedEffect(micRequest) {
        if (micRequest == 0) return@LaunchedEffect
        if (mic.hasPermission()) {
            events.push(InteractionType.UiMic, 1)
            return@LaunchedEffect
        }
        val activity = context.findActivity()
        if (activity == null) {
            events.push(InteractionType.UiMic, 0)
            return@LaunchedEffect
        }
        ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.RECORD_AUDIO), MIC_REQUEST_CODE)
        val lifecycle = lifecycleOwner.lifecycle
        var dialogShown = false
        val granted = withTimeoutOrNull(MIC_ANSWER_WAIT_MS) {
            while (!mic.hasPermission()) {
                val resumed = lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)
                if (!resumed) dialogShown = true
                // The dialog paused the app and it is back: the player said no.
                if (dialogShown && resumed) return@withTimeoutOrNull false
                delay(MIC_POLL_MS)
            }
            true
        } ?: false
        events.push(InteractionType.UiMic, if (granted) 1 else 0)
    }

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
            mic.stop()
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
                        Log.i(TAG, "Drag start on target ${drag.target} by ${value.interactionKind}")
                    },
                    onDragEnd = { events.push(InteractionType.DragEnd, drag.target, timeNanos = System.nanoTime()) },
                    onDragCancel = { events.push(InteractionType.DragEnd, drag.target, timeNanos = System.nanoTime()) },
                ) { value ->
                    val root = drag.root ?: return@detectSpatialDragGesture
                    // A hand or controller holding the object moves it directly; a ray, gaze or mouse pointer slides it on a plane facing the player.
                    val viaRay = value.interactionKind != InteractionKind.DirectPinch && value.interactionKind != InteractionKind.Poke
                    events.push(InteractionType.DragMove, drag.target, stageDelta(value.dragAmount, root, density, converter), System.nanoTime(), viaRay)
                }
            },
        attachments = {
            AttachmentPanel(id = PANEL_INFO) {
                val language by ui.language.collectAsState()
                PanguType(language) {
                key(language) {
                val screen by ui.screen.collectAsState()
                val pet by ui.pet.collectAsState()
                val studio by ui.studio.collectAsState()
                val egg by ui.egg.collectAsState()
                val counters by ui.counters.collectAsState()
                val micLevel by ui.micLevel.collectAsState()
                val assist by ui.assist.collectAsState()
                val settings by ui.settings.collectAsState()
                InfoPanel(
                    content = ScreenContent.of(screen),
                    state = PanelState(pet, studio, egg, counters, micLevel, assist, settings),
                    loc = loc,
                    scale = scale,
                    actions = panelActions,
                )
                }
                }
            }
            AttachmentPanel(id = PANEL_HANDLE) {
                val language by ui.language.collectAsState()
                PanguType(language) {
                key(language) {
                val screen by ui.screen.collectAsState()
                HandleLabel(screen == ScreenId.C02 || screen == ScreenId.C37, loc, scale)
                }
                }
            }
            AttachmentPanel(id = PANEL_PIE) {
                val language by ui.language.collectAsState()
                PanguType(language) {
                key(language) {
                val screen by ui.screen.collectAsState()
                val pet by ui.pet.collectAsState()
                val egg by ui.egg.collectAsState()
                PieMenu(
                    visible = screen == ScreenId.C24 || screen == ScreenId.C08,
                    items = if (screen == ScreenId.C08) eggPieItems(egg?.suggested) else petPieItems(pet?.suggested.orEmpty()),
                    loc = loc,
                    scale = scale,
                    ringRadiusPx = uiConfig.pieRadius / uiConfig.metresPerMockupPx,
                ) { events.push(InteractionType.UiPie, it) }
                }
                }
            }
            AttachmentPanel(id = PANEL_TOAST) {
                val language by ui.language.collectAsState()
                PanguType(language) {
                key(language) {
                val toast by ui.toast.collectAsState()
                Toast(toast, loc, scale, uiConfig.toastSec)
                }
                }
            }
            AttachmentPanel(id = PANEL_PAUSE) {
                val language by ui.language.collectAsState()
                PanguType(language) {
                key(language) {
                val paused by ui.paused.collectAsState()
                PauseDim(paused, loc, scale)
                }
                }
            }
        },
    ) { content, attachments ->
        // Stage-space poses are converted through this root, so it goes into the scene first.
        perf.milestone("scene setup started")
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
        val meshes = MeshFactory(perf)
        val habitatFactory = HabitatFactory(meshes)
        val alienFactory = AlienFactory(meshes)
        val propFactory = PropFactory(meshes)
        val eggFactory = EggFactory(meshes)
        val shellColor = logic.catalog.bodyColor(logic.look)
        perf.milestone("head pose ready")
        // The four sets upload side by side: each upload waits for an engine frame, so this costs
        // about as long as the slowest set rather than the sum of them.
        val alienLoaded = withContext(Dispatchers.Default) {
            val alien = async {
                runCatching { alienFactory.preload(context.assets) }
                    .onFailure { Log.e(TAG, "Could not load the alien model", it) }
                    .isSuccess
            }
            listOf(
                async { habitatFactory.preloadMeshes() },
                async { propFactory.preloadMeshes() },
                async { eggFactory.preloadMeshes(shellColor) },
            ).awaitAll()
            alien.await()
        }
        perf.milestone("meshes uploaded")
        val habitat = habitatFactory.create()
        root.addChild(habitat)
        val infoPanel = attachments.entity(PANEL_INFO)?.also { habitat.addChild(it) }
        val handleLabel = attachments.entity(PANEL_HANDLE)?.also { habitat.addChild(it) }
        val pie = attachments.entity(PANEL_PIE)?.also { habitat.addChild(it) }
        val rig = if (alienLoaded) alienFactory.create().also { habitat.addChild(it.root) } else null
        val tray = propFactory.createTray().also { habitat.addChild(it) }
        val foods = List(PetWorld.FOOD_COUNT) { i -> propFactory.createFood(i).also { habitat.addChild(it) } }
        val (tub, bubbles) = propFactory.createTub().also { habitat.addChild(it.first) }
        val ball = propFactory.createBall().also { habitat.addChild(it) }
        val crumbs = List(MAX_CRUMBS) { i -> propFactory.createCrumb(i).also { habitat.addChild(it) } }
        val beam = propFactory.createBeam().also { habitat.addChild(it) }
        val skyRing = propFactory.createSkyRing().also { habitat.addChild(it) }
        val eggRig = eggFactory.create(shellColor, alienFactory.tipShapes).also { habitat.addChild(it.root) }
        val nest = eggFactory.createNestProps(placement.habitatScale).also { props ->
            habitat.addChild(props.blanket)
            habitat.addChild(props.drape)
            props.beads.forEach { habitat.addChild(it) }
            habitat.addChild(props.pedestal)
        }
        val eggPresenter = EggPresenter(eggRig, nest, eggFactory, meshes, placement.habitatScale, logic.egg.config.eggScale)

        // Soft contact shadows: the pad receives, the pet casts.
        habitat.findEntity("PH_PadTop")?.components?.set(GroundShadowComponent(false, true))
        rig?.root?.components?.set(GroundShadowComponent(true, false))
        meshes.toon = ToonMaterial.load()
        sfx.petEmitter = rig?.root
        sfx.uiEmitter = infoPanel ?: habitat
        sfx.eggEmitter = eggRig.root

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
            SceneEntities(infoPanel, handleLabel, pie, rig, tray, foods, tub, bubbles, ball, crumbs, beam, skyRing, nest.pedestal, eggPresenter),
            uiConfig,
            placement.habitatScale,
            meshes,
            petConfig.adultHeight * petConfig.hatchGrowth / AlienParts.MODEL_HEIGHT / placement.habitatScale,
            rig?.let { TipMorpher(it, alienFactory.tipMorphSources(), meshes) },
        )
        perf.root = root
        perf.coldStartDone()
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
private const val MAX_CRUMBS = 5
private const val PANEL_TOAST = "panel_toast"
private const val PANEL_PAUSE = "panel_pause"
private const val HEAD_WAIT_MS = 2000L
private const val MIC_REQUEST_CODE = 7
private const val MIC_ANSWER_WAIT_MS = 60_000L
private const val MIC_POLL_MS = 300L

/** The Activity behind a Compose context, if any. */
private fun Context.findActivity(): Activity? {
    var c: Context? = this
    while (c is ContextWrapper) {
        if (c is Activity) return c
        c = c.baseContext
    }
    return null
}

/** Poses below this height are treated as "tracking not ready yet" (the Stage origin is at the feet). */
private const val MIN_HEAD_HEIGHT = 0.2f
