package com.tyust.course.ui.system.glass

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.AnimationSpec
import androidx.compose.animation.core.spring
import androidx.compose.foundation.MutatorMutex
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.util.fastCoerceIn
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlin.math.abs
import android.os.SystemClock

class DampedDragAnimation(
    private val animationScope: CoroutineScope,
    val initialValue: Float,
    val valueRange: ClosedRange<Float>,
    val visibilityThreshold: Float,
    val initialScale: Float,
    val pressedScale: Float,
    private val directManipulationSpec: AnimationSpec<Float> =
        spring(1f, 1000f, visibilityThreshold),
    private val settleAnimationSpec: AnimationSpec<Float> =
        spring(0.5f, 340f, visibilityThreshold),
    private val releaseScaleAnimationSpec: AnimationSpec<Float> =
        spring(0.34f, 280f, 0.001f),
    val onDragStarted: DampedDragAnimation.(position: Offset) -> Unit,
    val onDragStopped: DampedDragAnimation.() -> Unit,
    val onDrag: DampedDragAnimation.(size: IntSize, dragAmount: Offset) -> Unit,
    val onDragCancelled: DampedDragAnimation.() -> Unit = {},
    private val pressScaleAnimationSpec: AnimationSpec<Float>? = null,
) {
    private val velocityAnimationSpec = spring(0.5f, 300f, visibilityThreshold * 10f)
    // tint/色散转玻璃过渡：略降刚度让\"实色→玻璃\"更平滑，不突兀。
    private val pressProgressAnimationSpec = spring(0.85f, 360f, 0.001f)
    // 按下阶段：较高阻尼，缩放跟手贴合，不抖。
    private val scaleXAnimationSpec = spring(0.6f, 250f, 0.001f)
    private val scaleYAnimationSpec = spring(0.7f, 250f, 0.001f)

    private val valueAnimation = Animatable(initialValue, visibilityThreshold)
    private val velocityAnimation = Animatable(0f, 5f)
    private val pressProgressAnimation = Animatable(0f, 0.001f)
    private val scaleXAnimation = Animatable(initialScale, 0.001f)
    private val scaleYAnimation = Animatable(initialScale, 0.001f)

    private val mutatorMutex = MutatorMutex()
    private val velocityTracker = VelocityTracker()
    private var interactionJob: Job? = null
    private var valueJob: Job? = null
    private var velocityJob: Job? = null
    private var requestedValue by mutableFloatStateOf(initialValue)
    private var reducedMotion = false
    private var gestureStartValue = initialValue

    val value: Float get() = if (reducedMotion) requestedValue else valueAnimation.value
    val targetValue: Float get() = requestedValue
    val pressProgress: Float get() = if (reducedMotion) 0f else pressProgressAnimation.value
    val scaleX: Float get() = if (reducedMotion) initialScale else scaleXAnimation.value
    val scaleY: Float get() = if (reducedMotion) initialScale else scaleYAnimation.value
    val velocity: Float get() = if (reducedMotion) 0f else velocityAnimation.value
    val positionVelocity: Float get() = if (reducedMotion) 0f else valueAnimation.velocity

    fun setReducedMotion(reduced: Boolean, selectedValue: Float = targetValue) {
        reducedMotion = reduced
        if (reduced) snapToValue(selectedValue)
    }

    private fun snapToValue(value: Float) {
        requestedValue = value.coerceIn(valueRange)
        interactionJob?.cancel()
        valueJob?.cancel()
        velocityJob?.cancel()
        interactionJob = animationScope.launch {
            valueAnimation.snapTo(requestedValue)
            velocityAnimation.snapTo(0f)
            pressProgressAnimation.snapTo(0f)
            scaleXAnimation.snapTo(initialScale)
            scaleYAnimation.snapTo(initialScale)
        }
    }

    val modifier: Modifier = Modifier.pointerInput(Unit) {
        inspectDragGestures(
            onDragStart = { down ->
                gestureStartValue = targetValue
                press()
                onDragStarted(down.position)
            },
            onDragEnd = {
                onDragStopped()
                release()
            },
            onDragCancel = {
                animateToValue(gestureStartValue)
                onDragCancelled()
            }
        ) { change, dragAmount ->
            onDrag(size, dragAmount)
        }
    }

    fun press() {
        if (reducedMotion) return
        interactionJob?.cancel()
        valueJob?.cancel()
        velocityTracker.resetTracking()
        interactionJob = animationScope.launch {
            launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
            launch { scaleXAnimation.animateTo(pressedScale, pressScaleAnimationSpec ?: scaleXAnimationSpec) }
            launch { scaleYAnimation.animateTo(pressedScale, pressScaleAnimationSpec ?: scaleYAnimationSpec) }
        }
    }

    fun release() {
        if (reducedMotion) { snapToValue(targetValue); return }
        interactionJob?.cancel()
        // A second press can interrupt the position spring. Resume its target before waiting
        // for the release gate, otherwise the gate waits forever on a stationary midpoint.
        valueJob?.cancel()
        valueJob = animationScope.launch {
            valueAnimation.animateTo(targetValue, settleAnimationSpec) { updateVelocity() }
            settleVelocity()
        }
        interactionJob = animationScope.launch {
            awaitReleaseGate()
            startReleaseAnimations(this)
        }
    }

    fun updateValue(value: Float) {
        val targetValue = value.coerceIn(valueRange)
        requestedValue = targetValue
        if (reducedMotion) { snapToValue(targetValue); return }
        valueJob?.cancel()
        valueJob = animationScope.launch {
            valueAnimation.animateTo(targetValue, directManipulationSpec) { updateVelocity() }
            settleVelocity()
        }
    }

    fun animateToValue(value: Float) {
        val target = value.coerceIn(valueRange)
        requestedValue = target
        if (reducedMotion) { snapToValue(target); return }
        interactionJob?.cancel()
        valueJob?.cancel()
        interactionJob = animationScope.launch {
            mutatorMutex.mutate {
                // 结构化会话：增亮与位移并行推进，褪光协程等 value 走过大半程就启动，
                // 于是"褪光"与"位移"是重叠的，而不是等位移完全结束才回弹。
                coroutineScope {
                    launch { pressProgressAnimation.animateTo(1f, pressProgressAnimationSpec) }
                    launch { scaleXAnimation.animateTo(pressedScale, pressScaleAnimationSpec ?: scaleXAnimationSpec) }
                    launch { scaleYAnimation.animateTo(pressedScale, pressScaleAnimationSpec ?: scaleYAnimationSpec) }
                    launch {
                        // value 被另一路 animateTo 抢占是正常竞争，必须就地消化：
                        // 一旦让它逃逸出去，会连坐取消同作用域的褪光协程，
                        // 控件就永久卡在按下态（放大 + 表面透明）。
                        try {
                            valueAnimation.animateTo(target, settleAnimationSpec) {
                                updateVelocity()
                            }
                            settleVelocity()
                        } catch (_: CancellationException) {
                            currentCoroutineContext().ensureActive()
                        }
                    }
                    launch {
                        awaitReleaseGate()
                        startReleaseAnimations(this)
                    }
                }
            }
        }
    }

    /** 滑动过大半程即开始褪光缩小，避免全亮白环拖出长残影。 */
    private suspend fun awaitReleaseGate() {
        withFrameNanos { }
        if (value != targetValue) {
            val threshold = maxOf(visibilityThreshold, (valueRange.endInclusive - valueRange.start) * 0.15f)
            snapshotFlow { valueAnimation.value }
                .filter { abs(it - targetValue) <= threshold }
                .first()
        }
    }

    private fun startReleaseAnimations(scope: CoroutineScope) {
        scope.launch { pressProgressAnimation.animateTo(0f, pressProgressAnimationSpec) }
        scope.launch { scaleXAnimation.animateTo(initialScale, releaseScaleAnimationSpec) }
        scope.launch { scaleYAnimation.animateTo(initialScale, releaseScaleAnimationSpec) }
    }

    private fun updateVelocity() {
        val valueSpan = valueRange.endInclusive - valueRange.start
        if (valueSpan <= 0f) {
            animationScope.launch { velocityAnimation.snapTo(0f) }
            return
        }
        velocityTracker.addPosition(
            SystemClock.uptimeMillis(),
            Offset(value, 0f)
        )
        val targetVelocity = velocityTracker.calculateVelocity().x / valueSpan
        velocityJob?.cancel()
        velocityJob = animationScope.launch { velocityAnimation.animateTo(targetVelocity, velocityAnimationSpec) }
    }

    private fun settleVelocity() {
        velocityJob?.cancel()
        velocityJob = animationScope.launch { velocityAnimation.animateTo(0f, velocityAnimationSpec) }
    }
}
