package com.k2767.course.ui

import android.os.SystemClock
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import com.k2767.course.ui.system.glass.GlassLensCaptureObserver
import java.io.File
import java.util.Collections
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 31, maxSdkVersion = 32)
class GlassCapturePerformanceDeviceTest {
    @Test fun measurePrimaryOpticalStagesOnTheLivePage() {
        val events = Collections.synchronizedList(mutableListOf<JSONObject>())
        val prefix = InstrumentationRegistry.getArguments().getString("capturePrefix") ?: "optics"
        var phase = "entrance"
        GlassLensCaptureObserver.onTiming = { tag, stage, nanos ->
            events += JSONObject().put("phase", phase).put("tag", tag).put("stage", stage)
                .put("durationMs", nanos / 1_000_000.0).put("atMs", SystemClock.uptimeMillis())
        }
        try {
            DemoUiDriver().use { ui ->
                ui.navigate("抢课")
                val activity = requireNotNull(ui.main)
                val decor = activity.window.decorView
                val origin = IntArray(2)
                ui.onMain { decor.getLocationOnScreen(origin) }
                val button = ui.boundsOf("开始执行")
                ui.screenshot("$prefix-start-rest")
                repeat(3) { round ->
                    phase = "press-$round"
                    val down = SystemClock.uptimeMillis()
                    fun touch(action: Int, x: Int) {
                        ui.onMain {
                            MotionEvent.obtain(down, SystemClock.uptimeMillis(), action,
                                (x - origin[0]).toFloat(), (button.centerY() - origin[1]).toFloat(), 0).also {
                                decor.dispatchTouchEvent(it); it.recycle()
                            }
                        }
                    }
                    touch(MotionEvent.ACTION_DOWN, button.centerX())
                    SystemClock.sleep(180)
                    val outside = (button.left - button.width()).coerceAtLeast(0)
                    touch(MotionEvent.ACTION_MOVE, outside)
                    touch(MotionEvent.ACTION_UP, outside)
                    SystemClock.sleep(500)
                }
                phase = "fan"
                ui.longClick("开始执行")
                ui.waitText("关闭操作菜单")
                ui.screenshot("$prefix-start-fan")
                ui.back()
                ui.waitText("关闭操作菜单", false)
                SystemClock.sleep(500)
                val directory = File(activity.getExternalFilesDir(null), "performance-validation").apply { mkdirs() }
                File(directory, "$prefix-stages.json").writeText(JSONArray(synchronized(events) { events.toList() }).toString(2))
            }
        } finally { GlassLensCaptureObserver.onTiming = null }
    }
}
