package com.tyust.course.ui.system.glass

import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.tyust.course.ui.theme.CourseSelectorTheme
import com.tyust.course.ui.system.LiquidTaskControls
import com.tyust.course.ui.system.TaskControlsState
import com.tyust.course.ui.system.TaskQuickAction
import com.tyust.course.ui.system.AnimatedIconSpec
import com.tyust.course.ui.system.rememberTaskControlsState
import androidx.compose.ui.platform.LocalDensity
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 31, maxSdkVersion = 32)
class GlassLensSourceCachingDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    @Test fun overlayFramesReuseStaticBackgroundAndDeliverTheirFinalPixels() {
        lateinit var anchor: GlassLensAnchor
        var phase by mutableIntStateOf(0)
        var backgroundColor by mutableStateOf(Color.White)
        compose.setContent {
            CourseSelectorTheme {
                anchor = requireNotNull(rememberGlassLensAnchor("cache-probe", overlaySource = {
                    drawCircle(if (phase % 2 == 0) Color.Red else Color.Blue, 30.dp.toPx(), center)
                }) {
                    drawRect(backgroundColor)
                    for (x in 0..20) drawLine(Color.Black, Offset(x * 16.dp.toPx(), 0f),
                        Offset(x * 16.dp.toPx(), size.height), 2.dp.toPx())
                })
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(160.dp).glassLensAnchor(anchor), contentAlignment = Alignment.Center) {
                        Box(Modifier.size(96.dp).testTag("cached-lens").glassLens(anchor,
                            GlassLensOpticsProvider { w, h -> GlassLensOptics(minOf(w, h) / 2, h * 0.15f, h * 0.20f, 0f, 0f, 1f) }))
                    }
                }
            }
        }
        compose.waitForIdle()
        Thread.sleep(400) // Includes the initial wallpaper-settle invalidation and async GL frame.
        val initial = compose.onNodeWithTag("cached-lens").captureToImage().asAndroidBitmap()
        var backgroundCaptures = 0
        var overlayCaptures = 0
        compose.runOnIdle { backgroundCaptures = anchor.backgroundCaptureCount; overlayCaptures = anchor.overlayCaptureCount }
        repeat(5) {
            compose.runOnIdle { phase++; anchor.invalidateOverlay() }
            compose.waitForIdle()
            Thread.sleep(80)
        }
        val final = compose.onNodeWithTag("cached-lens").captureToImage().asAndroidBitmap()
        compose.runOnIdle {
            assertEquals("Icon animation must not re-capture or blur the page", backgroundCaptures, anchor.backgroundCaptureCount)
            assertTrue(anchor.overlayCaptureCount >= overlayCaptures + 5)
        }
        assertTrue(android.graphics.Color.red(initial.getPixel(initial.width / 2, initial.height / 2)) > 200)
        assertTrue("The last overlay update must reach the glass", android.graphics.Color.blue(final.getPixel(final.width / 2, final.height / 2)) > 200)
        compose.runOnIdle {
            backgroundCaptures = anchor.backgroundCaptureCount
            overlayCaptures = anchor.overlayCaptureCount
            backgroundColor = Color.Magenta
            anchor.invalidateBackground()
        }
        compose.waitForIdle()
        Thread.sleep(100)
        val scrolled = compose.onNodeWithTag("cached-lens").captureToImage().asAndroidBitmap()
        compose.runOnIdle {
            assertEquals(backgroundCaptures + 1, anchor.backgroundCaptureCount)
            assertEquals("Scrolling must reuse settled glyphs instead of reading them back again",
                overlayCaptures, anchor.overlayCaptureCount)
        }
        assertFalse("The newly sampled background must still reach the glass", final.sameAs(scrolled))
        assertTrue(android.graphics.Color.blue(scrolled.getPixel(scrolled.width / 2, scrolled.height / 2)) > 200)
    }

    @Test fun primaryControlCapturesOnlyItsReachableAreaAndExpandsForTheFan() {
        lateinit var anchor: GlassLensAnchor
        lateinit var controls: TaskControlsState
        var pixelDensity = 1f
        compose.setContent {
            CourseSelectorTheme {
                pixelDensity = LocalDensity.current.density
                anchor = requireNotNull(rememberGlassLensAnchor("bounded-controls") { drawRect(Color.White) })
                controls = rememberTaskControlsState()
                val actions = List(6) { TaskQuickAction("$it", "操作 $it", AnimatedIconSpec.Add, onClick = {}) }
                LiquidTaskControls(actions, 24.dp, controls, lensAnchor = anchor) { _, _, _ ->
                    Box(Modifier.size(64.dp).glassLens(anchor, GlassLensOpticsProvider { w, h ->
                        GlassLensOptics(minOf(w, h) / 2f, h * 0.15f, h * 0.20f, 0f, 0f, 1f)
                    }).testTag("bounded-primary"))
                }
            }
        }
        compose.runOnIdle {
            assertEquals(112f, anchor.sizePx.width / pixelDensity, 1f)
            assertEquals(112f, anchor.sizePx.height / pixelDensity, 1f)
            controls.expanded = true
        }
        compose.waitForIdle()
        compose.runOnIdle {
            assertTrue("The complete fan must fit its sampling region", anchor.sizePx.width / pixelDensity > 240f)
            assertTrue("Opening the fan must not capture the whole screen", anchor.sizePx.height / pixelDensity < 340f)
            controls.close()
        }
        compose.waitForIdle()
        compose.runOnIdle { assertEquals(112f, anchor.sizePx.width / pixelDensity, 1f) }
    }
}
