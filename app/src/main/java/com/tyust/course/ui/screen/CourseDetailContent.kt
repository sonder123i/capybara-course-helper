package com.tyust.course.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.Orientation
import androidx.compose.foundation.gestures.draggable
import androidx.compose.foundation.gestures.rememberDraggableState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.tyust.course.schedule.ScheduleConflict
import com.tyust.course.ui.system.*

@Immutable
data class CourseDetailUiState(
    val course: ScheduleCourseUi,
    val conflicts: List<ScheduleConflict> = emptyList(),
    val reminderEnabled: Boolean = false,
    val reminderAvailable: Boolean = true,
    val reminderDescription: String = "未开启",
    val needsPermission: Boolean = false,
    val needsTime: Boolean = false,
    val invalidWeeks: Boolean = false,
    val sourceCenterX: Float? = null
)

/** Presentation-only surface; tests can exercise every reminder state without creating alarms. */
@Composable
fun CourseDetailContent(
    ui: CourseDetailUiState, sheet: ScheduleBottomSheetState, onClose: () -> Unit,
    onReminderChanged: (Boolean) -> Unit, onPermission: () -> Unit,
    onConfigureTime: () -> Unit, onEdit: () -> Unit, onDelete: () -> Unit
) {
    val colors = MaterialTheme.colorScheme
    val density = LocalDensity.current
    val reduced = rememberGlassAccessibilityMode().reduceMotion
    val progress = LocalDialogProgress.current
    val connection = remember(sheet, onClose) { sheet.nestedScroll(onClose) }
    SideEffect { sheet.density = density.density; sheet.reducedMotion = reduced }
    val course = ui.course
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val wideDetails = maxWidth >= 360.dp && density.fontScale <= 1.3f && course.location.length < 36
        val titleDrift = ui.sourceCenterX?.let { (it - with(density) { maxWidth.toPx() } / 2f)
            .coerceIn(-with(density) { 36.dp.toPx() }, with(density) { 36.dp.toPx() }) } ?: 0f
        val desired = maxOf(maxHeight * 0.70f, (400f * density.fontScale.coerceAtMost(1.5f)).dp)
        val height = desired.coerceAtMost(maxHeight * 0.90f)
        Surface(modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp).height(height)
            .testTag("course-detail-surface").onSizeChanged { sheet.height = it.height.toFloat() }
            .nestedScroll(connection).semantics { contentDescription = "课程详情" },
            shape = RoundedCornerShape(28.dp), color = colors.surface,
            border = BorderStroke(1.dp, colors.outlineVariant.copy(alpha = 0.55f)), shadowElevation = 10.dp) {
            Column(Modifier.background(Brush.verticalGradient(listOf(course.color.copy(alpha = 0.08f), colors.surface), endY = with(density) { 160.dp.toPx() }))
                .padding(horizontal = 20.dp)) {
                Box(Modifier.fillMaxWidth().height(30.dp)
                    .draggable(rememberDraggableState { sheet.dragBy(it) }, Orientation.Vertical,
                        onDragStopped = { sheet.finishDrag(it, onClose) })
                    .semantics { contentDescription = "下拉关闭课程详情" }, contentAlignment = Alignment.Center) {
                    Box(Modifier.size(36.dp, 4.dp).background(colors.outlineVariant, RoundedCornerShape(2.dp)))
                }
                Row(Modifier.fillMaxWidth().padding(bottom = 18.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).graphicsLayer {
                        val p = if (reduced) 1f else progress?.value?.coerceIn(0f, 1f) ?: 1f
                        translationY = (1f - p) * 12.dp.toPx()
                        translationX = (1f - p) * titleDrift
                        alpha = (p * 1.6f).coerceIn(0f, 1f)
                    }) {
                        Box(Modifier.size(32.dp, 4.dp).background(course.color, RoundedCornerShape(2.dp)))
                        Spacer(Modifier.height(9.dp))
                        Text(course.name, Modifier.semantics { heading() }, fontSize = 24.sp, lineHeight = 30.sp,
                            fontWeight = FontWeight.Bold, color = colors.onSurface, maxLines = 3,
                            overflow = androidx.compose.ui.text.style.TextOverflow.Ellipsis)
                        if (course.isCustom) Text("自定义课程", Modifier.padding(top = 4.dp), style = MaterialTheme.typography.labelMedium,
                            color = colors.onSurfaceVariant)
                    }
                    IconButton(onClose, Modifier.size(48.dp)) {
                        AnimatedLineIcon(AnimatedIconSpec.Close, description = "关闭课程详情", tint = colors.onSurfaceVariant)
                    }
                }
                Column(Modifier.weight(1f).verticalScroll(rememberScrollState()).testTag("course-detail-scroll"),
                    verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    val weekday = "一二三四五六日".getOrElse(course.day - 1) { ' ' }
                    if (wideDetails) Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        DetailInfoTile("上课时间", "周" + weekday + " · 第 " + course.startPeriod + "–" + course.endPeriod + " 节",
                            AnimatedIconSpec.Clock, Modifier.weight(1f).detailEntrance(0, progress, reduced))
                        DetailInfoTile("上课地点", course.location.ifBlank { "未指定地点" }, AnimatedIconSpec.Location,
                            Modifier.weight(1f).detailEntrance(1, progress, reduced))
                    } else {
                        DetailInfoTile("上课时间", "周" + weekday + " · 第 " + course.startPeriod + "–" + course.endPeriod + " 节",
                            AnimatedIconSpec.Clock, Modifier.detailEntrance(0, progress, reduced))
                        DetailInfoTile("上课地点", course.location.ifBlank { "未指定地点" }, AnimatedIconSpec.Location,
                            Modifier.detailEntrance(1, progress, reduced))
                    }
                    DetailInfoLine("授课教师", course.teacher.ifBlank { "未指定教师" }, AnimatedIconSpec.Person,
                        Modifier.detailEntrance(2, progress, reduced))
                    DetailInfoLine("上课周次", course.weeks, AnimatedIconSpec.Calendar,
                        Modifier.detailEntrance(3, progress, reduced))
                    if (ui.invalidWeeks) Text("周次待核对，暂不能安排提醒。", color = colors.error, style = MaterialTheme.typography.bodySmall)
                    ui.conflicts.forEach { conflict ->
                        Surface(color = colors.errorContainer, shape = RoundedCornerShape(14.dp)) {
                            Row(Modifier.padding(12.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                AnimatedLineIcon(AnimatedIconSpec.Warning, Modifier.size(20.dp), tint = colors.onErrorContainer)
                                Text("与「" + conflict.otherName + "」在第 " + conflict.weeks.joinToString("、") + " 周、第 " +
                                    conflict.startPeriod + "–" + conflict.endPeriod + " 节重叠", color = colors.onErrorContainer,
                                    style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    Surface(Modifier.detailEntrance(4, progress, reduced), color = colors.surfaceContainerLow, shape = RoundedCornerShape(20.dp)) {
                        Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                AnimatedLineIcon(AnimatedIconSpec.Bell, tint = if (ui.reminderEnabled) colors.primary else colors.onSurfaceVariant,
                                    state = if (ui.reminderEnabled) IconVisualState.Selected else IconVisualState.Idle)
                                Column(Modifier.weight(1f)) {
                                    Text("课程提醒", style = MaterialTheme.typography.titleSmall)
                                    Text("上课前 15 分钟", style = MaterialTheme.typography.bodySmall, color = colors.onSurfaceVariant)
                                }
                                LiquidSwitch(ui.reminderEnabled, onReminderChanged, enabled = ui.reminderAvailable)
                            }
                            Text(ui.reminderDescription, color = colors.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                            if (ui.needsPermission) SystemSecondaryButton("设置提醒权限", onPermission, Modifier.fillMaxWidth())
                            if (ui.needsTime) SystemSecondaryButton("设置学期时间", onConfigureTime, Modifier.fillMaxWidth())
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
                if (course.isCustom) Row(Modifier.fillMaxWidth().padding(top = 12.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    SystemSecondaryButton("删除", onDelete, Modifier.weight(1f),
                        leadingIcon = { AnimatedLineIcon(AnimatedIconSpec.Delete, Modifier.size(18.dp)) })
                    SystemPrimaryButton("编辑课程", onEdit, Modifier.weight(2f),
                        leadingIcon = { AnimatedLineIcon(AnimatedIconSpec.Edit, Modifier.size(18.dp)) })
                } else Spacer(Modifier.height(16.dp))
            }
        }
    }
}

@Composable
private fun DetailInfoTile(label: String, value: String, icon: AnimatedIconSpec, modifier: Modifier = Modifier) {
    Surface(modifier.fillMaxWidth(), color = MaterialTheme.colorScheme.surfaceContainerLow, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                AnimatedLineIcon(icon, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.primary)
                Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Text(value, style = MaterialTheme.typography.titleSmall, lineHeight = 22.sp)
        }
    }
}

@Composable
private fun DetailInfoLine(label: String, value: String, icon: AnimatedIconSpec, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 8.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
        AnimatedLineIcon(icon, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(value, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

private fun Modifier.detailEntrance(index: Int, progress: State<Float>?, reduced: Boolean) = graphicsLayer {
    if (!reduced) {
        val delay = index * 0.065f
        val p = (((progress?.value ?: 1f) - delay) / (1f - delay)).coerceIn(0f, 1f)
        alpha = p
        translationY = (1f - p) * 14.dp.toPx()
    }
}
