package com.tyust.course.ui.system

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.drawBackdrop
import com.kyant.backdrop.effects.blur

enum class SystemCardSurface { Stable, FrostedContent }

/** Content shares the wallpaper layer and only blurs it; it never owns a lens capture. */
@Composable
internal fun Modifier.frostedContentSurface(
    shape: Shape,
    backdrop: Backdrop? = LocalAppBackdrop.current,
    appearance: WallpaperAppearanceColors = rememberWallpaperRegionAppearance()
): Modifier {
    val highContrast = rememberGlassAccessibilityMode().highContrast
    val light = appearance.usesDarkForeground
    val baseAlpha = if (light) 0.42f else 0.58f
    val alpha = if (highContrast) 0.96f else maxOf(baseAlpha, appearance.surface.alpha).coerceAtMost(0.88f)
    val tint = (if (light) Color.White else Color(0xFF171B22)).copy(alpha = alpha)
    val surface = if (backdrop != null && isBackdropSupported()) {
        Modifier.drawBackdrop(
            backdrop = backdrop,
            shape = { shape },
            effects = { blur(20.dp.toPx()) },
            highlight = { null }, shadow = { null }, innerShadow = { null },
            onDrawSurface = { drawRect(tint) }
        )
    } else Modifier.background(tint, shape)
    return this.clip(shape).then(surface).drawWithContent {
        drawContent()
        drawLine(
            Color.White.copy(alpha = if (light) 0.30f else 0.10f),
            Offset(16.dp.toPx(), 0.5.dp.toPx()),
            Offset(size.width - 16.dp.toPx(), 0.5.dp.toPx()),
            strokeWidth = 0.5.dp.toPx()
        )
    }
}
