package com.k2767.course.academic

import android.graphics.Bitmap
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.k2767.course.model.Course
import com.k2767.course.ui.screen.AcademicSupportDialog
import com.k2767.course.ui.screen.CourseListScreen
import com.k2767.course.ui.screen.GrabProScreen
import com.k2767.course.ui.system.GlassWindowHost
import com.k2767.course.ui.theme.CourseSelectorTheme
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Local UI fixtures never initialize a student account or send a school request. */
@RunWith(AndroidJUnit4::class)
class AcademicParityDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun capture(name: String) {
        compose.waitForIdle()
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val directory = File(instrumentation.targetContext.getExternalFilesDir(null), "academic-parity").apply { mkdirs() }
        val bitmap = compose.onRoot().captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        bitmap.recycle()
    }

    @Test fun fourSystemSupportAndLimitsRemainReadableAndTheDialogCloses() {
        val open = mutableStateOf(true)
        compose.setContent { CourseSelectorTheme { GlassWindowHost {
            if (open.value) AcademicSupportDialog(AcademicSystem.QZ_OLD.id) { open.value = false }
        } } }
        compose.onNodeWithText("四类教务支持与限制").assertIsDisplayed()
        compose.onNodeWithText(AcademicCapabilities.ACCOUNT_LIMIT).assertExists()
        capture("support-top")
        compose.onNodeWithText("旧强智 · 当前学校", useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        capture("support-limits")
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithText("四类教务支持与限制").assertDoesNotExist()
    }

    @Test fun immediateManualQueueAndSmartTargetBothReachTheirStartActions() {
        val fuzzy = mutableStateOf(false)
        var starts = 0
        var monitors = 0
        val manual = Course().apply { name = "高等数学"; teacher = ""; time = ""; classId = "" }
        compose.setContent { CourseSelectorTheme { GlassWindowHost {
            GrabProScreen(isRunning = false, successCount = 0, failCount = 0, retryCount = 0,
                targetCourseName = if (fuzzy.value) "高等数学" else null, targetCourseTeacher = "", logText = "",
                interval = "1500", onIntervalChange = {}, maxRetry = "100", onMaxRetryChange = {},
                onStart = { starts++ }, onStop = {}, onClearLog = {}, queue = listOf(manual),
                supportsParallel = false, supportsImmediateManual = true, systemNotice = "旧强智按账号串行提交",
                isFuzzyMatchMode = fuzzy.value, fuzzyMatchTarget = if (fuzzy.value) "高等数学" else null,
                onStartFuzzyMatch = { monitors++ })
        } } }
        compose.onNodeWithText("开始执行").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, starts); fuzzy.value = true }
        compose.onNodeWithText("开始监控").performScrollTo().assertIsEnabled().performClick()
        compose.runOnIdle { assertEquals(1, monitors) }
        capture("smart-target")
    }

    @Test fun supportControlsFitANarrowAndShortViewport() {
        val open = mutableStateOf(true)
        compose.setContent {
            val compact = Configuration(LocalConfiguration.current).apply { screenWidthDp = 320; screenHeightDp = 568; smallestScreenWidthDp = 320 }
            CompositionLocalProvider(LocalConfiguration provides compact) {
                CourseSelectorTheme { Box(Modifier.requiredSize(320.dp, 568.dp).testTag("compact-viewport")) {
                    GlassWindowHost { if (open.value) AcademicSupportDialog(AcademicSystem.ZF_OLD.id) { open.value = false } }
                } }
            }
        }
        compose.onNodeWithText(AcademicCapabilities.SCHOOL_LIMIT, useUnmergedTree = true).performScrollTo().assertIsDisplayed()
        val viewport = compose.onNodeWithTag("compact-viewport").getUnclippedBoundsInRoot()
        val button = compose.onNodeWithText("关闭").getUnclippedBoundsInRoot()
        assertTrue(button.left >= viewport.left && button.right <= viewport.right)
        assertTrue(button.top >= viewport.top && button.bottom <= viewport.bottom)
        capture("support-compact")
        compose.onNodeWithText("关闭").performClick()
        compose.onNodeWithText("四类教务支持与限制").assertDoesNotExist()
    }

    @Test fun sameCourseInTwoRoundsExpandsAndSelectsIndependently() {
        val courses = listOf("a", "b").map { round -> Course().apply {
            courseId = "C"; classId = "S"; name = "高等数学"; teacher = "教师$round"; time = "周一 1-2 节"; credit = "2"
            completeParams["academic_system"] = AcademicSystem.ZF_OLD.id
            completeParams["academic_scope_id"] = round
            completeParams["academic_course_id"] = "C"
        } }
        val selected = mutableStateOf(emptySet<String>())
        compose.setContent { CourseSelectorTheme { GlassWindowHost {
            CourseListScreen(courses = courses, isLoading = false, onRefresh = {}, onSearch = {},
                onCourseSelect = {}, onAutoGrab = {}, isDetailsReady = true, isMultiSelectMode = true,
                selectedClassIds = selected.value, onToggleSelection = { id, checked ->
                    selected.value = if (checked) selected.value - id else selected.value + id
                })
        } } }
        compose.onAllNodesWithContentDescription("展开教学班").assertCountEquals(2)
        compose.onAllNodesWithContentDescription("展开教学班")[0].performClick()
        compose.onAllNodesWithContentDescription("展开教学班")[0].performClick()
        compose.onAllNodesWithContentDescription("收起教学班").assertCountEquals(2)
        compose.onAllNodes(isToggleable())[0].performClick()
        compose.onAllNodes(isToggleable())[0].assertIsOn()
        compose.onAllNodes(isToggleable())[1].assertIsOff()
        compose.runOnIdle { assertEquals(setOf(courses[0].catalogSelectionKey()), selected.value) }
        capture("course-multiselect")
    }
}
