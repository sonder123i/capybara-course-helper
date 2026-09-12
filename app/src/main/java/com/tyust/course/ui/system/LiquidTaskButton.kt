package com.tyust.course.ui.system

import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.updateTransition
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.unit.dp
import com.tyust.course.ui.theme.MotionProfile
import com.tyust.course.ui.system.glass.liquidChip
import com.tyust.course.ui.system.glass.GlassChipAppearance
import com.tyust.course.ui.system.glass.rememberInteractiveOptics

val TaskButtonDiameter = 64.dp

/** The same circular touch target is retained for ready, running and scheduled states. */
@Composable
fun LiquidTaskButton(
    text: String, supportingText: String, running: Boolean, stopping: Boolean,
    enabled: Boolean, onClick: () -> Unit, expanded: Boolean, menuProgress: Float,
    onToggleMenu: () -> Unit, modifier: Modifier = Modifier
) {
    val colors = MaterialTheme.colorScheme
    val reduced = rememberGlassAccessibilityMode().reduceMotion
    val transition = updateTransition(stopping || running, label = "task-control")
    val stopProgress by transition.animateFloat(transitionSpec = {
        if (reduced) snap() else MotionProfile.iconSpring()
    }, label = "play-to-stop") { if (it) 1f else 0f }
    val p = menuProgress.coerceIn(0f, 1f)
    val menuVisible = expanded || p > 0f
    val interaction = remember { MutableInteractionSource() }
    val optics = rememberInteractiveOptics()
    val backdrop = LocalAppBackdrop.current?.takeIf { isBackdropSupported() }
    val pressed by interaction.collectIsPressedAsState()
    val pressScale by animateFloatAsState(if (pressed) 0.93f else 1f,
        if (reduced) snap() else MotionProfile.iconSpring(), label = "primary-press")
    val material = if (backdrop != null) Modifier.liquidChip(
        backdrop, CircleShape, optics, enabled = enabled || menuVisible,
        elevation = 6.dp, interactive = false,
        appearance = GlassChipAppearance.PrimaryAction, opticalFeedback = true
    ) else Modifier.background(colors.surfaceContainerHigh, CircleShape)
        .border(1.dp, colors.outlineVariant.copy(alpha = 0.5f), CircleShape)
    val glyphAlpha = if (enabled || menuVisible) 1f else 0.72f
    Box(
        modifier.size(TaskButtonDiameter)
            .graphicsLayer {
                if (!reduced) {
                    scaleX = pressScale * (1f - 0.025f * p)
                    scaleY = scaleX
                }
            }
            .then(material)
            .background(Brush.linearGradient(listOf(
                colors.primary.copy(alpha = if (enabled || menuVisible) 0.035f else 0.015f),
                Color.Transparent,
                colors.primary.copy(alpha = 0.02f)
            )), CircleShape)
            .clip(CircleShape)
            .then(if (!reduced && (enabled || menuVisible)) optics.gestureModifier else Modifier)
            .clickable(interaction, indication = null,
                enabled = enabled || menuVisible, role = Role.Button,
                onClick = { if (menuVisible) onToggleMenu() else onClick() })
            .semantics {
                contentDescription = if (menuVisible) "收起任务操作" else text
                stateDescription = supportingText + if (menuVisible) "，轻点收起" else "，长按展开任务操作"
                onLongClick(label = "展开任务操作") { onToggleMenu(); true }
            }
            .testTag("grab-primary-action"),
        contentAlignment = Alignment.Center
    ) {
        FilledTaskGlyph(
            stopProgress,
            Modifier.size(28.dp).graphicsLayer {
                alpha = 1f - p
                if (!reduced) { rotationZ = -35f * p; scaleX = 1f - p * 0.3f; scaleY = scaleX }
            },
            colors.primary.copy(alpha = glyphAlpha)
        )
        AnimatedLineIcon(
            AnimatedIconSpec.Close,
            Modifier.size(25.dp).graphicsLayer {
                alpha = p
                if (!reduced) { rotationZ = 90f * (1f - p); scaleX = 0.6f + 0.4f * p; scaleY = scaleX }
            },
            tint = colors.onSurface
        )
    }
}

/** A filled play triangle becomes a compact stop square without an extra ring or badge. */
@Composable
private fun FilledTaskGlyph(stopProgress: Float, modifier: Modifier, color: Color) {
    val path = remember { Path() }
    Canvas(modifier) {
        val t = stopProgress.coerceIn(0f, 1f)
        fun between(from: Float, to: Float) = from + (to - from) * t
        path.reset()
        path.moveTo(between(8f, 7f), between(5.5f, 7f))
        path.lineTo(between(19f, 17f), between(12f, 7f))
        path.lineTo(between(19f, 17f), between(12f, 17f))
        path.lineTo(between(8f, 7f), between(18.5f, 17f))
        path.close()
        scale(size.width / 24f, size.height / 24f, Offset.Zero) { drawPath(path, color) }
    }
}
