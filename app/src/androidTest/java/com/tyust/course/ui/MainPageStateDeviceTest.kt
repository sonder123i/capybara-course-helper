package com.tyust.course.ui

import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tyust.course.BuildConfig
import com.tyust.course.MainActivity
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.test.platform.app.InstrumentationRegistry
import android.graphics.Bitmap
import android.os.Build
import java.io.File

/** The standalone preview uses DemoData; this class must never exercise a real account. */
@RunWith(AndroidJUnit4::class)
class MainPageStateDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<MainActivity>()

    companion object {
        @BeforeClass @JvmStatic fun requirePreview() { assumeTrue(BuildConfig.UI_PREVIEW) }
    }

    @Before fun useControlledFrameClock() {
        // The whole page also owns Android draw callbacks. Advance animation time explicitly
        // so Compose's eager test clock does not race those real window frames at startup.
        compose.mainClock.autoAdvance = false
        settlePage()
    }

    private fun settlePage() {
        compose.mainClock.advanceTimeBy(1200)
        compose.waitForIdle()
    }

    private fun navigate(label: String) {
        compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag("main-navigation"))).performClick()
        settlePage()
    }

    @Test fun captureFiveMainPages() {
        val prefix = InstrumentationRegistry.getArguments().getString("capturePrefix") ?: "api${Build.VERSION.SDK_INT}-default"
        val directory = File(compose.activity.getExternalFilesDir(null), "liquid-validation").apply { mkdirs() }
        listOf("课程", "课表", "抢课", "成绩", "设置").forEachIndexed { index, label ->
            navigate(label)
            compose.onNode(hasText(label) and hasAnyAncestor(hasTestTag("main-navigation"))).assertIsSelected()
            Thread.sleep(350) // Allow the asynchronous API 31/32 source capture to publish its final frame.
            File(directory, "$prefix-page-$index.png").outputStream().use { output ->
                compose.onRoot().captureToImage().asAndroidBitmap().compress(Bitmap.CompressFormat.PNG, 100, output)
            }
        }
    }

    @Test fun childTabsSurvivePageChangesAndActivityRecreation() {
        compose.onNode(hasText("已选") and isSelectable()).performClick()
        settlePage()
        navigate("成绩")
        compose.onNode(hasText("总体") and isSelectable()).performClick()
        settlePage()
        navigate("课程")
        compose.onNode(hasText("已选") and isSelectable()).assertIsSelected()
        navigate("成绩")
        compose.onNode(hasText("总体") and isSelectable()).assertIsSelected()
        compose.activityRule.scenario.recreate()
        settlePage()
        compose.onNode(hasText("总体") and isSelectable()).assertIsSelected()
        navigate("课程")
        compose.onNode(hasText("已选") and isSelectable()).assertIsSelected()
    }

    @Test fun selectedSemesterSurvivesLeavingTheGradesPage() {
        navigate("成绩")
        compose.onNode(hasText("学期") and isSelectable()).performClick()
        settlePage()
        compose.onNode(hasText("2025-2026-2") and isEnabled()).performTouchInput { click() }
        settlePage()
        compose.onNodeWithText("2024-2025-1").performClick()
        settlePage()
        navigate("设置")
        navigate("成绩")
        compose.onNode(hasText("2024-2025-1") and isEnabled()).assertIsDisplayed()
        compose.onNodeWithText("正在加载学期成绩…").assertDoesNotExist()
    }
}
