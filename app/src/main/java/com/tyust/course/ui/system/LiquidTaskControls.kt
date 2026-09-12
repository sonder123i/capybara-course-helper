package com.tyust.course.ui.system

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitLongPressOrCancellation
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tyust.course.ui.theme.MotionProfile
import com.tyust.course.ui.system.glass.liquidChip
import com.tyust.course.ui.system.glass.rememberInteractiveOptics
import kotlin.math.PI
import kotlin.math.asin
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.math.roundToInt

val TaskControlsReservedHeight = TaskButtonDiameter + 12.dp
private val TaskOrbitDiameter = 48.dp

data class TaskQuickAction(
    val id: String, val label: String, val icon: AnimatedIconSpec,
    val enabled: Boolean = true, val selected: Boolean = false,
    val caption: String = label, val iconProgress: Float? = null, val onClick: () -> Unit
)

@Stable
class TaskControlsState internal constructor() {
    var expanded by mutableStateOf(false)
    var highlightedId by mutableStateOf<String?>(null)
    internal val animation = Animatable(0f)
    internal var pendingAction: (() -> Unit)? = null
    val progress: Float get() = animation.value.coerceIn(0f, 1f)
    fun close() { pendingAction = null; expanded = false; highlightedId = null }
    internal fun select(action: () -> Unit) {
        if (!expanded || pendingAction != null) return
        pendingAction = action
        expanded = false
        highlightedId = null
    }
}

@Composable
fun rememberTaskControlsState(): TaskControlsState {
    val state = remember { TaskControlsState() }
    val reduced = rememberGlassAccessibilityMode().reduceMotion
    LaunchedEffect(state.expanded, reduced) {
        if (state.expanded) state.pendingAction = null
        val target = if (state.expanded) 1f else 0f
        if (reduced) state.animation.snapTo(target)
        else state.animation.animateTo(target, if (state.expanded) spring(dampingRatio = 0.78f, stiffness = 390f)
            else androidx.compose.animation.core.tween(com.tyust.course.ui.theme.ModuleMotion.ExitMillis))
        if (!state.expanded) {
            val action = state.pendingAction
            state.pendingAction = null
            action?.invoke()
        }
    }
    DisposableEffect(state) { onDispose { state.close() } }
    return state
}

/** Glass controls unfold on one compact quarter-circle around the primary button. */
@Composable
fun LiquidTaskControls(
    actions: List<TaskQuickAction>, bottomInset: Dp, state: TaskControlsState,
    modifier: Modifier = Modifier,
    primary: @Composable (expanded: Boolean, progress: Float, toggle: () -> Unit) -> Unit
) {
    val reduced = rememberGlassAccessibilityMode().reduceMotion
    val haptic = LocalHapticFeedback.current
    val density = LocalDensity.current
    val hitRadius = with(density) { 32.dp.toPx() }
    val latestActions by rememberUpdatedState(actions)
    val centers = remember { mutableMapOf<String, Offset>() }
    var dockOrigin by remember { mutableStateOf(Offset.Zero) }
    var rootOrigin by remember { mutableStateOf(Offset.Zero) }
    var dockSize by remember { mutableStateOf(IntSize.Zero) }
    SideEffect { centers.keys.retainAll(actions.map { it.id }.toSet()) }
    BackHandler(state.expanded) { state.close() }
    BoxWithConstraints(modifier.fillMaxSize().onGloballyPositioned { rootOrigin = it.positionInRoot() }) {
        val p = state.progress
        val flight = state.animation.value.coerceIn(0f, 1.055f)
        val dockCenter = dockOrigin - rootOrigin + Offset(dockSize.width / 2f, dockSize.height / 2f)
        val halfCount = actions.size / 2
        // Leave equal horizontal/vertical clearance between square touch targets.
        // Slightly wider angular gaps around the diagonal keep a single arc compact.
        val spacingFactor = when {
            actions.size <= 1 -> 1f
            actions.size % 2 == 0 -> sqrt((halfCount * halfCount + (halfCount - 1) * (halfCount - 1)).toFloat())
            else -> halfCount * sqrt(2f)
        }
        val desiredRadius = maxOf(108.dp, (TaskOrbitDiameter + 4.dp) * spacingFactor)
        val radius = minOf(desiredRadius, (maxWidth - 88.dp).coerceAtLeast(80.dp),
            (maxHeight - bottomInset - 96.dp).coerceAtLeast(80.dp))
        val surfaceRadius = radius + 34.dp
        val shown = p > 0f || state.expanded
        if (shown) {
            Box(Modifier.fillMaxSize()
                .background(Color.Black.copy(alpha = 0.12f * p))
                .clickable(remember { MutableInteractionSource() }, indication = null) { state.close() }
                .semantics { contentDescription = "关闭操作菜单" }.testTag("task-fan-scrim"))
            TaskFanSurface(surfaceRadius, p, flight, reduced,
                Modifier.offset {
                    with(density) {
                        IntOffset((dockCenter.x - surfaceRadius.toPx()).roundToInt(),
                            (dockCenter.y - surfaceRadius.toPx()).roundToInt())
                    }
                })
            val highlighted = actions.firstOrNull { it.id == state.highlightedId }
            if (highlighted != null) {
            Column(
                Modifier.offset {
                    with(density) {
                        IntOffset((dockCenter.x - surfaceRadius.toPx() + 12.dp.toPx()).roundToInt(),
                            (dockCenter.y - surfaceRadius.toPx()).roundToInt())
                    }
                }.width(136.dp).graphicsLayer {
                    alpha = (p * 2f - 1f).coerceIn(0f, 1f)
                    translationY = -size.height - 10.dp.toPx()
                }
                    .shadow(3.dp, RoundedCornerShape(16.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHigh, RoundedCornerShape(16.dp))
                    .padding(horizontal = 10.dp, vertical = 9.dp)
                    .clearAndSetSemantics {},
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(highlighted.label, style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center, maxLines = 2)
                Spacer(Modifier.height(4.dp))
                Text("松手执行",
                    style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center)
            }
            }
        }
        actions.forEachIndexed { index, action ->
            key(action.id) {
                val angle = if (actions.size == 1) PI * 0.25 else {
                    val mirroredIndex = minOf(index, actions.lastIndex - index)
                    val firstHalf = asin((mirroredIndex / spacingFactor).toDouble().coerceIn(0.0, 1.0))
                    if (index * 2 <= actions.lastIndex) firstHalf else PI * 0.5 - firstHalf
                }
                val stagger = index * 0.045f
                val local = (flight * (1f + stagger) - stagger).coerceIn(0f, 1.055f)
                if (shown) {
                    TaskOrbitButton(action, reachable = state.expanded && local > 0.9f,
                        highlighted = state.highlightedId == action.id,
                        reveal = ((local - 0.45f) / 0.55f).coerceIn(0f, 1f),
                        modifier = Modifier.offset {
                            with(density) {
                                IntOffset((dockCenter.x - radius.toPx() * cos(angle).toFloat() * local - TaskOrbitDiameter.toPx() / 2f).roundToInt(),
                                    (dockCenter.y - radius.toPx() * sin(angle).toFloat() * local - TaskOrbitDiameter.toPx() / 2f).roundToInt())
                            }
                        }.graphicsLayer {
                            alpha = (local * 1.4f).coerceIn(0f, 1f)
                            if (!reduced) {
                                scaleX = 0.45f + local * 0.55f; scaleY = scaleX
                                rotationZ = (1f - local) * -18f
                            }
                        },
                        onPosition = { centers[action.id] = it },
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                            state.select { latestActions.firstOrNull { it.id == action.id && it.enabled }?.onClick?.invoke() }
                        })
                }
            }
        }
        Box(Modifier.align(Alignment.BottomEnd).padding(end = 16.dp, bottom = bottomInset + 12.dp)) {
            Box(Modifier.testTag("grab-action-dock")
                .onGloballyPositioned { dockOrigin = it.positionInRoot(); dockSize = it.size }
                .pointerInput(state, hitRadius) {
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        val held = awaitLongPressOrCancellation(down.id) ?: return@awaitEachGesture
                        if (latestActions.isEmpty()) return@awaitEachGesture
                        var traveled = false
                        var released = false
                        state.expanded = true
                        state.highlightedId = null
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        try {
                            while (true) {
                                // Once held, the fan owns this pointer. Consume its release before
                                // the child's clickable sees it, including when there was no drag.
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == held.id } ?: break
                                if (change.isConsumed) break
                                change.consume()
                                val position = dockOrigin + change.position
                                if ((change.position - Offset(dockSize.width / 2f, dockSize.height / 2f)).getDistance() > hitRadius) traveled = true
                                val nearest = if (state.progress < 0.85f) null else
                                    latestActions.filter { it.enabled && centers.containsKey(it.id) }
                                        .minByOrNull { (centers.getValue(it.id) - position).getDistance() }
                                        ?.takeIf { (centers.getValue(it.id) - position).getDistance() <= hitRadius }?.id
                                if (nearest != state.highlightedId) {
                                    state.highlightedId = nearest
                                    if (nearest != null) haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                if (!change.pressed) {
                                    val id = state.highlightedId
                                    if (id != null) state.select {
                                        latestActions.firstOrNull { it.id == id && it.enabled }?.onClick?.invoke()
                                    } else if (traveled) state.close()
                                    // Releasing over the origin leaves the menu available for a tap.
                                    released = true
                                    break
                                }
                            }
                        } finally {
                            if (!released) state.close()
                        }
                    }
                }) {
                primary(state.expanded, p) {
                    if (state.expanded || state.progress > 0f) state.close() else state.expanded = true
                }
            }
        }
    }
}

@Composable
private fun TaskFanSurface(
    radius: Dp, progress: Float, flight: Float, reduced: Boolean, modifier: Modifier
) {
    val extent = radius + 36.dp
    val shape = RoundedCornerShape(topStart = radius, topEnd = 28.dp,
        bottomEnd = 36.dp, bottomStart = 28.dp)
    val optics = rememberInteractiveOptics()
    val backdrop = LocalAppBackdrop.current?.takeIf { isBackdropSupported() }
    val colors = MaterialTheme.colorScheme
    val material = if (backdrop != null) Modifier.liquidChip(backdrop, shape, optics,
        elevation = 8.dp, interactive = false)
    else Modifier.background(colors.surfaceContainerHigh, shape)
        .border(1.dp, colors.outlineVariant.copy(alpha = 0.5f), shape)
    Box(modifier.size(extent)
        .graphicsLayer {
            alpha = progress
            transformOrigin = TransformOrigin(radius / extent, radius / extent)
            if (!reduced) { scaleX = 0.12f + 0.88f * flight; scaleY = scaleX }
        }
        .then(material)
        .background(colors.surface.copy(alpha = 0.08f), shape)
        .testTag("task-fan-surface"))
}

@Composable
private fun TaskOrbitButton(
    action: TaskQuickAction, reachable: Boolean, highlighted: Boolean, reveal: Float,
    modifier: Modifier, onPosition: (Offset) -> Unit, onClick: () -> Unit
) {
    val reduced = rememberGlassAccessibilityMode().reduceMotion
    val colors = MaterialTheme.colorScheme
    val interaction = remember { MutableInteractionSource() }
    val optics = rememberInteractiveOptics()
    val backdrop = LocalAppBackdrop.current?.takeIf { isBackdropSupported() }
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.93f else if (highlighted) 1.12f else 1f,
        if (reduced) snap() else MotionProfile.iconSpring(), label = "fan-focus")
    val active = highlighted || action.selected || pressed
    val emphasis by animateFloatAsState(if (active) 1f else 0f,
        if (reduced) snap() else MotionProfile.iconSpring(), label = "fan-emphasis")
    val material = if (backdrop != null) Modifier.liquidChip(backdrop, CircleShape, optics,
        enabled = action.enabled, elevation = 3.dp, interactive = false)
    else Modifier.background(colors.surfaceContainerHigh, CircleShape)
        .border(1.dp, colors.outlineVariant.copy(alpha = 0.5f), CircleShape)
    Column(
        modifier.size(TaskOrbitDiameter).onGloballyPositioned { onPosition(it.boundsInRoot().center) }
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .then(material)
            .clip(CircleShape)
            .background(lerp(colors.surface.copy(alpha = 0.08f), colors.primary.copy(alpha = 0.18f), emphasis.coerceIn(0f, 1f)))
            .then(if (!reduced && reachable && action.enabled) optics.gestureModifier else Modifier)
            .clickable(interaction, indication = null,
                enabled = reachable && action.enabled, role = Role.Button, onClick = onClick)
            .then(if (reachable) Modifier.semantics {
                contentDescription = action.label; selected = action.selected
            } else Modifier.clearAndSetSemantics {})
            .testTag("task-quick-" + action.id),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterVertically)
    ) {
        TaskSvgIcon(action.icon, reveal, active,
            (if (active) colors.primary else colors.onSurface).copy(alpha = if (action.enabled) 1f else 0.35f),
            Modifier.size(23.dp), modeProgress = action.iconProgress)
        Text(action.caption, Modifier.clearAndSetSemantics {},
            style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp),
            fontWeight = if (highlighted) FontWeight.SemiBold else FontWeight.Medium,
            color = colors.onSurface.copy(alpha = if (action.enabled) 1f else 0.4f), maxLines = 1)
    }
}
