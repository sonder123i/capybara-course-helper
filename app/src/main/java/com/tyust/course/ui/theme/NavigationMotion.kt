package com.tyust.course.ui.theme

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.abs

data class PageTransitionSpec(val enterDistance: Float = 16f, val exitDistance: Float = 20f, val exitScale: Float = 0.985f)

object MotionProfile {
    val Navigation = PageTransitionSpec()
    const val IconMillis = 280
    const val PressMillis = 150
    const val DetailStaggerMillis = 35L
    const val HierarchyEnterDp = 22f
    const val HierarchyBehindDp = 6f
    const val SheetExitMillis = 280
    fun hierarchySpring() = androidx.compose.animation.core.spring<Float>(0.76f, 220f)
    fun sheetSpring() = androidx.compose.animation.core.spring<Float>(0.78f, 210f)
    fun pageSpring() = androidx.compose.animation.core.spring<Float>(0.74f, 180f)
    fun iconSpring() = androidx.compose.animation.core.spring<Float>(0.68f, 420f)
    fun pagerSpring() = MotionSpring.snappy<Float>()
}

data class PageMotion(val x: Float = 0f, val alpha: Float = 1f, val scale: Float = 1f)

/** Captures the currently drawn state before retargeting, including a reversed gesture. */
@Stable
class NavigationMotionState(initial: Int, private val scope: CoroutineScope) {
    private val progress = Animatable(1f)
    private var startPosition = initial.toFloat()
    private var endPosition = initial.toFloat()
    private var direction = 1f
    private var animation: Job? = null
    var target by mutableIntStateOf(initial)
        private set
    var pages by mutableStateOf(mapOf(initial to PageMotion()))
        private set
    val position: Float get() = startPosition + (endPosition - startPosition) * progress.value
    fun weight(page: Int): Float = (1f - abs(position - page)).coerceIn(0f, 1f)

    fun transform(page: Int): PageMotion {
        val start = pages[page] ?: PageMotion(alpha = 0f)
        val p = progress.value.coerceIn(0f, 1f)
        val incoming = page == target
        val alphaProgress = if (incoming) ((p - 0.25f) / 0.75f).coerceIn(0f, 1f) else (p / 0.35f).coerceIn(0f, 1f)
        val end = if (incoming) PageMotion() else PageMotion(-direction * MotionProfile.Navigation.exitDistance, 0f, MotionProfile.Navigation.exitScale)
        return PageMotion(start.x + (end.x - start.x) * p,
            start.alpha + (end.alpha - start.alpha) * alphaProgress, start.scale + (end.scale - start.scale) * p)
    }

    fun select(page: Int, reduced: Boolean, releasedPosition: Float? = null, releasedVelocity: Float? = null) {
        if (releasedPosition == null && page == target && (!reduced || !progress.isRunning)) return
        val current = releasedPosition ?: position
        val velocity = releasedVelocity ?: (progress.velocity * (endPosition - startPosition))
        val snapshots = pages.keys.associateWith(::transform).filterValues { it.alpha > 0.001f }.toMutableMap()
        animation?.cancel()
        direction = if (page >= current) 1f else -1f
        target = page
        snapshots.putIfAbsent(page, PageMotion(direction * MotionProfile.Navigation.enterDistance, 0f, 1f))
        pages = snapshots
        startPosition = current
        endPosition = page.toFloat()
        animation = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            progress.snapTo(0f)
            if (reduced) progress.snapTo(1f) else progress.animateTo(1f, MotionProfile.pageSpring(),
                initialVelocity = if (abs(endPosition - startPosition) > 0.01f) velocity / (endPosition - startPosition) else 0f)
            pages = mapOf(page to PageMotion())
        }
    }

    fun dispose() { animation?.cancel() }
}

val LocalNavigationMotion = staticCompositionLocalOf<NavigationMotionState?> { null }

@Composable
fun rememberNavigationMotionState(selected: Int, account: String, reduced: Boolean): NavigationMotionState {
    val scope = rememberCoroutineScope()
    val state = remember(account, scope) { NavigationMotionState(selected, scope) }
    DisposableEffect(state) { onDispose { state.dispose() } }
    LaunchedEffect(state, selected, reduced) { state.select(selected, reduced) }
    return state
}

@Composable
fun NavigationPages(state: NavigationMotionState, modifier: Modifier = Modifier, content: @Composable (Int) -> Unit) {
    val density = LocalDensity.current
    Box(modifier.fillMaxSize()) {
        state.pages.keys.sortedBy { if (it == state.target) 1 else 0 }.forEach { page ->
            key(page) {
                Box(Modifier.fillMaxSize().graphicsLayer {
                    val frame = state.transform(page)
                    translationX = with(density) { frame.x.dp.toPx() }
                    alpha = frame.alpha
                    scaleX = frame.scale
                    scaleY = frame.scale
                }.then(if (page != state.target) Modifier.clearAndSetSemantics {} else Modifier)) {
                    content(page)
                    if (page != state.target) Box(Modifier.fillMaxSize().pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false).consume()
                            do { val event = awaitPointerEvent(); event.changes.forEach { it.consume() } }
                            while (event.changes.any { it.pressed })
                        }
                    })
                }
            }
        }
    }
}

object SchedulePagerMotion {
    fun scale(offset: Float) = 1f - 0.03f * abs(offset).coerceIn(0f, 1f)
    fun alpha(offset: Float) = 1f - 0.22f * abs(offset).coerceIn(0f, 1f)
}
