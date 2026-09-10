package com.tyust.course.ui.system

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.kyant.backdrop.Backdrop
import com.tyust.course.ui.system.glass.adaptiveGlassChip
import com.tyust.course.ui.system.glass.applyChipContentDeformation
import com.tyust.course.ui.system.glass.rememberInteractiveOptics

internal class GlassRailAction(
    val description: String,
    val onClick: () -> Unit,
    val enabled: Boolean = true,
    val icon: @Composable () -> Unit
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun GlassActionRail(
    actions: List<GlassRailAction>,
    modifier: Modifier = Modifier,
    backdrop: Backdrop? = LocalControlBackdrop.current
) {
    require(actions.size in 1..2)
    val optics = rememberInteractiveOptics()
    val reduced = rememberGlassAccessibilityMode().reduceMotion
    val colors = LocalWallpaperAppearanceColors.current
    Box(
        modifier.width((48 * actions.size).dp).height(48.dp)
            .adaptiveGlassChip(backdrop, RoundedCornerShape(24.dp), optics,
                enabled = actions.any { it.enabled }),
        contentAlignment = Alignment.Center
    ) {
        Row(Modifier.graphicsLayer {
            if (!reduced) applyChipContentDeformation(
                optics, GlassRecipe.ChipDragTravelDp.dp.toPx(), GlassRecipe.ChipDragStretch,
                GlassRecipe.ChipIconPressDepth, GlassRecipe.ChipContentDeformDamping
            )
        }) {
            actions.forEach { action ->
                TooltipBox(
                    positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
                    tooltip = { PlainTooltip { Text(action.description) } },
                    state = rememberTooltipState()
                ) {
                    Box(
                        Modifier.size(48.dp)
                            .semantics { contentDescription = action.description }
                            .clickable(
                                interactionSource = remember { MutableInteractionSource() },
                                indication = null, enabled = action.enabled,
                                role = Role.Button, onClick = action.onClick
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        CompositionLocalProvider(LocalContentColor provides colors.onSurface.copy(
                            alpha = if (action.enabled) 1f else 0.38f
                        )) { Box(Modifier.size(21.dp), contentAlignment = Alignment.Center) { action.icon() } }
                    }
                }
            }
        }
        if (actions.size == 2) Box(Modifier.width(0.5.dp).height(18.dp).background(colors.border.copy(alpha = 0.14f)))
    }
}
