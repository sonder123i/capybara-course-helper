package com.tyust.course.ui.system.glass

import android.graphics.Bitmap
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.tyust.course.BottomNavItem
import com.tyust.course.ui.system.CapsuleNavigationBar
import com.tyust.course.ui.system.LiquidSegmentedControl
import com.tyust.course.ui.theme.CourseSelectorTheme
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 31, maxSdkVersion = 32)
class GlassLensContinuityDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()
    @After fun clearCaptureObserver() { GlassLensCaptureObserver.onCaptured = null }

    @Test fun segmentedLabelsUpdateWhileBackgroundReadbackIsBusy() {
        val capturedLabels = java.util.concurrent.atomic.AtomicReference<Bitmap?>()
        GlassLensCaptureObserver.onCaptured = { tag, _, bitmap ->
            if (tag.startsWith("seg-")) capturedLabels.set(bitmap)
        }
        var label by mutableStateOf("1111")
        compose.setContent {
            CourseSelectorTheme {
                MaterialTheme(colorScheme = MaterialTheme.colorScheme.copy(primary = Color.Red)) {
                val backdrop = rememberLayerBackdrop()
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(Color.White))
                    LiquidSegmentedControl(listOf("学期", "总体", "考试"), 0, {},
                        modifier = Modifier.testTag("segment-control"), backdrop = backdrop, labelContent = { index, _, _ ->
                            Text(if (index == 0) label else "测试", fontSize = 16.sp,
                                fontFamily = FontFamily.Monospace, fontWeight = FontWeight.ExtraBold,
                                color = Color.Red)
                        })
                }
                }
            }
        }
        compose.waitForIdle()
        Thread.sleep(700)
        fun capture(name: String): Bitmap {
            val control = compose.onNodeWithTag("segment-control").captureToImage().asAndroidBitmap()
            val bitmap = Bitmap.createBitmap(control, control.width / 12, control.height / 3,
                control.width / 6, control.height / 3)
            val directory = File(compose.activity.getExternalFilesDir(null), "glass-continuity").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return bitmap
        }
        val before = capture("segment-before")
        val opticalSource = requireNotNull(capturedLabels.get())
        var sourceGlyphPixels = 0
        for (y in 0 until opticalSource.height) for (x in 0 until opticalSource.width) {
            val pixel = opticalSource.getPixel(x, y)
            if (android.graphics.Color.red(pixel) > 180 && android.graphics.Color.green(pixel) < 100) sourceGlyphPixels++
        }
        assertTrue("Labels must remain inside the refracted source, not only on a plain foreground", sourceGlyphPixels > 50)
        val entered = CountDownLatch(1)
        val unblock = CountDownLatch(1)
        GlassLensCaptureDispatcher.post { entered.countDown(); unblock.await(8, TimeUnit.SECONDS) }
        assertTrue(entered.await(3, TimeUnit.SECONDS))
        val pending: Bitmap
        try {
            compose.runOnIdle { label = "8888" }
            compose.waitForIdle()
            compose.waitUntil(timeoutMillis = 250) {
                val control = compose.onNodeWithTag("segment-control").captureToImage().asAndroidBitmap()
                !before.sameAs(Bitmap.createBitmap(control, control.width / 12, control.height / 3,
                    control.width / 6, control.height / 3))
            }
            pending = capture("segment-pending")
        } finally { unblock.countDown() }
        Thread.sleep(700)
        val settled = capture("segment-settled")
        assertFalse("The live label must change immediately", before.sameAs(pending))
        assertEquals(settled.width, pending.width)
        assertEquals(settled.height, pending.height)
        var changed = 0
        var red = 0
        for (y in 0 until pending.height) for (x in 0 until pending.width) {
            fun isLabel(pixel: Int) = android.graphics.Color.red(pixel) > 180 &&
                android.graphics.Color.green(pixel) < 100 && android.graphics.Color.blue(pixel) < 100
            val actual = isLabel(pending.getPixel(x, y))
            if (actual) red++
            if (actual != isLabel(settled.getPixel(x, y))) changed++
        }
        assertTrue("The selected label disappeared", red > 50)
        assertTrue("A pending optical capture covered the live label: $changed differing glyph pixels", changed < 20)
    }

    @Test fun movingLensKeepsBackgroundLabelsInPlaceWhileRenderingIsBusy() {
        var position by mutableStateOf(20.dp)
        var density = 1f
        compose.setContent {
            CourseSelectorTheme {
                density = LocalDensity.current.density
                val anchor = rememberGlassLensAnchor("moving-label") {
                    drawRect(Color.White)
                    drawRect(Color.Red, Offset(84.dp.toPx(), 42.dp.toPx()), Size(12.dp.toPx(), 12.dp.toPx()))
                }
                Box(Modifier.size(220.dp, 96.dp).glassLensAnchor(anchor)) {
                    Box(Modifier.offset(x = position, y = 20.dp).size(140.dp, 56.dp)
                        .testTag("moving-lens").glassLens(anchor, GlassLensOpticsProvider { _, h ->
                            GlassLensOptics(h / 2f, 6.dp.value * density, 8.dp.value * density, 0f, 0f, 1f)
                        }))
                }
            }
        }
        compose.waitForIdle()
        Thread.sleep(500)
        fun labelCenter(): Float {
            val bitmap = compose.onNodeWithTag("moving-lens").captureToImage().asAndroidBitmap()
            var count = 0
            var totalX = 0L
            for (y in bitmap.height / 3 until bitmap.height * 2 / 3) for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                if (android.graphics.Color.red(pixel) > 200 && android.graphics.Color.green(pixel) < 80) {
                    count++
                    totalX += x
                }
            }
            assertTrue("The label vanished from the moving lens", count > 20)
            return totalX.toFloat() / count
        }
        val before = labelCenter()
        val entered = CountDownLatch(1)
        val unblock = CountDownLatch(1)
        GlassLensEngine.post { entered.countDown(); unblock.await(5, TimeUnit.SECONDS) }
        assertTrue(entered.await(3, TimeUnit.SECONDS))
        try {
            compose.runOnIdle { position += 24.dp }
            compose.waitForIdle()
            assertEquals("A cached refraction frame must not drag the stationary label along with the lens",
                before - 24f * density, labelCenter(), 1.5f)
        } finally { unblock.countDown() }
    }

    @Test fun settledNavigationReplaysWithoutWaitingForBackdropReadback() {
        val selected = mutableIntStateOf(3)
        val destinations = listOf(BottomNavItem.Courses, BottomNavItem.Schedule,
            BottomNavItem.Grab, BottomNavItem.Grades, BottomNavItem.Settings)
        compose.setContent {
            CourseSelectorTheme {
                val backdrop = rememberLayerBackdrop()
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(Modifier.fillMaxSize().layerBackdrop(backdrop).background(Color(0xFFE2EAF6)))
                    CapsuleNavigationBar(destinations, selected.intValue, { selected.intValue = it },
                        backdrop = backdrop, modifier = Modifier.testTag("live-navigation"))
                }
            }
        }
        compose.waitForIdle()
        Thread.sleep(600)
        compose.mainClock.autoAdvance = false
        fun capture(name: String): Bitmap {
            compose.waitForIdle()
            val bitmap = compose.onNodeWithTag("live-navigation").captureToImage().asAndroidBitmap()
            val directory = File(compose.activity.getExternalFilesDir(null), "glass-continuity").apply { mkdirs() }
            File(directory, "$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            return bitmap
        }
        val baseline = capture("navigation-rest")
        val entered = CountDownLatch(1)
        val unblock = CountDownLatch(1)
        GlassLensCaptureDispatcher.post { entered.countDown(); unblock.await(5, TimeUnit.SECONDS) }
        assertTrue(entered.await(3, TimeUnit.SECONDS))
        try {
            compose.onNodeWithText("成绩").performClick()
            var previous = baseline
            repeat(3) { index ->
                compose.mainClock.advanceTimeBy(128)
                val current = capture("navigation-replay-$index")
                assertFalse("The visible icon froze while the background capture was pending at frame $index",
                    previous.sameAs(current))
                previous = current
            }
            compose.mainClock.advanceTimeBy(256)
            assertTrue("The replay must settle without waiting for another background capture",
                baseline.sameAs(capture("navigation-replay-settled")))
            assertEquals(3, selected.intValue)
        } finally {
            unblock.countDown()
            compose.mainClock.autoAdvance = true
        }
    }
}
