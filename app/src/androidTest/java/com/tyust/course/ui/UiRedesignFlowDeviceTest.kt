package com.tyust.course.ui

import android.app.Activity
import android.app.Application
import android.os.Bundle
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.tyust.course.BuildConfig
import com.tyust.course.MainActivity
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.concurrent.atomic.AtomicReference

/** Native screenrecord on the activity's actual MuMu display; demo callbacks only. */
internal class DisplayRecording(activity: Activity, name: String) : AutoCloseable {
    private val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    private val directory = File(activity.getExternalFilesDir(null), "redesign-recordings").apply { mkdirs() }
    private val file = File(directory, name + ".mp4")
    private val log = File(directory, name + ".log")
    private val pid: String
    init {
        assumeTrue(BuildConfig.UI_PREVIEW)
        val display = requireNotNull(activity.display).displayId
        val displays = device.executeShellCommand("dumpsys display")
        val physical = Regex("displayId=" + display + ", uniqueId='local:([0-9]+)'")
            .find(displays)?.groupValues?.get(1) ?: error("Physical display missing for " + display)
        val command = "screenrecord --display-id " + physical +
            " --size 540x1200 --bit-rate 6M --time-limit 90 " + file.absolutePath +
            " >" + log.absolutePath + " 2>&1 </dev/null &\necho $!\n"
        val script = File(directory, name + ".start.sh").apply { writeText(command) }
        pid = device.executeShellCommand("sh " + script.absolutePath).trim()
        require(pid.matches(Regex("[0-9]+"))) { "Recording could not start: " + pid }
        File(directory, name + ".json").writeText("{\"logicalDisplay\":" + display +
            ",\"physicalDisplay\":\"" + physical + "\",\"package\":\"" + activity.packageName + "\"}")
    }
    override fun close() {
        val stop = File(directory, file.nameWithoutExtension + ".stop.sh").apply { writeText("kill -2 " + pid + "\n") }
        val probe = File(directory, file.nameWithoutExtension + ".probe.sh").apply { writeText("kill -0 " + pid + " 2>/dev/null\necho $?\n") }
        device.executeShellCommand("sh " + stop.absolutePath)
        val until = SystemClock.uptimeMillis() + 5000
        while (SystemClock.uptimeMillis() < until && device.executeShellCommand("sh " + probe.absolutePath).trim() == "0") {
            SystemClock.sleep(100)
        }
        assertTrue("Screen recording missing; " + log.takeIf { it.exists() }?.readText(), file.length() > 10_000)
    }
}

@RunWith(AndroidJUnit4::class)
class UiRedesignFlowDeviceTest {
    @Test fun recordColdStartAndAllSixFeedbackAreasWithDemoData() {
        assumeTrue(BuildConfig.UI_PREVIEW)
        val app = InstrumentationRegistry.getInstrumentation().targetContext.applicationContext as Application
        val cold = AtomicReference<DisplayRecording?>()
        val recordingError = AtomicReference<Throwable?>()
        val callback = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) {
                if (activity is MainActivity && cold.get() == null) {
                    runCatching { DisplayRecording(activity, "06-cold-start") }
                        .onSuccess { cold.set(it) }.onFailure { recordingError.set(it) }
                }
            }
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        }
        app.registerActivityLifecycleCallbacks(callback)
        try {
            DemoUiDriver().use { ui ->
                recordingError.get()?.let { throw AssertionError("Unable to record the cold start", it) }
                cold.get()?.close()
                app.unregisterActivityLifecycleCallbacks(callback)
                ui.screenshot("06-cold-start-end")
                DisplayRecording(requireNotNull(ui.main), "01-05-interactions").use {
                    ui.navigate("课程")
                    ui.click("筛选")
                    ui.screenshot("01-filter-panel")
                    ui.click("有余量")
                    ui.click("应用")
                    ui.screenshot("01-filter-active")
                    ui.navigate("成绩")
                    ui.screenshot("02-grades-expanded")
                    fun swipe(up: Boolean) {
                        val activity = requireNotNull(ui.main)
                        val display = activity.display!!.displayId
                        val width = activity.window.decorView.width
                        val height = activity.window.decorView.height
                        val a = (height * if (up) .72f else .32f).toInt()
                        val b = (height * if (up) .32f else .72f).toInt()
                        ui.shell("input -d " + display + " swipe " + width / 2 + " " + a + " " + width / 2 + " " + b + " 650")
                        SystemClock.sleep(400)
                    }
                    swipe(true)
                    ui.screenshot("02-grades-collapsed")
                    ui.click("考试")
                    ui.screenshot("02-grades-without-share")
                    ui.navigate("课表")
                    ui.click("数据结构")
                    ui.waitText("关闭课程详情")
                    ui.screenshot("03-detail-light")
                    ui.back()
                    ui.waitText("关闭课程详情", false)
                    ui.navigate("抢课")
                    ui.screenshot("04-console-waiting")
                    ui.longClick("开始执行")
                    ui.screenshot("04-console-fan")
                    ui.click("切换为模糊监控")
                    ui.screenshot("04-console-fuzzy")
                    ui.click("精确执行")
                    ui.click("设置定时任务")
                    ui.waitText("选择抢课时间")
                    ui.screenshot("04-console-timing-picker")
                    ui.click("取消")
                    ui.click("取消定时设置")
                    ui.longClick("开始执行")
                    ui.click("添加课程")
                    ui.waitText("添加课程到队列")
                    val weekday = ui.boundsOf("周几")
                    val period = ui.boundsOf("节次")
                    assertTrue("Time selectors must stay side by side", kotlin.math.abs(weekday.top - period.top) <= 2 && weekday.right < period.left)
                    ui.screenshot("04-console-time-selectors")
                    ui.click("取消")
                    ui.click("开始执行")
                    ui.click("停止执行")
                    ui.screenshot("04-console-stopped")
                    ui.click("开始执行")
                    ui.screenshot("04-console-running")
                    ui.await("Demo queue did not complete", 20_000) { ui.hasText("执行结束 · 有失败") }
                    ui.screenshot("04-console-result")
                    repeat(3) { swipe(true) }
                    ui.click("高级设置")
                    ui.screenshot("04-console-advanced")
                    ui.navigate("设置")
                    ui.navigate("课表")
                    ui.navigate("成绩")
                    ui.navigate("课程")
                    ui.screenshot("05-navigation-end")
                }
            }
        } finally {
            app.unregisterActivityLifecycleCallbacks(callback)
        }
    }
}
