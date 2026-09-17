package com.k2767.course.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
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
 * 「今日与明日」小组件：左右两栏，和 WakeUp 课程表那张一致。
 *
 * 与「今日课程」共用同一份快照与排序规则，只有「明天」这一栏按 +1 天重新算日期与周次
 * （跨周时周次会跟着推进，所以单双周课不会错）。
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
    // 每栏行数按真实尺寸算：顶栏 + 「今天/明天」小标题占 76dp，每行三行文字约 58dp
    val maxRows = rowsThatFit(LocalSize.current.height, chromeHeight = 76.dp, rowHeight = 58.dp, max = 8)
    // 今天这一栏丢掉已下课的课：位置有限，上完的让位给后面的；明天不受影响
    val today = snapshot?.let { WidgetToday.rowsAt(it, dayOffset = 0, max = maxRows, skipEnded = true) }.orEmpty()
    val tomorrow = snapshot?.let { WidgetToday.rowsAt(it, dayOffset = 1, max = maxRows) }.orEmpty()
    val hadToday = snapshot?.let { WidgetToday.rowsAt(it, dayOffset = 0).isNotEmpty() } == true
    val openApp = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?.let { actionStartActivity(it) }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(colors.surface)
            .cornerRadius(20.dp)
            .then(if (openApp != null) GlanceModifier.clickable(openApp) else GlanceModifier)
            .padding(14.dp)
    ) {
        WidgetHeader(snapshot, System.currentTimeMillis())
        Spacer(GlanceModifier.height(8.dp))
        Row(
            modifier = GlanceModifier.fillMaxSize(),
            verticalAlignment = Alignment.Vertical.Top
        ) {
            DayColumn(
                label = "今天",
                rows = today,
                emptyText = context.getString(
                    if (hadToday) R.string.widget_empty_finished else R.string.widget_empty_today
                ),
                hasData = snapshot != null && snapshot.courses.isNotEmpty(),
                modifier = GlanceModifier.defaultWeight()
            )
            ColumnDivider()
            DayColumn(
                label = "明天",
                rows = tomorrow,
                emptyText = context.getString(R.string.widget_empty_tomorrow),
                hasData = snapshot != null && snapshot.courses.isNotEmpty(),
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
    modifier: GlanceModifier
) {
    Column(modifier = modifier.fillMaxHeight()) {
        WidgetColumnLabel(label)
        Spacer(GlanceModifier.height(6.dp))
        when {
            !hasData -> WidgetHint("")
            rows.isEmpty() -> WidgetHint(emptyText)
            else -> rows.forEach { row ->
                WidgetCourseRow(row, barHeight = 42)
                Spacer(GlanceModifier.height(6.dp))
            }
        }
    }
}