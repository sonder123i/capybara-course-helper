package com.k2767.course.ui.system

import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.k2767.course.ui.theme.MotionProfile

@Composable
fun AnimatedValueText(value: String, modifier: Modifier = Modifier,
    color: Color = MaterialTheme.colorScheme.onSurface, style: TextStyle = MaterialTheme.typography.bodyMedium) {
    val reduced = rememberGlassAccessibilityMode().reduceMotion
    AnimatedContent(value, modifier, transitionSpec = {
        if (reduced) EnterTransition.None togetherWith ExitTransition.None
        else ((fadeIn(tween(MotionProfile.IconMillis)) + slideInVertically(tween(MotionProfile.IconMillis)) { it / 2 }) togetherWith
            (fadeOut(tween(MotionProfile.PressMillis)) + slideOutVertically(tween(MotionProfile.IconMillis)) { -it / 2 }))
            .using(SizeTransform(clip = false))
    }, label = "value-change") { Text(it, color = color, style = style, maxLines = 1) }
}

@Composable
fun FilterCountBadge(count: Int, modifier: Modifier = Modifier) {
    val reduced = rememberGlassAccessibilityMode().reduceMotion
    AnimatedVisibility(count > 0, modifier, enter = if (reduced) EnterTransition.None else
        fadeIn(tween(MotionProfile.PressMillis)) + scaleIn(MotionProfile.iconSpring(), initialScale = 0.65f),
        exit = if (reduced) ExitTransition.None else fadeOut(tween(MotionProfile.PressMillis)) + scaleOut(targetScale = 0.65f)) {
        Box(Modifier.testTag("filter-count-badge")
            .semantics { stateDescription = count.toString() + " 个筛选条件已生效" }
            .defaultMinSize(minWidth = 16.dp, minHeight = 16.dp)
            .clip(RoundedCornerShape(50)).background(MaterialTheme.colorScheme.primary)
            .padding(horizontal = 4.dp, vertical = 1.dp), contentAlignment = Alignment.Center) {
            AnimatedValueText(if (count > 9) "9+" else count.toString(),
                color = MaterialTheme.colorScheme.onPrimary,
                style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp, lineHeight = 12.sp, fontWeight = FontWeight.Bold))
        }
    }
}
