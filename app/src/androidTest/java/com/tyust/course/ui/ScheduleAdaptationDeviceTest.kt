package com.tyust.course.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.text.TextLayoutResult
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tyust.course.BuildConfig
import com.tyust.course.ui.screen.*
import com.tyust.course.ui.theme.CourseSelectorTheme
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
class ScheduleAdaptationDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun isolatedPreviewOnly() = assumeTrue(BuildConfig.UI_PREVIEW)

    @Test fun headerUsesParentWidthAndWeekdaysStayAlignedWithEveryCourseColumn() {
        val width = mutableIntStateOf(412)
        val font = mutableFloatStateOf(1f)
        val collapse = mutableFloatStateOf(0f)
        val courses = (1..7).map { ScheduleCourseUi("课程", "教师", "A101", it, 1, 2, "1-25周", Color.Blue, id = "day-$it") }
        compose.setContent {
            val density = minOf(LocalDensity.current.density, LocalWindowInfo.current.containerSize.width.toFloat() / width.intValue)
            CompositionLocalProvider(LocalDensity provides Density(density, font.floatValue)) {
                CourseSelectorTheme {
                    Column(Modifier.requiredSize(width.intValue.dp, 720.dp).background(MaterialTheme.colorScheme.background)
                        .testTag("schedule-viewport")) {
                        WeekHeaderCompact(24, {}, {}, collapseFraction = collapse.floatValue, firstWeekDate = "2026-03-02", showToday = true)
                        Box(Modifier.weight(1f)) { ScheduleGrid(courses, 25, periodCount = 4, onCourseClick = {}) }
                    }
                }
            }
        }
        for (w in listOf(320, 360, 412)) for (f in listOf(1f, 1.3f, 1.6f)) {
            compose.runOnIdle { width.intValue = w; font.floatValue = f }
            for (p in listOf(0f, 0.4f, 0.75f, 1f)) {
                compose.runOnIdle { collapse.floatValue = p }
                val viewport = compose.onNodeWithTag("schedule-viewport").fetchSemanticsNode().boundsInRoot
                val title = compose.onNodeWithTag("schedule-header-title", true).fetchSemanticsNode().boundsInRoot
                val actions = compose.onNodeWithTag("schedule-header-actions", true).fetchSemanticsNode().boundsInRoot
                assertTrue("Title overlaps actions at $w/$f/$p", title.right <= actions.left + 1f || title.bottom <= actions.top + 1f)
                assertTrue(actions.left >= viewport.left && actions.right <= viewport.right + 1f)
                assertTrue("Date and controls share the first row", title.right <= actions.left + 1f)
                val actionBounds = listOf("日视图", "周视图", "更多课表操作").map {
                    compose.onNodeWithContentDescription(it, useUnmergedTree = true).fetchSemanticsNode().boundsInRoot
                }
                compose.onNodeWithTag("schedule-today", useUnmergedTree = true).assertIsDisplayed()
                actionBounds.forEach { assertEquals(actionBounds.first().center.y, it.center.y, 1f) }
                for (day in 1..7) {
                    val label = compose.onNodeWithTag("schedule-weekday-$day", true).fetchSemanticsNode().boundsInRoot
                    val card = compose.onNodeWithTag("schedule-course-day-$day", true).fetchSemanticsNode().boundsInRoot
                    assertTrue("Weekday $day is shifted at $w/$f/$p", abs(label.center.x - card.center.x) <= 2f)
                }
                // 周次在三段式标签里（「第 」+ 数字 + 「 周」），不再是一个整串；
                // 用行容器的 bounds 验证它没有被标题区裁掉。
                val weekLabel = compose.onNodeWithTag("schedule-header-week", useUnmergedTree = true)
                    .fetchSemanticsNode().boundsInRoot
                assertTrue("Week label clipped at $w/$f/$p", weekLabel.left >= title.left && weekLabel.right <= title.right + 1f)

            }
            compose.runOnIdle { collapse.floatValue = 0f }
            capture("header-${w}-font$f")
        }
    }

    @Test fun collapsedActionsRemainReachableAndControlsRestoreAfterReversingDrag() {
        val collapse = mutableFloatStateOf(0f)
        val day = mutableStateOf(false)
        var todayClicks = 0
        var pixelsPerDp = 1f
        compose.setContent {
            val density = minOf(LocalDensity.current.density, LocalWindowInfo.current.containerSize.width.toFloat() / 320)
            pixelsPerDp = density
            CompositionLocalProvider(LocalDensity provides Density(density, 1.6f)) {
                CourseSelectorTheme {
                    Column(Modifier.requiredSize(320.dp, 720.dp).testTag("schedule-viewport")) {
                        WeekHeaderCompact(24, {}, {}, collapseFraction = collapse.floatValue, firstWeekDate = "2026-03-02",
                            showToday = true, dayView = day.value, onDayView = { day.value = it }, onTodayClick = { todayClicks++ })
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { collapse.floatValue = 0.75f }
        compose.mainClock.advanceTimeBy(80)
        compose.runOnIdle { collapse.floatValue = 0.2f }
        compose.mainClock.advanceTimeBy(1000)
        compose.onNodeWithContentDescription("日视图", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("schedule-today", useUnmergedTree = true).assertIsDisplayed()
        compose.runOnIdle { collapse.floatValue = 1f }
        compose.mainClock.advanceTimeBy(1000)
        compose.mainClock.autoAdvance = true
        compose.onNodeWithContentDescription("日视图", useUnmergedTree = true).assertIsDisplayed().performClick()
        compose.runOnIdle { assertTrue(day.value) }
        val bounds = compose.onNodeWithTag("schedule-today").fetchSemanticsNode().boundsInRoot
        assertTrue("Today must retain a 48 dp target", bounds.width >= 48 * pixelsPerDp - 1 && bounds.height >= 48 * pixelsPerDp - 1)
        compose.onNodeWithTag("schedule-today").performClick()
        compose.runOnIdle { assertEquals(1, todayClicks); collapse.floatValue = 0f }
        compose.onNodeWithContentDescription("周视图", useUnmergedTree = true).assertIsDisplayed()
        compose.onNodeWithTag("schedule-today", useUnmergedTree = true).assertIsDisplayed()
        // 收尾动画跑完再交还给下一个测试：残留的时钟/动画会让下一个
        // setContent 找不到 compose hierarchy（MuMu 上实测会偶发）。
        compose.waitForIdle()
    }

    @Test fun courseCardKeepsNameAndLocationWithinTheirLineBudget() {
        val width = mutableIntStateOf(360)
        val height = mutableIntStateOf(720)
        val font = mutableFloatStateOf(1f)
        val courses = listOf(
            ScheduleCourseUi("思想道德与法治", "张文博、李思远", "五象校区 敦行教学楼东区 A1208 多媒体教室", 2, 1, 1,
                "1-25周", Color(0xFF63ADEC), id = "long-one"),
            ScheduleCourseUi("跨学科联合研讨与实验课程", "王若宁、陈建明", "五象校区 敦行教学楼东区三层 302 研讨教室（从东侧走廊进入）", 4, 1, 2,
                "1-25周", Color(0xFFE8B553), isCustom = true, customId = "long-two", id = "long-two"),
            // 复刻用户反馈的那一列：长楼名 + 纯数字教室，七列布局下最窄的真实场景。
            ScheduleCourseUi("马克思主义基本原理", "刘雪纯", "九龙湖校区 九龙湖教学楼 406", 3, 1, 2,
                "1-25周", Color(0xFFE86A6A), id = "long-three")
        )
        compose.setContent {
            val density = minOf(LocalDensity.current.density, LocalWindowInfo.current.containerSize.width.toFloat() / width.intValue)
            CompositionLocalProvider(LocalDensity provides Density(density, font.floatValue)) {
                CourseSelectorTheme {
                    Box(Modifier.requiredSize(width.intValue.dp, height.intValue.dp)
                        .background(MaterialTheme.colorScheme.background).testTag("schedule-viewport")) {
                        ScheduleGrid(courses, 1, periodCount = 4, onCourseClick = {})
                    }
                }
            }
        }
        for (landscape in listOf(false, true)) for (w in listOf(320, 360, 412)) for (f in listOf(1f, 1.3f, 1.6f)) {
            compose.runOnIdle { width.intValue = if (landscape) 640 else w; height.intValue = if (landscape) w else 720; font.floatValue = f }
            courses.forEach { course ->
                // 课程名与地点各有固定行数预算（3 行 / 2 行）。卡片不再排教师行，
                // 省下的纵向空间留给楼栋与教室——与 1.x 的老布局一致。
                val nameLayouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithTag("schedule-name-${course.id}", true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(nameLayouts) }
                val name = nameLayouts.single()
                assertEquals(course.name, name.layoutInput.text.text)
                assertTrue("Course name exceeded its 3-line budget at $w/$f/$landscape", name.lineCount <= 3)
                if (!name.hasVisualOverflow) {
                    assertEquals(course.name.length, name.getLineEnd(name.lineCount - 1))
                }

                val locationLayouts = mutableListOf<TextLayoutResult>()
                compose.onNodeWithTag("schedule-location-${course.id}", true)
                    .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(locationLayouts) }
                val location = locationLayouts.single()
                assertEquals(com.tyust.course.schedule.ScheduleLocation.compact(course.location),
                    location.layoutInput.text.text)
                assertTrue("Location exceeded its 2-line budget at $w/$f/$landscape: ${course.location}",
                    location.lineCount <= 2)
                if (!location.hasVisualOverflow) {
                    // 完整显示时房间号必须还在末尾：折行可以，丢楼号不行。
                    val room = requireNotNull(com.tyust.course.schedule.ScheduleLocation.room(course.location))
                    assertTrue("Room number was lost", location.layoutInput.text.text.endsWith(room))
                }
            }
            // 教师信息从卡片移出（详情页仍完整），不能残留占位的 Text 节点。
            compose.onAllNodesWithTag("schedule-teacher-${courses.first().id}", true).assertCountEquals(0)
            if (!landscape && (f == 1f || w == 320)) capture("classroom-${w}-font$f")
        }
    }

    /**
     * 七列均分后每列只有约 42dp。这里把四张真实宽度的卡片并排画出来：
     * 楼名允许折成两行，房间号必须留在卡片里。截图供人眼复核。
     */
    @Test fun narrowColumnKeepsBuildingNameAndRoomNumber() {
        val courses = listOf(
            ScheduleCourseUi("马克思主义基本原理", "刘雪纯", "九龙湖校区 九龙湖教学楼 406", 3, 1, 2,
                "1-25周", Color(0xFFE86A6A), id = "narrow-long"),
            ScheduleCourseUi("线性代数A", "蔡奥丽", "九龙湖校区 明志楼 418", 4, 1, 2,
                "1-25周", Color(0xFF63ADEC), id = "narrow-mid"),
            ScheduleCourseUi("棒垒球", "王", "九龙湖校区 南应师足球场", 5, 1, 2,
                "1-25周", Color(0xFF7AC77A), id = "narrow-field"),
            ScheduleCourseUi("大学英语Ⅲ", "邓尚仪", "九龙湖校区 致远楼 A519", 6, 1, 2,
                "1-25周", Color(0xFFE8B553), id = "narrow-short")
        )
        compose.setContent {
            CourseSelectorTheme {
                Row(
                    Modifier.requiredSize(268.dp, 168.dp)
                        .background(MaterialTheme.colorScheme.background)
                        .testTag("schedule-viewport")
                        .padding(horizontal = 4.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    courses.forEach { course ->
                        Box(Modifier.width(42.dp).fillMaxHeight()) {
                            CourseCard(course, onClick = {})
                        }
                    }
                }
            }
        }
        courses.forEach { course ->
            val layouts = mutableListOf<TextLayoutResult>()
            compose.onNodeWithTag("schedule-location-${course.id}", true)
                .performSemanticsAction(SemanticsActions.GetTextLayoutResult) { it(layouts) }
            val layout = layouts.single()
            assertTrue("地点超出两行预算: ${course.location}", layout.lineCount <= 2)
            val room = com.tyust.course.schedule.ScheduleLocation.room(course.location)
            if (!layout.hasVisualOverflow && room != null) {
                assertTrue("房间号被挤掉: ${course.location}", layout.layoutInput.text.text.endsWith(room))
            }
        }
        capture("narrow-column")
    }

    /**
     * 「现在是第几周」反推锚点：选完周次后，第一周日期必须让 weekAt 重新算出同一周。
     */
    @Test fun weekNumberPickerDerivesTheSemesterAnchor() {
        val context = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("week-picker-test", android.content.Context.MODE_PRIVATE)
            .also { it.edit().clear().commit() }
        val manager = com.tyust.course.manager.ScheduleSettingsManager(prefs)
        compose.setContent {
            CourseSelectorTheme {
                Box(Modifier.requiredSize(360.dp, 800.dp)
                    .background(MaterialTheme.colorScheme.background).testTag("schedule-viewport")) {
                    ScheduleSettingsScreen(manager = manager, onClose = {})
                }
            }
        }
        compose.onNodeWithText("现在是第几周").performScrollTo().assertExists()
        compose.onNodeWithText("现在是第几周").performClick()
        // 未设锚点时滚轮停在第 1 周；直接确认即把本周一写成第一周。
        compose.onNodeWithText("确定").assertExists()
        captureScreen("week-picker")
        compose.onNodeWithText("确定").performClick()
        compose.waitForIdle()
        val anchor = manager.semesterStartDate
        assertTrue("反推后应写回第一周日期", anchor > 0L)
        assertEquals(1, com.tyust.course.schedule.ScheduleDates.weekAt(
            com.tyust.course.schedule.ScheduleTimeBase.dateFromMillis(anchor), System.currentTimeMillis()))
    }

    private fun capture(name: String) {
        val directory = File(compose.activity.getExternalFilesDir(null), "light-glass-validation").apply { mkdirs() }
        if (android.os.Build.VERSION.SDK_INT < 26) {
            androidx.test.uiautomator.UiDevice.getInstance(androidx.test.platform.app.InstrumentationRegistry.getInstrumentation())
                .takeScreenshot(File(directory, "$name.png"))
            return
        }
        val bitmap = compose.onNodeWithTag("schedule-viewport").captureToImage().asAndroidBitmap()
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        publishToGallery(bitmap, name)
    }

    /** 整屏截图：弹窗跑在独立窗口里，captureToImage 抓不到。 */
    private fun captureScreen(name: String) {
        val directory = File(compose.activity.getExternalFilesDir(null), "light-glass-validation").apply { mkdirs() }
        val file = File(directory, "$name.png")
        androidx.test.uiautomator.UiDevice.getInstance(
            androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()).takeScreenshot(file)
        BitmapFactory.decodeFile(file.path)?.let { publishToGallery(it, name) }
    }

    // 仪器测试跑完会卸载被测应用，external files 随之消失；同时往相册放一份，
    // 测试结束后还能直接在系统相册里回看这次渲染。
    private fun publishToGallery(bitmap: Bitmap, name: String) {
        runCatching {
            val values = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Images.Media.DISPLAY_NAME, "$name.png")
                put(android.provider.MediaStore.Images.Media.MIME_TYPE, "image/png")
                put(android.provider.MediaStore.Images.Media.RELATIVE_PATH,
                    android.os.Environment.DIRECTORY_PICTURES + "/capybara-shots")
            }
            val resolver = compose.activity.contentResolver
            val uri = resolver.insert(android.provider.MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: return
            resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        }
    }
}
