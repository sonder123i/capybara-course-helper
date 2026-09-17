package com.k2767.course.ui.theme

import androidx.compose.runtime.*
import androidx.compose.ui.graphics.Color

@Stable
internal class AppSystemBarState {
    val surfaces = mutableStateMapOf<Any, Pair<Long, Color>>()
    private var sequence = 0L
    fun attach(owner: Any, color: Color) { surfaces[owner] = (surfaces[owner]?.first ?: ++sequence) to color }
    val statusSurface: Color get() = surfaces.values.maxByOrNull { it.first }?.second ?: Color.Transparent
}

internal val LocalSystemBarState = staticCompositionLocalOf<AppSystemBarState?> { null }

/** Report the actual tint drawn above the wallpaper in the system status-bar area. */
@Composable
internal fun ReportStatusBarSurface(color: Color) {
    val state = LocalSystemBarState.current ?: return
    val owner = remember { Any() }
    SideEffect { state.attach(owner, color) }
    DisposableEffect(state, owner) { onDispose { state.surfaces.remove(owner) } }
}
