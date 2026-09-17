package com.k2767.course.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import java.util.Calendar

private val SmallSize = DpSize(160.dp, 110.dp)
private val MediumSize = DpSize(240.dp, 150.dp)
private val LargeSize = DpSize(320.dp, 220.dp)

/** 浅色 / 深色两套取色走资源（values / values-night），ColorProvider 按当前配置解析。 */
private class WidgetColors(val surface: ColorProvider, val ink: ColorProvider, val muted: ColorProvider)

@Composable
private fun widgetColors() = WidgetColors(
    surface = ColorProvider(R.color.widget_surface),
    ink = ColorProvider(R.color.widget_ink),
    muted = ColorProvider(R.color.widget_muted)
)

/**
 * 「今日课程」桌面小组件。
 *
 * 读 App 写下的快照（[CourseWidgetData]），自行计算今天该上哪些课——
 * 所以系统每 30 分钟的定时刷新（见 course_widget_info.xml 的 updatePeriodMillis）
 * 就能让日期翻页、周次推进自动跟上，不需要 App 在后台活着。
 */
class CourseWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Responsive(setOf(SmallSize, MediumSize, LargeSize))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        // 快照在 provideContent 之外读取：这段跑在 App 进程里，不受 RemoteViews 限制。
        val snapshot = CourseWidgetData.read(context)
        provideContent { WidgetContent(snapshot) }
    }
}

class CourseWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = CourseWidget()
}

@Composable
private fun WidgetContent(snapshot: WidgetSnapshot?) {
    val context = LocalContext.current
    val colors = widgetColors()
    val height = LocalSize.current.height
    val maxRows = when {
        height >= LargeSize.height -> 6
        height >= MediumSize.height -> 3
        else -> 1
    }
    val now = System.currentTimeMillis()
    val rows = snapshot?.let { WidgetToday.rows(it, now = now, max = maxRows) }.orEmpty()
    val launch = context.packageManager.getLaunchIntentForPackage(context.packageName)
    val openApp = launch?.let { actionStartActivity(it) }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(colors.surface)
            .cornerRadius(20.dp)
            .then(if (openApp != null) GlanceModifier.clickable(openApp) else GlanceModifier)
            .padding(14.dp)
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.Vertical.CenterVertically
        ) {
            Text(
                text = snapshot?.school?.takeIf { it.isNotBlank() }
                    ?: context.getString(R.string.widget_no_school),
                modifier = GlanceModifier.defaultWeight(),
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.ink),
                maxLines = 1
            )
            Text(
                text = headerText(snapshot, now),
                style = TextStyle(fontSize = 11.sp, color = colors.muted),
                maxLines = 1
            )
        }

        Spacer(GlanceModifier.height(8.dp))

        when {
            snapshot == null -> Hint(context.getString(R.string.widget_no_data))
            snapshot.courses.isEmpty() -> Hint(context.getString(R.string.widget_no_data))
            rows.isEmpty() -> Hint(context.getString(R.string.widget_empty_today))
            else -> rows.forEach { row ->
                CourseRow(row)
                Spacer(GlanceModifier.height(6.dp))
            }
        }
    }
}

@Composable
private fun CourseRow(row: WidgetCourseRow) {
    val colors = widgetColors()
    val bar = WidgetPalette.colors.getOrElse(row.colorIndex) { WidgetPalette.colors.first() }
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .width(3.dp)
                .height(30.dp)
                .background(ColorProvider(Color(bar)))
                .cornerRadius(2.dp)
        ) {}
        Spacer(GlanceModifier.width(8.dp))
        Column(modifier = GlanceModifier.defaultWeight()) {
            Text(
                text = row.name,
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.Medium, color = colors.ink),
                maxLines = 1
            )
            val subtitle = listOf(row.location, row.time).filter { it.isNotBlank() }.joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Text(
                    text = subtitle,
                    style = TextStyle(fontSize = 11.sp, color = colors.muted),
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun Hint(text: String) {
    val colors = widgetColors()
    Box(
        modifier = GlanceModifier.fillMaxSize().padding(bottom = 4.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = text,
            style = TextStyle(fontSize = 12.sp, color = colors.muted),
            maxLines = 2
        )
    }
}

private val DayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

/** 顶栏右侧：`9.17 第3周 周四`。周次算不出来（没设开学日期）时只显示日期与星期。 */
private fun headerText(snapshot: WidgetSnapshot?, now: Long): String {
    val calendar = Calendar.getInstance().apply { timeInMillis = now }
    val date = "${calendar.get(Calendar.MONTH) + 1}.${calendar.get(Calendar.DAY_OF_MONTH)}"
    val week = snapshot?.let { WidgetToday.weekAt(it.firstWeekDate, now) }?.let { "第${it}周" }
    val day = DayNames[WidgetToday.dayOfWeek(now) - 1]
    return listOfNotNull(date, week, day).joinToString(" ")
}