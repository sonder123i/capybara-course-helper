package com.tyust.course.ui

import android.app.UiModeManager
import android.content.Context
import android.os.SystemClock
import android.provider.Settings
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tyust.course.manager.AppearanceSettingsManager
import com.tyust.course.manager.WallpaperPreset
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class MainVisualDeviceTest {
    @Test fun captureLightDarkWallpaperNarrowLandscapeAndLargeText() {
        DemoUiDriver().use { ui ->
            val context = InstrumentationRegistry.getInstrumentation().targetContext
            val resolver = context.contentResolver
            val appearancePrefs = context.getSharedPreferences("appearance_settings", Context.MODE_PRIVATE)
            val originalAppearance = appearancePrefs.all.toMap()
            val previousFont = Settings.System.getString(resolver, Settings.System.FONT_SCALE)
            val previousMotion = Settings.Global.getString(resolver, Settings.Global.ANIMATOR_DURATION_SCALE)
            val previousNight = (context.getSystemService(Context.UI_MODE_SERVICE) as UiModeManager).nightMode
            val display = requireNotNull(ui.main).display!!.displayId
            val originalSize = ui.shell("wm size -d $display")
            val override = Regex("Override size: (\\d+x\\d+)").find(originalSize)?.groupValues?.get(1) ?: "reset"
            fun settle() { SystemClock.sleep(2200); ui.waitText("课程") }
            fun capture(prefix: String) {
                for (page in listOf("课程", "课表", "抢课", "成绩", "设置")) {
                    ui.navigate(page)
                    ui.screenshot("$prefix-$page")
                }
            }
            try {
                ui.shell("cmd uimode night no")
                ui.shell("settings put system font_scale 1.0")
                ui.onMain { AppearanceSettingsManager.updateWallpaper(WallpaperPreset.Aurora) }
                settle()
                capture("light")
                ui.shell("cmd uimode night yes")
                settle()
                capture("dark")
                ui.onMain { AppearanceSettingsManager.updateWallpaper(WallpaperPreset.Aurora) }
                settle()
                capture("wallpaper")
                ui.shell("cmd uimode night no")
                ui.onMain { AppearanceSettingsManager.updateWallpaper(WallpaperPreset.Aurora) }
                ui.shell("wm size 840x1800 -d $display")
                settle()
                assertTrue("Narrow layout was not applied", requireNotNull(ui.main).resources.configuration.screenWidthDp <= 360)
                capture("narrow")
                ui.shell("wm size $override -d $display")
                ui.shell("settings put system font_scale 1.6")
                settle()
                assertTrue("Large text was not applied", requireNotNull(ui.main).resources.configuration.fontScale > 1.5f)
                capture("large-text")
                ui.navigate("课表")
                val activity = requireNotNull(ui.main)
                val y = (activity.window.decorView.height * .48f).toInt()
                val width = activity.window.decorView.width
                ui.shell("input -d $display swipe ${width * 8 / 10} $y ${width * 2 / 10} $y 700")
                SystemClock.sleep(800)
                ui.screenshot("large-text-schedule-horizontal")
                ui.shell("settings put system font_scale 1.0")
                ui.shell("wm size 2400x1080 -d $display")
                settle()
                assertTrue("Landscape was not applied", requireNotNull(ui.main).resources.configuration.screenWidthDp > requireNotNull(ui.main).resources.configuration.screenHeightDp)
                capture("landscape")
                ui.shell("wm size $override -d $display")
                ui.shell("settings put global animator_duration_scale 0")
                settle()
                capture("reduced-motion")
            } finally {
                ui.shell("wm size $override -d $display")
                if (previousFont == null) ui.shell("settings delete system font_scale") else ui.shell("settings put system font_scale $previousFont")
                if (previousMotion == null) ui.shell("settings delete global animator_duration_scale") else ui.shell("settings put global animator_duration_scale $previousMotion")
                ui.shell("cmd uimode night ${when(previousNight) { UiModeManager.MODE_NIGHT_YES -> "yes"; UiModeManager.MODE_NIGHT_NO -> "no"; else -> "auto" }}")
                ui.onMain {
                    appearancePrefs.edit().clear().apply {
                        originalAppearance.forEach { (key, value) ->
                            when (value) {
                                is String -> putString(key, value)
                                is Boolean -> putBoolean(key, value)
                                is Int -> putInt(key, value)
                                is Long -> putLong(key, value)
                                is Float -> putFloat(key, value)
                            }
                        }
                    }.commit()
                    AppearanceSettingsManager.initialize(context)
                }
            }
        }
    }
}
