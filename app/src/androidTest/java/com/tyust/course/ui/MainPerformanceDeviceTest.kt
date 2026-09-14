package com.tyust.course.ui

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.runtime.Recomposer
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tyust.course.BuildConfig
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Collections

/** Real display clock, local demo data, no Compose test clock or production account. */
@RunWith(AndroidJUnit4::class)
class MainPerformanceDeviceTest {
    @Test fun measurePrimaryPressAndFanWithTheRealDisplayClock() {
        assumeTrue(BuildConfig.UI_PREVIEW)
        val worker = HandlerThread("control-frame-metrics").apply { start() }
        val samples = Collections.synchronizedList(mutableListOf<Pair<Long, Long>>())
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            samples += metrics.getMetric(FrameMetrics.TOTAL_DURATION) to
                (metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION) + metrics.getMetric(FrameMetrics.DRAW_DURATION))
        }
        try {
            DemoUiDriver().use { ui ->
                ui.navigate("抢课")
                val activity = requireNotNull(ui.main)
                val display = activity.display!!.displayId
                val output = File(activity.getExternalFilesDir(null), "performance-validation").apply { mkdirs() }
                val prefix = InstrumentationRegistry.getArguments().getString("capturePrefix") ?: "sample"
                ui.onMain { activity.window.addOnFrameMetricsAvailableListener(listener, Handler(worker.looper)) }
                try {
                    repeat(3) { round ->
                        val button = ui.boundsOf("开始执行")
                        SystemClock.sleep(1000)
                        samples.clear()
                        val started = SystemClock.elapsedRealtime()
                        // Cancel outside the target before long-press takeover; no execution starts.
                        repeat(3) {
                            val outside = (button.left - button.width()).coerceAtLeast(0)
                            ui.shell("input -d $display swipe ${button.centerX()} ${button.centerY()} $outside ${button.centerY()} 240")
                            SystemClock.sleep(500)
                        }
                        // Holding and releasing in place must leave a usable fan, then Back closes it.
                        repeat(3) {
                            ui.longClick("开始执行")
                            ui.waitText("关闭操作菜单")
                            ui.back()
                            ui.waitText("关闭操作菜单", false)
                        }
                        SystemClock.sleep(500)
                        val measured = synchronized(samples) { samples.toList() }
                        assertTrue("The real control animation must produce frames", measured.isNotEmpty())
                        File(output, "$prefix-start-controls-$round.json").writeText(JSONObject()
                            .put("scenario", "start-controls").put("round", round)
                            .put("elapsedMs", SystemClock.elapsedRealtime() - started)
                            .put("api", android.os.Build.VERSION.SDK_INT).put("refreshRate", activity.display!!.refreshRate)
                            .put("totalDurationNs", JSONArray(measured.map { it.first }))
                            .put("layoutDrawDurationNs", JSONArray(measured.map { it.second })).toString(2))
                    }
                } finally {
                    ui.onMain { activity.window.removeOnFrameMetricsAvailableListener(listener) }
                }
            }
        } finally {
            worker.quitSafely()
        }
    }

    @Test fun measureIdleAndRepeatedNavigation() {
        assumeTrue("Performance measurements require the isolated demo variant", BuildConfig.UI_PREVIEW)
        val worker = HandlerThread("frame-metrics").apply { start() }
        val frames = Collections.synchronizedList(mutableListOf<Long>())
        val layoutDraw = Collections.synchronizedList(mutableListOf<Long>())
        val deadlines = Collections.synchronizedList(mutableListOf<Long>())
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            frames += metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            layoutDraw += metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION) + metrics.getMetric(FrameMetrics.DRAW_DURATION)
            deadlines += if (android.os.Build.VERSION.SDK_INT >= 31) metrics.getMetric(FrameMetrics.DEADLINE) else 0L
        }
        try {
        DemoUiDriver().use { driver ->
        val activity = requireNotNull(driver.main)
        val refreshRate = activity.display?.refreshRate ?: 60f
        val displayId = activity.display?.displayId ?: 0
        val width = activity.window.decorView.width
        val height = activity.window.decorView.height
        val output = File(activity.getExternalFilesDir(null), "performance-validation").apply { mkdirs() }
        fun scroll(down: Boolean) {
            val from = (height * if (down) .72 else .38).toInt()
            val to = (height * if (down) .38 else .72).toInt()
            driver.shell("input -d $displayId swipe ${width / 2} $from ${width / 2} $to 500")
            SystemClock.sleep(800)
        }
        try {
            driver.onMain { activity.window.addOnFrameMetricsAvailableListener(listener, Handler(worker.looper)) }
            val label = InstrumentationRegistry.getArguments().getString("capturePrefix") ?: "sample"
            for (tab in listOf("课程", "课表", "抢课", "成绩", "设置")) {
                // Refresh accessibility state and verify selection before attributing
                // frame metrics to this page. Cached nodes can point at a previous tab.
                driver.navigate(tab)
                driver.screenshot("$label-$tab")
            }
            fun measure(name: String, round: Int, action: () -> Unit) {
                SystemClock.sleep(1200)
                frames.clear(); layoutDraw.clear(); deadlines.clear()
                val before = Recomposer.runningRecomposers.value.sumOf { it.changeCount }
                val start = SystemClock.elapsedRealtime()
                action()
                SystemClock.sleep(200)
                val duration = SystemClock.elapsedRealtime() - start
                val total = synchronized(frames) { frames.toList() }
                val ui = synchronized(layoutDraw) { layoutDraw.toList() }
                val deadline = synchronized(deadlines) { deadlines.toList() }
                File(output, "$label-$name-$round.json").writeText(JSONObject()
                    .put("scenario", name).put("round", round).put("elapsedMs", duration)
                    .put("package", BuildConfig.APPLICATION_ID).put("refreshRate", refreshRate)
                    .put("widthPx", width).put("heightPx", height).put("displayId", displayId)
                    .put("recompositions", Recomposer.runningRecomposers.value.sumOf { it.changeCount } - before)
                    .put("totalDurationNs", JSONArray(total)).put("layoutDrawDurationNs", JSONArray(ui))
                    .put("deadlineNs", JSONArray(deadline)).toString(2))
            }
            repeat(3) { round ->
                driver.navigate("成绩")
                measure("idle", round) { SystemClock.sleep(10_000) }
                measure("navigation", round) {
                    for (tab in listOf("设置", "课程", "课表", "抢课", "成绩")) driver.navigate(tab)
                }
                measure("grades-scroll", round) { repeat(2) { scroll(true); scroll(false) } }
                driver.navigate("课表")
                measure("schedule-scroll", round) { repeat(2) { scroll(true); scroll(false) } }
                driver.navigate("抢课")
                measure("grab-scroll", round) { repeat(2) { scroll(true); scroll(false) } }
            }
        } finally {
            driver.onMain { activity.window.removeOnFrameMetricsAvailableListener(listener) }
        }
        }
        } finally {
            worker.quitSafely()
        }
    }
}
