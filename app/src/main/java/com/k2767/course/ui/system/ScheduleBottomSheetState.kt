package com.k2767.course.ui.system

import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.nestedscroll.NestedScrollConnection
import androidx.compose.ui.input.nestedscroll.NestedScrollSource
import androidx.compose.ui.unit.Velocity
import com.k2767.course.ui.theme.MotionSpring
import kotlinx.coroutines.Job
import kotlinx.coroutines.currentCoroutineContext

fun shouldDismissScheduleSheet(offset: Float, height: Float, velocity: Float, density: Float): Boolean = when {
    height <= 0 -> false
    velocity < -1000f * density -> false
    else -> offset >= height * 0.25f || velocity > 1000f * density
}

@Stable
class ScheduleBottomSheetState {
    var sourceBounds by mutableStateOf<Rect?>(null)
    var offset by mutableFloatStateOf(0f)
        private set
    var height by mutableFloatStateOf(1f)
    var density: Float = 1f
    var reducedMotion: Boolean = false
    private var settleJob: Job? = null
    private val rebound = Animatable(0f)

    fun dragBy(delta: Float): Float {
        settleJob?.cancel()
        val previous = offset
        offset = (offset + delta).coerceIn(0f, height)
        return offset - previous
    }

    fun predictiveProgress(progress: Float) {
        settleJob?.cancel()
        offset = height * progress.coerceIn(0f, 1f)
    }

    suspend fun restore(velocity: Float? = null) {
        val job = currentCoroutineContext()[Job]
        val previousVelocity = velocity ?: rebound.velocity
        if (settleJob !== job) settleJob?.cancel()
        settleJob = job
        try {
            if (reducedMotion) offset = 0f
            else {
                rebound.snapTo(offset)
                rebound.animateTo(0f, com.k2767.course.ui.theme.MotionProfile.sheetSpring(), initialVelocity = previousVelocity) {
                    offset = value.coerceIn(0f, height)
                }
            }
        } finally { if (settleJob === job) settleJob = null }
    }

    suspend fun finishDrag(velocity: Float, dismiss: () -> Unit) {
        if (shouldDismissScheduleSheet(offset, height, velocity, density)) dismiss() else restore(velocity)
    }

    fun nestedScroll(dismiss: () -> Unit): NestedScrollConnection = object : NestedScrollConnection {
        override fun onPreScroll(available: Offset, source: NestedScrollSource): Offset =
            if (source == NestedScrollSource.UserInput && available.y < 0f && offset > 0f) Offset(0f, dragBy(available.y)) else Offset.Zero
        override fun onPostScroll(consumed: Offset, available: Offset, source: NestedScrollSource): Offset =
            if (source == NestedScrollSource.UserInput && available.y > 0f) Offset(0f, dragBy(available.y)) else Offset.Zero
        override suspend fun onPreFling(available: Velocity): Velocity {
            if (offset <= 0f) return Velocity.Zero
            finishDrag(available.y, dismiss)
            return Velocity(0f, available.y)
        }
    }
}
