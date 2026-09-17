package com.k2767.course.ui.system.glass

import androidx.compose.runtime.Immutable
import com.k2767.course.ui.system.GlassAccessibilityMode
import com.k2767.course.ui.system.GlassMaterialRole
import com.k2767.course.ui.system.GlassMaterialSpec
import com.k2767.course.ui.system.GlassMaterials
import com.k2767.course.ui.system.GlassRecipe

/** Geometry and paint are resolved once for both AGSL and the API 31/32 lens. */
@Immutable
data class GlassChipAppearance(
    val refractionHeightDp: Float? = null,
    val refractionAmountDp: Float? = null,
    val refractionFloor: Float = 0.62f,
    val lightSurfaceAlpha: Float = GlassRecipe.ChipSurfaceAlphaLight,
    val darkSurfaceAlpha: Float = 0.84f,
    val lightPressedSurfaceScale: Float = GlassRecipe.ChipPressedSurfaceScale,
    val darkPressedSurfaceScale: Float = 1f,
    /** A blurred large panel can use fewer optical pixels; contours and content stay native. */
    val maxOffscreenPixels: Int = Int.MAX_VALUE
) {
    fun material(accessibility: GlassAccessibilityMode, interactionProgress: Float = 0f): GlassMaterialSpec {
        val base = GlassMaterials.resolve(GlassMaterialRole.Interactive, accessibility, interactionProgress)
        return base.copy(
            refractionHeightDp = refractionHeightDp?.let { it * if (accessibility.reduceMotion) 0.55f else 1f }
                ?: base.refractionHeightDp,
            refractionAmountDp = refractionAmountDp?.let { it * if (accessibility.reduceMotion) 0.45f else 1f }
                ?: base.refractionAmountDp
        )
    }

    fun surfaceAlpha(light: Boolean, press: Float, highContrast: Boolean): Float {
        val base = if (light) lightSurfaceAlpha else darkSurfaceAlpha
        if (highContrast) return (base + 0.20f).coerceAtMost(0.90f)
        val pressedScale = if (light) lightPressedSurfaceScale else darkPressedSurfaceScale
        return base * (1f - press.coerceIn(0f, 1f) * (1f - pressedScale))
    }

    companion object {
        val Default = GlassChipAppearance()
        val BlurredPanel = GlassChipAppearance(maxOffscreenPixels = 160_000)
        val PrimaryAction = GlassChipAppearance(
            refractionHeightDp = 20f,
            refractionAmountDp = 28f,
            refractionFloor = 0.95f,
            lightSurfaceAlpha = 0.06f,
            darkSurfaceAlpha = 0.10f,
            darkPressedSurfaceScale = GlassRecipe.ChipPressedSurfaceScale
        )
    }
}
