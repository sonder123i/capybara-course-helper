package com.k2767.course.ui.system.glass

import android.graphics.Bitmap
import android.graphics.Color as AndroidColor
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.captureToImage
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.k2767.course.ui.theme.CourseSelectorTheme
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 31, maxSdkVersion = 32)
class GlassLensCaptureDeviceTest {
    @get:Rule val compose = createAndroidComposeRule<ComponentActivity>()

    private fun centerColor(): Int = compose.onNodeWithTag("capture-lens").captureToImage()
        .asAndroidBitmap().let { it.getPixel(it.width / 2, it.height / 2) }

    @Test fun sourceIsReadyBeforeAnEnteringControlBecomesVisible() {
        lateinit var anchor: GlassLensAnchor
        var shown by mutableStateOf(false)
        compose.setContent {
            CourseSelectorTheme {
                anchor = requireNotNull(rememberGlassLensAnchor("entering-control") { drawRect(Color.Red) })
                Box(Modifier.size(160.dp).glassLensAnchor(anchor, prewarm = true), contentAlignment = Alignment.Center) {
                    if (shown) Box(Modifier.size(96.dp).testTag("capture-lens").glassLens(anchor,
                        GlassLensOpticsProvider { _, h -> GlassLensOptics(h / 2f, 12f, 20f, 0f, 0f, 1f) }))
                }
            }
        }
        compose.waitUntil(3_000) { anchor.sourceFrame != null }
        compose.runOnIdle { shown = true }
        compose.waitUntil(3_000) { AndroidColor.red(centerColor()) > 220 }
    }

    @Test fun boundedCaptureKeepsSamplingInTheSamePlaceAsTheVisibleControl() {
        lateinit var anchor: GlassLensAnchor
        var offset by mutableStateOf(0.dp)
        compose.setContent {
            CourseSelectorTheme {
                anchor = requireNotNull(rememberGlassLensAnchor("bounded-source", maxCapturePixels = 20_000) {
                    drawRect(Color.Blue)
                    drawRect(Color.Red, Offset(100.dp.toPx(), 100.dp.toPx()),
                        androidx.compose.ui.geometry.Size(40.dp.toPx(), 40.dp.toPx()))
                })
                Box(Modifier.size(240.dp).glassLensAnchor(anchor), contentAlignment = Alignment.Center) {
                    Box(Modifier.offset(x = offset).size(96.dp).testTag("capture-lens").glassLens(anchor,
                        GlassLensOpticsProvider { _, h -> GlassLensOptics(h / 2f, 12f, 20f, 0f, 0f, 1f) }))
                }
            }
        }
        compose.waitUntil(3_000) { anchor.sourceFrame != null && AndroidColor.red(centerColor()) > 220 }
        val pixels = requireNotNull(anchor.sourceFrame).geometry.size
        assertTrue(pixels.width * pixels.height <= 20_000)
        assertTrue(anchor.sizePx.width * anchor.sizePx.height > 20_000)
        compose.runOnIdle { offset = 48.dp }
        compose.waitUntil(3_000) { AndroidColor.blue(centerColor()) > 220 }
    }

    @Test fun capturesAfterTheSourceLayerHasRecordedItsNewContent() {
        lateinit var anchor: GlassLensAnchor
        var color by mutableStateOf(Color.Red)
        compose.setContent {
            CourseSelectorTheme {
                val density = LocalDensity.current
                val backdrop = rememberLayerBackdrop()
                anchor = requireNotNull(rememberGlassLensAnchor("after-source") {
                    drawBackdropSource(backdrop, density, it)
                })
                Box(Modifier.size(160.dp).glassLensAnchor(anchor), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(96.dp).testTag("capture-lens").glassLens(anchor,
                        GlassLensOpticsProvider { _, h -> GlassLensOptics(h / 2f, 12f, 20f, 0f, 0f, 1f) }))
                    // This source records after the lens, as can happen with subcomposed
                    // headers and their list. It must still deliver the final blue frame.
                    Box(Modifier.fillMaxSize().alpha(0f).layerBackdrop(backdrop).background(color))
                }
            }
        }
        compose.waitUntil(5_000) { AndroidColor.red(centerColor()) > 220 }
        compose.runOnIdle { color = Color.Blue; anchor.invalidate() }
        compose.waitUntil(5_000) { AndroidColor.blue(centerColor()) > 220 }
        compose.runOnIdle {
            assertFalse("Hardware readback must not block pointer processing", anchor.lastReadbackOnMain)
            assertTrue(anchor.lastReadbackNanos > 0)
        }
    }

    @Test fun updatesDuringReadbackDeliverTheFinalGeometryWithoutAnotherGesture() {
        lateinit var anchor: GlassLensAnchor
        var color by mutableStateOf(Color.Red)
        var shifted by mutableStateOf(false)
        compose.setContent {
            CourseSelectorTheme {
                anchor = requireNotNull(rememberGlassLensAnchor("in-flight-layout") { drawRect(color) })
                Box(Modifier.fillMaxSize()) {
                    Box(Modifier.offset(y = if (shifted) 180.dp else 30.dp)
                        .size(if (shifted) 180.dp else 140.dp).glassLensAnchor(anchor),
                        contentAlignment = Alignment.Center) {
                        Box(Modifier.size(72.dp).testTag("capture-lens").glassLens(anchor,
                            GlassLensOpticsProvider { _, h -> GlassLensOptics(h / 2f, 12f, 20f, 0f, 0f, 1f) }))
                    }
                }
            }
        }
        compose.waitUntil(5_000) { AndroidColor.red(centerColor()) > 220 }
        val readbackStarted = CountDownLatch(1)
        val unblock = CountDownLatch(1)
        GlassLensCaptureDispatcher.post { readbackStarted.countDown(); unblock.await(3, TimeUnit.SECONDS) }
        assertTrue(readbackStarted.await(3, TimeUnit.SECONDS))
        try {
            compose.runOnIdle { color = Color.Green; anchor.invalidate() }
            compose.runOnIdle { shifted = true; color = Color.Blue; anchor.invalidate() }
        } finally { unblock.countDown() }
        compose.waitUntil(5_000) { AndroidColor.blue(centerColor()) > 220 }
        compose.waitUntil(5_000) { !anchor.lastReadbackOnMain && anchor.lastReadbackNanos > 0 }
        Thread.sleep(250)
        assertTrue("A late old capture must not overwrite the settled result", AndroidColor.blue(centerColor()) > 220)
    }

    @Test fun sourceUploadCannotRenderCoordinatesFromThePreviousCapture() {
        val source = GlassLensSource()
        val target = GlassLensTarget(source)
        fun pixels(color: Int) = Bitmap.createBitmap(100, 100, Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        fun params(generation: Int) = GlassLensParams(60, 60, 20f, 20f, 12f, 8f, 0f, 0f, 0f, 1f,
            sourceGeneration = generation)
        try {
            source.uploadSource(pixels(AndroidColor.RED), 1)
            target.submit(params(1))
            compose.waitUntil(5_000) { target.latestFrame?.sourceGeneration == 1 }
            val first = target.latest
            source.uploadSource(pixels(AndroidColor.BLUE), 2)
            val uploaded = CountDownLatch(1)
            GlassLensEngine.post { uploaded.countDown() }
            assertTrue(uploaded.await(3, TimeUnit.SECONDS))
            Thread.sleep(80)
            assertSame("New pixels with old coordinates must not be published", first, target.latest)
            target.submit(params(2))
            compose.waitUntil(5_000) { target.latestFrame?.sourceGeneration == 2 }
            assertTrue(AndroidColor.blue(requireNotNull(target.latest).getPixel(30, 30)) > 220)
        } finally { target.release(); source.release() }
    }

    @Test fun refreshingTheSourceKeepsTheCompletedRefractionUntilReplacementArrives() {
        lateinit var anchor: GlassLensAnchor
        var strength by mutableFloatStateOf(0.4f)
        compose.setContent {
            CourseSelectorTheme {
                anchor = requireNotNull(rememberGlassLensAnchor("no-flash") {
                    drawRect(Color.White)
                    for (x in -20..20) drawLine(Color.Black, Offset(x * 20f, 0f),
                        Offset(x * 20f + size.height, size.height), 9f)
                })
                Box(Modifier.size(160.dp).glassLensAnchor(anchor), contentAlignment = Alignment.Center) {
                    Box(Modifier.size(96.dp).testTag("capture-lens").glassLens(anchor,
                        GlassLensOpticsProvider { _, h -> GlassLensOptics(h / 2f, h * 0.35f, h * strength, 0f, 0f, 1f) }))
                }
            }
        }
        compose.waitForIdle()
        Thread.sleep(600)
        val before = compose.onNodeWithTag("capture-lens").captureToImage().asAndroidBitmap()
        val previousGeneration = requireNotNull(anchor.captureFrame).generation
        val entered = CountDownLatch(1)
        val unblock = CountDownLatch(1)
        GlassLensCaptureDispatcher.post { entered.countDown(); unblock.await(5, TimeUnit.SECONDS) }
        assertTrue(entered.await(3, TimeUnit.SECONDS))
        try {
            compose.runOnIdle { anchor.invalidate() }
            compose.waitUntil(3_000) { requireNotNull(anchor.captureFrame).generation > previousGeneration }
            val during = compose.onNodeWithTag("capture-lens").captureToImage().asAndroidBitmap()
            assertTrue("A pending readback must not expose un-refracted pixels", before.sameAs(during))
            compose.runOnIdle { strength = 0.08f }
            compose.waitUntil(1_500) {
                !before.sameAs(compose.onNodeWithTag("capture-lens").captureToImage().asAndroidBitmap())
            }
        } finally { unblock.countDown() }
    }
}
