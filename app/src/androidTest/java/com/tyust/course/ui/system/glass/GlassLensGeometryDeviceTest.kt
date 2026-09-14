package com.tyust.course.ui.system.glass

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlin.math.abs

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 31)
class GlassLensGeometryDeviceTest {
    private fun params() = GlassLensParams(256, 232, 0f, 0f, 18f, 24f, 0f, 0f, 0f, 1f,
        corners = GlassLensCorners(190f, 22f, 30f, 18f))

    private fun render(target: GlassLensTarget, params: GlassLensParams): Bitmap {
        val ready = CountDownLatch(1)
        target.onFrameReady = { ready.countDown() }
        target.submit(params)
        assertTrue("GL did not publish a frame", ready.await(5, TimeUnit.SECONDS))
        assertFalse(target.failed)
        return requireNotNull(target.latest)
    }

    @Test fun asymmetricShaderMaskMatchesTheActualCanvasOutline() {
        val source = GlassLensSource()
        val target = GlassLensTarget(source)
        try {
            source.uploadSource(Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888).apply { eraseColor(Color.WHITE) }, 1)
            val output = render(target, params())
            val mask = Bitmap.createBitmap(256, 232, Bitmap.Config.ARGB_8888)
            val path = Path().apply { addRoundRect(RectF(0f, 0f, 256f, 232f),
                floatArrayOf(190f, 190f, 22f, 22f, 30f, 30f, 18f, 18f), Path.Direction.CW) }
            Canvas(mask).drawPath(path, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.WHITE })
            var mismatches = 0
            for (y in 0 until 232) for (x in 0 until 256) {
                val expected = Color.alpha(mask.getPixel(x, y))
                if (expected in 8..247) continue
                if (abs(expected - Color.alpha(output.getPixel(x, y))) > 100) mismatches++
            }
            assertTrue("The fan became a circle or used the wrong corner: $mismatches pixels", mismatches < 30)
        } finally { target.release(); source.release() }
    }

    @Test fun transformedLensSamplesTheCorrectBackgroundCoordinates() {
        val source = GlassLensSource()
        val target = GlassLensTarget(source)
        try {
            source.uploadSource(Bitmap.createBitmap(256, 256, Bitmap.Config.ARGB_8888).apply {
                for (y in 0 until height) for (x in 0 until width) setPixel(x, y, Color.rgb(x, y, 0))
            }, 1)
            val axes = GlassLensSourceAxes(0.7f, 0.18f, -0.12f, 0.8f)
            val output = render(target, params().copy(widthPx = 100, heightPx = 80, srcLeftPx = 80f, srcTopPx = 60f,
                corners = GlassLensCorners.uniform(12f), sourceAxes = axes))
            for (y in listOf(20, 40, 60)) for (x in listOf(20, 50, 80)) {
                val pixel = output.getPixel(x, y)
                assertEquals(80f + x * axes.xx + y * axes.yx, Color.red(pixel).toFloat(), 2f)
                assertEquals(60f + x * axes.xy + y * axes.yy, Color.green(pixel).toFloat(), 2f)
            }
        } finally { target.release(); source.release() }
    }

    @Test fun transparentSourcePixelsNeverBecomeOpaqueBlack() {
        val source = GlassLensSource()
        val target = GlassLensTarget(source)
        try {
            for ((generation, alpha) in listOf(0, 96).withIndex()) {
                source.uploadSource(Bitmap.createBitmap(300, 300, Bitmap.Config.ARGB_8888).apply {
                    eraseColor(Color.argb(alpha, 220, 180, 140))
                }, generation + 1)
                for ((amount, dispersion) in listOf(0f to 0f, 18f to 0f, 18f to 1f)) {
                    val output = render(target, params().copy(lensAmountPx = amount, dispersion = dispersion,
                        sourceGeneration = generation + 1))
                    for ((x, y) in listOf(128 to 116, 128 to 229, 235 to 80)) {
                        assertEquals("Transparent or fading content must retain its alpha at $x,$y",
                            alpha.toFloat(), Color.alpha(output.getPixel(x, y)).toFloat(), 2f)
                    }
                }
            }
        } finally { target.release(); source.release() }
    }

    @Test fun publishedFramesRemainUnchangedAcrossFurtherFramesResizeAndRelease() {
        val source = GlassLensSource()
        val target = GlassLensTarget(source)
        val held = mutableListOf<Pair<Bitmap, Bitmap>>()
        try {
            source.uploadSource(Bitmap.createBitmap(400, 300, Bitmap.Config.ARGB_8888).apply {
                for (y in 0 until height) for (x in 0 until width) setPixel(x, y, if (x % 20 < 10) Color.BLACK else Color.WHITE)
            }, 1)
            repeat(6) { index ->
                val frame = render(target, params().copy(srcLeftPx = index * 7f, widthPx = if (index > 3) 220 else 256))
                held += frame to frame.copy(Bitmap.Config.ARGB_8888, false)
                held.forEach { (published, snapshot) ->
                    assertFalse("A display list can still own this frame", published.isRecycled)
                    assertTrue("A later GL frame overwrote a published icon", published.sameAs(snapshot))
                }
            }
        } finally { target.release(); source.release() }
        Thread.sleep(50)
        held.forEach { (published, snapshot) -> assertFalse(published.isRecycled); assertTrue(published.sameAs(snapshot)) }
    }

    @Test fun largeBlurredSurfaceKeepsLogicalCoordinatesWithABoundedReadback() {
        val source = GlassLensSource()
        val target = GlassLensTarget(source)
        try {
            val pixels = IntArray(1000 * 800) { index -> Color.rgb((index % 1000) / 4, (index / 1000) / 4, 0) }
            source.uploadSource(Bitmap.createBitmap(pixels, 1000, 800, Bitmap.Config.ARGB_8888), 1)
            val output = render(target, params().copy(widthPx = 800, heightPx = 600,
                srcLeftPx = 80f, srcTopPx = 60f, corners = GlassLensCorners.uniform(24f), maxRenderPixels = 160_000))
            assertTrue("Only the blurred optical layer needs a bounded readback", output.width * output.height <= 160_000)
            assertEquals(800f / 600f, output.width.toFloat() / output.height, 0.01f)
            for (fy in listOf(0.25f, 0.75f)) for (fx in listOf(0.25f, 0.75f)) {
                val x = (output.width * fx).toInt()
                val y = (output.height * fy).toInt()
                val pixel = output.getPixel(x, y)
                assertEquals((80f + (x + 0.5f) * 800f / output.width) / 4f, Color.red(pixel).toFloat(), 2f)
                assertEquals((60f + (y + 0.5f) * 600f / output.height) / 4f, Color.green(pixel).toFloat(), 2f)
            }
        } finally { target.release(); source.release() }
    }
}
