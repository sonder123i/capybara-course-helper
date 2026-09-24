package com.k2767.course.ui.screen

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.os.Build
import androidx.annotation.DrawableRes
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.k2767.course.ui.system.DialogPresentation
import com.k2767.course.ui.system.GlassToaster
import com.k2767.course.ui.system.SystemDialog
import com.k2767.course.widget.CourseWidgetReceiver
import com.k2767.course.widget.TodayTomorrowWidgetReceiver
import com.k2767.course.widget.WeekWidgetReceiver

/**
 * 应用内可添加的桌面组件。
 *
 * 渲染一律用 `:widget` 模块那三个 Glance 组件，这里只是入口与说明——组件本体
 * 的排版、刷新与深浅色都在那边，不在这份清单里复制一遍。
 * 预览用静态图：Glance 的界面没法像 RemoteViews 那样在应用里实时摆出来。
 */
enum class CourseWidgetEntry(
    val labelRes: Int,
    val descriptionRes: Int,
    val cells: String,
    @param:DrawableRes val previewImage: Int?,
    /** Glance 的接收者对 app 模块只露得出类名：:widget 用 implementation 依赖 Glance，
     *  这里看不见它的父类型，所以只能按 Class<*> 交给 ComponentName。 */
    val receiver: Class<*>
) {
    Today(com.k2767.course.widget.R.string.widget_today_label,
        com.k2767.course.widget.R.string.widget_today_description,
        "4×2", com.k2767.course.widget.R.drawable.preview_today, CourseWidgetReceiver::class.java),
    TodayTomorrow(com.k2767.course.widget.R.string.widget_today_tomorrow_label,
        com.k2767.course.widget.R.string.widget_today_tomorrow_description,
        "4×2", com.k2767.course.widget.R.drawable.preview_today_tomorrow, TodayTomorrowWidgetReceiver::class.java),
    /**
     * 「今日时间轴」。它挂的还是 `WeekWidgetReceiver` —— 那个接收者原本是「周课表」，
     * 改名会让桌面上已有的组件升级后变成不可用的灰块，所以只换了内容。
     */
    Timeline(com.k2767.course.widget.R.string.widget_timeline_label,
        com.k2767.course.widget.R.string.widget_timeline_description,
        "5×3", null, WeekWidgetReceiver::class.java)
}

/**
 * 请求桌面把组件放到主屏上。
 *
 * 国内桌面（OriginOS / EMUI 等）经常直接报「不支持」，那样点了没任何反应最容易被
 * 当成 bug，所以这条路必须留一句人话兜底——用户还是要长按桌面自己加。
 */
object CourseWidgetPinning {
    // 版本判断必须【直接写在这一行的条件里】：先算成一个布尔变量再分支，
    // lint 认不出那是版本保护，会按 NewApi 报错（minSdk 24，该 API 26）。
    fun request(context: Context, entry: CourseWidgetEntry) {
        val manager = AppWidgetManager.getInstance(context)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && manager.isRequestPinAppWidgetSupported) {
            manager.requestPinAppWidget(ComponentName(context, entry.receiver), null, null)
        } else {
            GlassToaster.show("当前桌面不支持从这里添加，请长按桌面空白处，在小组件中添加「${context.getString(entry.labelRes)}」")
        }
    }
}

@Composable
fun ScheduleWidgetPicker(onDismiss: () -> Unit) {
    val context = LocalContext.current
    SystemDialog(
        onDismissRequest = onDismiss,
        title = { Text("选择桌面组件") },
        presentation = DialogPresentation.Bottom
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(
                text = "预览为组件的实际样式。添加后显示本机保存的课表，随系统深浅色切换。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            CourseWidgetEntry.entries.forEach { entry ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = stringResource(entry.labelRes),
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { CourseWidgetPinning.request(context, entry); onDismiss() }) {
                        Text("添加")
                    }
                }
                Text(
                    text = "${entry.cells} · " + stringResource(entry.descriptionRes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                entry.previewImage?.let { res ->
                    Image(
                        painter = painterResource(res),
                        contentDescription = stringResource(entry.labelRes),
                        contentScale = ContentScale.Fit,
                        alignment = Alignment.CenterStart,
                        modifier = Modifier.fillMaxWidth().padding(top = 2.dp)
                    )
                }
            }
            Text(
                text = "实际格数由桌面决定，长按组件可以调整尺寸。",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
