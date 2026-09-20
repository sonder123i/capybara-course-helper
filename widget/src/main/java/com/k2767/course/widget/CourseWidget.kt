package com.k2767.course.widget

import android.content.Context
import androidx.compose.runtime.Composable
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
import androidx.glance.layout.Column
import androidx.glance.layout.ColumnScope
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding

/**
 * 「今日课程」小组件：只列今天的课。
 *
 * 读 App 写下的快照（[CourseWidgetData]）自行计算今天该上哪些课，所以系统定时刷新
 * 就能让日期翻页、周次推进自动跟上，不需要 App 在后台活着。共用 UI 见 [WidgetUi]。
 */
class CourseWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // 快照在 provideContent 之外读取：这段跑在 App 进程里，不受 RemoteViews 限制。
        val snapshot = CourseWidgetData.read(context)
        // 定一个「下课闹钟」：下课那一刻重画，上完的课立刻让位给后面的课
        WidgetRefreshScheduler.schedule(context, WidgetToday.nextRefreshAt(snapshot))
        provideContent { TodayContent(snapshot) }
    }
}

class CourseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CourseWidget()
}

@Composable
private fun TodayContent(snapshot: WidgetSnapshot?) {
    val context = LocalContext.current
    val colors = widgetColors()
    // 每次渲染都取一次当前时刻：组件会长时间停在桌面上，缓存 now 会让跨天/跨课时段的判断失真
    val now = System.currentTimeMillis()
    // 列表可滚动，所以不再按高度截断——上下午的课全进列表，装不下的滑出来看。
    // skipEnded 保留：滑动要看到的是「接下来还要上的课」，不是回头翻已经下课的。
    val rows = snapshot?.let { WidgetToday.rows(it, now = now, skipEnded = true) }.orEmpty()
    // 今天本来有课、但都已经上完了 → 用另一句提示，避免说成「今天没有课」
    // 这一条必须走不带 skipEnded 的全天课表：rows 已被剥掉上完的课，拿它判断会误判成「今天没课」
    val hadCoursesToday = snapshot != null && WidgetToday.hasCoursesOn(snapshot, dayOffset = 0, now = now)
    val hasAnchor = snapshot?.let { WidgetToday.hasWeekAnchor(it, now) } == true
    // 今天没课时往后找第一天上什么课。注意判断依据是 hadCoursesToday 而不是 rows.isEmpty()：
    // 否则周三 17:00 打开组件（课上完了）会被当成「今天没课」而开始预告周四。
    val preview = if (snapshot != null && hasAnchor && !hadCoursesToday) {
        WidgetToday.nextDayWithCourses(snapshot, fromOffset = 0, now = now)
    } else null
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
        when {
            snapshot == null || snapshot.courses.isEmpty() -> WidgetHint(context.getString(R.string.widget_no_data))
            // 没填开学日期时一节都算不出来，这时说「今天没有课」是在撒谎
            !hasAnchor -> WidgetHint(context.getString(R.string.widget_no_semester_start))
            hadCoursesToday && rows.isNotEmpty() -> CourseList(rows, rowModifier)
            // 今天上完了：确认过今天确实有课，用「都上完啦」，不预告
            hadCoursesToday -> WidgetHint(context.getString(R.string.widget_empty_finished))
            // 今天没课 → 预告下一个有课日，栏头写真实星期几
            preview != null -> {
                WidgetColumnLabel(previewLabel(preview))
                Spacer(GlanceModifier.height(6.dp))
                CourseList(preview.rows, rowModifier)
            }
            // 往后一周都没课（假期）：说清楚是「一周」，别说成「今天没有课啦」
            else -> WidgetHint(context.getString(R.string.widget_empty_week_ahead))
        }
    }
}

/** 可滚动的课程列表。`defaultWeight` 让它吃掉顶栏之外的剩余高度，否则 ListView 会按内容撑开。 */
@Composable
private fun ColumnScope.CourseList(rows: List<WidgetCourseRow>, rowModifier: GlanceModifier) {
    LazyColumn(modifier = GlanceModifier.defaultWeight()) {
        items(rows) { row -> WidgetCourseRow(row, modifier = rowModifier) }
    }
}