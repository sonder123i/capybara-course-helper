package com.tyust.course.schedule

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.FrameLayout
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.tyust.course.ui.system.*
import com.tyust.course.ui.theme.CourseSelectorTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class ScheduleWidgetAppearanceActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)
        val manager = AppWidgetManager.getInstance(this)
        val provider = manager.getAppWidgetInfo(id)?.provider
        val style = ScheduleWidgetStyle.entries.firstOrNull { ComponentName(this, it.provider) == provider }
        if (id <= 0 || style == null) { finish(); return }
        setResult(Activity.RESULT_CANCELED)
        setContent { CourseSelectorTheme { GlassWindowHost {
            val saved = remember { ScheduleWidgetAppearances.read(this@ScheduleWidgetAppearanceActivity, id) }
            var image by rememberSaveable { mutableStateOf(saved.image) }
            var opacity by rememberSaveable { mutableIntStateOf(saved.opacity) }
            var blur by rememberSaveable { mutableIntStateOf(saved.blur) }
            var segmented by rememberSaveable { mutableStateOf(saved.segmented) }
            var tomorrow by rememberSaveable { mutableStateOf(saved.tomorrow) }
            var busy by remember { mutableStateOf(false) }
            var error by remember { mutableStateOf("") }
            val scope = rememberCoroutineScope()
            val appearance = ScheduleWidgetAppearance(image, opacity, blur, segmented, tomorrow)
            val latestImage by rememberUpdatedState(image)
            DisposableEffect(Unit) { onDispose {
                if (isFinishing && latestImage != saved.image) ScheduleWidgetAppearances.discardImage(this@ScheduleWidgetAppearanceActivity, latestImage)
            } }
            val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
                if (uri != null) scope.launch {
                    busy = true; error = ""
                    try {
                        val next = withContext(Dispatchers.IO) { ScheduleWidgetAppearances.importImage(this@ScheduleWidgetAppearanceActivity, uri) }
                        if (image != saved.image) ScheduleWidgetAppearances.discardImage(this@ScheduleWidgetAppearanceActivity, image)
                        image = next
                    } catch (e: Exception) { error = e.message ?: "图片读取失败" } finally { busy = false }
                }
            }
            val state = remember { widgetAppearancePreview() }
            val width = if (style == ScheduleWidgetStyle.Single) 160 else 300
            val height = if (style == ScheduleWidgetStyle.Double) 150 else if (style == ScheduleWidgetStyle.Single) 160 else 280
            var views by remember { mutableStateOf<android.widget.RemoteViews?>(null) }
            val dark = androidx.compose.foundation.isSystemInDarkTheme()
            LaunchedEffect(appearance, dark) {
                kotlinx.coroutines.delay(60)
                views = withContext(Dispatchers.Default) { ScheduleWidgetRenderer.views(this@ScheduleWidgetAppearanceActivity, state, width, height, style, appearance) }
            }
            GlassPageScaffold(title = "组件外观", onBack = { finish() }) { padding ->
                Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(20.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(style.title + " · 独立设置", style = MaterialTheme.typography.titleMedium)
                    Box(Modifier.fillMaxWidth().height(height.dp), contentAlignment = Alignment.Center) {
                        AndroidView(factory = { FrameLayout(it) }, modifier = Modifier.width(width.dp).height(height.dp), update = { host ->
                            views?.let { remote -> host.removeAllViews(); host.addView(remote.apply(host.context, host))
                                fun disable(view: android.view.View) { view.isClickable = false; if (view is android.view.ViewGroup) for (i in 0 until view.childCount) disable(view.getChildAt(i)) }
                                disable(host)
                            }
                        })
                    }
                    Text("示例预览；桌面组件显示当前账号的课表。", style = MaterialTheme.typography.bodySmall)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        SystemActionButton("选择背景图片", { pick.launch("image/*") }, enabled = !busy)
                        SystemActionButton("使用纯色", {
                            if (image != saved.image) ScheduleWidgetAppearances.discardImage(this@ScheduleWidgetAppearanceActivity, image)
                            image = ""
                        }, enabled = image.isNotBlank())
                    }
                    Text("背景不透明度 " + opacity + "%", Modifier.fillMaxWidth())
                    LiquidSlider({ opacity.toFloat() }, { opacity = it.toInt() }, valueRange = 0f..100f)
                    Text("图片模糊 " + blur, Modifier.fillMaxWidth())
                    LiquidSlider({ blur.toFloat() }, { blur = it.toInt() }, valueRange = 0f..32f, enabled = image.isNotBlank(), steps = 31)
                    if (style == ScheduleWidgetStyle.Countdown) {
                        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                            Text("大课按小节倒计时", Modifier.weight(1f))
                            Switch(checked = segmented, onCheckedChange = { segmented = it })
                        }
                        Text("倒计时到每个小节下课；课间显示“课间休息 · 距上课”，只有最后一节结束才显示“距下课”。",
                            style = MaterialTheme.typography.bodySmall)
                    }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text("今日课后显示明日课表", Modifier.weight(1f))
                        Switch(checked = tomorrow, onCheckedChange = { tomorrow = it })
                    }
                    Text("今日课程全部结束后，组件显示“今日课程结束”并列出明日课程；明日没有课时显示“明日无课”。",
                        style = MaterialTheme.typography.bodySmall)
                    if (busy) PageLoadingIcon("app.schedule")
                    if (error.isNotBlank()) Text(error, color = MaterialTheme.colorScheme.error)
                    SystemDialogButton(primary = true, enabled = !busy, onClick = {
                        ScheduleWidgetAppearances.save(this@ScheduleWidgetAppearanceActivity, id, appearance)
                        setResult(Activity.RESULT_OK, Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)); finish()
                    }) { Text("保存到这个组件") }
                }
            }
        } } }
    }
    companion object {
        fun open(context: Context, id: Int) = context.startActivity(Intent(context, ScheduleWidgetAppearanceActivity::class.java).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id))
    }
}

private fun widgetAppearancePreview(): ScheduleWidgetState {
    val now = System.currentTimeMillis()
    val monday = ScheduleDates.mondayOfWeek(now)
    val calendar = Calendar.getInstance().apply { timeInMillis = now }
    val day = ((calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7) + 1
    fun clock(offsetMinutes: Long) = SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(now + offsetMinutes * 60_000L))
    // 示例课程时间相对“现在”生成：倒计时预览会真的走动，不会一打开就停在 00:00。
    // 每节单独给出起止时间，分段倒计时的预览才能看出课间与最后一节的区别。
    val base = ScheduleTimeBase(ScheduleTimeBase.dateFromMillis(monday.timeInMillis),
        mapOf(1 to clock(-15), 2 to clock(35), 3 to clock(95), 4 to clock(145), 5 to clock(215), 6 to clock(265)),
        mapOf(1 to clock(25), 2 to clock(75), 3 to clock(135), 4 to clock(185), 5 to clock(255), 6 to clock(305)))
    val courses = listOf(ScheduleCourseRecord("a", "高等数学", "张老师", "博学楼 A205", day, 1, 2, "1-16"),
        ScheduleCourseRecord("b", "计算机网络", "李老师", "明理楼 B302", day, 3, 4, "1-16"),
        ScheduleCourseRecord("c", "大学英语", "陈老师", "博学楼 A102", day, 5, 6, "1-16"))
    return ScheduleWidgetState.from(ScheduleSnapshot("", "", "", courses, base, now, true), now)
}
