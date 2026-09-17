package com.k2767.course.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.MotionEvent
import android.view.PixelCopy
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Captures the actual entrance, before navigation's normal idle wait. */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 31, maxSdkVersion = 32)
class GlassPageEntranceDeviceTest {
    @Test fun captureGrabEntranceFrames() = captureEntrance("抢课", "精确执行")
    @Test fun captureScheduleEntranceFrames() = captureEntrance("课表", "本学期")

    @Test fun selectedLabelsSurviveRepeatedPageChangesAndIdle() {
        val captures = java.util.concurrent.ConcurrentHashMap<String, Pair<String, Bitmap>>()
        com.k2767.course.ui.system.glass.GlassLensCaptureObserver.onCaptured = { tag, frame, bitmap ->
            if (tag.startsWith("seg-")) captures[tag] = "$frame" to bitmap
        }
        try {
            DemoUiDriver().use { ui ->
                val activity = requireNotNull(ui.main)
                val directory = File(activity.getExternalFilesDir(null), "flow-validation")
                val prefix = InstrumentationRegistry.getArguments().getString("capturePrefix") ?: "idle-labels"
                val metadata = JSONArray()
                val failures = mutableListOf<String>()
                val origin = IntArray(2)
                ui.onMain { activity.window.decorView.getLocationOnScreen(origin) }
                val rounds = InstrumentationRegistry.getArguments().getString("captureRounds")
                    ?.toIntOrNull()?.coerceIn(1, 20) ?: 5
                repeat(rounds) { round ->
                    for ((page, label, tag) in listOf(
                        Triple("课表", "本学期", "seg-本学期_下学期"),
                        Triple("抢课", "精确执行", "seg-精确执行_模糊监控")
                    )) {
                        ui.navigate(page)
                        val bounds = Rect(ui.boundsOf(label)).apply {
                            offset(-origin[0], -origin[1])
                            inset(width() / 8, height() / 5)
                        }
                        for (idleMillis in listOf(0L, 1_500L, 4_000L)) {
                            SystemClock.sleep(idleMillis)
                            ui.waitSelected(page)
                            val name = "$prefix-$round-${if (page == "课表") "schedule" else "grab"}-$idleMillis"
                            ui.screenshot(name)
                            val bitmap = BitmapFactory.decodeFile(File(directory, "$name.png").absolutePath)
                            var glyphPixels = 0
                            for (y in bounds.top until bounds.bottom) for (x in bounds.left until bounds.right) {
                                val color = bitmap.getPixel(x, y)
                                if (Color.blue(color) > Color.red(color) + 60 && Color.blue(color) > Color.green(color) + 25) glyphPixels++
                            }
                            bitmap.recycle()
                            var sourceGlyphPixels = 0
                            captures[tag]?.let { (info, source) ->
                                File(directory, "$name-source.png").outputStream().use { source.compress(Bitmap.CompressFormat.PNG, 100, it) }
                                File(directory, "$name-source.txt").writeText(info)
                                for (y in 0 until source.height) for (x in 0 until source.width) {
                                    val color = source.getPixel(x, y)
                                    if (Color.alpha(color) > 100 && Color.blue(color) > Color.red(color) + 60 &&
                                        Color.blue(color) > Color.green(color) + 25) sourceGlyphPixels++
                                }
                            }
                            metadata.put(JSONObject().put("file", "$name.png").put("glyphPixels", glyphPixels)
                                .put("sourceGlyphPixels", sourceGlyphPixels))
                            File(directory, "$prefix-counts.json").writeText(metadata.toString(2))
                            if (glyphPixels <= 50) failures += "$name: $glyphPixels"
                            if (sourceGlyphPixels <= 50) failures += "$name optical source: $sourceGlyphPixels"
                        }
                    }
                }
                assertTrue("Selected labels vanished after repeated tab changes: $failures", failures.isEmpty())
            }
        } finally { com.k2767.course.ui.system.glass.GlassLensCaptureObserver.onCaptured = null }
    }

    private fun captureEntrance(page: String, selectedLabel: String) {
        val sources = java.util.Collections.synchronizedList(mutableListOf<Pair<String, Bitmap>>())
        com.k2767.course.ui.system.glass.GlassLensCaptureObserver.onCaptured = { tag, frame, bitmap ->
            if (tag == "seg-精确执行_模糊监控" || tag == "seg-本学期_下学期")
                sources += "${SystemClock.uptimeMillis()}: $frame" to bitmap
        }
        try {
        DemoUiDriver().use { ui ->
            ui.navigate("课程")
            val activity = requireNotNull(ui.main)
            val decor = activity.window.decorView
            val origin = IntArray(2)
            ui.onMain { decor.getLocationOnScreen(origin) }
            val grab = ui.boundsOf(page, bottomMost = true)
            val frames = mutableListOf<Pair<Long, Bitmap>>()
            val started = SystemClock.uptimeMillis()
            fun touch(action: Int) {
                ui.onMain {
                    MotionEvent.obtain(started, SystemClock.uptimeMillis(), action,
                        (grab.centerX() - origin[0]).toFloat(), (grab.centerY() - origin[1]).toFloat(), 0).also {
                        decor.dispatchTouchEvent(it)
                        it.recycle()
                    }
                }
            }
            touch(MotionEvent.ACTION_DOWN)
            SystemClock.sleep(40)
            touch(MotionEvent.ACTION_UP)
            val released = SystemClock.uptimeMillis()
            for (target in listOf(0L, 50L, 100L, 175L, 250L, 350L, 500L, 700L, 900L, 1200L)) {
                val delay = released + target - SystemClock.uptimeMillis()
                if (delay > 0) SystemClock.sleep(delay)
                val bitmap = Bitmap.createBitmap(decor.width, decor.height, Bitmap.Config.ARGB_8888)
                val latch = CountDownLatch(1)
                var result = PixelCopy.ERROR_UNKNOWN
                ui.onMain {
                    PixelCopy.request(activity.window, bitmap,
                        { result = it; latch.countDown() }, Handler(Looper.getMainLooper()))
                }
                assertTrue("Entrance capture failed", latch.await(3, TimeUnit.SECONDS) && result == PixelCopy.SUCCESS)
                frames += (SystemClock.uptimeMillis() - released) to bitmap
            }
            ui.waitSelected(page)
            val prefix = (InstrumentationRegistry.getArguments().getString("capturePrefix") ?: "entrance") +
                if (page == "课表") "-schedule" else ""
            val directory = File(activity.getExternalFilesDir(null), "entrance-validation").apply { mkdirs() }
            val metadata = JSONArray()
            val labelBounds = Rect(ui.boundsOf(selectedLabel)).apply {
                offset(-origin[0], -origin[1])
                inset(width() / 8, height() / 5)
            }
            val glyphCounts = mutableListOf<Int>()
            frames.forEachIndexed { index, (time, bitmap) ->
                val file = "$prefix-$index.png"
                File(directory, file).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                var glyphPixels = 0
                if (time >= 700L) {
                    for (y in labelBounds.top until labelBounds.bottom) for (x in labelBounds.left until labelBounds.right) {
                        val color = bitmap.getPixel(x, y)
                        if (Color.blue(color) > Color.red(color) + 60 && Color.blue(color) > Color.green(color) + 25) glyphPixels++
                    }
                    glyphCounts += glyphPixels
                }
                metadata.put(JSONObject().put("file", file).put("elapsedMs", time).put("glyphPixels", glyphPixels))
                bitmap.recycle()
            }
            File(directory, "$prefix.json").writeText(metadata.toString(2))
            synchronized(sources) { sources.toList() }.forEachIndexed { index, (info, bitmap) ->
                File(directory, "$prefix-source-$index.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
                File(directory, "$prefix-source-$index.txt").writeText(info)
            }
            assertTrue("Selected label vanished after page entrance: $glyphCounts",
                glyphCounts.isNotEmpty() && glyphCounts.all { it > 50 })
        }
        } finally { com.k2767.course.ui.system.glass.GlassLensCaptureObserver.onCaptured = null }
    }
}
