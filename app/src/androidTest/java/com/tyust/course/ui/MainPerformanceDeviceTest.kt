package com.tyust.course.ui

import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.FrameMetrics
import android.view.Window
import android.view.PixelCopy
import android.view.accessibility.AccessibilityNodeInfo
import android.graphics.Bitmap
import android.os.Looper
import android.app.Activity
import android.app.Application
import android.content.Intent
import android.os.Bundle
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import androidx.compose.runtime.Recomposer
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Configurator
import androidx.test.uiautomator.UiDevice
import com.tyust.course.BuildConfig
import com.tyust.course.MainActivity
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
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val device = UiDevice.getInstance(instrumentation)
        val previousTimeout = Configurator.getInstance().waitForIdleTimeout
        Configurator.getInstance().waitForIdleTimeout = 0
        // ActivityScenario waits for main-queue idleness; that is what this test measures.
        val app = instrumentation.targetContext.applicationContext as Application
        val resumed = CountDownLatch(1)
        var activity: MainActivity? = null
        val callbacks = object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(current: Activity) {
                if (current is MainActivity) { activity = current; resumed.countDown() }
            }
            override fun onActivityCreated(current: Activity, state: Bundle?) {}
            override fun onActivityStarted(current: Activity) {}
            override fun onActivityPaused(current: Activity) {}
            override fun onActivityStopped(current: Activity) {}
            override fun onActivitySaveInstanceState(current: Activity, state: Bundle) {}
            override fun onActivityDestroyed(current: Activity) {}
        }
        app.registerActivityLifecycleCallbacks(callbacks)
        app.startActivity(Intent(app, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK))
        assertTrue("Demo activity did not resume", resumed.await(15, TimeUnit.SECONDS))
        val scenario = object {
            fun onActivity(action: (MainActivity) -> Unit) = instrumentation.runOnMainSync { action(requireNotNull(activity)) }
            fun close() {
                onActivity { it.finish() }
                app.unregisterActivityLifecycleCallbacks(callbacks)
            }
        }
        val worker = HandlerThread("frame-metrics").apply { start() }
        val frames = Collections.synchronizedList(mutableListOf<Long>())
        val layoutDraw = Collections.synchronizedList(mutableListOf<Long>())
        val deadlines = Collections.synchronizedList(mutableListOf<Long>())
        var output: File? = null
        var refreshRate = 60f
        var displayId = 0
        var width = 0
        var height = 0
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            frames += metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            layoutDraw += metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION) + metrics.getMetric(FrameMetrics.DRAW_DURATION)
            deadlines += if (android.os.Build.VERSION.SDK_INT >= 31) metrics.getMetric(FrameMetrics.DEADLINE) else 0L
        }
        fun navigate(label: String) {
            val end = SystemClock.uptimeMillis() + 6000
            var found = false
            while (!found && SystemClock.uptimeMillis() < end) {
                fun find(root: AccessibilityNodeInfo?): List<AccessibilityNodeInfo> {
                    if (root == null) return emptyList()
                    return buildList {
                        if (root.text?.toString() == label) add(root)
                        for (index in 0 until root.childCount) addAll(find(root.getChild(index)))
                    }
                }
                val nodes = instrumentation.uiAutomation.windowsOnAllDisplays.get(displayId).orEmpty()
                    .flatMap { find(it.root) }
                fun bounds(node: AccessibilityNodeInfo) = android.graphics.Rect().also { node.getBoundsInScreen(it) }
                val node = nodes.maxByOrNull { bounds(it).bottom }
                if (node != null) {
                    val p = bounds(node)
                    device.executeShellCommand("input -d $displayId tap ${p.centerX()} ${p.centerY()}")
                    found = true
                } else SystemClock.sleep(100)
            }
            assertTrue("Navigation label missing: $label", found)
            SystemClock.sleep(1000)
        }
        fun scroll(down: Boolean) {
            val from = (height * if (down) .72 else .38).toInt()
            val to = (height * if (down) .38 else .72).toInt()
            device.executeShellCommand("input -d $displayId swipe ${width / 2} $from ${width / 2} $to 500")
            SystemClock.sleep(800)
        }
        try {
            SystemClock.sleep(2000)
            scenario.onActivity {
                refreshRate = it.display?.refreshRate ?: 60f
                displayId = it.display?.displayId ?: 0
                width = it.window.decorView.width
                height = it.window.decorView.height
                output = File(it.getExternalFilesDir(null), "performance-validation").apply { mkdirs() }
                it.window.addOnFrameMetricsAvailableListener(listener, Handler(worker.looper))
            }
            val label = InstrumentationRegistry.getArguments().getString("capturePrefix") ?: "sample"
            for (tab in listOf("课程", "课表", "抢课", "成绩", "设置")) {
                navigate(tab)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val captured = CountDownLatch(1)
                var captureResult = PixelCopy.ERROR_UNKNOWN
                scenario.onActivity { activity ->
                    PixelCopy.request(activity.window, bitmap, { result -> captureResult = result; captured.countDown() }, Handler(Looper.getMainLooper()))
                }
                assertTrue("Window screenshot failed", captured.await(3, TimeUnit.SECONDS) && captureResult == PixelCopy.SUCCESS)
                File(output, "$label-$tab.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                bitmap.recycle()
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
                navigate("成绩")
                measure("idle", round) { SystemClock.sleep(10_000) }
                measure("navigation", round) {
                    for (tab in listOf("设置", "课程", "课表", "抢课", "成绩")) navigate(tab)
                }
                measure("grades-scroll", round) { repeat(2) { scroll(true); scroll(false) } }
                navigate("课表")
                measure("schedule-scroll", round) { repeat(2) { scroll(true); scroll(false) } }
            }
        } finally {
            scenario.onActivity { it.window.removeOnFrameMetricsAvailableListener(listener) }
            worker.quitSafely()
            scenario.close()
            Configurator.getInstance().waitForIdleTimeout = previousTimeout
        }
    }
}
