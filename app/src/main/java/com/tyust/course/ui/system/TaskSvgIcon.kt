package com.tyust.course.ui.system

import android.graphics.PathMeasure
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.asAndroidPath
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.vector.PathParser
import com.tyust.course.ui.theme.MotionProfile
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

private data class TaskSvgPaths(val frame: String, val detail: String, val accent: String = "")

private val taskSvgPaths = mapOf(
    AnimatedIconSpec.Add to TaskSvgPaths(
        "M6.5 5.5H16A2.5 2.5 0 0 1 18.5 8V18A2.5 2.5 0 0 1 16 20.5H6.5A2.5 2.5 0 0 1 4 18V8A2.5 2.5 0 0 1 6.5 5.5Z",
        "M8 13H14.5 M11.25 9.75V16.25",
        "M8 2.5H17A4.5 4.5 0 0 1 21.5 7V16"),
    AnimatedIconSpec.ScanLock to TaskSvgPaths(
        "M3 8V5Q3 3 5 3H8 M16 3H19Q21 3 21 5V8 M21 16V19Q21 21 19 21H16 M8 21H5Q3 21 3 19V16",
        "M18 12A6 6 0 1 1 6 12A6 6 0 1 1 18 12Z"),
    AnimatedIconSpec.Clock to TaskSvgPaths(
        "M20.5 13A8.5 8.5 0 1 1 3.5 13A8.5 8.5 0 1 1 20.5 13Z",
        "M12 7.5V13L16 15",
        "M9 1.5H15 M12 1.5V4 M18.5 5.5L20.5 3.5 M5.75 13H6.75 M12 18.25V19.25"),
    AnimatedIconSpec.Settings to TaskSvgPaths(
        "M6 3V6 M6 11V21 M12 3V13 M12 18V21 M18 3V7 M18 12V21", ""),
    AnimatedIconSpec.Log to TaskSvgPaths(
        "M6.5 3H14.5L19.5 8V19A2 2 0 0 1 17.5 21H6.5A2 2 0 0 1 4.5 19V5A2 2 0 0 1 6.5 3Z M14.5 3V8H19.5",
        "M8 11.5H16 M8 14.75H16 M8 18H12.5"),
    AnimatedIconSpec.Courses to TaskSvgPaths(
        "M6 5V4Q6 2.5 7.5 2.5H16.5Q18 2.5 18 4V5 M4.5 10V8.5Q4.5 7 6 7H18Q19.5 7 19.5 8.5V10",
        "M5 12H19Q21 12 21 14V19Q21 21 19 21H5Q3 21 3 19V14Q3 12 5 12Z",
        "M6.5 16.5H7 M10 16.5H17.5")
)

private const val taskLockPath =
    "M7 10H17Q19 10 19 12V19Q19 21 17 21H7Q5 21 5 19V12Q5 10 7 10Z M8 10V7A4 4 0 0 1 16 7V10"

/** Layered SVG strokes animate once on entry and respond to focus without an idle render loop. */
@Composable
internal fun TaskSvgIcon(
    spec: AnimatedIconSpec, reveal: Float, active: Boolean, tint: Color,
    modifier: Modifier = Modifier, modeProgress: Float? = null
) {
    val accessibility = rememberGlassAccessibilityMode()
    val reduced = accessibility.reduceMotion
    val accent = MaterialTheme.colorScheme.primary.copy(alpha = tint.alpha)
    val knobSurface = MaterialTheme.colorScheme.surface
    val focus by animateFloatAsState(if (active) 1f else 0f,
        if (reduced) snap() else MotionProfile.iconSpring(), label = "task-svg-focus")
    val mode by animateFloatAsState(modeProgress ?: 1f,
        if (reduced) snap() else MotionProfile.iconSpring(), label = "task-svg-mode")
    val paths = taskSvgPaths[spec]
    if (paths == null) {
        AnimatedLineIcon(spec, modifier, tint = tint,
            state = if (active) IconVisualState.Selected else IconVisualState.Idle,
            sharedProgress = modeProgress ?: reveal)
        return
    }
    val frame = remember(paths) { PathParser().parsePathString(paths.frame).toPath() }
    val detail = remember(paths) { PathParser().parsePathString(paths.detail).toPath() }
    val accentPath = remember(paths) { PathParser().parsePathString(paths.accent).toPath() }
    val lock = remember { PathParser().parsePathString(taskLockPath).toPath() }
    val segment = remember { Path() }
    val measure = remember { PathMeasure() }
    Canvas(modifier) {
        val amount = if (reduced) 1f else reveal.coerceIn(0f, 1f)
        val outlineAmount = (amount / 0.78f).coerceIn(0f, 1f)
        val detailAmount = ((amount - 0.22f) / 0.78f).coerceIn(0f, 1f)
        val pulse = if (reduced) 0f else
            sin(amount * PI.toFloat()) + 0.65f * sin(focus.coerceIn(0f, 1f) * PI.toFloat())
        val width = if (accessibility.highContrast) 2.05f else 1.7f
        val stroke = Stroke(width, cap = StrokeCap.Round, join = StrokeJoin.Round)
        val fill = accent.copy(alpha = accent.alpha * (0.08f + 0.12f * focus.coerceIn(0f, 1f)) * amount)
        fun traced(path: Path, progress: Float = outlineAmount, color: Color = tint, alpha: Float = 1f) {
            if (progress >= 0.999f) { drawPath(path, color, alpha = alpha, style = stroke); return }
            if (progress <= 0f) return
            measure.setPath(path.asAndroidPath(), false)
            var length = 0f
            do { length += measure.length } while (measure.nextContour())
            var remaining = length * progress
            measure.setPath(path.asAndroidPath(), false)
            segment.reset()
            do {
                measure.getSegment(0f, minOf(remaining, measure.length), segment.asAndroidPath(), true)
                remaining -= measure.length
            } while (remaining > 0f && measure.nextContour())
            drawPath(segment, color, alpha = alpha, style = stroke)
        }
        fun softBox(x: Float, y: Float, w: Float, h: Float) =
            drawRoundRect(fill, Offset(x, y), Size(w, h), CornerRadius(2f))
        scale(size.width / 24f, size.height / 24f, Offset.Zero) {
            when (spec) {
                AnimatedIconSpec.Add -> {
                    translate(left = pulse * 0.7f, top = -pulse) {
                        traced(accentPath, color = accent, alpha = 0.65f)
                    }
                    rotate(-5f * pulse, Offset(11f, 16f)) {
                        softBox(4f, 5.5f, 14.5f, 15f)
                        traced(frame)
                        rotate(90f * pulse, Offset(11.25f, 13f)) {
                            traced(detail, detailAmount, accent)
                        }
                    }
                }
                AnimatedIconSpec.ScanLock -> {
                    val radar = mode.coerceIn(0f, 1f)
                    if (radar > 0f) {
                        drawCircle(fill, 6f, Offset(12f, 12f), alpha = radar)
                        traced(frame, alpha = radar)
                        traced(detail, detailAmount, accent, radar * 0.55f)
                        val angle = (-45f + 270f * amount + 65f * pulse) * PI.toFloat() / 180f
                        drawLine(accent, Offset(12f, 12f),
                            Offset(12f + cos(angle) * 5f, 12f + sin(angle) * 5f),
                            width, StrokeCap.Round, alpha = detailAmount * radar)
                        drawCircle(accent, 1.3f, Offset(14.7f, 8.7f), alpha = detailAmount * radar)
                        drawCircle(tint, 1f, Offset(12f, 12f), alpha = detailAmount * radar)
                    }
                    if (radar < 1f) {
                        softBox(5f, 10f, 14f, 11f)
                        traced(lock, alpha = 1f - radar)
                        drawLine(accent, Offset(12f, 14f), Offset(12f, 17f), width, StrokeCap.Round,
                            alpha = detailAmount * (1f - radar))
                    }
                }
                AnimatedIconSpec.Clock -> {
                    drawCircle(fill, 8.5f, Offset(12f, 13f))
                    traced(frame)
                    traced(accentPath, color = accent)
                    rotate(40f * pulse, Offset(12f, 13f)) { traced(detail, detailAmount, accent) }
                    drawCircle(accent, 1.05f, Offset(12f, 13f), alpha = detailAmount)
                }
                AnimatedIconSpec.Settings -> {
                    traced(frame, color = tint.copy(alpha = tint.alpha * 0.72f))
                    fun slider(x: Float, y: Float) {
                        val top = Offset(x - 2.25f, y - 2f)
                        drawRoundRect(knobSurface, top, Size(4.5f, 4f), CornerRadius(1.5f), alpha = detailAmount)
                        drawRoundRect(accent, top, Size(4.5f, 4f), CornerRadius(1.5f),
                            alpha = detailAmount, style = stroke)
                    }
                    slider(6f, 8.5f + 1.7f * pulse)
                    slider(12f, 15.5f - 1.7f * pulse)
                    slider(18f, 9.5f + 1.4f * pulse)
                }
                AnimatedIconSpec.Log -> {
                    rotate(-4f * pulse, Offset(12f, 17f)) {
                        softBox(4.5f, 3f, 15f, 18f)
                        traced(frame)
                        traced(detail, detailAmount, accent)
                        drawLine(accent, Offset(15.5f, 17f), Offset(15.5f, 19f), width, StrokeCap.Round,
                            alpha = detailAmount * (0.45f + 0.55f * focus.coerceIn(0f, 1f)))
                    }
                }
                AnimatedIconSpec.Courses -> {
                    translate(top = -pulse * 0.7f) { traced(frame, color = accent, alpha = 0.65f) }
                    translate(top = pulse * 0.7f) {
                        softBox(3f, 12f, 18f, 9f)
                        traced(detail)
                        traced(accentPath, detailAmount, accent)
                    }
                }
                else -> Unit
            }
        }
    }
}
