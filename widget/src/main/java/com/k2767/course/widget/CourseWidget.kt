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
import androidx.glance.layout.Column
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
    // 行数按真实尺寸算：顶栏 + 内边距占 44dp，每行（课名 + 地点/时间 + 间距）约 46dp
    val maxRows = rowsThatFit(LocalSize.current.height, chromeHeight = 44.dp, rowHeight = 46.dp, max = 10)
    val rows = snapshot?.let { WidgetToday.rows(it, max = maxRows) }.orEmpty()
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
        when {
            snapshot == null || snapshot.courses.isEmpty() -> WidgetHint(context.getString(R.string.widget_no_data))
            rows.isEmpty() -> WidgetHint(context.getString(R.string.widget_empty_today))
            else -> rows.forEach { row ->
                WidgetCourseRow(row)
                Spacer(GlanceModifier.height(6.dp))
            }
        }
    }
}