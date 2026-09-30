package com.technomancer.pangu.ecs.entities

import android.util.Log
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.resource.AssetBundle
import com.pico.spatial.core.ecs.resource.ShaderGraphMaterial

/**
 * Optional toon look from the Spatial Editor: if editor-asset.bundle contains a shader-graph
 * material named PanguToon (scene "Pangu"), the alien's body, stalk and tip use it instead of the
 * baked vertex-colour look. Without it the baked look stays and one line is logged.
 */
object ToonMaterial {
    private const val BUNDLE = "asset://editor-asset.bundle"
    private const val MATERIAL_PATH = "Pangu/Root/PanguToon"
    private val TOON_PARTS = listOf("Pet_Body", "Pet_Stalk", "Pet_Tip")

    fun tryApply(petRoot: Entity) {
        val material = try {
            val bundle = AssetBundle.load(BUNDLE)
            ShaderGraphMaterial.loadFromAssetBundle(bundle, MATERIAL_PATH)
        } catch (e: Exception) {
            Log.i("Pangu", "PanguToon material not found in the editor bundle; keeping the baked toon look")
            return
        }
        for (name in TOON_PARTS) {
            petRoot.findEntity(name)?.components?.get(ModelComponent::class.java)?.let { model ->
                if (model.materials.size > 0) model.materials.set(0, material)
            }
        }
        Log.i("Pangu", "Applied the PanguToon material to the alien")
    }
}
