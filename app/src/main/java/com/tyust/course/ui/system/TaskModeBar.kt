package com.tyust.course.ui.system

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

/** Matching stays in the liquid selector; timing opens a separate settings action. */
@Composable
fun TaskModeBar(
    fuzzy: Boolean,
    scheduled: Boolean,
    enabled: Boolean,
    onFuzzyChange: ((Boolean) -> Unit)?,
    onScheduleClick: (() -> Unit)?,
    modifier: Modifier = Modifier
) {
    if (onFuzzyChange == null && onScheduleClick == null) return
    val colors = MaterialTheme.colorScheme
    val fontScale = LocalDensity.current.fontScale
    Row(
        modifier.fillMaxWidth().heightIn(min = 48.dp).testTag("task-mode-bar"),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onFuzzyChange != null) {
            LiquidSegmentedControl(
                options = listOf("精确执行", "模糊监控"),
                selectedIndex = if (fuzzy) 1 else 0,
                onSelect = { onFuzzyChange(it == 1) },
                modifier = Modifier.weight(1f).testTag("task-matching-selector"),
                enabled = enabled && !scheduled,
                height = 48.dp,
                labelContent = { index, selection, color ->
                    val monitor = index == 1
                    val label = if (fontScale > 1.3f) {
                        if (monitor) "监控" else "精确"
                    } else {
                        if (monitor) "模糊监控" else "精确执行"
                    }
                    Row(
                        Modifier.padding(horizontal = 4.dp).clearAndSetSemantics {},
                        horizontalArrangement = Arrangement.spacedBy(5.dp, Alignment.CenterHorizontally),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        AnimatedLineIcon(
                            if (monitor) AnimatedIconSpec.ScanLock else AnimatedIconSpec.Lock,
                            Modifier.size(16.dp),
                            sharedProgress = if (monitor) 1f else null,
                            tint = color
                        )
                        Text(label, style = MaterialTheme.typography.labelSmall,
                            fontWeight = if (selection >= 0.55f) FontWeight.SemiBold else FontWeight.Medium,
                            color = color, maxLines = 1, softWrap = false)
                    }
                }
            )
        } else {
            Row(Modifier.weight(1f).padding(horizontal = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                AnimatedLineIcon(AnimatedIconSpec.Courses, Modifier.size(17.dp), tint = colors.primary)
                Text("队列执行", style = MaterialTheme.typography.labelMedium, color = colors.onSurface)
            }
        }
        if (onScheduleClick != null) {
            LiquidButton(
                onClick = onScheduleClick,
                enabled = enabled,
                modifier = Modifier.width(74.dp * fontScale.coerceIn(1f, 1.2f)).height(48.dp)
                    .semantics {
                        contentDescription = "设置定时任务"
                        stateDescription = if (scheduled) "已设置定时" else "未设置定时"
                    }.testTag("task-mode-timing"),
                style = LiquidButtonStyle.Surface,
                contentColor = if (scheduled) colors.primary else colors.onSurface,
                horizontalPadding = 8.dp
            ) {
                AnimatedLineIcon(AnimatedIconSpec.Clock, Modifier.size(16.dp),
                    state = if (scheduled) IconVisualState.Selected else IconVisualState.Idle)
                Text("定时", Modifier.clearAndSetSemantics {},
                    style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold,
                    maxLines = 1, softWrap = false)
            }
        }
    }
}
