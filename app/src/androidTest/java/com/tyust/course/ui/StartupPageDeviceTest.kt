package com.tyust.course.ui

import android.content.Context
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tyust.course.BuildConfig
import com.tyust.course.manager.StartupPage
import com.tyust.course.manager.StartupPagePreferences
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Runs the real main activity and settings route in the separate, offline demo package. */
@RunWith(AndroidJUnit4::class)
class StartupPageDeviceTest {
    private fun withPreferences(block: (StartupPagePreferences) -> Unit) {
        assumeTrue("Use the isolated demo variant", BuildConfig.UI_PREVIEW)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val raw = context.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
        val preferences = StartupPagePreferences(raw)
        val wasSet = raw.contains("startup_page")
        val previous = preferences.read()
        try { block(preferences) } finally {
            if (wasSet) preferences.write(previous) else raw.edit().remove("startup_page").commit()
        }
    }

    @Test fun settingsSelectionPersistsWithoutNavigatingAwayAndNextLaunchOpensSchedule() = withPreferences { preferences ->
        preferences.write(StartupPage.Courses)
        DemoUiDriver().use { ui ->
            ui.navigate("设置")
            scrollToStartupPage(ui)
            ui.click("启动首屏")
            ui.waitText("首屏页面")
            ui.click("课程")
            ui.waitText("课表")
            ui.screenshot("startup-picker-expanded")
            ui.click("课表")
            ui.await("The selected start page was not saved") { preferences.read() == StartupPage.Schedule }
            ui.screenshot("startup-picker-schedule")
            ui.click("完成")
            ui.waitText("课表 · 下次启动时显示")
            ui.waitSelected("设置")
            ui.screenshot("startup-settings-schedule")
        }
        DemoUiDriver().use { ui ->
            ui.waitSelected("课表")
            ui.screenshot("startup-schedule")
        }
    }

    @Test fun activityRecreationKeepsTheCurrentPageAndEveryPageCanBeAStartupDestination() = withPreferences { preferences ->
        for (page in StartupPage.entries) {
            preferences.write(page)
            DemoUiDriver().use { ui -> ui.waitSelected(page.label) }
        }
        preferences.write(StartupPage.Schedule)
        DemoUiDriver().use { ui ->
            ui.waitSelected("课表")
            ui.navigate("成绩")
            val original = requireNotNull(ui.main)
            ui.onMain { original.recreate() }
            ui.await("The recreated activity did not resume") { ui.main !== original && ui.foreground === ui.main }
            ui.waitSelected("成绩")
            assertEquals(StartupPage.Schedule, preferences.read())
        }
    }

    @Test fun unknownStartupPreferenceFallsBackToCourses() = withPreferences {
        InstrumentationRegistry.getInstrumentation().targetContext.getSharedPreferences("app_prefs", Context.MODE_PRIVATE)
            .edit().putString("startup_page", "removed-page").commit()
        DemoUiDriver().use { ui -> ui.waitSelected("课程") }
    }

    private fun scrollToStartupPage(ui: DemoUiDriver) {
        repeat(6) {
            if (ui.hasText("启动首屏")) return
            val activity = requireNotNull(ui.foreground)
            val width = activity.window.decorView.width
            val height = activity.window.decorView.height
            ui.shell("input -d ${activity.display!!.displayId} swipe ${width / 2} ${height * 3 / 4} ${width / 2} ${height / 3} 300")
            SystemClock.sleep(350)
        }
        ui.waitText("启动首屏")
    }
}
