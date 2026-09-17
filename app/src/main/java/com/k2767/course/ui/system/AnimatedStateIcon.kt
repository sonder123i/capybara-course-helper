package com.k2767.course.ui.system

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.material3.Icon
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import com.k2767.course.ui.theme.MotionProfile
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

@Composable
fun rememberActionIconProgress(event: Int): Float {
    val reduced = rememberGlassAccessibilityMode().reduceMotion
    val progress = remember { Animatable(0f) }
    LaunchedEffect(event, reduced) {
        if (reduced) progress.snapTo(0f)
        else if (event > 0) progress.animateTo(floor(progress.value) + 1f, tween(MotionProfile.IconMillis))
    }
    return progress.value
}

@Composable
fun AnimatedStateIcon(spec: AppSymbolSpec, progress: Float, tint: Color, modifier: Modifier = Modifier) {
    AppSymbol(spec, progress, tint, modifier)
}

@Composable
fun AnimatedStateIcon(icon: ImageVector, contentDescription: String?, progress: Float, tint: Color, modifier: Modifier = Modifier) {
    val spec = remember(icon) { animatedIconSpec(icon) }
    if (spec == null) Icon(icon, contentDescription, modifier, tint)
    else AnimatedLineIcon(spec, modifier, description = contentDescription, tint = tint, sharedProgress = progress.coerceIn(0f, 1f))
}

@Composable
fun ActionLineIcon(icon: ImageVector, contentDescription: String?, modifier: Modifier = Modifier,
    tint: Color = androidx.compose.material3.LocalContentColor.current, event: Int = 0,
    state: IconVisualState? = null) {
    val spec = remember(icon) { animatedIconSpec(icon) }
    if (spec == null) Icon(icon, contentDescription, modifier, tint)
    else AnimatedLineIcon(spec, modifier, state = state ?: when (icon) {
        Icons.Filled.Stop -> IconVisualState.Running
        Icons.Filled.Visibility -> IconVisualState.Selected
        else -> IconVisualState.Idle
    }, description = contentDescription, tint = tint, event = event)
}

/** Explicit compatibility map for existing callers; never infer behavior from an icon name. */
fun animatedIconSpec(icon: ImageVector): AnimatedIconSpec? = when (icon) {
    Icons.Filled.Refresh, Icons.Filled.Sync, Icons.Outlined.Refresh -> AnimatedIconSpec.Refresh
    Icons.Filled.Settings, Icons.Outlined.Settings -> AnimatedIconSpec.Settings
    Icons.Filled.FilterList, Icons.Outlined.FilterList, Icons.Filled.FilterAlt -> AnimatedIconSpec.Filter
    Icons.Filled.Share, Icons.Outlined.Share -> AnimatedIconSpec.Share
    Icons.Filled.Edit, Icons.Outlined.Edit -> AnimatedIconSpec.Edit
    Icons.Filled.Delete, Icons.Outlined.Delete, Icons.Filled.DeleteSweep -> AnimatedIconSpec.Delete
    Icons.Filled.Undo -> AnimatedIconSpec.Undo
    Icons.Filled.Add, Icons.Filled.AddCircleOutline -> AnimatedIconSpec.Add
    Icons.Filled.Close, Icons.Outlined.Close -> AnimatedIconSpec.Close
    Icons.Filled.Search, Icons.Outlined.Search -> AnimatedIconSpec.Search
    Icons.Filled.KeyboardArrowDown, Icons.Filled.ExpandMore -> AnimatedIconSpec.Chevron
    Icons.Filled.KeyboardArrowUp, Icons.Filled.ExpandLess -> AnimatedIconSpec.ChevronUp
    Icons.AutoMirrored.Filled.ArrowBack, Icons.AutoMirrored.Filled.KeyboardArrowLeft -> AnimatedIconSpec.Back
    Icons.AutoMirrored.Filled.ArrowForward, Icons.AutoMirrored.Filled.KeyboardArrowRight -> AnimatedIconSpec.Forward
    Icons.Filled.Visibility, Icons.Filled.VisibilityOff -> AnimatedIconSpec.Eye
    Icons.Filled.Lock, Icons.Outlined.Lock -> AnimatedIconSpec.Lock
    Icons.Filled.PlayArrow, Icons.Filled.Stop -> AnimatedIconSpec.PlayStop
    Icons.Filled.Pause -> AnimatedIconSpec.Pause
    Icons.Filled.Check, Icons.Filled.CheckCircleOutline -> AnimatedIconSpec.Check
    Icons.Filled.Schedule, Icons.Filled.AccessTime -> AnimatedIconSpec.Clock
    Icons.Filled.Notifications, Icons.Outlined.Notifications -> AnimatedIconSpec.Bell
    Icons.Filled.DateRange, Icons.Outlined.DateRange, Icons.Filled.CalendarMonth -> AnimatedIconSpec.Calendar
    Icons.Filled.LocationOn, Icons.Outlined.LocationOn -> AnimatedIconSpec.Location
    Icons.Filled.Person, Icons.Outlined.Person -> AnimatedIconSpec.Person
    Icons.Filled.MoreVert, Icons.Filled.MoreHoriz -> AnimatedIconSpec.More
    Icons.Filled.Warning, Icons.Outlined.Warning -> AnimatedIconSpec.Warning
    else -> null
}
