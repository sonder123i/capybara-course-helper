package com.k2767.course.ui.screen

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.k2767.course.schedule.ScheduleDates
import com.k2767.course.schedule.ScheduleMaxWeeks
import com.k2767.course.schedule.ScheduleWeeks
import com.k2767.course.ui.system.LocalAppOverlayBottomInset
import com.k2767.course.ui.system.PagePadding
import com.k2767.course.ui.system.SystemSecondaryButton
import java.util.Calendar

/**
 * 日视图：把选中的那一天按时序纵向列出来，一屏能装下的信息比网格多（教室、教师都能完整显示）。
 *
 * 与周视图**共用同一个 scrollState**：顶栏折叠行程是由它推导的（见 ScheduleScreen 里的
 * collapse），共用才能保证切换视图时顶栏不会跳一下。
 *
 * [topInset] 同样传「状态栏 + 顶栏展开高」这个常量，而不是 paddingValues 的顶部内边距——
 * 后者随顶栏收缩而变小，而它作用在 verticalScroll 内部，会让内容被多提一份。
 */
@Composable
fun ScheduleDayList(
    courses: List<ScheduleCourseUi>,
    week: Int,
    day: Int,
    firstWeekDate: String?,
    times: List<PeriodTimeUi>,
    agenda: ScheduleAgenda,
    now: Long,
    actualWeek: Int?,
    isNextSemester: Boolean,
    onCourseClick: (ScheduleCourseUi) -> Unit,
    onCalendar: () -> Unit = {},
    scrollState: ScrollState = rememberScrollState(),
    topInset: Dp = 0.dp
) {
    // 必须按当前周过滤：否则单双周的课会在同一时段一起冒出来，「还剩 N 堂」
    // 也会把不属于本周的算进去。周视图正是用 isInWeek 决定显示哪些。
    val daily = remember(courses, week, day) {
        courses.filter { it.day == day && isInWeek(it.weeks, week) }
            .sortedWith(compareBy<ScheduleCourseUi> { it.startPeriod }.thenBy { it.name })
    }
    val unknown = remember(daily) { countUnknownWeeks(daily) }
    val isToday = !isNextSemester && week == actualWeek && day == ScheduleDates.dayAt(now)
    val date = remember(firstWeekDate, week, day) { ScheduleDates.date(firstWeekDate, week, day) }
    val outOfRange = if (week < 1 || week > ScheduleMaxWeeks) dayOutOfRange(week) else null

    Column(
        modifier = Modifier
            .fillMaxSize()
            .testTag("schedule-day-list")
            .verticalScroll(scrollState)
            .padding(start = PagePadding, end = PagePadding, top = topInset + 12.dp,
                bottom = LocalAppOverlayBottomInset.current + 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        when {
            ScheduleDates.firstMonday(firstWeekDate) == null -> ScheduleNotice(
                "设置开学日期后显示当天课程", "设置开学日期", onCalendar
            )

            outOfRange != null -> ScheduleNotice(outOfRange)

            else -> {
                Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = date?.let {
                            "${it.get(Calendar.MONTH) + 1}月${it.get(Calendar.DAY_OF_MONTH)}日 · " +
                                "周${"一二三四五六日"[day - 1]}"
                        } ?: "周${"一二三四五六日"[day - 1]}",
                        modifier = Modifier.testTag("schedule-day-title"),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    // 「还剩」只对今天成立：别的那一天既没有正在上的课，也没有下一节可言。
                    Text(
                        text = if (isToday) {
                            "今日 ${daily.size} 堂" + when {
                                unknown > 0 -> " · $unknown 堂周次待核对"
                                daily.isNotEmpty() && agenda.remaining(now) == 0 -> " · 已结束"
                                else -> " · 还剩 ${agenda.remaining(now)} 堂"
                            }
                        } else {
                            "当日 ${daily.size} 堂" + if (unknown > 0) " · $unknown 堂周次待核对" else ""
                        },
                        modifier = Modifier.testTag("schedule-day-summary"),
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                if (daily.isEmpty()) ScheduleNotice(if (isToday) "今天没有课程" else "当天没有课程")
                daily.forEach { course ->
                    ScheduleDayCourse(course, times) { onCourseClick(course) }
                }
            }
        }
    }
}

@Composable
private fun ScheduleNotice(message: String, action: String? = null, onAction: () -> Unit = {}) {
    Column(
        Modifier.fillMaxWidth().padding(vertical = 20.dp).testTag("schedule-notice"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(message, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        if (action != null) SystemSecondaryButton(text = action, onClick = onAction)
    }
}

/**
 * 日视图里的一行课：左侧是上课时段（起止时间 + 中间一段连接竖线），右侧是课程卡片。
 * 卡片配色与网格卡片同一套来源（course.color），切视图时同一门课颜色不变。
 */
@Composable
private fun ScheduleDayCourse(
    course: ScheduleCourseUi,
    periodTimes: List<PeriodTimeUi>,
    onClick: () -> Unit
) {
    val startTime = periodTimes.firstOrNull { it.period == course.startPeriod }?.startTime.orEmpty()
    val endTime = periodTimes.firstOrNull { it.period == course.endPeriod }?.endTime.orEmpty()
    val unknownWeeks = remember(course.weeks) { !ScheduleWeeks.parse(course.weeks).valid }
    val containerColor = androidx.compose.ui.graphics.lerp(
        MaterialTheme.colorScheme.surface,
        course.color,
        if (course.isCustom) 0.30f else 0.22f
    )
    val hasStatus = course.hasConflict || course.isCurrent || course.isNext || course.isCustom || unknownWeeks

    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top
    ) {
        Column(
            modifier = Modifier.width(56.dp).padding(top = 10.dp),
            horizontalAlignment = Alignment.End
        ) {
            Text(startTime, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Box(
                modifier = Modifier
                    .padding(vertical = 4.dp)
                    .width(1.dp)
                    .height(20.dp)
                    .background(MaterialTheme.colorScheme.outlineVariant)
            )
            Text(endTime, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }

        Spacer(Modifier.width(12.dp))

        Surface(
            modifier = Modifier
                .weight(1f)
                .testTag("schedule-day-course-${course.id}")
                .clickable(onClick = onClick),
            shape = RoundedCornerShape(14.dp),
            color = containerColor,
            contentColor = MaterialTheme.colorScheme.onSurface,
            border = BorderStroke(0.6.dp, course.color.copy(alpha = 0.50f))
        ) {
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                Box(
                    modifier = Modifier
                        .width(3.dp)
                        .fillMaxHeight()
                        .background(course.color.copy(alpha = 0.85f))
                )
                Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = course.name,
                            modifier = Modifier.weight(1f),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            text = "第${course.startPeriod}-${course.endPeriod}节",
                            modifier = Modifier.padding(start = 8.dp),
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        if (hasStatus) {
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                imageVector = when {
                                    course.hasConflict || unknownWeeks -> Icons.Default.Warning
                                    course.isCustom -> Icons.Default.Edit
                                    else -> Icons.Default.PlayArrow
                                },
                                contentDescription = when {
                                    unknownWeeks -> "周次待核对"
                                    course.hasConflict -> "同一时段有其他课程"
                                    course.isCurrent -> "正在上课"
                                    course.isNext -> "下一节"
                                    else -> null
                                },
                                modifier = Modifier.size(13.dp),
                                tint = if (course.hasConflict || unknownWeeks) {
                                    MaterialTheme.colorScheme.error
                                } else {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                }
                            )
                        }
                    }
                    if (course.location.isNotBlank() || course.teacher.isNotBlank()) {
                        Spacer(Modifier.height(6.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            if (course.location.isNotBlank()) {
                                Text(
                                    text = compactLocation(course.location),
                                    modifier = Modifier.weight(1f),
                                    fontSize = 12.sp,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            } else {
                                Spacer(Modifier.weight(1f))
                            }
                            if (course.teacher.isNotBlank()) Text(
                                text = course.teacher,
                                modifier = Modifier.padding(start = 8.dp),
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}
