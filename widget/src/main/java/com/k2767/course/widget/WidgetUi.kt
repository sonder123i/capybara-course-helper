package com.k2767.course.widget

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.appwidget.cornerRadius
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

/** 浅色 / 深色两套取色走资源（values / values-night），ColorProvider 按当前配置解析。 */
internal class WidgetColors(val surface: ColorProvider, val ink: ColorProvider, val muted: ColorProvider)

@Composable
internal fun widgetColors() = WidgetColors(
    surface = ColorProvider(R.color.widget_surface),
    ink = ColorProvider(R.color.widget_ink),
    muted = ColorProvider(R.color.widget_muted)
)

internal val DayNames = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

// ---------- 周课表网格的纵向排布 ----------

/** 顶栏 + 间隔 + 星期行占掉的高度。底部露出空白就调大，最后一行被切就调小。 */
internal val WeekChromeHeight = 44.dp

/** 行高下限：再低课名就看不清了。 */
internal val WeekMinRowHeight = 15.dp

/** 行高上限：超过这个高度一行就显得空，不如把高度让给更多节次。 */
internal val WeekIdealRowHeight = 34.dp

/** 一天最多显示 12 节。 */
internal const val WeekMaxRows = 12

/**
 * 周课表网格排布：返回「显示几节」到「每节多高」。
 *
 * 必须守住两条规则，缺一条就会出现「组件拉高后下面一大片留白」：
 *
 * 1. **行数不能封顶在「已用节次」上。** 已用节次回答的是「这个学生有几节课」，
 *    不是「组件能显示几行」——两者被混成一个变量后，组件变高时行数被压回去，
 *    多出来的高度只能空着。
 * 2. **行高必须由最终行数反推**（`available / rows`），而不是先算行高再算行数。
 *    反推才能保证 `rowHeight × rows` 恰好等于可用高度，任何尺寸都精确填满。
 */
internal fun weekGridRows(available: Dp, periodsUsed: Int): Pair<Int, Dp> {
    if (available <= 0.dp) return 1 to 1.dp
    val used = periodsUsed.coerceAtLeast(1)
    val base = (available / used).coerceIn(WeekMinRowHeight, WeekIdealRowHeight)
    val rows = (available.value / base.value).toInt().coerceIn(1, WeekMaxRows)
    return rows to (available / rows)
}

/** 顶栏右侧：`9.17 第3周 周四`。周次算不出来时只显示日期与星期。 */
internal fun headerText(snapshot: WidgetSnapshot?, now: Long): String {
    val calendar = Calendar.getInstance().apply { timeInMillis = now }
    val date = "${calendar.get(Calendar.MONTH) + 1}.${calendar.get(Calendar.DAY_OF_MONTH)}"
    val week = snapshot?.let { WidgetToday.weekAt(it.firstWeekDate, now) }?.let { "第${it}周" }
    val day = DayNames[WidgetToday.dayOfWeek(now) - 1]
    return listOfNotNull(date, week, day).joinToString(" ")
}

/** 顶栏：左边学校名，右边日期 / 周次 / 星期。 */
@Composable
internal fun WidgetHeader(snapshot: WidgetSnapshot?, now: Long) {
    val colors = widgetColors()
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Text(
            text = snapshot?.school?.takeIf { it.isNotBlank() } ?: "未绑定学校",
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
}

/** 「今天」/「明天」这类小标题。 */
@Composable
internal fun WidgetColumnLabel(text: String) {
    val colors = widgetColors()
    Text(
        text = text,
        style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = colors.muted),
        maxLines = 1
    )
}

/**
 * 预告某一天时的栏头：`周三`。若那天就是今天则写「今天」。
 *
 * 必须写真实的星期几。组件没课时会往后跳到有课的那天，周六打开时这一栏装的是周一的课——
 * 沿用「今天」的标签会让人以为周一那几节课就在今天，比不显示还糟。
 * 后面的日期不用「下周一」这种说法：「周一」＋顶栏的日期已经够定位，多两个字会挤掉课名。
 */
internal fun previewLabel(day: PreviewDay): String =
    if (day.offset == 0) "今天" else DayNames[day.day - 1]

/**
 * 一行课：左侧配色条 + 三行文字（课名 / 教室 / 时间）。
 *
 * 教室与时间各占一行而不是挤在一行——上课前第一眼要看到的是「去哪个教室」，
 * 挤在一行时地点会把时间顶掉（也和 WakeUp 课程表的组件一致）。
 */
@Composable
internal fun WidgetCourseRow(row: WidgetCourseRow, barHeight: Int = 46, modifier: GlanceModifier = GlanceModifier) {
    val colors = widgetColors()
    val bar = WidgetPalette.colors.getOrElse(row.colorIndex) { WidgetPalette.colors.first() }
    Row(
        modifier = GlanceModifier.fillMaxWidth().then(modifier),
        verticalAlignment = Alignment.Vertical.CenterVertically
    ) {
        Box(
            modifier = GlanceModifier
                .width(3.dp)
                .height(barHeight.dp)
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
            if (row.location.isNotBlank()) {
                Text(
                    text = row.location,
                    style = TextStyle(fontSize = 11.sp, color = colors.muted),
                    maxLines = 1
                )
            }
            if (row.time.isNotBlank()) {
                Text(
                    text = row.time,
                    style = TextStyle(fontSize = 11.sp, color = colors.muted),
                    maxLines = 1
                )
            }
        }
    }
}

/** 空状态 / 提示文案，居中显示。 */
@Composable
internal fun WidgetHint(text: String) {
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