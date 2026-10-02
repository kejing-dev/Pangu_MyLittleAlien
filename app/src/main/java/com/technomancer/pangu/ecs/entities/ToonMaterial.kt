package com.technomancer.pangu.ecs.entities

import android.util.Log
import com.pico.spatial.core.ecs.Entity
import com.pico.spatial.core.ecs.ModelComponent
import com.pico.spatial.core.ecs.resource.AssetBundle
import com.pico.spatial.core.ecs.resource.MaterialCullingMode
import com.pico.spatial.core.ecs.resource.ShaderGraphMaterial
import com.pico.spatial.core.math.Color3
import com.pico.spatial.core.math.Vector3

/**
 * Optional toon look from the Spatial Editor: if editor-asset.bundle contains a shader-graph
 * material named PanguToon (scene "Pangu"), the alien's body, stalk and tips use it instead of the
 * baked vertex-colour look. Body and tips each get their own copy so they can take different
 * colours. Without the material the baked look stays and one line is logged.
 *
 * The material's parameters: toonBaseColor, toonShadeColor (Color3), toonGloss, toonRim, toonSaturation (Float)
 * and toonLightDir (Vector3, object space). The editor reserves plain names such as baseColor.
 */
class ToonMaterial private constructor(private val body: ShaderGraphMaterial, private val tip: ShaderGraphMaterial) {
    private var shownBody = -1
    private var shownTip = -1

    /** Puts the toon material on the rig with [bodyColor] on the body and stalk and [tipColor] on the tips. */
    fun applyLook(rig: AlienRig, bodyColor: Int, tipColor: Int) {
        if (bodyColor != shownBody) {
            body.setParameter("toonBaseColor", color3(bodyColor))
            shownBody = bodyColor
        }
        if (tipColor != shownTip) {
            tip.setParameter("toonBaseColor", color3(tipColor))
            shownTip = tipColor
        }
        rig.bodyModels.forEach { use(it, body) }
        rig.tipModels.forEach { use(it, tip) }
    }

    private fun use(entity: Entity, material: ShaderGraphMaterial) {
        val model = entity.components[ModelComponent::class.java] ?: return
        if (model.materials.size > 0 && model.materials[0] !== material) model.materials.set(0, material)
    }

    companion object {
        private const val BUNDLE = "asset://editor-asset.bundle"
        private const val MATERIAL_PATH = "Pangu/Root/PanguToon"

        // Part settings from the toon spec: body and stalk, then tips.
        private val BODY_SHADE = Color3(0.8f, 0.76f, 0.86f)
        private val TIP_SHADE = Color3(0.86f, 0.82f, 0.9f)
        private const val BODY_GLOSS = 0.10f
        private const val TIP_GLOSS = 0.25f
        private const val RIM = 0.25f
        private val LIGHT = Vector3(-0.55f, 0.75f, 0.9f)

        /** Loads two copies of PanguToon and sets their fixed parameters, or returns null if it isn't there. */
        fun load(): ToonMaterial? = try {
            val bundle = AssetBundle.load(BUNDLE)
            val body = ShaderGraphMaterial.loadFromAssetBundle(bundle, MATERIAL_PATH).also { setup(it, BODY_SHADE, BODY_GLOSS) }
            // Tips draw both sides, so a crease in a morphing tip shows its back instead of a hole.
            val tip = ShaderGraphMaterial.loadFromAssetBundle(bundle, MATERIAL_PATH).also {
                setup(it, TIP_SHADE, TIP_GLOSS)
                it.setCullingMode(MaterialCullingMode.NONE)
            }
            Log.i("Pangu", "Using the PanguToon material for the alien")
            ToonMaterial(body, tip)
        } catch (e: Exception) {
            Log.i("Pangu", "PanguToon material not found or not usable (${e.message}); keeping the baked toon look")
            null
        }

        private fun setup(material: ShaderGraphMaterial, shade: Color3, gloss: Float) {
            material.setParameter("toonShadeColor", shade)
            material.setParameter("toonGloss", gloss)
            material.setParameter("toonRim", RIM)
            material.setParameter("toonSaturation", 1f)
            material.setParameter("toonLightDir", LIGHT)
        }

        private fun color3(color: Int) = Color3(((color shr 16) and 0xFF) / 255f, ((color shr 8) and 0xFF) / 255f, (color and 0xFF) / 255f)
    }
}
