package com.k2767.course.ui

import android.content.pm.ActivityInfo
import android.graphics.Rect
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.k2767.course.BuildConfig
import com.k2767.course.manager.AppThemeMode
import com.k2767.course.manager.AppearanceSettingsManager
import com.k2767.course.manager.ScheduleSettingsManager
import com.k2767.course.manager.WallpaperMode
import com.k2767.course.manager.WallpaperPreset
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Real activity, real glass controls and normal system animation speed; demo business data only. */
@RunWith(AndroidJUnit4::class)
class MotionPolishFlowDeviceTest {
    @Test fun recordNavigationJellyDetailsAndActualStartButton() {
        assumeTrue(BuildConfig.UI_PREVIEW)
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val oldMode = AppearanceSettingsManager.mode
        val oldPreset = AppearanceSettingsManager.wallpaper
        val oldColor = AppearanceSettingsManager.customColor
        val oldTheme = AppearanceSettingsManager.themeMode
        val oldGlass = AppearanceSettingsManager.glassEffectEnabled
        val courseId = "motion-polish-visual-fixture"
        val manager = ScheduleSettingsManager.getInstance()
        try {
            instrumentation.runOnMainSync {
                AppearanceSettingsManager.updateWallpaper(WallpaperPreset.Aurora)
                AppearanceSettingsManager.updateThemeMode(AppThemeMode.Light)
                AppearanceSettingsManager.updateGlassEffect(true)
            }
            DemoUiDriver().use { ui ->
                fun tap(rect: Rect, delay: Long = 160) {
                    val display = requireNotNull(ui.foreground).display!!.displayId
                    ui.shell("input -d $display tap ${rect.centerX()} ${rect.centerY()}")
                    SystemClock.sleep(delay)
                }
                DisplayRecording(requireNotNull(ui.main), "motion-01-navigation-segments").use {
                    listOf("课表", "抢课", "成绩", "设置", "课程").forEach { label ->
                        ui.navigate(label)
                        tap(ui.boundsOf(label, bottomMost = true), 650)
                    }
                    val tabs = listOf("课表", "设置", "课程", "成绩").map { ui.boundsOf(it, bottomMost = true) }
                    tabs.forEach { tap(it, 70) }
                    ui.waitSelected("成绩")
                    SystemClock.sleep(700)
                    listOf("总体", "考试", "学期", "考试", "总体", "学期").forEach { ui.click(it) }
                    val first = ui.boundsOf("学期")
                    val last = ui.boundsOf("考试")
                    val display = requireNotNull(ui.main).display!!.displayId
                    ui.shell("input -d $display swipe ${first.centerX()} ${first.centerY()} ${last.centerX()} ${last.centerY()} 600")
                    ui.shell("input -d $display swipe ${last.centerX()} ${last.centerY()} ${first.centerX()} ${first.centerY()} 420")
                    SystemClock.sleep(900)
                    ui.screenshot("motion-grades-jelly-settled")
                }
                ui.navigate("课表")
                ui.onMain {
                    manager.addCustomCourse(ScheduleSettingsManager.CustomCourse(courseId, "交互设计实验", "明理楼 A302",
                        "林老师", 2, 1, 2, "1-18周"))
                }
                ui.waitText("交互设计实验")
                DisplayRecording(requireNotNull(ui.main), "motion-02-course-details").use {
                    ui.click("交互设计实验")
                    ui.waitText("关闭课程详情")
                    ui.screenshot("motion-detail-light")
                    ui.click("编辑课程")
                    ui.back()
                    ui.waitText("关闭课程详情")
                    ui.onMain { AppearanceSettingsManager.updateThemeMode(AppThemeMode.Dark) }
                    SystemClock.sleep(1000)
                    ui.waitText("关闭课程详情")
                    ui.screenshot("motion-detail-dark")
                    ui.onMain { AppearanceSettingsManager.updateThemeMode(AppThemeMode.Light) }
                    SystemClock.sleep(900)
                    val handle = ui.boundsOf("下拉关闭课程详情")
                    val activity = requireNotNull(ui.foreground)
                    val display = activity.display!!.displayId
                    val dragEnd = minOf(handle.centerY() + 1000, activity.window.decorView.height - 72)
                    ui.shell("input -d $display swipe ${handle.centerX()} ${handle.centerY()} ${handle.centerX()} $dragEnd 400")
                    ui.waitText("关闭课程详情", false)
                    ui.click("交互设计实验")
                    ui.click("更多课程操作")
                    ui.screenshot("motion-detail-more")
                    ui.click("删除课程")
                    ui.waitText("关闭课程详情", false)
                    ui.waitText("撤销")
                    ui.click("撤销")
                    ui.waitText("交互设计实验")
                    assertTrue(manager.getCustomCourses().any { it.id == courseId })
                }
                ui.click("交互设计实验")
                val oldOrientation = requireNotNull(ui.main).requestedOrientation
                try {
                    ui.onMain { ui.main!!.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                    ui.await("Activity did not enter landscape") {
                        ui.main?.window?.decorView?.let { it.width > it.height } == true
                    }
                    ui.waitText("关闭课程详情")
                    ui.screenshot("motion-detail-real-landscape")
                    ui.click("关闭课程详情")
                } finally {
                    ui.onMain { ui.main!!.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT }
                    ui.await("Activity did not return to portrait") {
                        ui.main?.window?.decorView?.let { it.height > it.width } == true
                    }
                    ui.onMain { ui.main!!.requestedOrientation = oldOrientation }
                }
                ui.navigate("抢课")
                DisplayRecording(requireNotNull(ui.main), "motion-03-start-button").use {
                    ui.screenshot("motion-start-light")
                    ui.longClick("开始执行")
                    ui.screenshot("motion-start-fan")
                    ui.click("切换为模糊监控")
                    ui.click("精确执行")
                    ui.onMain { AppearanceSettingsManager.updateThemeMode(AppThemeMode.Dark) }
                    SystemClock.sleep(1000)
                    ui.screenshot("motion-start-dark")
                    ui.onMain { AppearanceSettingsManager.updateGlassEffect(false) }
                    SystemClock.sleep(800)
                    ui.screenshot("motion-start-glass-off")
                    ui.onMain {
                        AppearanceSettingsManager.updateGlassEffect(true)
                        AppearanceSettingsManager.updateThemeMode(AppThemeMode.Light)
                    }
                    SystemClock.sleep(1000)
                    ui.screenshot("motion-start-restored")
                }
            }
        } finally {
            instrumentation.runOnMainSync {
                manager.removeCustomCourse(courseId)
                AppearanceSettingsManager.updateGlassEffect(oldGlass)
                AppearanceSettingsManager.updateThemeMode(oldTheme)
                AppearanceSettingsManager.updateWallpaper(oldPreset)
                when (oldMode) {
                    WallpaperMode.Image -> AppearanceSettingsManager.useImageWallpaper()
                    WallpaperMode.Color -> oldColor?.let { AppearanceSettingsManager.updateCustomColor(it) }
                    WallpaperMode.Preset -> Unit
                }
            }
        }
    }
}
