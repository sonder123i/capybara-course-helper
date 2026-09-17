package com.k2767.course.ui.system

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.snap
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.k2767.course.ui.theme.MotionProfile

enum class SymbolResult { None, Success, Failure }

@Composable
fun QueueStateSymbol(index: Int, running: Boolean, result: SymbolResult, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    val reduced = rememberGlassAccessibilityMode().reduceMotion
    val active by animateFloatAsState(if (running || result != SymbolResult.None) 1f else 0f,
        if (reduced) snap() else MotionProfile.iconSpring(), label = "queue-state")
    Box(modifier.size(24.dp).semantics {
        contentDescription = when {
            running -> "正在执行"
            result == SymbolResult.Success -> "执行成功"
            result == SymbolResult.Failure -> "执行失败"
            else -> "等待执行"
        }
    }, contentAlignment = Alignment.Center) {
        Text((index + 1).toString(), Modifier.graphicsLayer {
            alpha = 1f - active.coerceIn(0f, 1f)
            translationY = -6.dp.toPx() * active
        }.clearAndSetSemantics {}, color = tint)
        RequestStateSymbol(running, result, modifier = Modifier.graphicsLayer {
            alpha = active.coerceIn(0f, 1f)
        }, tint = tint)
    }
}

@Composable
fun VisibilitySymbol(revealed: Boolean, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    AnimatedLineIcon(AnimatedIconSpec.Eye, modifier,
        if (revealed) IconVisualState.Selected else IconVisualState.Idle,
        description = if (revealed) "隐藏密码" else "显示密码", tint = tint)
}

@Composable
fun RunStateSymbol(running: Boolean, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    AnimatedLineIcon(AnimatedIconSpec.PlayStop, modifier,
        if (running) IconVisualState.Running else IconVisualState.Idle, tint = tint)
}

@Composable
fun RequestStateSymbol(
    running: Boolean = false,
    result: SymbolResult = SymbolResult.None,
    queue: Boolean = false,
    modifier: Modifier = Modifier,
    tint: Color = LocalContentColor.current
) {
    AnimatedLineIcon(if (queue) AnimatedIconSpec.AddCheck else AnimatedIconSpec.Request, modifier,
        state = when {
            running -> IconVisualState.Running
            result == SymbolResult.Success -> IconVisualState.Success
            result == SymbolResult.Failure -> IconVisualState.Failure
            else -> IconVisualState.Idle
        }, tint = tint)
}