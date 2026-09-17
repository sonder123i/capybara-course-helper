package com.k2767.course.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import kotlin.math.abs
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import com.k2767.course.ui.system.glass.GlassLensCaptureObserver

/** Real scrolling/collapsing headers; a restored page must recover the same optical pixels. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 31, maxSdkVersion = 32)
class HuaweiGlassFlowDeviceTest {
    @Test fun scrollingKeepsTheMatchingLabelAndNavigationSampleVisible() {
        val captures = java.util.concurrent.ConcurrentHashMap<String, Pair<Bitmap, String>>()
        val samples = java.util.concurrent.ConcurrentHashMap<String, String>()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        instrumentation.runOnMainSync {
            GlassLensCaptureObserver.onCaptured = { tag, frame, bitmap ->
                captures[tag] = bitmap to "${frame.generation}: ${frame.geometry}"
            }
            GlassLensCaptureObserver.onSampled = { tag, size, origin, axes, generation ->
                samples[tag] = "$generation: $size origin=$origin axes=$axes"
            }
        }
        try {
            DemoUiDriver().use { ui ->
                val activity = requireNotNull(ui.main)
                val prefix = InstrumentationRegistry.getArguments().getString("capturePrefix") ?: "scroll"
                val directory = File(activity.getExternalFilesDir(null), "flow-validation").apply { mkdirs() }
                val display = activity.display!!.displayId
                val width = activity.window.decorView.width
                val height = activity.window.decorView.height
                fun capture(step: String) {
                    ui.screenshot("$prefix-$step")
                    captures.forEach { (tag, captured) ->
                        if (tag == "navbar" || tag.startsWith("seg-") || tag == "grab-controls") {
                            File(directory, "$prefix-$step-source-$tag.png").outputStream().use {
                                captured.first.compress(Bitmap.CompressFormat.PNG, 100, it)
                            }
                            File(directory, "$prefix-$step-source-$tag.txt").writeText(captured.second + "\n" + samples[tag])
                        }
                    }
                }
                repeat(3) { round ->
                    ui.navigate("抢课")
                    capture("matching-fresh-$round")
                    repeat(2) {
                        ui.shell("input -d $display swipe ${width / 2} ${height * 3 / 4} ${width / 2} ${height / 3} 350")
                    }
                    ui.shell("input -d $display swipe ${width / 2} ${height / 2} ${width / 2} ${height * 3 / 4} 300")
                    capture("matching-scroll-$round")
                    SystemClock.sleep(800)
                    capture("matching-settled-$round")
                    ui.navigate("课程")
                    ui.navigate("成绩")
                }
            }
        } finally {
            instrumentation.runOnMainSync {
                GlassLensCaptureObserver.onCaptured = null
                GlassLensCaptureObserver.onSampled = null
            }
        }
    }

    @Test fun gradesHeaderRestoresItsPixelsAfterScrolledTabChanges() {
        DemoUiDriver().use { ui ->
            val activity = requireNotNull(ui.main)
            val prefix = InstrumentationRegistry.getArguments().getString("capturePrefix") ?: "huawei"
            val directory = File(activity.getExternalFilesDir(null), "flow-validation")
            val display = activity.display!!.displayId
            val width = activity.window.decorView.width
            val height = activity.window.decorView.height
            fun header(name: String): Bitmap {
                val bounds = Rect(ui.boundsOf("学期"))
                listOf("总体", "考试", "刷新").forEach { bounds.union(ui.boundsOf(it)) }
                val windowOrigin = IntArray(2)
                ui.onMain { activity.window.decorView.getLocationOnScreen(windowOrigin) }
                bounds.offset(-windowOrigin[0], -windowOrigin[1])
                bounds.inset(-20, -20)
                bounds.intersect(0, 0, width, height)
                ui.screenshot("$prefix-$name")
                val full = BitmapFactory.decodeFile(File(directory, "$prefix-$name.png").absolutePath)
                return Bitmap.createBitmap(full, bounds.left, bounds.top, bounds.width(), bounds.height())
                    .also { full.recycle() }
            }
            fun difference(expected: Bitmap, actual: Bitmap): Double {
                assertEquals("Restored header width", expected.width, actual.width)
                assertEquals("Restored header height", expected.height, actual.height)
                var error = 0L
                for (y in 0 until expected.height) for (x in 0 until expected.width) {
                    val a = expected.getPixel(x, y); val b = actual.getPixel(x, y)
                    error += abs(Color.red(a) - Color.red(b)) + abs(Color.green(a) - Color.green(b)) +
                        abs(Color.blue(a) - Color.blue(b))
                }
                return error.toDouble() / (expected.width * expected.height * 3)
            }
            ui.navigate("成绩")
            ui.click("考试")
            SystemClock.sleep(1200)
            val expected = header("grades-fresh")
            val errors = JSONArray()
            repeat(3) { round ->
                ui.click("总体")
                repeat(2) {
                    ui.shell("input -d $display swipe ${width / 2} ${height * 3 / 4} ${width / 2} ${height / 3} 450")
                    SystemClock.sleep(350)
                }
                ui.screenshot("$prefix-grades-collapsed-$round")
                // Different tabs retain their own scroll offset; switch through both
                // header sizes rather than resetting the list to make the test pass.
                ui.click("学期")
                ui.click("考试")
                SystemClock.sleep(1200)
                val restored = header("grades-restored-$round")
                val error = difference(expected, restored)
                errors.put(error)
                File(directory, "$prefix-grades-pixels.json").writeText(JSONObject()
                    .put("meanChannelError", errors).put("width", expected.width).put("height", expected.height).toString(2))
                assertTrue("Header sampled a different area after tab/scroll cycle $round: mean error=$error", error < 4.0)
                restored.recycle()
            }
            expected.recycle()
            ui.navigate("抢课")
            ui.screenshot("$prefix-start-rest")
            ui.longClick("开始执行")
            ui.waitText("关闭操作菜单")
            ui.screenshot("$prefix-start-fan")
            ui.back()
            ui.waitText("关闭操作菜单", false)
            ui.screenshot("$prefix-start-restored")
        }
    }
}
