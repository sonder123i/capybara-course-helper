package com.tyust.course.ui

import android.graphics.Bitmap
import android.os.Handler
import android.os.HandlerThread
import android.view.FrameMetrics
import android.view.Window
import androidx.compose.runtime.Recomposer
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tyust.course.BuildConfig
import com.tyust.course.MainActivity
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.util.Collections

/** Repeatable local-data workload; never reads or changes a real student account. */
@RunWith(AndroidJUnit4::class)
class MainPerformanceDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    companion object {
        @BeforeClass @JvmStatic fun requirePreview() { assumeTrue(BuildConfig.UI_PREVIEW) }
    }

    private fun settle() {
        compose.mainClock.advanceTimeBy(1200)
        compose.waitForIdle()
    }

    private fun navigate(label: String) {
        compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag("main-navigation"))).performClick()
        settle()
    }

    @Test fun measureIdleAndRepeatedNavigation() {
        compose.mainClock.autoAdvance = false
        settle()
        navigate("成绩")
        compose.onNode(hasText("学期") and isSelectable()).performClick()
        settle()
        val output = File(compose.activity.getExternalFilesDir(null), "performance-validation").apply { mkdirs() }
        val label = InstrumentationRegistry.getArguments().getString("capturePrefix") ?: "sample"
        val frames = Collections.synchronizedList(mutableListOf<Long>())
        val uiFrames = Collections.synchronizedList(mutableListOf<Long>())
        val worker = HandlerThread("frame-metrics").apply { start() }
        val listener = Window.OnFrameMetricsAvailableListener { _, metrics, _ ->
            frames += metrics.getMetric(FrameMetrics.TOTAL_DURATION)
            uiFrames += metrics.getMetric(FrameMetrics.LAYOUT_MEASURE_DURATION) + metrics.getMetric(FrameMetrics.DRAW_DURATION)
        }
        compose.runOnUiThread { compose.activity.window.addOnFrameMetricsAvailableListener(listener, Handler(worker.looper)) }
        try {
            val before = Recomposer.runningRecomposers.value.sumOf { it.changeCount }
            repeat(120) { compose.mainClock.advanceTimeByFrame(); Thread.sleep(16) }
            val idleChanges = Recomposer.runningRecomposers.value.sumOf { it.changeCount } - before
            val idleFrames = frames.size
            frames.clear()
            uiFrames.clear()
            repeat(3) {
                compose.onRoot().performTouchInput {
                    swipe(Offset(width * .5f, height * .76f), Offset(width * .5f, height * .37f), 480)
                }
                settle()
                compose.onRoot().performTouchInput {
                    swipe(Offset(width * .5f, height * .37f), Offset(width * .5f, height * .76f), 480)
                }
                settle()
                navigate("设置")
                navigate("成绩")
                compose.onNode(hasText("总体") and isSelectable()).performClick()
                settle()
                compose.onNode(hasText("学期") and isSelectable()).performClick()
                settle()
            }
            Thread.sleep(350)
            val frameCopy = synchronized(frames) { frames.toList() }
            val uiCopy = synchronized(uiFrames) { uiFrames.toList() }
            File(output, "$label.json").writeText(JSONObject()
                .put("idleRecompositions", idleChanges).put("idleFrames", idleFrames)
                .put("totalDurationNs", JSONArray(frameCopy))
                .put("layoutDrawDurationNs", JSONArray(uiCopy)).toString(2))
            File(output, "$label-grades.png").outputStream().use {
                compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, it)
            }
        } finally {
            compose.runOnUiThread { compose.activity.window.removeOnFrameMetricsAvailableListener(listener) }
            worker.quitSafely()
        }
    }
}
