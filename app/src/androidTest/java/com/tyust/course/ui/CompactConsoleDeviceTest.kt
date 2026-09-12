package com.tyust.course.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.tyust.course.BuildConfig
import com.tyust.course.demo.DemoData
import com.tyust.course.ui.screen.*
import com.tyust.course.ui.system.*
import com.tyust.course.ui.theme.CourseSelectorTheme
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Local callbacks only: compact controls are exercised without scheduling or selecting courses. */
@RunWith(AndroidJUnit4::class)
class CompactConsoleDeviceTest {
    @get:Rule val compose = createComposeRule()
    @Before fun demoOnly() = assumeTrue(BuildConfig.UI_PREVIEW)

    @Test fun queueStaysCompactAndInlineActionsReorderDeleteAndDisable() {
        val queue = mutableStateListOf(*DemoData.grabQueue().take(3).toTypedArray())
        val width = mutableIntStateOf(360)
        val font = mutableFloatStateOf(1f)
        val running = mutableStateOf(false)
        val night = mutableStateOf(false)
        val initial = queue.map { it.uuid }
        var density = 1f
        compose.setContent {
            density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, font.floatValue)) {
                CourseSelectorTheme(darkTheme = night.value) {
                    LazyColumn(Modifier.requiredWidth(width.intValue.dp).height(400.dp)
                        .background(MaterialTheme.colorScheme.background).testTag("frame"),
                        verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        grabQueueItems(queue.toList(), -1, emptyMap(), running.value, false,
                            onMoveItem = { from, to -> queue.add(to, queue.removeAt(from)) },
                            onRemoveItem = { queue.removeAt(it) }, onAddCourse = {},
                            showMode = false, supportsManualAdd = false)
                    }
                }
            }
        }
        for (w in listOf(320, 360, 412)) for (f in listOf(1f, 1.6f)) for (dark in listOf(false, true)) {
            compose.runOnIdle { width.intValue = w; font.floatValue = f; night.value = dark }
            val card = compose.onNodeWithTag("queue-item-" + initial[1]).fetchSemanticsNode().boundsInRoot
            assertTrue("Course row too tall at " + w + "/" + f, card.height / density <= if (f == 1f) 61f else 82f)
            val actions = listOf("up", "down", "delete").map {
                compose.onNodeWithTag("queue-" + it + "-" + initial[1]).fetchSemanticsNode().boundsInRoot
            }
            actions.forEach {
                assertTrue(it.left >= card.left && it.right <= card.right + 1f)
                assertTrue(it.width / density >= 47.9f && it.height / density >= 47.9f)
                assertEquals(actions[0].center.y, it.center.y, 1f)
            }
            actions.zipWithNext().forEach { (left, right) -> assertTrue(left.right <= right.left + 1f) }
            if (w == 360 && f == 1f) capture("queue-" + if (dark) "dark" else "light")
        }
        compose.onNodeWithTag("queue-up-" + initial.first()).assertDoesNotExist()
        compose.onNodeWithTag("queue-down-" + initial.last()).assertDoesNotExist()
        compose.onNodeWithTag("queue-down-" + initial[0]).performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf(initial[1], initial[0], initial[2]), queue.map { it.uuid }) }
        compose.onNodeWithTag("queue-up-" + initial[0]).performTouchInput { click() }
        compose.runOnIdle { assertEquals(initial, queue.map { it.uuid }) }
        compose.onNodeWithTag("queue-delete-" + initial[1]).performTouchInput { click() }
        compose.runOnIdle { assertEquals(listOf(initial[0], initial[2]), queue.map { it.uuid }); running.value = true }
        compose.onNodeWithTag("queue-down-" + initial[0]).assertIsNotEnabled()
        compose.onNodeWithTag("queue-delete-" + initial[0]).assertIsNotEnabled()
    }

    @Test fun modesAndTimingRemainSeparateAccessibleControlsAtLargeFont() {
        val fuzzy = mutableStateOf(false)
        val enabled = mutableStateOf(true)
        val font = mutableFloatStateOf(1f)
        var timingOpens = 0
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, font.floatValue)) {
                CourseSelectorTheme {
                    Box(Modifier.width(272.dp).height(80.dp).testTag("frame")) {
                        TaskModeBar(fuzzy.value, false, enabled.value, { fuzzy.value = it }, { timingOpens++ })
                    }
                }
            }
        }
        for (scale in listOf(1f, 1.6f)) {
            compose.runOnIdle { font.floatValue = scale }
            compose.onNodeWithContentDescription("模糊监控").performTouchInput { click() }
            compose.onNodeWithContentDescription("模糊监控").assertIsSelected()
            compose.onNodeWithContentDescription("精确执行").performTouchInput { click() }
            compose.onNodeWithContentDescription("精确执行").assertIsSelected()
            compose.onNodeWithTag("task-mode-timing").performTouchInput { click() }
            val selector = compose.onNodeWithTag("task-matching-selector").fetchSemanticsNode().boundsInRoot
            val timing = compose.onNodeWithTag("task-mode-timing").fetchSemanticsNode().boundsInRoot
            assertTrue(selector.right < timing.left)
        }
        compose.runOnIdle { assertEquals(2, timingOpens); assertFalse(fuzzy.value); enabled.value = false }
        compose.onNodeWithContentDescription("精确执行").assertIsNotEnabled()
        compose.onNodeWithTag("task-mode-timing").assertIsNotEnabled()
    }

    @Test fun timingOpensSettingsAndPendingTaskLocksEditingUntilCancelled() {
        val scheduled = mutableStateOf(false)
        val pending = mutableStateOf(false)
        var pickerOpens = 0
        val queue = DemoData.grabQueue().take(2)
        compose.setContent {
            CourseSelectorTheme {
                Box(Modifier.fillMaxSize().testTag("frame")) {
                    GrabProScreen(false, 0, 0, 0, null, null, "", "800", {}, "5", {},
                        onStart = {}, onStop = {}, onClearLog = {}, queue = queue,
                        showQueueModeLabels = false, showScheduleWarning = false,
                        isScheduledMode = scheduled.value, onScheduledModeChange = { scheduled.value = it },
                        scheduledDateTime = "2026/12/20 09:00", hasScheduledTask = pending.value,
                        onScheduledStart = { pending.value = true }, onCancelScheduledTask = { pending.value = false },
                        onPickDateTime = { pickerOpens++ }, onFuzzyMatchModeChange = {})
                }
            }
        }
        compose.onNodeWithTag("task-mode-timing").performClick()
        compose.runOnIdle { assertTrue(scheduled.value); assertEquals(1, pickerOpens) }
        compose.onNodeWithTag("task-cancel-timing").performClick()
        compose.runOnIdle { assertFalse(scheduled.value) }
        compose.onNodeWithContentDescription("精确执行").assertIsEnabled()
        compose.onNodeWithTag("task-mode-timing").performClick()
        compose.onNodeWithContentDescription("创建定时任务").performClick()
        compose.onNodeWithTag("task-mode-timing").assertIsNotEnabled()
        compose.onNodeWithTag("task-cancel-timing").assertIsNotEnabled()
        compose.onNodeWithTag("queue-down-" + queue.first().uuid).assertIsNotEnabled()
        compose.onNodeWithContentDescription("取消定时任务").performClick()
        compose.onNodeWithTag("task-mode-timing").assertIsEnabled()
        compose.runOnIdle { assertFalse(pending.value); assertEquals(2, pickerOpens) }
    }

    @Test fun fanFitsNarrowScreensAndLongPressDragExecutesOnlyTheChosenAction() {
        val width = mutableIntStateOf(320)
        val font = mutableFloatStateOf(1f)
        lateinit var state: TaskControlsState
        var chosen = ""
        var starts = 0
        val ids = listOf("add", "match", "schedule", "advanced", "logs", "queue")
        val captions = listOf("添加", "监控", "定时", "参数", "日志", "队列")
        val icons = listOf(AnimatedIconSpec.Add, AnimatedIconSpec.ScanLock, AnimatedIconSpec.Clock,
            AnimatedIconSpec.Settings, AnimatedIconSpec.Log, AnimatedIconSpec.Courses)
        val actions = ids.mapIndexed { index, id ->
            TaskQuickAction(id, captions[index], icons[index], enabled = id != "match",
                caption = captions[index]) { chosen = id }
        }
        compose.setContent {
            val density = LocalDensity.current.density
            CompositionLocalProvider(LocalDensity provides Density(density, font.floatValue)) {
                CourseSelectorTheme {
                    state = rememberTaskControlsState()
                    Box(Modifier.requiredWidth(width.intValue.dp).height(650.dp)
                        .background(MaterialTheme.colorScheme.background).testTag("frame")) {
                        GlassWindowHost {
                            LiquidTaskControls(actions, 72.dp, state) { expanded, progress, toggle ->
                                LiquidTaskButton("开始执行", "就绪", false, false, true, { starts++ },
                                    expanded, progress, toggle)
                            }
                        }
                    }
                }
            }
        }
        for (w in listOf(320, 360, 412)) for (f in listOf(1f, 1.6f)) {
            compose.runOnIdle { width.intValue = w; font.floatValue = f; state.expanded = true }
            val frame = compose.onNodeWithTag("frame").fetchSemanticsNode().boundsInRoot
            val dock = compose.onNodeWithTag("grab-action-dock").fetchSemanticsNode().boundsInRoot
            val pixelsPerDp = frame.width / w
            assertEquals("The primary action must be circular", dock.width, dock.height, 1f)
            assertEquals(64f, dock.width / pixelsPerDp, 0.6f)
            assertEquals("Dock must leave 16dp on the right", 16f, (frame.right - dock.right) / pixelsPerDp, 0.6f)
            assertEquals("Dock must stay 12dp above the 72dp navigation safe area", 84f,
                (frame.bottom - dock.bottom) / pixelsPerDp, 0.6f)
            val buttons = ids.map { compose.onNodeWithTag("task-quick-" + it).fetchSemanticsNode().boundsInRoot }
            buttons.forEach {
                assertTrue(it.left >= frame.left - 1 && it.right <= frame.right + 1)
                assertTrue(it.top >= frame.top - 1 && it.bottom <= frame.bottom + 1)
                assertTrue("Quarter fan must remain left of its origin", it.center.x <= dock.center.x + 1f)
                assertTrue("Quarter fan must remain above its origin", it.center.y <= dock.center.y + 1f)
            }
            val radii = buttons.map { (it.center - dock.center).getDistance() }
            radii.forEach {
                assertEquals("All six actions must share a single circular arc", radii[0], it, 1.5f)
                assertTrue("The single arc must remain close to the main button", it / pixelsPerDp in 180f..190f)
            }
            val surface = compose.onNodeWithTag("task-fan-surface").fetchSemanticsNode().boundsInRoot
            assertTrue("The glass fan must fit the screen", surface.left >= frame.left && surface.right <= frame.right)
            buttons.forEach {
                assertTrue("Every action must retain a 48dp touch target", it.width / pixelsPerDp >= 47.5f)
                assertFalse("Actions must not overlap the primary button", it.overlaps(dock))
                assertTrue("Actions must stay within the glass fan's bounds",
                    it.left >= surface.left && it.top >= surface.top && it.right <= surface.right && it.bottom <= surface.bottom)
            }
            for (i in buttons.indices) for (j in i + 1 until buttons.size) {
                assertFalse("Fan touch areas overlap at " + w + "/" + f, buttons[i].overlaps(buttons[j]))
            }
            compose.onNodeWithTag("task-quick-match").assertIsNotEnabled()
            if (w == 360 && f == 1.6f) capture("fan-large-font")
        }
        compose.onNodeWithTag("task-quick-add").performTouchInput { click() }
        compose.runOnIdle { assertEquals("add", chosen); assertFalse(state.expanded); chosen = "" }
        compose.mainClock.autoAdvance = false
        compose.mainClock.advanceTimeBy(1200)
        val root = compose.onNodeWithTag("frame")
        val frame = root.fetchSemanticsNode().boundsInRoot
        val dock = compose.onNodeWithTag("grab-primary-action").fetchSemanticsNode().boundsInRoot
        val origin = dock.center - frame.topLeft
        root.performTouchInput { down(origin) }
        compose.mainClock.advanceTimeBy(700)
        root.performTouchInput { moveTo(origin + Offset(0f, -1f)) }
        compose.mainClock.advanceTimeBy(1200)
        compose.runOnIdle { assertTrue("Long press must open the fan", state.expanded) }
        val target = compose.onNodeWithTag("task-quick-schedule").fetchSemanticsNode().boundsInRoot
        root.performTouchInput { moveTo(Offset(target.center.x, target.top + dock.height * 0.4f) - frame.topLeft); up() }
        compose.mainClock.advanceTimeBy(1200)
        compose.runOnIdle { assertEquals("schedule", chosen); assertEquals(0, starts); assertFalse(state.expanded) }
    }

    private fun capture(name: String) {
        val bitmap = compose.onNodeWithTag("frame").captureToImage().asAndroidBitmap()
        val folder = File(InstrumentationRegistry.getInstrumentation().targetContext.getExternalFilesDir(null), "compact-validation")
        folder.mkdirs()
        File(folder, name + ".png").outputStream().use { bitmap.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, it) }
    }
}
