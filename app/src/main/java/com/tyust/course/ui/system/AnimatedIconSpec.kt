package com.tyust.course.ui.system

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import android.graphics.PathMeasure
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.tyust.course.ui.theme.MotionProfile
import kotlinx.coroutines.isActive
import kotlin.math.PI
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.cos
import kotlin.math.sin

/** Shared 24-unit, round-ended line art. Geometry never changes the measured touch target. */
enum class AnimatedIconSpec {
    Courses, Calendar, Target, Grades, Settings, Filter, Share, Refresh,
    PlayStop, Request, Edit, Delete, Undo, Add, AddCheck, Close, Chevron, ChevronUp, Back, Forward,
    Eye, Bell, Clock, Location, Person, Check, Search, Pause, More, Warning, Lock, Expand,
    ActionFan, ScanLock, Log
}

enum class IconVisualState { Idle, Selected, Expanded, Running, Success, Failure, Paused }

@Composable
fun AnimatedLineIcon(
    spec: AnimatedIconSpec,
    modifier: Modifier = Modifier,
    state: IconVisualState = IconVisualState.Idle,
    description: String? = null,
    tint: Color = LocalContentColor.current,
    event: Int = 0,
    sharedProgress: Float? = null
) {
    val reduced = rememberGlassAccessibilityMode().reduceMotion
    val target = when (state) {
        IconVisualState.Selected, IconVisualState.Expanded, IconVisualState.Running,
        IconVisualState.Success, IconVisualState.Failure, IconVisualState.Paused -> 1f
        else -> 0f
    }
    val selection by animateFloatAsState(target, if (reduced) snap() else MotionProfile.iconSpring(), label = "line-icon-state")
    val success by animateFloatAsState(if (state == IconVisualState.Success) 1f else 0f,
        if (reduced) snap() else tween(MotionProfile.IconMillis), label = "line-icon-success")
    val failure by animateFloatAsState(if (state == IconVisualState.Failure) 1f else 0f,
        if (reduced) snap() else tween(MotionProfile.IconMillis), label = "line-icon-failure")
    val busy by animateFloatAsState(if (state == IconVisualState.Running) 1f else 0f,
        if (reduced) snap() else tween(MotionProfile.IconMillis), label = "line-icon-busy")
    val eventProgress = remember { Animatable(1f) }
    LaunchedEffect(event, reduced) {
        if (reduced) eventProgress.snapTo(1f)
        else if (event > 0) {
            eventProgress.animateTo(0.15f, tween(60))
            eventProgress.animateTo(1f, MotionProfile.iconSpring())
        }
    }
    val rotation = remember { Animatable(0f) }
    LaunchedEffect(state == IconVisualState.Running, reduced, spec) {
        if (reduced) rotation.snapTo(0f)
        else if (state == IconVisualState.Running && (spec == AnimatedIconSpec.Refresh || spec == AnimatedIconSpec.Request || spec == AnimatedIconSpec.Expand)) {
            while (isActive) rotation.animateTo(rotation.value + 360f, tween(850, easing = LinearEasing))
        } else {
            rotation.animateTo(ceil(rotation.value / 360f) * 360f, tween(MotionProfile.IconMillis))
            rotation.snapTo(0f)
        }
    }
    val path = remember { Path() }
    val segment = remember { Path() }
    val measure = remember { PathMeasure() }
    Canvas(modifier.size(24.dp).then(if (description != null) Modifier.semantics { contentDescription = description } else Modifier)) {
        val p = (sharedProgress ?: selection).coerceIn(0f, 1f)
        val eventP = if (reduced || !eventProgress.isRunning) 1f else eventProgress.value.coerceIn(0f, 1f)
        val pulse = if (reduced) 0f else sin((if (eventP < 1f) eventP else p) * PI.toFloat())
        scale(size.width / 24f, size.height / 24f, Offset.Zero) {
            drawLineGlyph(spec, p, pulse, eventP, rotation.value, success, failure, busy, tint, path, segment, measure)
        }
    }
}

/** Kept independent of composition so preview frames and real controls use identical paths. */
internal fun DrawScope.drawLineGlyph(
    spec: AnimatedIconSpec, progress: Float, pulse: Float, event: Float,
    rotation: Float, success: Float, failure: Float, busy: Float, color: Color,
    path: Path, segment: Path, measure: PathMeasure
) {
    val stroke = Stroke(1.8f, cap = StrokeCap.Round, join = StrokeJoin.Round)
    fun line(x1: Float, y1: Float, x2: Float, y2: Float, alpha: Float = 1f) =
        drawLine(color, Offset(x1, y1), Offset(x2, y2), 1.8f, StrokeCap.Round, alpha = alpha)
    fun circle(x: Float, y: Float, radius: Float, alpha: Float = 1f) =
        drawCircle(color, radius, Offset(x, y), alpha = alpha, style = stroke)
    fun box(x: Float, y: Float, width: Float, height: Float, radius: Float = 2f) =
        drawRoundRect(color, Offset(x, y), Size(width, height), CornerRadius(radius), style = stroke)
    fun outline(reveal: Float = 1f, alpha: Float = 1f, build: Path.() -> Unit) {
        path.reset(); path.build()
        if (reveal >= 0.999f) drawPath(path, color, alpha, stroke)
        else if (reveal > 0f) {
            // A glyph can have several pen lifts. Measure every contour so a share
            // arrow or undo curve never pops in only at the last frame.
            measure.setPath(path.asAndroidPath(), false)
            var length = 0f
            do { length += measure.length } while (measure.nextContour())
            var remaining = length * reveal
            measure.setPath(path.asAndroidPath(), false); segment.reset()
            do {
                measure.getSegment(0f, minOf(remaining, measure.length), segment.asAndroidPath(), true)
                remaining -= measure.length
            } while (remaining > 0f && measure.nextContour())
            drawPath(segment, color, alpha, stroke)
        }
    }
    val p = progress
    val drawIn = if (event < 1f) 0.35f + 0.65f * event else 1f
    when (spec) {
        AnimatedIconSpec.Courses -> repeat(3) { row ->
            val t = (p * 1.6f - row * 0.3f).coerceIn(0f, 1f)
            circle(4.5f, 6f + 6f * row, 0.8f + 0.25f * t)
            line(8.5f, 6f + 6f * row, 17.5f + 2.5f * t, 6f + 6f * row)
        }
        AnimatedIconSpec.Calendar -> {
            box(3.5f, 5f - pulse * 0.5f, 17f, 16f, 3f)
            line(7.5f, 3f - pulse, 7.5f, 7f); line(16.5f, 3f - pulse, 16.5f, 7f)
            line(4f, 10f, 20f, 10f)
            repeat(3) { column ->
                val t = (p * 1.6f - column * 0.3f).coerceIn(0f, 1f)
                line(7f + column * 5f, 14f, 7f + column * 5f + 1.2f * t, 14f)
                line(7f + column * 5f, 17.5f, 7f + column * 5f + 1.2f * t, 17.5f, 0.65f + 0.35f * t)
            }
        }
        AnimatedIconSpec.Target -> {
            circle(12f, 12f, 7f - pulse * 0.7f); circle(12f, 12f, 2f + p)
            line(12f, 2f, 12f, 5f); line(19f, 12f, 22f, 12f)
            line(12f, 19f, 12f, 22f); line(2f, 12f, 5f, 12f)
        }
        AnimatedIconSpec.Grades -> {
            val heights = floatArrayOf(8f, 15f, 11f)
            repeat(3) { i ->
                val t = (p * 1.6f - i * 0.3f).coerceIn(0f, 1f)
                val height = heights[i] * (0.6f + 0.4f * t)
                box(4f + i * 6f, 21f - height, 3.5f, height, 1.4f)
            }
        }
        AnimatedIconSpec.Settings -> rotate(26f * pulse, Offset(12f, 12f)) {
            outline {
                repeat(48) { point ->
                    val angle = point * PI.toFloat() / 24f
                    val r = if (point % 6 in 1..3) 9f else 7.3f
                    val x = 12f + r * cos(angle); val y = 12f + r * sin(angle)
                    if (point == 0) moveTo(x, y) else lineTo(x, y)
                }; close()
            }
            circle(12f, 12f, 3f)
        }
        AnimatedIconSpec.Filter -> repeat(3) { i ->
            val x = when (i) { 0 -> 8f + 7f * p; 1 -> 16f - 8f * p; else -> 10f + 4f * p }
            val y = 5.5f + i * 6.5f
            line(3f, y, x - 2.3f, y); line(x + 2.3f, y, 21f, y)
            circle(x, y, 2.2f)
        }
        AnimatedIconSpec.Share -> {
            outline { moveTo(7f, 10f); lineTo(5f, 10f); lineTo(5f, 20f); lineTo(19f, 20f); lineTo(19f, 10f); lineTo(17f, 10f) }
            outline(drawIn) { moveTo(12f, 15f); lineTo(12f, 3f - pulse); moveTo(8f, 7f - pulse); lineTo(12f, 3f - pulse); lineTo(16f, 7f - pulse) }
        }
        AnimatedIconSpec.Refresh, AnimatedIconSpec.Request -> {
            val remaining = (1f - success) * (1f - failure)
            rotate(rotation + if (event < 1f) 360f * event else 0f, Offset(12f, 12f)) {
                drawArc(color, 42f, 284f, false, Offset(4f, 4f), Size(16f, 16f), remaining, stroke)
                outline(alpha = remaining) { moveTo(20f, 3f); lineTo(20f, 9f); lineTo(14f, 9f) }
            }
            outline(success, success) { moveTo(5f, 12f); lineTo(10f, 17f); lineTo(20f, 6f) }
            outline(failure, failure) { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) }
        }
        AnimatedIconSpec.PlayStop -> outline {
            moveTo(8f - 2f * p, 4.5f + 1.5f * p)
            lineTo(19f - p, 12f - 6f * p)
            lineTo(8f + 10f * p, 19.5f - 1.5f * p)
            lineTo(8f - 2f * p, 12f + 6f * p); close()
        }
        AnimatedIconSpec.Edit -> rotate(-8f * pulse, Offset(12f, 12f)) {
            outline(drawIn) { moveTo(4f, 20f); lineTo(5f, 15f); lineTo(16.5f, 3.5f); lineTo(20.5f, 7.5f); lineTo(9f, 19f); close(); moveTo(14f, 6f); lineTo(18f, 10f) }
            line(12f, 21f, 20f, 21f)
        }
        AnimatedIconSpec.Delete -> {
            outline { moveTo(6f, 8f); lineTo(7f, 21f); lineTo(17f, 21f); lineTo(18f, 8f); moveTo(10f, 11f); lineTo(10.5f, 17f); moveTo(14f, 11f); lineTo(13.5f, 17f) }
            rotate(-18f * pulse, Offset(5f, 6f)) {
                line(3.5f, 6f - pulse, 20.5f, 6f - pulse)
                outline { moveTo(9f, 5f - pulse); lineTo(9f, 3f - pulse); lineTo(15f, 3f - pulse); lineTo(15f, 5f - pulse) }
            }
        }
        AnimatedIconSpec.Undo -> outline(drawIn) {
            moveTo(4f, 5f); lineTo(4f, 11f); lineTo(10f, 11f); moveTo(4f, 11f)
            cubicTo(10f, 1f, 23f, 7f, 20f, 16f); cubicTo(18f, 21f, 11f, 22f, 7f, 18f)
        }
        AnimatedIconSpec.Add -> rotate(90f * pulse, Offset(12f, 12f)) {
            line(4f, 12f, 20f, 12f); line(12f, 4f, 12f, 20f)
        }
        AnimatedIconSpec.AddCheck -> {
            fun blend(start: Float, end: Float) = start + (end - start) * success
            line(5f, 12f, blend(19f, 10f), blend(12f, 17f), 1f - failure)
            line(blend(12f, 10f), blend(5f, 17f), blend(12f, 20f), blend(19f, 6f), 1f - failure)
            outline(failure, failure) { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) }
        }
        AnimatedIconSpec.Close -> rotate(90f * pulse, Offset(12f, 12f)) {
            line(6f, 6f, 18f, 18f); line(18f, 6f, 6f, 18f)
        }
        AnimatedIconSpec.Chevron -> rotate(180f * p, Offset(12f, 12f)) {
            translate(top = if (event < 1f) 2f * pulse else 0f) {
                outline { moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f) }
            }
        }
        AnimatedIconSpec.Expand -> {
            rotate(180f * p, Offset(12f, 12f)) {
                outline(alpha = 1f - busy) { moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f) }
            }
            rotate(rotation, Offset(12f, 12f)) {
                drawArc(color, 42f, 284f, false, Offset(4f, 4f), Size(16f, 16f), busy, stroke)
            }
        }
        AnimatedIconSpec.ChevronUp -> rotate(180f - 180f * p, Offset(12f, 12f)) {
            translate(top = if (event < 1f) 2f * pulse else 0f) {
                outline { moveTo(6f, 9f); lineTo(12f, 15f); lineTo(18f, 9f) }
            }
        }
        AnimatedIconSpec.Back -> outline(drawIn) { moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f) }
        AnimatedIconSpec.Forward -> outline(drawIn) { moveTo(9f, 5f); lineTo(16f, 12f); lineTo(9f, 19f) }
        AnimatedIconSpec.Eye -> {
            val opening = 4f + 3f * p
            outline { moveTo(2f, 12f); cubicTo(7f, 12f - opening, 17f, 12f - opening, 22f, 12f); cubicTo(17f, 12f + opening, 7f, 12f + opening, 2f, 12f) }
            circle(12f, 12f, 2.5f)
            line(4f, 4f, 4f + 16f * (1f - p), 4f + 16f * (1f - p), 1f - p)
        }
        AnimatedIconSpec.Bell -> rotate(12f * pulse, Offset(12f, 5f)) {
            outline { moveTo(5f, 17f); lineTo(7f, 14f); lineTo(7f, 9f); cubicTo(7f, 2f, 17f, 2f, 17f, 9f); lineTo(17f, 14f); lineTo(19f, 17f); close() }
            drawArc(color, 10f, 160f, false, Offset(9f, 17f), Size(6f, 5f), style = stroke)
            line(12f, 2f, 12f, 3f)
        }
        AnimatedIconSpec.Clock -> {
            circle(12f, 12f, 9f)
            rotate(pulse * 38f, Offset(12f, 12f)) { line(12f, 6f, 12f, 12f); line(12f, 12f, 16f, 14f) }
        }
        AnimatedIconSpec.Location -> {
            outline { moveTo(12f, 22f); cubicTo(8f, 17f, 5f, 13f, 5f, 9f); cubicTo(5f, 0f, 19f, 0f, 19f, 9f); cubicTo(19f, 13f, 16f, 17f, 12f, 22f); close() }
            circle(12f, 9f, 2.5f)
        }
        AnimatedIconSpec.Person -> { circle(12f, 7f, 3.5f); outline { moveTo(4f, 21f); cubicTo(4f, 11f, 20f, 11f, 20f, 21f) } }
        AnimatedIconSpec.Check -> outline(drawIn) { moveTo(5f, 12f); lineTo(10f, 17f); lineTo(20f, 6f) }
        AnimatedIconSpec.Search -> { circle(10.5f, 10.5f, 6.5f); line(15.5f, 15.5f, 21f - pulse, 21f - pulse) }
        AnimatedIconSpec.Pause -> { line(8f, 5f, 8f, 19f); line(16f, 5f, 16f, 19f) }
        AnimatedIconSpec.More -> repeat(3) { circle(5f + it * 7f, 12f - pulse * sin(it * PI.toFloat() / 2f), 0.8f) }
        AnimatedIconSpec.ActionFan -> {
            val remaining = 1f - p
            circle(12f, 17f, 2f * remaining, remaining)
            repeat(3) { index ->
                val angle = PI * (0.2 + index * 0.3)
                circle(12f + cos(angle).toFloat() * (8f + 2f * p), 17f - sin(angle).toFloat() * (9f + 3f * p), 1.3f, remaining)
            }
            outline(p) { moveTo(6f, 6f); lineTo(18f, 18f); moveTo(18f, 6f); lineTo(6f, 18f) }
        }
        AnimatedIconSpec.ScanLock -> {
            outline(alpha = 1f - p) { moveTo(5f, 11f); lineTo(19f, 11f); lineTo(19f, 21f); lineTo(5f, 21f); close()
                moveTo(8f, 11f); lineTo(8f, 6f); cubicTo(8f, 1f, 16f, 1f, 16f, 6f); lineTo(16f, 11f) }
            outline(p, p) {
                moveTo(3f, 8f); lineTo(3f, 3f); lineTo(8f, 3f); moveTo(16f, 3f); lineTo(21f, 3f); lineTo(21f, 8f)
                moveTo(21f, 16f); lineTo(21f, 21f); lineTo(16f, 21f); moveTo(8f, 21f); lineTo(3f, 21f); lineTo(3f, 16f)
            }
            line(12f - 5f * p, 15f - 3f * p, 12f + 5f * p, 17f - 5f * p)
            circle(12f, 12f, 4f * p, p * 0.45f)
        }
        AnimatedIconSpec.Log -> {
            outline(drawIn) { moveTo(6f, 3f); lineTo(15f, 3f); lineTo(20f, 8f); lineTo(20f, 21f); lineTo(4f, 21f); lineTo(4f, 3f); close()
                moveTo(15f, 3f); lineTo(15f, 8f); lineTo(20f, 8f) }
            repeat(3) { index ->
                val reveal = (p * 1.5f - index * 0.25f).coerceIn(0f, 1f)
                line(8f, 11f + index * 3.5f, 13f + reveal * 3f, 11f + index * 3.5f)
            }
        }
        AnimatedIconSpec.Warning -> { outline { moveTo(12f, 3f); lineTo(22f, 21f); lineTo(2f, 21f); close() }; line(12f, 9f, 12f, 14f); line(12f, 17.5f, 12f, 17.6f) }
        AnimatedIconSpec.Lock -> {
            box(5f, 10f, 14f, 11f, 2f)
            outline { moveTo(8f, 10f); lineTo(8f, 6f); cubicTo(8f, 1f, 16f, 1f, 16f, 6f); lineTo(16f, 10f) }
            line(12f, 14f, 12f, 17f)
        }
    }
}
