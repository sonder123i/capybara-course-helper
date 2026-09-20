package com.k2767.course.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.unit.ColorProvider

private val NarrowSize = DpSize(240.dp, 130.dp)

/** 中间那条竖分隔线：浅灰、随时间高度撑满。 */
@Composable
private fun ColumnDivider() {
    Spacer(GlanceModifier.width(10.dp))
    Box(
        modifier = GlanceModifier
            .width(1.dp)
            .fillMaxHeight()
            .background(ColorProvider(R.color.widget_divider))
    ) {}
    Spacer(GlanceModifier.width(10.dp))
}

/**
 * 「近日课程」小组件：左右两栏，和 WakeUp 课程表那张一致。
 *
 * 两栏都不是写死的「今天/明天」，而是各往后找第一个**有课的日子**（见
 * [WidgetToday.nextDayWithCourses]），中间没课的日子直接跳过：
 *
 * - 今天有课 → 左栏今天，右栏往后第一个有课的日子（通常就是明天）
 * - 今天没课 → 左栏往后第一个有课的日子，右栏再往后一个（周末就是这样提前看到周一周二）
 *
 * 栏头按实际那天写星期几，所以周六打开时左边写的是「周一」而不是「今天」。
 * 与「今日课程」共用同一份快照与排序规则。
 */
class TodayTomorrowWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = CourseWidgetData.read(context)
        // 定一个「下课闹钟」：上完的课立刻让位给后面的课
        WidgetRefreshScheduler.schedule(context, WidgetToday.nextRefreshAt(snapshot))
        provideContent { TodayTomorrowContent(snapshot) }
    }
}

class TodayTomorrowWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayTomorrowWidget()
}

@Composable
private fun TodayTomorrowContent(snapshot: WidgetSnapshot?) {
    val context = LocalContext.current
    val colors = widgetColors()
    // 组件会长时间停在桌面上，每次渲染现取，别缓存
    val now = System.currentTimeMillis()
    val hasData = snapshot != null && snapshot.courses.isNotEmpty()
    val hasAnchor = snapshot?.let { WidgetToday.hasWeekAnchor(it, now) } == true

    // 两栏各自往后找「有课的一天」。左栏从今天找，右栏从左栏那天的后一天接着找，
    // 所以不会两栏撞在同一天上，也不会因为中间夹着没课的日子就断掉
    val left = if (snapshot != null && hasAnchor) {
        WidgetToday.nextDayWithCourses(snapshot, fromOffset = 0, now = now)
    } else null
    val right = if (snapshot != null && hasAnchor && left != null) {
        WidgetToday.nextDayWithCourses(snapshot, fromOffset = left.offset + 1, now = now)
    } else null

    // 左栏那天是不是「今天」——决定空提示该说「今天没有课啦」还是「都上完啦」
    val leftIsToday = left?.offset == 0
    // 今天上完了：左栏今天没课但它本来有课。此时不预告，直接说「都上完啦」，
    // 否则周三 17:00 打开会看到「周四」跳出来，像是今天被跳过了
    val todayFinished = leftIsToday && !WidgetToday.hasCoursesOn(snapshot!!, dayOffset = 0, now = now)
    val openApp = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?.let { actionStartActivity(it) }
    // 列表条目是独立的 RemoteViews，不继承根容器的点击，所以每行自己挂一次；行距也并进来，
    // 一个条目只能放一个可组合项，再塞 Spacer 会多出一行。
    val rowSpacing = GlanceModifier.padding(bottom = 6.dp)
    val rowModifier = openApp?.let { rowSpacing.then(GlanceModifier.clickable(it)) } ?: rowSpacing

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(colors.surface)
            .cornerRadius(20.dp)
            .then(if (openApp != null) GlanceModifier.clickable(openApp) else GlanceModifier)
            .padding(14.dp)
    ) {
        WidgetHeader(snapshot, now)
        Spacer(GlanceModifier.height(8.dp))
        Row(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Vertical.Top
        ) {
            DayColumn(
                label = if (left != null) previewLabel(left) else "今天",
                rows = if (todayFinished) emptyList() else left?.rows.orEmpty(),
                emptyText = context.getString(
                    when {
                        // 算不出周次时两栏都是空的，但原因不是「没课」，别报成没课
                        !hasAnchor -> R.string.widget_no_semester_start
                        todayFinished -> R.string.widget_empty_finished
                        // 今天没课、往后一周也没课 → 假期，说清是一周
                        left == null -> R.string.widget_empty_week_ahead
                        else -> R.string.widget_empty_today
                    }
                ),
                hasData = hasData,
                rowModifier = rowModifier,
                modifier = GlanceModifier.defaultWeight()
            )
            ColumnDivider()
            DayColumn(
                label = if (right != null) previewLabel(right) else "明天",
                rows = right?.rows.orEmpty(),
                emptyText = context.getString(
                    when {
                        !hasAnchor -> R.string.widget_no_semester_start
                        // 左栏已经是往后找的第一天，右栏还找不到 → 说明一周内只有那一天有课
                        right == null -> R.string.widget_empty_week_ahead
                        else -> R.string.widget_empty_tomorrow
                    }
                ),
                hasData = hasData,
                rowModifier = rowModifier,
                modifier = GlanceModifier.defaultWeight()
            )
        }
    }
}

@Composable
private fun DayColumn(
    label: String,
    rows: List<WidgetCourseRow>,
    emptyText: String,
    hasData: Boolean,
    rowModifier: GlanceModifier,
    modifier: GlanceModifier
) {
    Column(modifier = modifier.fillMaxHeight()) {
        WidgetColumnLabel(label)
        Spacer(GlanceModifier.height(6.dp))
        when {
            !hasData -> WidgetHint("")
            rows.isEmpty() -> WidgetHint(emptyText)
            // defaultWeight 让列表吃掉栏头之外的剩余高度，两栏各自滚动互不相干
            else -> LazyColumn(GlanceModifier.defaultWeight()) {
                items(rows) { row -> WidgetCourseRow(row, barHeight = 42, modifier = rowModifier) }
            }
        }
    }
}