package com.tyust.course.ui.system

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.material3.LocalContentColor
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import kotlinx.coroutines.isActive
import kotlin.math.ceil

enum class SymbolResult { None, Success, Failure }

@Composable
fun QueueStateSymbol(index: Int, running: Boolean, result: SymbolResult, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    val reduced = rememberGlassAccessibilityMode().reduceMotion
    val active = animateFloatAsState(if (running || result != SymbolResult.None) 1f else 0f,
        if (reduced) snap() else tween(240), label = "queuePresence")
    Box(modifier.size(24.dp).semantics {
        contentDescription = when {
            running -> "正在执行"
            result == SymbolResult.Success -> "执行成功"
            result == SymbolResult.Failure -> "执行失败"
            else -> "等待执行"
        }
    }, contentAlignment = Alignment.Center) {
        Text("${index + 1}", Modifier.graphicsLayer { alpha = 1f - active.value }, color = tint)
        RequestStateSymbol(running, result, modifier = Modifier.graphicsLayer { alpha = active.value }, tint = tint)
    }
}

@Composable
fun VisibilitySymbol(revealed: Boolean, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    val reduced = rememberGlassAccessibilityMode().reduceMotion
    val progress = animateFloatAsState(if (revealed) 1f else 0f, if (reduced) snap() else tween(240), label = "eye")
    val path = remember { Path() }
    Canvas(modifier.size(24.dp).semantics { contentDescription = if (revealed) "隐藏密码" else "显示密码" }) {
        scale(size.width / 24f, size.height / 24f, Offset.Zero) {
            val p = progress.value
            val opening = 3f + 4f * p
            path.reset()
            path.moveTo(2f, 12f)
            path.cubicTo(7f, 12f - opening, 17f, 12f - opening, 22f, 12f)
            path.cubicTo(17f, 12f + opening, 7f, 12f + opening, 2f, 12f)
            drawPath(path, tint, style = Stroke(1.8f, cap = StrokeCap.Round))
            drawCircle(tint, 2.5f, Offset(12f, 12f), style = Stroke(1.8f), alpha = 0.3f + 0.7f * p)
            if (p < 1f) drawLine(tint, Offset(4f, 4f), Offset(4f + 16f * (1f - p), 4f + 16f * (1f - p)), 1.8f, StrokeCap.Round)
        }
    }
}

@Composable
fun RunStateSymbol(running: Boolean, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    val reduced = rememberGlassAccessibilityMode().reduceMotion
    val progress = animateFloatAsState(if (running) 1f else 0f, if (reduced) snap() else tween(240), label = "runState")
    val path = remember { Path() }
    Canvas(modifier.size(24.dp)) {
        scale(size.width / 24f, size.height / 24f, Offset.Zero) {
            val p = progress.value
            path.reset()
            path.moveTo(8f - 2f * p, 5f + p)
            path.lineTo(19f - p, 12f - 6f * p)
            path.lineTo(8f + 10f * p, 19f - p)
            path.lineTo(8f - 2f * p, 12f + 6f * p)
            path.close()
            drawPath(path, tint)
        }
    }
}

@Composable
fun RequestStateSymbol(
    running: Boolean = false,
    result: SymbolResult = SymbolResult.None,
    queue: Boolean = false,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    val reduced = rememberGlassAccessibilityMode().reduceMotion
    val rotation = remember { Animatable(0f) }
    val success = animateFloatAsState(if (result == SymbolResult.Success) 1f else 0f,
        if (reduced) snap() else tween(240), label = "requestSuccess")
    val failure = animateFloatAsState(if (result == SymbolResult.Failure) 1f else 0f,
        if (reduced) snap() else tween(240), label = "requestFailure")
    LaunchedEffect(running, reduced) {
        if (running && !reduced) {
            while (isActive) rotation.animateTo(rotation.value + 360f, tween(900, easing = LinearEasing))
        } else if (reduced) rotation.snapTo(0f)
        else {
            rotation.animateTo(ceil(rotation.value / 360f) * 360f, tween(220))
            rotation.snapTo(0f)
        }
    }
    Canvas(modifier.size(24.dp)) {
        scale(size.width / 24f, size.height / 24f, Offset.Zero) {
            val p = success.value
            val f = failure.value
            if (queue) {
                fun point(x: Float, y: Float, endX: Float, endY: Float) = Offset(x + (endX - x) * p, y + (endY - y) * p)
                drawLine(tint, point(5f, 12f, 5f, 12f), point(19f, 12f, 10f, 17f), 1.9f, StrokeCap.Round)
                drawLine(tint, point(12f, 5f, 10f, 17f), point(12f, 19f, 20f, 6f), 1.9f, StrokeCap.Round)
            } else {
                rotate(rotation.value, Offset(12f, 12f)) {
                    drawArc(tint, 40f, 290f, false, Offset(4f, 4f), Size(16f, 16f),
                        alpha = (1f - p) * (1f - f), style = Stroke(1.8f, cap = StrokeCap.Round))
                    drawLine(tint, Offset(20f, 3f), Offset(20f, 9f), 1.8f, StrokeCap.Round, alpha = (1f - p) * (1f - f))
                    drawLine(tint, Offset(20f, 9f), Offset(14f, 9f), 1.8f, StrokeCap.Round, alpha = (1f - p) * (1f - f))
                }
                if (p > 0f) {
                    drawLine(tint, Offset(5f, 12f), Offset(5f + 5f * p, 12f + 5f * p), 1.9f, StrokeCap.Round, alpha = p)
                    drawLine(tint, Offset(10f, 17f), Offset(10f + 10f * p, 17f - 11f * p), 1.9f, StrokeCap.Round, alpha = p)
                }
                if (f > 0f) {
                    drawLine(tint, Offset(6f, 6f), Offset(6f + 12f * f, 6f + 12f * f), 1.9f, StrokeCap.Round, alpha = f)
                    drawLine(tint, Offset(18f, 6f), Offset(18f - 12f * f, 6f + 12f * f), 1.9f, StrokeCap.Round, alpha = f)
                }
            }
        }
    }
}
