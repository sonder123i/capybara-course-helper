package com.tyust.course.ui

import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** Main-window state uses real display time; deterministic gestures have their own fixtures. */
@RunWith(AndroidJUnit4::class)
class MainPageStateDeviceTest {
    @Test fun captureFiveMainPages() {
        DemoUiDriver().use { ui ->
            val prefix = InstrumentationRegistry.getArguments().getString("capturePrefix") ?: "pages"
            listOf("课程", "课表", "抢课", "成绩", "设置").forEachIndexed { index, page ->
                ui.navigate(page)
                ui.screenshot("$prefix-$index")
            }
        }
    }

    @Test fun childTabsSurvivePageChangesAndActivityRecreation() {
        DemoUiDriver().use { ui ->
            ui.click("已选")
            ui.navigate("成绩")
            ui.click("总体")
            ui.navigate("课程")
            ui.waitSelected("已选")
            ui.navigate("成绩")
            ui.waitSelected("总体")
            val old = ui.main
            ui.onMain { requireNotNull(old).recreate() }
            ui.await("Activity recreation did not finish") { ui.main !== old }
            ui.waitSelected("总体")
            ui.navigate("课程")
            ui.waitSelected("已选")
        }
    }

    @Test fun selectedSemesterSurvivesLeavingTheGradesPage() {
        DemoUiDriver().use { ui ->
            ui.navigate("成绩")
            ui.click("学期")
            ui.click("2025-2026-2")
            ui.click("2024-2025-1")
            ui.navigate("设置")
            ui.navigate("成绩")
            ui.waitText("2024-2025-1")
            ui.waitText("正在加载学期成绩…", false)
        }
    }
}
