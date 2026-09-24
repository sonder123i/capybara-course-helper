package com.k2767.course.ui

import android.os.SystemClock
import android.content.res.Configuration
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.WindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.k2767.course.BuildConfig
import com.k2767.course.academic.AcademicCapabilities
import com.k2767.course.academic.AcademicSystem
import com.k2767.course.manager.AppearanceSettingsManager
import com.k2767.course.manager.WallpaperMode
import com.k2767.course.model.SchoolConfig
import com.k2767.course.ui.screen.AddSchoolDialog
import com.k2767.course.ui.screen.EditSchoolConfigDialog
import com.k2767.course.ui.screen.UsageNoticeDialog
import com.k2767.course.ui.screen.MeasuredGradesHeader
import com.k2767.course.ui.screen.WeekHeaderCompact
import com.k2767.course.ui.system.*
import com.k2767.course.ui.theme.CourseSelectorTheme
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class FeedbackFixesDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun isolatedVariantOnly() = assumeTrue(BuildConfig.UI_PREVIEW)

    @Test fun addSchoolWaitsForRetractionAndRepeatedTapsOpenOneDialog() {
        var opened = 0
        val dialog = mutableStateOf(false)
        compose.setContent {
            CourseSelectorTheme {
                GlassWindowHost(Modifier.testTag("feedback-frame")) {
                    Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp)) {
                        Text("学校选择")
                        Spacer(Modifier.height(24.dp))
                        SystemPicker(listOf("测试大学", "另一所大学"), 0, {},
                            modifier = Modifier.testTag("feedback-picker"),
                            actionLabel = "添加学校", onAction = { opened++; dialog.value = true })
                    }
                    if (dialog.value) SystemDialog(onDismissRequest = { dialog.value = false },
                        title = { Text("添加学校弹窗") },
                        confirmButton = { SystemPrimaryButton("完成", { dialog.value = false }) }) {
                        Text("下拉浮层已完成回收")
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        DisplayRecording(compose.activity, "feedback-picker-retraction").use {
            compose.onNodeWithTag("feedback-picker").performTouchInput { click() }
            movieFrames(75)
            capture("picker-open")
            compose.onNodeWithText("添加学校").performTouchInput { doubleClick() }
            movieFrames(5)
            assertEquals("The action must wait for the closing spring", 0, opened)
            compose.onNodeWithText("添加学校弹窗").assertDoesNotExist()
            capture("picker-retracting")
            movieFrames(110)
            compose.onNodeWithText("添加学校弹窗").assertIsDisplayed()
            assertEquals(1, opened)
            capture("picker-dialog-after-retraction")
        }
    }

    @Test fun leavingThePageCancelsTheDeferredSchoolAction() {
        val visible = mutableStateOf(true)
        var opened = 0
        compose.setContent {
            CourseSelectorTheme {
                GlassWindowHost {
                    if (visible.value) Column(Modifier.fillMaxSize().padding(24.dp)) {
                        Spacer(Modifier.height(80.dp))
                        SystemPicker(listOf("测试大学"), 0, {}, Modifier.testTag("cancel-picker"),
                            actionLabel = "添加学校", onAction = { opened++ })
                    }
                }
            }
        }
        compose.onNodeWithTag("cancel-picker").performTouchInput { click() }
        compose.onNodeWithText("添加学校").assertIsDisplayed()
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("添加学校").performTouchInput { click() }
        compose.runOnUiThread { visible.value = false }
        compose.mainClock.advanceTimeBy(2500)
        compose.waitForIdle()
        assertEquals(0, opened)
    }

    @Test fun pickerFitsWidthFontThemeAndGlassCombinations() {
        val width = mutableIntStateOf(320)
        val font = mutableFloatStateOf(1f)
        val night = mutableStateOf(false)
        val selected = mutableIntStateOf(0)
        val oldGlass = AppearanceSettingsManager.glassEffectEnabled
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, font.floatValue)) {
                CourseSelectorTheme(darkTheme = night.value) {
                    Box(Modifier.requiredWidth(width.intValue.dp).height(680.dp).testTag("feedback-frame")) {
                        GlassWindowHost {
                            Column(Modifier.fillMaxSize().systemBarsPadding().padding(20.dp)) {
                                Text("教务类型")
                                Spacer(Modifier.height(32.dp))
                                SystemPicker(listOf("自动识别", "新正方", "旧正方", "新强智", "旧强智"), selected.intValue,
                                    { selected.intValue = it }, Modifier.testTag("matrix-picker"))
                                Text("下方内容保持原位", Modifier.testTag("matrix-following"))
                            }
                        }
                    }
                }
            }
        }
        try {
            for (w in listOf(320, 360, 412)) for (f in listOf(1f, 1.6f)) for (dark in listOf(false, true)) for (glass in listOf(false, true)) {
                compose.runOnIdle {
                    width.intValue = w; font.floatValue = f; night.value = dark
                    selected.intValue = 0; AppearanceSettingsManager.updateGlassEffect(glass)
                }
                val baseline = compose.onNodeWithTag("matrix-following").fetchSemanticsNode().boundsInRoot.top
                compose.onNodeWithTag("matrix-picker").performTouchInput { click() }
                val frame = compose.onNodeWithTag("feedback-frame").fetchSemanticsNode().boundsInRoot
                val option = compose.onNode(hasText("新正方") and isSelectable())
                option.assertIsDisplayed()
                val bounds = option.fetchSemanticsNode().boundsInRoot
                assertTrue(bounds.left >= frame.left && bounds.right <= frame.right + 1)
                assertEquals(baseline, compose.onNodeWithTag("matrix-following").fetchSemanticsNode().boundsInRoot.top, 1f)
                if (w == 320 && f == 1.6f) capture("picker-320-large-" + if (dark) "dark-$glass" else "light-$glass")
                option.performClick()
                compose.waitForIdle()
                assertEquals(1, selected.intValue)
            }
        } finally { compose.runOnIdle { AppearanceSettingsManager.updateGlassEffect(oldGlass) } }
    }

    @Test fun allFiveSchoolTypesSurviveAddressParsingAndFormSave() {
        val case = mutableIntStateOf(0)
        val show = mutableStateOf(true)
        var saved: SchoolConfig? = null
        compose.setContent {
            CourseSelectorTheme {
                GlassWindowHost {
                    key(case.intValue) {
                        if (show.value) AddSchoolDialog(onDismiss = { show.value = false }, onConfirm = {
                            saved = it.toSchoolConfig(); show.value = false
                        })
                    }
                }
            }
        }
        for ((index, type) in AcademicCapabilities.selectableSystems.withIndex()) {
            if (index > 0) compose.runOnIdle { case.intValue = index; saved = null; show.value = true }
            compose.onNodeWithTag("school-system-picker", useUnmergedTree = true).performScrollTo().performTouchInput { click() }
            compose.onNode(hasText(AcademicCapabilities.selectionLabel(type)) and isSelectable()).performClick()
            compose.onAllNodes(hasSetTextAction())[0].performScrollTo().performTextInput("http://jw.example.edu.cn:8080/custom/framework/xsMainV.htmlx")
            compose.onNodeWithText("解析网址").performScrollTo().assertIsEnabled().performClick()
            compose.onNodeWithText("添加", substring = false).assertIsEnabled().performClick()
            compose.waitForIdle()
            val restored = SchoolConfig.fromJson(requireNotNull(saved).toJson())
            assertEquals(type.id, restored.academicSystem)
            assertEquals("/custom", restored.basePath)
            assertEquals("http", restored.protocol)
            assertEquals("jw.example.edu.cn:8080", restored.domain)
        }
    }

    @Test fun customImageHeadersDoNotWashOutTheWallpaperAtTheirEdges() {
        assumeTrue("This visual regression uses an imported image in the isolated preview app",
            AppearanceSettingsManager.mode == WallpaperMode.Image)
        compose.waitUntil(5_000) { AppearanceSettingsManager.style.image != null }
        val page = mutableIntStateOf(-1)
        val collapse = mutableFloatStateOf(0f)
        val night = mutableStateOf(false)
        compose.setContent {
            val density = LocalDensity.current
            val actualWindow = LocalWindowInfo.current
            val previewWindow = remember(actualWindow, density) {
                object : WindowInfo by actualWindow {
                    override val containerSize = with(density) {
                        IntSize(320.dp.roundToPx(), 620.dp.roundToPx())
                    }
                }
            }
            val configuration = Configuration(LocalConfiguration.current).apply {
                screenWidthDp = 320
                screenHeightDp = 620
                smallestScreenWidthDp = 320
            }
            CompositionLocalProvider(LocalConfiguration provides configuration, LocalWindowInfo provides previewWindow) {
                CourseSelectorTheme(darkTheme = night.value) {
                    Box(Modifier.requiredWidth(320.dp).height(620.dp)) {
                        GlassWindowHost(Modifier.testTag("feedback-frame")) {
                            when (page.intValue) {
                                0 -> MeasuredGradesHeader("7 门课程", listOf("学期", "总体", "考试"), 0, {},
                                    collapse.floatValue, LocalAppBackdrop.current, true, true, false, {}, {}, { _, _ -> })
                                1 -> WeekHeaderCompact(currentWeek = 1, onPrevClick = {}, onNextClick = {},
                                    collapseFraction = collapse.floatValue,
                                    sampleBackdrop = LocalAppBackdrop.current)
                            }
                        }
                    }
                }
            }
        }
        val root = compose.onNodeWithTag("feedback-frame")
        for (dark in listOf(false, true)) {
            compose.runOnIdle { page.intValue = -1; night.value = dark; collapse.floatValue = 0f }
            val baseline = root.captureToImage().asAndroidBitmap()
            val scale = baseline.width / 320f
            for (header in 0..1) {
                compose.runOnIdle { page.intValue = header; collapse.floatValue = 0f }
                val expanded = root.captureToImage().asAndroidBitmap()
                for (xDp in listOf(2f, 318f)) for (yDp in listOf(45f, 85f, 125f)) {
                    val x = (xDp * scale).toInt().coerceIn(0, baseline.width - 1)
                    val y = (yDp * scale).toInt().coerceIn(0, baseline.height - 1)
                    val before = baseline.getPixel(x, y)
                    val after = expanded.getPixel(x, y)
                    val difference = listOf(0, 8, 16).maxOf { shift ->
                        kotlin.math.abs(((before shr shift) and 255) - ((after shr shift) and 255))
                    }
                    assertTrue("Header $header/$dark adds a pale sheet at $xDp,$yDp (difference=$difference)", difference <= 6)
                }
                capture("image-header-$header-$dark-expanded")
                compose.runOnIdle { collapse.floatValue = 0.65f }
                capture("image-header-$header-$dark-moving")
                compose.runOnIdle { collapse.floatValue = 1f }
                capture("image-header-$header-$dark-collapsed")
            }
        }
    }

    @Test fun editingTheExistingCompatibilityTypeKeepsItAndItsCustomPaths() {
        val existing = SchoolConfig("existing", "兼容大学", "jw.example.edu.cn", "https").apply {
            academicSystem = AcademicSystem.LEGACY_ZF.id
            courseListPath = "/custom/list"
        }
        var saved: SchoolConfig? = null
        compose.setContent {
            CourseSelectorTheme { GlassWindowHost {
                EditSchoolConfigDialog(existing, {}, { saved = it })
            } }
        }
        compose.onNodeWithText("保存", substring = false).performClick()
        compose.runOnIdle {
            assertEquals(AcademicSystem.LEGACY_ZF.id, saved?.academicSystem)
            assertEquals("/custom/list", saved?.courseListPath)
        }
    }

    @Test fun privacyExplanationIsReadableAtLargeFontAndDefaultsOn() {
        var choice: Boolean? = null
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.6f)) {
                CourseSelectorTheme {
                    Box(Modifier.requiredWidth(320.dp).height(560.dp).testTag("feedback-frame")) {
                        GlassWindowHost { UsageNoticeDialog { choice = it } }
                    }
                }
            }
        }
        compose.onNodeWithText("匿名使用统计").assertIsDisplayed()
        compose.onNodeWithText("参与匿名统计").performScrollTo().assertIsDisplayed()
        capture("usage-notice-320-large")
        compose.onNodeWithTag("usage-notice-confirm").assertIsDisplayed().performClick()
        compose.runOnIdle { assertEquals(true, choice) }
    }

    @Test fun circularQuarterMenuRecordsLongPressDragAndWaitsForRetraction() {
        lateinit var state: TaskControlsState
        var chosen = ""
        var starts = 0
        val oldGlass = AppearanceSettingsManager.glassEffectEnabled
        AppearanceSettingsManager.updateGlassEffect(true)
        val icons = listOf(AnimatedIconSpec.Add, AnimatedIconSpec.ScanLock, AnimatedIconSpec.Clock,
            AnimatedIconSpec.Settings, AnimatedIconSpec.Log, AnimatedIconSpec.Courses)
        val captions = listOf("添加", "监控", "定时", "参数", "日志", "队列")
        val actions = captions.mapIndexed { index, label ->
            TaskQuickAction(index.toString(), label, icons[index]) { chosen = label }
        }
        compose.setContent {
            CourseSelectorTheme { GlassWindowHost(Modifier.testTag("feedback-frame")) {
                Column(Modifier.fillMaxSize().systemBarsPadding().padding(24.dp)) {
                    Text("抢课工作台", style = MaterialTheme.typography.headlineMedium)
                    Spacer(Modifier.height(16.dp))
                    Text("长按展开任务操作，滑动后松手执行。", style = MaterialTheme.typography.bodyMedium)
                }
                state = rememberTaskControlsState()
                LiquidTaskControls(actions, 72.dp, state) { expanded, progress, toggle ->
                    LiquidTaskButton("开始执行", "课程已就绪", false, false, true, { starts++ }, expanded, progress, toggle)
                }
            } }
        }
        try {
            compose.mainClock.autoAdvance = false
            DisplayRecording(compose.activity, "quarter-fan-gesture").use {
                val root = compose.onNodeWithTag("feedback-frame")
                val frame = root.fetchSemanticsNode().boundsInRoot
                val origin = compose.onNodeWithTag("grab-primary-action").fetchSemanticsNode().boundsInRoot.center - frame.topLeft
                capture("quarter-button-rest")
                root.performTouchInput { down(origin) }
                movieFrames(45)
                root.performTouchInput { moveTo(origin + androidx.compose.ui.geometry.Offset(0f, -1f)) }
                movieFrames(55)
                assertTrue(state.expanded)
                capture("quarter-fan-open")
                val target = compose.onNodeWithTag("task-quick-2").fetchSemanticsNode().boundsInRoot.center - frame.topLeft
                root.performTouchInput { moveTo(target) }
                movieFrames(20)
                capture("quarter-fan-clock-focus")
                root.performTouchInput { up() }
                movieFrames(3)
                assertEquals("The action waits until the fan is back at its origin", "", chosen)
                movieFrames(70)
                assertEquals("定时", chosen)
                assertEquals(0, starts)
                capture("quarter-fan-retracted")
            }
        } finally { compose.runOnUiThread { AppearanceSettingsManager.updateGlassEffect(oldGlass) } }
    }

    @Test fun releasingLongPressAtTheRoundButtonLeavesMenuOpenForTapSelection() {
        lateinit var state: TaskControlsState
        var chosen = 0
        var starts = 0
        compose.setContent {
            CourseSelectorTheme { GlassWindowHost(Modifier.testTag("feedback-frame")) {
                state = rememberTaskControlsState()
                LiquidTaskControls(listOf(TaskQuickAction("clock", "定时", AnimatedIconSpec.Clock) { chosen++ }), 72.dp, state) { expanded, progress, toggle ->
                    LiquidTaskButton("开始执行", "就绪", false, false, true, { starts++ }, expanded, progress, toggle)
                }
            } }
        }
        compose.mainClock.autoAdvance = false
        val root = compose.onNodeWithTag("feedback-frame")
        val origin = compose.onNodeWithTag("grab-primary-action").fetchSemanticsNode().boundsInRoot.center - root.fetchSemanticsNode().boundsInRoot.topLeft
        root.performTouchInput { down(origin) }
        movieFrames(45)
        assertTrue("Holding without a drag must open the menu", state.expanded)
        root.performTouchInput { up() }
        movieFrames(45)
        assertTrue("Releasing at the origin must leave the menu open", state.expanded)
        assertEquals(0, starts)
        compose.onNodeWithTag("task-quick-clock").performTouchInput { click() }
        movieFrames(65)
        assertEquals(1, chosen)
        assertFalse(state.expanded)
        compose.onNodeWithTag("grab-primary-action").performTouchInput { click() }
        movieFrames(5)
        assertEquals("The next short tap must still execute the primary action", 1, starts)
        root.performTouchInput { down(origin) }
        movieFrames(45)
        root.performTouchInput { up() }
        movieFrames(45)
        assertTrue(state.expanded)
        compose.onNodeWithTag("grab-primary-action").performTouchInput { click() }
        movieFrames(65)
        assertFalse("A new short tap must close the open menu", state.expanded)
        assertEquals("Closing the menu must not start the task", 1, starts)
        root.performTouchInput { down(origin) }
        movieFrames(45)
        assertTrue(state.expanded)
        root.performTouchInput { cancel() }
        movieFrames(65)
        assertFalse("Cancelling a held gesture must dismiss the menu", state.expanded)
        assertEquals(1, chosen)
        assertEquals(1, starts)
    }

    private fun movieFrames(count: Int) {
        repeat(count) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle(); SystemClock.sleep(16) }
    }

    private fun capture(name: String) {
        val bitmap = compose.onNodeWithTag("feedback-frame").captureToImage().asAndroidBitmap()
        val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "feedback-validation").apply { mkdirs() }
        File(folder, "$name.png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
