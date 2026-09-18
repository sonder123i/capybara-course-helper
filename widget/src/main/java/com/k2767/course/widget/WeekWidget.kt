package com.k2767.course.widget

import android.content.Context
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
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider

private val OnColorBar = ColorProvider(Color(0xFFFFFFFF))

/**
 * 「周课表」网格小组件：一~日 × 节次的整周表格，当前周的课按单双周过滤后落格。
 *
 * 跨多节的课只在起始节次写课名，之后画同色延续块（Glance 没有 rowSpan），
 * 一列看上去就是一条连续的课条。当天那一列的表头会加粗。
 */
class WeekWidget : GlanceAppWidget() {
    override val sizeMode: SizeMode = SizeMode.Exact

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = CourseWidgetData.read(context)
        provideContent { WeekContent(snapshot) }
    }
}

class WeekWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = WeekWidget()
}

@Composable
private fun WeekContent(snapshot: WidgetSnapshot?) {
    val context = LocalContext.current
    val colors = widgetColors()
    // 网格按真实尺寸排。行数与行高的分配见 weekGridRows：
    // 可用高度一定被精确填满，组件拉高时是「多显示几节」而不是「行变高后下面留白」。
    val periodsUsed = snapshot?.courses?.maxOfOrNull { it.endPeriod.coerceAtMost(WeekMaxRows) }
        ?.coerceAtLeast(1) ?: 9
    val available = (LocalSize.current.height - WeekChromeHeight).coerceAtLeast(40.dp)
    val (maxPeriods, rowHeight) = weekGridRows(available, periodsUsed)
    val cells = snapshot?.let { WidgetToday.cellsForWeek(it, maxPeriods = maxPeriods) }.orEmpty()
    val today = WidgetToday.dayOfWeek(System.currentTimeMillis())
    val openApp = context.packageManager.getLaunchIntentForPackage(context.packageName)
        ?.let { actionStartActivity(it) }

    Column(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(colors.surface)
            .cornerRadius(20.dp)
            .then(if (openApp != null) GlanceModifier.clickable(openApp) else GlanceModifier)
            .padding(12.dp)
    ) {
        WidgetHeader(snapshot, System.currentTimeMillis())
        Spacer(GlanceModifier.height(8.dp))
        when {
            snapshot == null || snapshot.courses.isEmpty() ->
                WidgetHint(context.getString(R.string.widget_no_data))
            // 网格没有周次就一格都填不上，得说清是缺开学日期而不是「这周没课」
            !WidgetToday.hasWeekAnchor(snapshot) ->
                WidgetHint(context.getString(R.string.widget_no_semester_start))
            else -> {
                Row(modifier = GlanceModifier.fillMaxWidth()) {
                    Spacer(GlanceModifier.width(14.dp))
                    DayNames.forEachIndexed { index, day ->
                        Box(modifier = GlanceModifier.defaultWeight(), contentAlignment = Alignment.Center) {
                            Text(
                                text = day.removePrefix("周"),
                                style = TextStyle(
                                    fontSize = 9.sp,
                                    fontWeight = if (index + 1 == today) FontWeight.Bold else FontWeight.Normal,
                                    color = if (index + 1 == today) colors.ink else colors.muted
                                ),
                                maxLines = 1
                            )
                        }
                    }
                }
                Spacer(GlanceModifier.height(4.dp))
                for (period in 1..maxPeriods) {
                    Row(
                        modifier = GlanceModifier.fillMaxWidth().height(rowHeight),
                        verticalAlignment = Alignment.Vertical.CenterVertically
                    ) {
                        Box(modifier = GlanceModifier.width(14.dp), contentAlignment = Alignment.Center) {
                            Text(
                                text = "$period",
                                style = TextStyle(fontSize = 8.sp, color = colors.muted),
                                maxLines = 1
                            )
                        }
                        for (day in 1..7) {
                            val cell = cells[day to period]
                            Box(
                                modifier = GlanceModifier
                                    .defaultWeight()
                                    .fillMaxHeight()
                                    .padding(end = 2.dp, bottom = 2.dp)
                            ) {
                                if (cell != null) {
                                    val bar = WidgetPalette.colors.getOrElse(cell.colorIndex) {
                                        WidgetPalette.colors.first()
                                    }
                                    Box(
                                        modifier = GlanceModifier
                                            .fillMaxSize()
                                            .background(ColorProvider(Color(bar)))
                                            .cornerRadius(4.dp)
                                            .padding(2.dp),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        if (!cell.continued && cell.name.isNotBlank()) {
                                            Text(
                                                text = cell.name,
                                                style = TextStyle(
                                                    fontSize = 9.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    color = OnColorBar
                                                ),
                                                maxLines = 2
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}