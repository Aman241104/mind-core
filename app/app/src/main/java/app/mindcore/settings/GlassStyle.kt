package app.mindcore.settings

/**
 * Everything about how the liquid glass looks. Maps 1:1 to Kyant0/backdrop effects:
 * vibrancy(), blur(radius), lens(height, amount, depthEffect, chromaticAberration), highlight, surface tint.
 * Colors are ARGB; null means "follow the theme".
 */
data class GlassStyle(
    val preset: GlassPreset = GlassPreset.Liquid,
    val vibrancy: Boolean = true,
    val blur: Float = 8f, // dp
    val lensHeight: Float = 24f, // dp: how far in from the edge the bending starts
    val lensAmount: Float = 24f, // dp: how strongly the edge bends what's behind
    val chromaticAberration: Boolean = true, // rainbow fringe on the sliding tab puck
    val depthEffect: Boolean = false,
    val tint: Long? = null,
    val tintOpacity: Float = 0.4f,
    val pillColor: Long? = null,
    val pillOpacity: Float = 0.1f,
    val highlightColor: Long? = null,
    val highlightOpacity: Float = 0.5f,
    val adaptiveContrast: Boolean = true,
    val glassTabBar: Boolean = true,
    val glassCaptureButton: Boolean = true,
) {
    fun withPreset(p: GlassPreset): GlassStyle = when (p) {
        GlassPreset.Liquid -> GlassStyle().copy(
            tint = tint, pillColor = pillColor, highlightColor = highlightColor,
            glassTabBar = glassTabBar, glassCaptureButton = glassCaptureButton, adaptiveContrast = adaptiveContrast,
        )
        GlassPreset.Frosted -> copy(preset = p, vibrancy = true, blur = 18f, lensHeight = 8f, lensAmount = 6f,
            chromaticAberration = false, depthEffect = false, tintOpacity = 0.55f, highlightOpacity = 0.35f)
        GlassPreset.Clear -> copy(preset = p, vibrancy = false, blur = 1.5f, lensHeight = 30f, lensAmount = 44f,
            chromaticAberration = true, depthEffect = true, tintOpacity = 0.12f, highlightOpacity = 0.8f)
        GlassPreset.Custom -> copy(preset = p)
    }
}

enum class GlassPreset(val label: String) { Liquid("Liquid"), Frosted("Frosted"), Clear("Clear"), Custom("Custom") }
