package com.k2767.course.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.k2767.course.manager.AppearanceSettingsManager
import com.k2767.course.ui.system.GlassMaterialRole
import com.k2767.course.ui.system.GlassMaterials
import com.k2767.course.ui.system.GlassRecipe
import com.k2767.course.ui.system.LocalAppBackdrop
import com.k2767.course.ui.system.LocalModalBackdrop
import com.k2767.course.ui.system.glass.LocalGlassLensModalAnchor
import com.k2767.course.ui.system.glass.glassLens
import com.k2767.course.ui.system.glass.glassLensOpticsFrom
import com.k2767.course.ui.system.glass.glassRim
import com.k2767.course.ui.system.isBackdropSupported
import com.k2767.course.ui.system.isRuntimeLensEnabled
import com.k2767.course.ui.system.modalSurfaceAlpha
import com.k2767.course.ui.system.rememberGlassAccessibilityMode
import com.k2767.course.ui.system.rememberGlassDarkTheme
import com.k2767.course.ui.system.glassSurfaceColor
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur
import com.kyant.backdrop.effects.lens
import com.kyant.backdrop.effects.vibrancy
import com.kyant.backdrop.shadow.Shadow

/**
 * 课表页统一的玻璃面。卡片是可读性优先的半透表面，
 * 但滚动的课程卡片自己【绝不】捕获 backdrop——一屏几十张卡片各挂一层捕获，
 * 顶栏和详情卡的折射就再也没有预算可用了。
 */
@Composable
internal fun scheduleCardColor(accent: Color, amount: Float): Color {
    val surface = glassSurfaceColor()
    return lerp(surface, accent.copy(alpha = surface.alpha), amount)
}

@Composable
internal fun scheduleCardBorder(accent: Color, emphasized: Boolean = false): BorderStroke {
    val colors = MaterialTheme.colorScheme
    if (!AppearanceSettingsManager.glassEffectEnabled) {
        return BorderStroke(0.7.dp, if (emphasized) accent.copy(alpha = 0.5f) else colors.outlineVariant)
    }
    val light = !rememberGlassDarkTheme()
    return BorderStroke(0.8.dp, Brush.linearGradient(listOf(
        Color.White.copy(alpha = if (light) 0.86f else 0.28f),
        accent.copy(alpha = if (emphasized) 0.46f else 0.15f),
        Color.White.copy(alpha = if (light) 0.48f else 0.12f)
    )))
}

@Composable
internal fun Modifier.scheduleGlassSheen(): Modifier {
    if (!AppearanceSettingsManager.glassEffectEnabled) return this
    val alpha = if (rememberGlassDarkTheme()) 0.07f else 0.18f
    return background(Brush.verticalGradient(listOf(Color.White.copy(alpha = alpha), Color.Transparent)))
}

/** 详情卡只有一枚光学面，复用已有的常驻 modal 捕获源，不为它另开一层。 */
@Composable
internal fun Modifier.scheduleDetailGlass(): Modifier {
    val shape = RoundedCornerShape(28.dp)
    val backdrop = (LocalModalBackdrop.current ?: LocalAppBackdrop.current)?.takeIf { isBackdropSupported() }
        ?: return background(MaterialTheme.colorScheme.surface, shape)
    val density = LocalDensity.current
    val accessibility = rememberGlassAccessibilityMode()
    val material = GlassMaterials.resolve(GlassMaterialRole.Modal, accessibility)
    val anchor = LocalGlassLensModalAnchor.current
    val dark = rememberGlassDarkTheme()
    val surface = MaterialTheme.colorScheme.surface.copy(alpha = modalSurfaceAlpha(dark, accessibility.highContrast))
    return glassLens(anchor, optics = { width, height ->
        glassLensOpticsFrom(material, density, cornerRadiusPx = with(density) { 28.dp.toPx() },
            minDimensionPx = minOf(width, height), pressScalesRefraction = false, chromaticAberrationAtRest = false)
    }).drawBackdrop(backdrop = backdrop, shape = { shape }, effects = {
        vibrancy()
        if (isRuntimeLensEnabled()) {
            blur(GlassRecipe.DialogBlurDp.dp.toPx())
            lens(GlassRecipe.DialogRefractionHeightDp.dp.toPx(), GlassRecipe.DialogRefractionAmountDp.dp.toPx())
        } else if (anchor == null) blur((GlassRecipe.DialogBlurDp * 2).dp.toPx())
    }, onDrawBackdrop = { draw -> if (anchor == null) draw() },
        highlight = { null }, shadow = { Shadow(alpha = material.shadowAlpha) }, onDrawSurface = { drawRect(surface) }
    ).glassRim(shape, intensity = 0.8f, isLightTheme = !dark)
}
