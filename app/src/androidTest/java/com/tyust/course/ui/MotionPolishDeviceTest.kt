package com.tyust.course.ui

import android.graphics.Bitmap
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.boundsInWindow
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalWindowInfo
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntSize
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.tyust.course.BuildConfig
import com.tyust.course.BottomNavItem
import com.tyust.course.ui.screen.CourseDetailContent
import com.tyust.course.ui.screen.CourseDetailUiState
import com.tyust.course.ui.screen.CourseCard
import com.tyust.course.ui.screen.LocalScheduleFocus
import com.tyust.course.ui.screen.ScheduleFocusRegistry
import com.tyust.course.ui.screen.ScheduleCourseUi
import com.tyust.course.manager.AppearanceSettingsManager
import com.tyust.course.ui.system.*
import com.tyust.course.ui.system.glass.*
import com.tyust.course.ui.theme.CourseSelectorTheme
import com.tyust.course.ui.theme.NavigationMotionState
import com.tyust.course.ui.theme.NavigationPages
import com.tyust.course.ui.theme.rememberNavigationMotionState
import com.tyust.course.ui.theme.moduleEntrance
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import kotlin.math.hypot

@RunWith(AndroidJUnit4::class)
class MotionPolishDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @Before fun isolatedVariantOnly() = assumeTrue(BuildConfig.UI_PREVIEW)

    @Test fun coursesGlyphKeepsItsCutoutsAndHasNoTrailingTriangle() {
        compose.setContent {
            Box(Modifier.fillMaxSize().background(Color.White), contentAlignment = Alignment.Center) {
                Box(Modifier.size(128.dp).testTag("books-silhouette"), contentAlignment = Alignment.Center) {
                    PhosphorNavigationIcon(AppSymbolSpec.Courses, 1f, { 0f }, Color(0xFF1868A8), Modifier.size(96.dp))
                }
            }
        }
        val bitmap = captureNode("books-silhouette", "books-silhouette")
        val inset = bitmap.width / 8f
        val unit = bitmap.width * 0.75f / 256f
        for (y in (inset + 226f * unit).toInt() until bitmap.height) for (x in 0 until bitmap.width) {
            assertEquals("The Books silhouette must end at its spines", android.graphics.Color.WHITE, bitmap.getPixel(x, y))
        }
        for (y in listOf(56f, 200f)) {
            assertEquals("The upright book's end bands must remain open", android.graphics.Color.WHITE,
                bitmap.getPixel((inset + 80f * unit).toInt(), (inset + y * unit).toInt()))
        }
    }

    @Test fun everyNavigationIconReplaysWithoutRepeatingNavigation() {
        val destinations = listOf(BottomNavItem.Courses, BottomNavItem.Schedule, BottomNavItem.Grab, BottomNavItem.Grades, BottomNavItem.Settings)
        val selected = mutableIntStateOf(0)
        var calls = 0
        compose.setContent {
            CourseSelectorTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                    val background = rememberLayerBackdrop()
                    Canvas(Modifier.fillMaxSize().layerBackdrop(background)) {
                        drawRect(Color(0xFFEEF2F8))
                        for (x in 0..20) drawLine(Color(0xFF90B5D9), Offset(x * 24.dp.toPx(), 0f),
                            Offset(x * 24.dp.toPx() + size.height * 0.2f, size.height), 6.dp.toPx())
                    }
                    CapsuleNavigationBar(destinations, selected.intValue, { calls++; selected.intValue = it },
                        backdrop = background, modifier = Modifier.testTag("navigation-fixture"))
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        DisplayRecording(compose.activity, "phosphor-navigation-replay").use {
            destinations.forEachIndexed { index, item ->
                if (index > 0) compose.onNodeWithText(item.label).performClick()
                compose.mainClock.advanceTimeBy(800)
                val baseline = captureNode("navigation-fixture", "nav-$index-rest")
                val committedCalls = calls
                compose.onNodeWithText(item.label).performClick()
                movieFrames(8)
                val middle = captureNode("navigation-fixture", "nav-$index-middle")
                assertFalse("${item.label} needs a visible one-shot animation", baseline.sameAs(middle))
                assertEquals("Replay cannot repeat route or business callbacks", committedCalls, calls)
                movieFrames(28)
                assertTrue("${item.label} must settle exactly", baseline.sameAs(captureNode("navigation-fixture", "nav-$index-settled")))
            }
        }
        assertEquals(4, calls)
        compose.mainClock.autoAdvance = true
    }

    @Test fun opticalAndVisibleIconCopiesShareEveryInterruptedFrame() {
        val selected = mutableIntStateOf(0)
        val reduced = mutableStateOf(false)
        lateinit var playback: NavigationIconPlayback
        compose.setContent {
            CourseSelectorTheme {
                playback = rememberNavigationIconPlayback(5, selected.intValue, reduced.value)
                Column(Modifier.fillMaxSize().background(Color.White), verticalArrangement = Arrangement.Center) {
                    repeat(2) { copy ->
                        Row(Modifier.fillMaxWidth().height(64.dp).testTag("icon-copy-$copy"),
                            horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                            AppSymbolSpec.entries.forEachIndexed { index, spec ->
                                PhosphorNavigationIcon(spec, if (selected.intValue == index) 1f else 0.35f,
                                    { playback.phase(index) }, Color(0xFF1868A8), Modifier.size(48.dp))
                            }
                        }
                    }
                }
            }
        }
        compose.mainClock.autoAdvance = false
        for (index in listOf(0, 3, 1, 4, 2, 0)) {
            compose.runOnIdle { selected.intValue = index; playback.replay(index) }
            compose.mainClock.advanceTimeBy(96)
            val visible = captureNode("icon-copy-0", "icon-shared-$index")
            val optical = captureNode("icon-copy-1", "icon-optical-$index")
            assertTrue("Visible and sampling glyphs must draw the same frame: $index", visible.sameAs(optical))
        }
        compose.runOnIdle { reduced.value = true }
        compose.mainClock.advanceTimeBy(32)
        compose.runOnIdle { (0..4).forEach { assertEquals(0f, playback.phase(it), 0.0001f) }; playback.replay(0) }
        compose.mainClock.advanceTimeBy(100)
        compose.runOnIdle { assertEquals(0f, playback.phase(0), 0.0001f) }
        compose.mainClock.autoAdvance = true
    }

    @Test fun pageGroupsDoNotRestartOnRefreshAndReverseFromTheirCurrentFrame() {
        val selected = mutableIntStateOf(0)
        val revision = mutableIntStateOf(0)
        lateinit var navigation: NavigationMotionState
        compose.setContent {
            CourseSelectorTheme {
                navigation = rememberNavigationMotionState(selected.intValue, "module-fixture", false)
                NavigationPages(navigation) { page ->
                    Column(Modifier.fillMaxSize().background(Color.White)) {
                        Text("标题 $page", Modifier.moduleEntrance(0))
                        Text("内容 ${revision.intValue}", Modifier.moduleEntrance(1))
                        Text("操作", Modifier.moduleEntrance(2))
                    }
                }
            }
        }
        compose.waitForIdle()
        compose.mainClock.autoAdvance = false
        compose.runOnIdle { assertEquals(1f, navigation.moduleProgress(0), 0.0001f); revision.intValue++ }
        compose.mainClock.advanceTimeBy(16)
        compose.runOnIdle { assertEquals(1f, navigation.moduleProgress(0), 0.0001f); selected.intValue = 1 }
        compose.mainClock.advanceTimeBy(96)
        compose.runOnIdle {
            val before = navigation.moduleProgress(1)
            assertTrue(before > 0f && before < 1f)
            val position = navigation.position
            navigation.select(2, false)
            assertEquals(position, navigation.position, 0.001f)
            assertEquals(before, navigation.moduleProgress(1), 0.001f)
            navigation.select(1, false)
            assertEquals(before, navigation.moduleProgress(1), 0.001f)
            selected.intValue = 1
        }
        compose.mainClock.advanceTimeBy(1600)
        compose.runOnIdle { assertEquals(1f, navigation.moduleProgress(1), 0.0001f) }
        compose.mainClock.autoAdvance = true
    }

    @Test fun segmentedSelectionShowsJellyAndSettlesAfterReversalAndResize() {
        val selected = mutableIntStateOf(1)
        val compact = mutableStateOf(false)
        compose.setContent {
            CourseSelectorTheme {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(360.dp, 100.dp).testTag("segment-fixture"), contentAlignment = Alignment.Center) {
                        LiquidSegmentedControl(listOf("学期", "总体", "考试"), selected.intValue, { selected.intValue = it },
                            Modifier.width(if (compact.value) 280.dp else 320.dp), backdrop = null,
                            height = if (compact.value) 36.dp else 52.dp)
                    }
                }
            }
        }
        val settled = captureNode("segment-fixture", "segmented-rest")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("考试").performClick()
        compose.mainClock.advanceTimeBy(96)
        val moving = captureNode("segment-fixture", "segmented-stretch")
        assertFalse("The indicator needs a visible moving frame", settled.sameAs(moving))
        compose.onNodeWithText("学期").performClick()
        compose.mainClock.advanceTimeBy(64)
        captureNode("segment-fixture", "segmented-reverse")
        compose.runOnIdle { compact.value = true }
        compose.mainClock.advanceTimeBy(48)
        compose.onNodeWithText("总体").performClick()
        compose.mainClock.advanceTimeBy(1600)
        compose.runOnIdle { compact.value = false }
        compose.mainClock.advanceTimeBy(800)
        compose.onNodeWithText("总体").assertIsSelected()
        assertTrue("Reversal and toolbar resizing must end at the original exact shape",
            settled.sameAs(captureNode("segment-fixture", "segmented-settled")))
        compose.mainClock.autoAdvance = true
    }

    @Test fun sourceCardEntranceCanBeDismissedWithoutJumpingOrEarlyRemoval() {
        lateinit var host: DialogHostState
        lateinit var handle: DialogHandle
        val sheet = ScheduleBottomSheetState().apply { sourceBounds = Rect(80f, 200f, 230f, 390f) }
        var dismissed = 0
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CourseSelectorTheme {
                host = rememberDialogHostState()
                Box(Modifier.fillMaxSize().background(Color.White)) {
                    DisposableEffect(host) {
                        handle = host.show({ dismissed++ }, DialogPresentation.Bottom, sheet) {
                            Box(Modifier.fillMaxWidth().height(360.dp).background(Color(0xFFE6EBF4)).testTag("hero-sheet"))
                        }
                        onDispose { host.dismiss(handle, false) }
                    }
                    DialogHost(host)
                }
            }
        }
        compose.mainClock.advanceTimeBy(80)
        compose.waitForIdle()
        compose.runOnIdle {
            val dialog = host.dialogs.single()
            val frame = dialog.lastSheetFrame
            assertTrue("The sheet should still be expanding from the tapped card", frame.scaleX in 0.25f..0.95f)
            host.dismiss(handle)
            assertEquals("Exit starts at the last drawn transform", frame, dialog.exitSheetFrame)
            assertEquals(0, dismissed)
        }
        compose.mainClock.advanceTimeBy(80)
        compose.onNodeWithTag("hero-sheet").assertExists()
        compose.runOnIdle { assertEquals(0, dismissed) }
        compose.mainClock.advanceTimeBy(300)
        compose.onNodeWithTag("hero-sheet").assertDoesNotExist()
        compose.runOnIdle { assertEquals(1, dismissed) }
        compose.mainClock.autoAdvance = true
    }

    @Test fun tappedCardOwnsTheOriginWhenAdjacentWeeksContainTheSameCourse() {
        val registry = ScheduleFocusRegistry()
        val course = ScheduleCourseUi("同一课程", "教师", "A101", 1, 1, 2, "1-18周", Color.Blue)
        val expected = arrayOfNulls<Rect>(2)
        var captured: Rect? = null
        compose.setContent {
            CourseSelectorTheme {
                CompositionLocalProvider(LocalScheduleFocus provides registry) {
                    Column(Modifier.fillMaxSize()) {
                        repeat(2) { index ->
                            Box(Modifier.size(100.dp, 140.dp).onGloballyPositioned { expected[index] = it.boundsInWindow() }) {
                                CourseCard(course) { captured = registry.bounds(course.id) }
                            }
                        }
                    }
                }
            }
        }
        compose.onAllNodesWithText("同一课程")[0].performClick()
        compose.runOnIdle { assertEquals("The clicked card overrides the adjacent week's origin", expected[0], captured) }
        compose.onAllNodesWithText("同一课程")[1].performClick()
        compose.runOnIdle { assertEquals(expected[1], captured) }
    }

    @Test fun courseDetailFinishesExpandingAndRevealingContentWithin240Millis() {
        lateinit var host: DialogHostState
        val sheet = ScheduleBottomSheetState().apply { sourceBounds = Rect(80f, 200f, 230f, 390f) }
        compose.mainClock.autoAdvance = false
        compose.setContent {
            CourseSelectorTheme {
                host = rememberDialogHostState()
                Box(Modifier.fillMaxSize()) {
                    DisposableEffect(host) {
                        val handle = host.show({}, DialogPresentation.Bottom, sheet) {
                            Box(Modifier.fillMaxWidth().height(360.dp).background(Color.White))
                        }
                        onDispose { host.dismiss(handle, false) }
                    }
                    DialogHost(host)
                }
            }
        }
        compose.mainClock.advanceTimeBy(32) // Initial composition and first animation frame.
        compose.mainClock.advanceTimeBy(240)
        compose.runOnIdle {
            val dialog = host.dialogs.single()
            assertTrue("Entry and all content groups should be complete", dialog.visibility.isIdle)
            assertEquals(1f, dialog.lastSheetFrame.scaleX, 0.001f)
            assertEquals(1f, dialog.lastPresence, 0.001f)
        }
        compose.mainClock.autoAdvance = true
    }

    @Test fun detailWrapsContentAndKeepsEditingReachableAcrossViewports() {
        val width = mutableIntStateOf(412)
        val height = mutableIntStateOf(720)
        val font = mutableFloatStateOf(1f)
        val dark = mutableStateOf(false)
        val longContent = mutableStateOf(false)
        var deleted = 0
        var permission = 0
        var configured = 0
        val sheet = ScheduleBottomSheetState()
        compose.setContent {
            val physicalWidth = LocalWindowInfo.current.containerSize.width
            val density = minOf(LocalDensity.current.density, physicalWidth.toFloat() / width.intValue)
            CompositionLocalProvider(LocalDensity provides Density(density, font.floatValue)) {
                CourseSelectorTheme(darkTheme = dark.value) {
                    val host = rememberDialogHostState()
                    CompositionLocalProvider(LocalDialogHost provides host, LocalAppBackdrop provides null, LocalModalBackdrop provides null) {
                        GlassOverlayHost(Modifier.requiredSize(width.intValue.dp, height.intValue.dp)
                            .background(MaterialTheme.colorScheme.background).testTag("detail-viewport")) {
                            DisposableEffect(host) {
                                val handle = host.show({}, DialogPresentation.Bottom, sheet) {
                                    val course = ScheduleCourseUi(
                                        if (longContent.value) "课程详情与交互设计实践：跨学科联合研讨与实验课程" else "数据结构",
                                        "林老师 · 联合授课团队", if (longContent.value) "创新中心三层 302 研讨教室，入口位于东侧走廊。".repeat(8) else "明理楼 A302",
                                        2, 3, 4, "1–16 周（单）、18 周", Color(0xFF2985CD), true, "viewport-course", id = "custom:viewport-course")
                                    CourseDetailContent(CourseDetailUiState(course, reminderEnabled = longContent.value,
                                        reminderDescription = if (longContent.value) "待授权：允许通知后生效，并补全学期时间" else "未开启",
                                        needsPermission = longContent.value, needsTime = longContent.value), sheet,
                                        { host.dismiss() }, {}, { permission++ }, { configured++ }, {}, { deleted++ })
                                }
                                onDispose { host.dismiss(handle, false) }
                            }
                            DialogHost(host)
                        }
                    }
                }
            }
        }
        val short = compose.onNodeWithTag("course-detail-surface", true).fetchSemanticsNode().boundsInRoot.height
        captureNode("detail-viewport", "detail-compact-412")
        compose.runOnIdle { longContent.value = true }
        val long = compose.onNodeWithTag("course-detail-surface", true).fetchSemanticsNode().boundsInRoot.height
        assertTrue("Height should follow content instead of a fixed percentage", long > short + 30)
        for (landscape in listOf(false, true)) for (w in listOf(320, 360, 412)) for (f in listOf(1f, 1.3f, 1.6f)) {
            compose.runOnIdle {
                width.intValue = if (landscape) 640 else w
                height.intValue = if (landscape) w else 720
                font.floatValue = f
                dark.value = f == 1.3f
            }
            val viewport = compose.onNodeWithTag("detail-viewport").fetchSemanticsNode().boundsInRoot
            val surface = compose.onNodeWithTag("course-detail-surface", true).fetchSemanticsNode().boundsInRoot
            assertTrue("Sheet exceeds the 85% cap: $w/$f/$landscape", surface.height <= viewport.height * 0.85f + 1)
            assertTrue(surface.left >= viewport.left && surface.right <= viewport.right)
            compose.onNodeWithText("编辑课程").assertIsDisplayed()
            val body = compose.onNodeWithTag("course-detail-scroll", true).fetchSemanticsNode().boundsInRoot
            assertTrue("A usable scroll body must remain at $w/$f/$landscape", body.height >= viewport.height * 0.10f)
            compose.onNodeWithText("设置提醒权限").performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithText("设置学期时间").performScrollTo().assertIsDisplayed().performClick()
            compose.onNodeWithText("编辑课程").assertIsDisplayed()
            if (f == 1.6f || w == 412) captureNode("detail-viewport", "detail-$w-font$f-landscape$landscape")
        }
        assertEquals(18, permission)
        assertEquals(18, configured)
        compose.runOnIdle { width.intValue = 360; height.intValue = 720; font.floatValue = 1f; dark.value = false }
        compose.onNodeWithText("删除课程").assertDoesNotExist()
        compose.onNodeWithContentDescription("更多课程操作").performClick()
        compose.onNodeWithText("删除课程").assertIsDisplayed()
        captureNode("detail-viewport", "detail-more-menu")
        compose.mainClock.autoAdvance = false
        compose.onNodeWithText("删除课程").performClick()
        compose.mainClock.advanceTimeBy(80)
        assertEquals("Deletion waits for menu exit", 0, deleted)
        compose.mainClock.advanceTimeBy(220)
        compose.waitForIdle()
        assertEquals(1, deleted)
        compose.mainClock.autoAdvance = true
    }

    /** An overlay can dim a stripe, but cannot move a dark stripe into a light stripe. */
    @Test fun primaryButtonRefractsTextureAtRestAndUnderPressure() {
        val shown = mutableStateOf(false)
        val dark = mutableStateOf(false)
        val texture = mutableIntStateOf(0)
        val oldGlass = AppearanceSettingsManager.glassEffectEnabled
        AppearanceSettingsManager.updateGlassEffect(true)
        compose.setContent {
            CourseSelectorTheme(darkTheme = dark.value) {
                GlassWindowHost {
                    val source = rememberLayerBackdrop()
                    val density = LocalDensity.current
                    val bitmapWallpaper = remember {
                        Bitmap.createBitmap(360, 800, Bitmap.Config.ARGB_8888).apply {
                            for (y in 0 until height) for (x in 0 until width) {
                                val darkLine = kotlin.math.sin((x + y * 0.35) / 7.0) > 0.35
                                setPixel(x, y, if (darkLine) 0xFF193A52.toInt() else 0xFFF3EBE0.toInt())
                            }
                        }.asImageBitmap()
                    }
                    val anchor = rememberGlassLensRegion("primary-probe", texture.intValue) { coordinates ->
                        drawBackdropSource(source, density, coordinates)
                    }
                    CompositionLocalProvider(LocalAppBackdrop provides source, LocalControlBackdrop provides source,
                        LocalGlassLensAnchor provides anchor) {
                        Box(Modifier.fillMaxSize().glassLensAnchor(anchor), contentAlignment = Alignment.Center) {
                            Canvas(Modifier.fillMaxSize().layerBackdrop(source)) {
                                drawRect(Color(0xFFF3F4F6))
                                val cell = 8.dp.toPx()
                                if (texture.intValue == 2) {
                                    drawImage(bitmapWallpaper, dstSize = IntSize(size.width.toInt(), size.height.toInt()))
                                } else if (texture.intValue == 1) {
                                    for (x in 0..(size.width / cell).toInt()) for (y in 0..(size.height / cell).toInt()) {
                                        if ((x + y) % 2 == 0) drawRect(Color(0xFF263344), Offset(x * cell, y * cell), Size(cell, cell))
                                    }
                                } else {
                                    for (x in -(size.height / cell).toInt()..(size.width / cell).toInt()) {
                                        drawLine(Color(0xFF263344), Offset(x * cell * 2, 0f),
                                            Offset(x * cell * 2 + size.height, size.height), cell * 0.7f)
                                    }
                                }
                            }
                            Box(Modifier.size(128.dp).testTag("optical-fixture"), contentAlignment = Alignment.Center) {
                                if (shown.value) LiquidTaskButton("开始", "准备就绪", false, false, true, {}, false, 0f, {})
                            }
                        }
                    }
                }
            }
        }
        try {
            for (isDark in listOf(false, true)) for (pattern in 0..2) {
                val kind = listOf("diagonal", "checker", "image")[pattern]
                compose.runOnIdle { dark.value = isDark; texture.intValue = pattern; shown.value = false }
                val source = capture("source-$isDark-$kind")
                compose.runOnIdle { shown.value = true }
                val resting = capture("rest-$isDark-$kind")
                val restMoved = movedStripeFraction(source, resting)
                compose.mainClock.autoAdvance = false
                compose.onNodeWithTag("grab-primary-action").performTouchInput { down(center + Offset(8f, -8f)) }
                compose.mainClock.advanceTimeBy(180)
                val pressed = capture("press-$isDark-$kind")
                compose.onNodeWithTag("grab-primary-action").performTouchInput { up() }
                compose.mainClock.autoAdvance = true
                val pressMoved = movedStripeFraction(source, pressed)
                Log.i("MotionPolish", "refraction dark=$isDark texture=$kind rest=$restMoved press=$pressMoved")
                assertTrue("Resting texture must be displaced: $restMoved", restMoved > 0.025f)
                assertTrue("Pressed texture must be displaced: $pressMoved", pressMoved > 0.025f)
            }
            compose.runOnIdle { AppearanceSettingsManager.updateGlassEffect(false); texture.intValue = 0 }
            val solid = captureNode("grab-primary-action", "primary-glass-off-diagonal")
            compose.runOnIdle { texture.intValue = 1 }
            val otherTexture = captureNode("grab-primary-action", "primary-glass-off-checker")
            val center = solid.width / 2f
            for (y in 0 until solid.height) for (x in 0 until solid.width) {
                if (hypot(x - center, y - center) < center * 0.85f) {
                    assertEquals("Glass-off action must use an opaque, stable surface",
                        solid.getPixel(x, y), otherTexture.getPixel(x, y))
                }
            }
        } finally {
            compose.mainClock.autoAdvance = true
            compose.runOnIdle { AppearanceSettingsManager.updateGlassEffect(oldGlass) }
        }
    }

    private fun capture(name: String): Bitmap {
        return captureNode("optical-fixture", name)
    }

    private fun captureNode(tag: String, name: String): Bitmap {
        compose.waitForIdle()
        if (android.os.Build.VERSION.SDK_INT in 31..32) Thread.sleep(100)
        val bitmap = compose.onNodeWithTag(tag).captureToImage().asAndroidBitmap()
        val directory = File(compose.activity.getExternalFilesDir(null), "motion-polish").apply { mkdirs() }
        File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return bitmap
    }

    private fun movieFrames(count: Int) {
        repeat(count) { compose.mainClock.advanceTimeByFrame(); compose.waitForIdle(); android.os.SystemClock.sleep(16) }
    }

    private fun movedStripeFraction(source: Bitmap, result: Bitmap): Float {
        val center = source.width / 2f
        val radius = source.width / 4f
        var samples = 0
        var moved = 0
        fun light(pixel: Int) = (android.graphics.Color.red(pixel) + android.graphics.Color.green(pixel) + android.graphics.Color.blue(pixel)) / 3
        for (x in 0 until source.width) for (y in 0 until source.height) {
            val r = hypot(x - center, y - center) / radius
            // Exclude glyph, contour and antialiased silhouette; inspect the optical interior.
            if (r !in 0.50f..0.87f) continue
            val a = light(source.getPixel(x, y))
            val b = light(result.getPixel(x, y))
            if (a < 90 || a > 190) {
                samples++
                if ((a < 90 && b > 165) || (a > 190 && b < 105)) moved++
            }
        }
        return moved.toFloat() / samples.coerceAtLeast(1)
    }
}
