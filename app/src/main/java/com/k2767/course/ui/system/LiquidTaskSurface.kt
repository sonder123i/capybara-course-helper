package com.k2767.course.ui.system

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.k2767.course.ui.system.glass.liquidChip
import com.k2767.course.ui.system.glass.rememberInteractiveOptics
import com.k2767.course.ui.theme.MotionProfile

/**
 * A single refracting task surface. No translucent Material elevation and no nested
 * rectangular fill: both create dark inner edges under a transparent card.
 * The wallpaper source is a sibling of the page, never the card's own capture.
 */
@Composable
fun LiquidTaskSurface(
    modifier: Modifier = Modifier,
    accent: Color = MaterialTheme.colorScheme.primary,
    emphasized: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    cornerRadius: Dp = 22.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    val optics = rememberInteractiveOptics()
    val reduced = rememberGlassAccessibilityMode().reduceMotion
    val emphasis by animateFloatAsState(if (emphasized) 1f else 0f,
        if (reduced) snap() else MotionProfile.iconSpring(), label = "task-glass-emphasis")
    val shape = RoundedCornerShape(cornerRadius)
    val backdrop = LocalAppBackdrop.current?.takeIf { isBackdropSupported() }
    val colors = MaterialTheme.colorScheme
    val glass = if (backdrop != null) Modifier.liquidChip(backdrop, shape, optics,
        elevation = 0.dp, interactive = false)
    else Modifier.background(colors.surface, shape)
    Column(
        modifier.fillMaxWidth()
            .graphicsLayer {
                if (!reduced) {
                    val travel = optics.dragTravel(2.dp.toPx())
                    translationX = travel.x; translationY = travel.y
                    scaleX = 1f - 0.015f * optics.pressProgress
                    scaleY = 1f - 0.025f * optics.pressProgress
                }
            }
            .then(glass)
            .clip(shape)
            .drawWithContent {
                val pressed = optics.opticalProgress
                drawRect(Brush.linearGradient(
                    listOf(accent.copy(alpha = 0.025f + emphasis.coerceIn(0f, 1f) * 0.065f),
                        Color.Transparent, accent.copy(alpha = 0.015f + pressed * 0.035f)),
                    start = Offset.Zero, end = Offset(size.width, size.height)))
                drawContent()
            }
            .then(if (!reduced) optics.gestureModifier else Modifier)
            .padding(contentPadding),
        verticalArrangement = Arrangement.spacedBy(12.dp),
        content = content
    )
}
