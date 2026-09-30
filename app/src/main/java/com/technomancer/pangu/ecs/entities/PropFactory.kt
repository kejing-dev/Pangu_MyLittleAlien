package com.technomancer.pangu.ecs.entities

import com.pico.spatial.core.ecs.CollisionComponent
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.HoverEffectComponent
import com.pico.spatial.core.ecs.InteractableComponent
import com.pico.spatial.core.ecs.TransformComponent
import com.pico.spatial.core.ecs.resource.PhysicsMaterialResource
import com.pico.spatial.core.ecs.resource.ShapeResource
import com.pico.spatial.core.math.Vector3
import com.technomancer.pangu.ecs.components.TagComponent
import com.technomancer.pangu.ecs.toSdk
import com.technomancer.pangu.logic.art.MeshData
import com.technomancer.pangu.logic.art.PrimitiveMeshes
import com.technomancer.pangu.logic.input.TargetId
import com.technomancer.pangu.logic.pet.PetWorld

/** The snack tray and its five draggable snacks, all gray-box shapes. */
class PropFactory(private val meshes: MeshFactory) {

    private val parts: Map<String, MeshData> by lazy {
        mapOf(
            TRAY to PrimitiveMeshes.cylinder(0.085f, 0.008f, segments = 48),
            "PH_Food0" to PrimitiveMeshes.sphere(0.012f),
            "PH_Food1" to PrimitiveMeshes.capsule(0.009f, 0.026f),
            "PH_Food2" to PrimitiveMeshes.cone(0.011f, 0f, 0.024f),
            "PH_Food3" to PrimitiveMeshes.cylinder(0.009f, 0.026f, segments = 16),
            "PH_Food4" to PrimitiveMeshes.halfDome(0.013f, 0.006f),
        )
    }

    /** Uploads the prop meshes; call off the main thread. */
    fun preloadMeshes() {
        for ((name, data) in parts) meshes.mesh(data, name)
    }

    /** The tray (squashed into an oval) at its spot on the pad. Snacks are separate so they can move freely. */
    fun createTray(): Entity =
        meshes.model(TRAY, parts.getValue(TRAY), TRAY_COLOR, PetWorld.TRAY_CENTRE.toSdk()).apply {
            components[TransformComponent::class.java]?.setScaleVector(Vector3(1f, 1f, 0.6f))
        }

    fun createFood(index: Int): Entity {
        val name = "PH_Food$index"
        return meshes.model(name, parts.getValue(name), FOOD_COLORS[index]).apply {
            components.set(InteractableComponent())
            components.set(CollisionComponent(listOf(ShapeResource.createSphere(FOOD_GRAB_RADIUS)), PhysicsMaterialResource()))
            components.set(HoverEffectComponent())
            components.set(TagComponent(TargetId.food(index)))
        }
    }

    companion object {
        const val TRAY = "PH_FoodTray"
        private const val TRAY_COLOR = 0xECE4D7
        private const val FOOD_GRAB_RADIUS = 0.022f

        /** Fizzberry, Honey bun, Comet drop, Moon milk, Crisp leaf. */
        val FOOD_COLORS = intArrayOf(0xD9644F, 0xF2DE9B, 0xC9B7DA, 0xFFFFFF, 0xBFD3AE)
    }
}
