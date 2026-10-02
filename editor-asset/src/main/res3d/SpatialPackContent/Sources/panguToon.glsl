// PanguToon: banded toon shading for the alien's body, antenna stalk and tips.
// Use with a Custom Function node in the Spatial Editor shader graph (functionName: panguToon).
// Node inputs, in this order: toonNormal, toonViewDir, toonBaseColor, toonShadeColor, toonGloss, toonRim, toonSaturation, toonLightDir.
// Node output: toonColor. Feed toonNormal and toonViewDir from Normal and View Direction nodes, both in object space.

void panguToon(vec3 toonNormal, vec3 toonViewDir, vec3 toonBaseColor, vec3 toonShadeColor, float toonGloss, float toonRim,
                float toonSaturation, vec3 toonLightDir, out vec3 toonColor)
{
    vec3 n = normalize(toonNormal);
    // A zero light direction (an unset input) falls back to the default upper-left-front light.
    vec3 l = dot(toonLightDir, toonLightDir) > 1e-8 ? normalize(toonLightDir) : normalize(vec3(-0.55, 0.75, 0.9));
    vec3 v = normalize(toonViewDir);
    vec3 h = normalize(l + v);
    float ndl = dot(n, l);

    // Three bands: lit, shade, and a deeper shade on the far side.
    float lit = smoothstep(-0.05, 0.08, ndl);
    float deep = smoothstep(-0.55, -0.35, ndl);
    vec3 shade = toonBaseColor * toonShadeColor;
    vec3 c = mix(mix(shade * 0.86, shade, deep), toonBaseColor, lit);

    // Small hard highlight and a toonRim light on the lit side.
    c += vec3(smoothstep(0.968, 0.985, dot(n, h)) * toonGloss);
    c += vec3(smoothstep(0.62, 0.9, 1.0 - max(dot(n, v), 0.0)) * toonRim * lit);

    // Saturation 1 keeps the colour; lower values fade it towards grey.
    float grey = dot(c, vec3(0.299, 0.587, 0.114));
    toonColor = clamp(mix(vec3(grey), c, toonSaturation), 0.0, 1.0);
}
