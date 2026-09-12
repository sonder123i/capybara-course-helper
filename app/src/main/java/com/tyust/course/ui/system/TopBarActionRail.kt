package com.tyust.course.ui.system

import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onPlaced
import androidx.compose.ui.layout.positionInParent
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import com.tyust.course.ui.system.glass.InteractiveOptics
import com.tyust.course.ui.system.glass.adaptiveGlassChip
import com.tyust.course.ui.system.glass.applyChipContentDeformation
import kotlin.math.roundToInt
import com.kyant.backdrop.Backdrop
import com.tyust.course.ui.system.glass.LiquidActionGroup
import com.tyust.course.ui.system.glass.LiquidActionGroupScope

object TopBarLayoutMetrics {
    val TouchTarget = 48.dp
    val VisualSize = 34.dp
    val IconSize = 20.dp
    val ActionSpacing = 4.dp
    val DragTravel = 2.dp
    const val ScaleChange = 0.02f
    @Composable fun height() = rememberScreenMetrics().tall(64.dp, 56.dp)
    @Composable fun segmentHeight() = rememberScreenMetrics().tall(52.dp, 44.dp)
    @Composable fun segmentWidth() = rememberScreenMetrics().wide(200.dp, 152.dp)
}

/** The 48dp cell owns layout/semantics; the smaller glass disc owns only optics. */
@Composable
internal fun TopBarActionItem(
    description: String?, onClick: () -> Unit, enabled: Boolean, presence: Float,
    backdrop: Backdrop?, optics: InteractiveOptics, onBounds: (Rect) -> Unit,
    content: @Composable () -> Unit
) {
    val reduced = rememberGlassAccessibilityMode().reduceMotion
    val visible = presence.coerceIn(0f, 1f)
    val reachable = enabled && visible > 0.6f
    Box(Modifier
        .layout { measurable, constraints ->
            val child = measurable.measure(constraints)
            layout((child.width * visible).roundToInt(), child.height) { child.place(0, 0) }
        }
        .size(TopBarLayoutMetrics.TouchTarget)
        .onPlaced { onBounds(Rect(it.positionInParent(), Size(it.size.width.toFloat(), it.size.height.toFloat()))) }
        .graphicsLayer { alpha = visible; scaleX = 0.82f + 0.18f * visible; scaleY = scaleX }
        .then(if (reachable) Modifier.semantics { if (description != null) contentDescription = description } else Modifier.clearAndSetSemantics {})
        .clickable(remember { MutableInteractionSource() }, indication = null, enabled = reachable, role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Box(Modifier.size(TopBarLayoutMetrics.VisualSize)
            .adaptiveGlassChip(backdrop, CircleShape, optics, enabled = enabled, interactive = enabled))
        Box(Modifier.fillMaxSize().graphicsLayer {
            if (!reduced) applyChipContentDeformation(optics, 2.dp.toPx(), 0.02f, 0.02f, GlassRecipe.ChipContentDeformDamping)
        }, contentAlignment = Alignment.Center) { content() }
    }
}

val LocalTopBarMotion = staticCompositionLocalOf { false }

@Composable
fun TopBarActionRail(modifier: Modifier = Modifier, spacing: Dp = TopBarLayoutMetrics.ActionSpacing,
    backdrop: Backdrop? = LocalControlBackdrop.current, content: @Composable LiquidActionGroupScope.() -> Unit) {
    CompositionLocalProvider(LocalTopBarMotion provides true,
        androidx.compose.material3.LocalMinimumInteractiveComponentSize provides TopBarLayoutMetrics.TouchTarget) {
        LiquidActionGroup(modifier, spacing, backdrop, mergeEnabled = false, content = content)
    }
}
