package com.tyust.course.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import com.tyust.course.manager.AppearanceSettingsManager
import com.tyust.course.manager.WallpaperMode
import com.tyust.course.manager.WallpaperStyle
import com.tyust.course.manager.WallpaperToneMap

@Immutable
data class AppAppearance(val isDark: Boolean = false)

val LocalAppAppearance = staticCompositionLocalOf { AppAppearance() }

@Composable
internal fun rememberAppWallpaperToneMap(dark: Boolean = LocalAppAppearance.current.isDark): WallpaperToneMap {
    val original = AppearanceSettingsManager.toneMap
    val useNightPreset = dark && AppearanceSettingsManager.mode == WallpaperMode.Preset && !AppearanceSettingsManager.style.isDark
    return remember(original, useNightPreset) {
        if (useNightPreset) WallpaperToneMap.uniform(BackgroundDark.toArgb()) else original
    }
}

/** Night variants affect built-in presets only; imported pictures and colors stay intact. */
@Composable
fun rememberAppWallpaperStyle(): WallpaperStyle {
    val original = AppearanceSettingsManager.style
    val dark = LocalAppAppearance.current.isDark
    val mode = AppearanceSettingsManager.mode
    return remember(original, dark, mode) {
        if (!dark || mode != WallpaperMode.Preset || original.isDark) original else original.copy(
            baseColor = BackgroundDark,
            glowColor = Color(0xFF263349),
            shadeColor = Color(0xFF05080E),
            isDark = true,
            accents = original.accents.map { it.copy(alpha = it.alpha * 0.55f) }
        )
    }
}
