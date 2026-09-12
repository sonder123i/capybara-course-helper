package com.tyust.course.ui.system

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreHoriz
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Dp
import com.tyust.course.ui.system.glass.glassSheet

data class SystemMenuAction(val title: String, val icon: ImageVector, val onClick: () -> Unit)

@Composable
fun SystemActionMenu(description: String, actions: List<SystemMenuAction>, modifier: Modifier = Modifier,
    anchorWidth: Dp = 48.dp, trigger: (@Composable (() -> Unit) -> Unit)? = null) {
    var expanded by remember { mutableStateOf(false) }
    var opensUp by remember { mutableStateOf(false) }
    var space by remember { mutableFloatStateOf(Float.MAX_VALUE) }
    val density = LocalDensity.current
    val reduced = rememberGlassAccessibilityMode().reduceMotion
    val motion by animateFloatAsState(if (expanded) 1f else 0f, if (reduced) snap() else com.tyust.course.ui.theme.MotionProfile.iconSpring(), label = "actionMenu")
    val progress = motion.coerceIn(0f, 1f)
    val rowHeight = (48f * density.fontScale.coerceAtLeast(1f)).dp
    val desired = rowHeight * actions.size + 8.dp
    val bodyHeight = minOf(desired, with(density) { space.toDp() }).coerceAtLeast(0.dp)
    val backdrop = LocalModalBackdrop.current?.takeIf { isBackdropSupported() }
    val menu: @Composable () -> Unit = {
        Box(Modifier.fillMaxWidth().height((bodyHeight + 8.dp) * progress).clip(RoundedCornerShape(0.dp))) {
            Column(Modifier.padding(top = if (opensUp) 0.dp else 8.dp, bottom = if (opensUp) 8.dp else 0.dp)
                .fillMaxWidth().height(bodyHeight).graphicsLayer {
                    alpha = progress
                    scaleX = 0.96f + 0.04f * progress
                    scaleY = scaleX
                    transformOrigin = TransformOrigin(1f, if (opensUp) 1f else 0f)
                }.then(if (backdrop != null) Modifier.glassSheet(backdrop, 16.dp) else
                    Modifier.background(MaterialTheme.colorScheme.surface, RoundedCornerShape(16.dp)))
                .clip(RoundedCornerShape(16.dp)).verticalScroll(rememberScrollState()).padding(vertical = 4.dp)) {
                actions.forEachIndexed { index, action ->
                    Row(Modifier.fillMaxWidth().heightIn(min = rowHeight).clickable(
                        enabled = expanded, role = Role.Button, onClick = { expanded = false; action.onClick() }
                    ).padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        Text(action.title, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                        ActionLineIcon(action.icon, null, Modifier.size(20.dp))
                    }
                    if (index < actions.lastIndex) SystemDivider()
                }
            }
        }
    }
    AnchoredGlassPortal(
        active = expanded || progress > 0f,
        anchorHeight = 48.dp,
        renderedHeight = 48.dp + (bodyHeight + 8.dp) * progress,
        desiredBodyHeight = desired,
        onDismiss = { expanded = false },
        onSpaceAvailable = { available, up -> space = available; opensUp = up },
        modifier = modifier.width(anchorWidth).height(48.dp),
        popupWidth = 244.dp
    ) {
        Column(Modifier.fillMaxWidth()) {
            if (opensUp && progress > 0f) menu()
            Box(Modifier.fillMaxWidth().height(48.dp), contentAlignment = Alignment.CenterEnd) {
                if (trigger != null) trigger { expanded = !expanded }
                else IconButton(onClick = { expanded = !expanded }, modifier = Modifier.size(48.dp)) {
                    AnimatedLineIcon(AnimatedIconSpec.More, Modifier.size(22.dp),
                        state = if (expanded) IconVisualState.Expanded else IconVisualState.Idle, description = description)
                }
            }
            if (!opensUp && progress > 0f) menu()
        }
    }
}
