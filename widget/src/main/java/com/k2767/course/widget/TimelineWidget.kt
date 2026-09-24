package com.k2767.course.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

/**
 * 「今日时间轴」小组件：从今天起往后几天，按真实上课时刻排的一列课。
 *
 * 左侧是时间轴（起止时刻 + 一枚课程色点），右侧是课名与教室；正在上的那一节
 * 课名加粗、时刻用正文字色，一眼能看出「现在该在哪」。列表可滚动，所以不按
 * 高度截断——装不下的滑出来看（与「今日课程」同一套做法）。
 *
 * 它取代的是原来的「周课表」网格。
 */
class TimelineWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = CourseWidgetData.read(context)
        // 这一列整条都是按「现在几点」排的：下课那一刻就得重画，否则正在上的高亮
        // 会一直停在上一节上，而系统定时刷新最短 30 分钟。与「今日课程」同一个闹钟。
        WidgetRefreshScheduler.schedule(context, WidgetToday.nextRefreshAt(snapshot))
        provideContent { TimelineContent(snapshot) }
    }
}

/**
 * 【不要改这个类名，也不要改包名。】
 *
 * 桌面上已经摆了「周课表」的用户，系统记的是这个接收者的组件名；一改名字，
 * 那些组件升级后就会变成「不可用」的灰块，只能自己删了重加。所以内容从整周
 * 网格换成时间轴时，只有它保留原名——组件身份不变，画的东西变了。
 * 新建的类（[TimelineWidget]）与清单里的 label / provider xml 都可以照实改名。
 */
class WeekWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TimelineWidget()
}

@Composable
private fun TimelineContent(snapshot: WidgetSnapshot?) {
    val context = LocalContext.current
    val colors = widgetColors()
    val now = System.currentTimeMillis()
    val rows = snapshot?.let { WidgetToday.timeline(it, now = now) }.orEmpty()
    val openApp = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?.let { actionStartActivity(it) }
    // 列表条目是独立的 RemoteViews，不继承根容器的点击，所以每行自己挂一次；
    // 行距也并进来——一个条目只能放一个可组合项，再塞 Spacer 会多出一行。
    val spacing = GlanceModifier.padding(bottom = 8.dp)
    val rowModifier = openApp?.let { spacing.then(GlanceModifier.clickable(it)) } ?: spacing

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
        when {
            snapshot == null || snapshot.courses.isEmpty() ->
                WidgetHint(context.getString(R.string.widget_no_data))
            // 没填开学日期就一节都排不出来，这时说「没课」是在撒谎
            !WidgetToday.hasWeekAnchor(snapshot, now) ->
                WidgetHint(context.getString(R.string.widget_no_semester_start))
            rows.isEmpty() ->
                WidgetHint(context.getString(R.string.widget_empty_week_ahead))
            else -> LazyColumn(modifier = GlanceModifier.defaultWeight()) {
                items(rows) { row -> TimelineEntry(row, rowModifier) }
            }
        }
    }
}

@Composable
private fun TimelineEntry(row: WidgetTimelineRow, modifier: GlanceModifier) {
    val colors = widgetColors()
    val bar = WidgetPalette.colors.getOrElse(row.colorIndex) { WidgetPalette.colors.first() }
    Column(modifier = GlanceModifier.fillMaxWidth().then(modifier)) {
        if (row.firstOfDay) {
            WidgetColumnLabel(dayLabel(row.dayOffset, row.day))
            Spacer(GlanceModifier.height(4.dp))
        }
        Row(verticalAlignment = Alignment.Vertical.CenterVertically) {
            Text(
                text = row.time,
                modifier = GlanceModifier.width(66.dp),
                style = TextStyle(
                    fontSize = 10.sp,
                    // 正在上的那一节要能从一片灰时刻里跳出来
                    color = if (row.happening) colors.ink else colors.muted
                ),
                maxLines = 1
            )
            Spacer(GlanceModifier.width(6.dp))
            Box(
                modifier = GlanceModifier
                    .size(7.dp)
                    .background(ColorProvider(Color(bar)))
                    .cornerRadius(4.dp)
            ) {}
            Spacer(GlanceModifier.width(8.dp))
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = row.name,
                    style = TextStyle(
                        fontSize = 13.sp,
                        fontWeight = if (row.happening) FontWeight.Bold else FontWeight.Medium,
                        color = colors.ink
                    ),
                    maxLines = 1
                )
                if (row.location.isNotBlank()) {
                    Text(
                        text = row.location,
                        style = TextStyle(fontSize = 11.sp, color = colors.muted),
                        maxLines = 1
                    )
                }
            }
        }
    }
}
