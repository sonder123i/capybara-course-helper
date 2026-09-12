package com.tyust.course.ui.system

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.runtime.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import kotlin.math.ceil
import kotlin.math.floor

/** One clock per tab, shared by the visible glyph, optical copy and minimized capsule. */
@Stable
class NavigationIconPlayback(count: Int, private val scope: CoroutineScope) {
    private val clocks = List(count) { Animatable(0f) }
    private val jobs = arrayOfNulls<Job>(count)
    private var selected = -1
    private var reduced = false

    fun phase(index: Int): Float = clocks[index].value.let { it - floor(it) }

    fun select(index: Int, reduceMotion: Boolean) {
        if (reduced != reduceMotion) {
            reduced = reduceMotion
            if (reduced) clocks.indices.forEach { settle(it, immediately = true) }
        }
        if (selected == index) return
        if (selected >= 0) settle(selected, immediately = reduced)
        selected = index
        replay(index)
    }

    fun replay(index: Int) {
        if (reduced) return
        val clock = clocks[index]
        jobs[index]?.cancel()
        // Retarget an unfinished cycle from its current frame; never snap back to zero.
        val end = floor(clock.value) + if (clock.value % 1f > 0.001f) 2f else 1f
        jobs[index] = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            clock.animateTo(end, tween(((end - clock.value) * 480).toInt(), easing = LinearEasing))
        }
    }

    private fun settle(index: Int, immediately: Boolean) {
        jobs[index]?.cancel()
        val clock = clocks[index]
        jobs[index] = scope.launch(start = CoroutineStart.UNDISPATCHED) {
            val end = ceil(clock.value)
            if (immediately) clock.snapTo(end) else clock.animateTo(end, tween(160))
        }
    }

    fun dispose() = jobs.forEach { it?.cancel() }
}

@Composable
fun rememberNavigationIconPlayback(count: Int, selected: Int, reduced: Boolean): NavigationIconPlayback {
    val scope = rememberCoroutineScope()
    val state = remember(count, scope) { NavigationIconPlayback(count, scope) }
    LaunchedEffect(state, selected, reduced) { state.select(selected, reduced) }
    DisposableEffect(state) { onDispose { state.dispose() } }
    return state
}
