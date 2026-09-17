package com.k2767.course.ui.system.glass

import android.graphics.Bitmap
import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 31)
class GlassFrameSchedulingDeviceTest {
    private fun params(width: Int = 100) = GlassLensParams(
        widthPx = width, heightPx = 64, srcLeftPx = 16f, srcTopPx = 16f,
        cornerRadiusPx = 20f, thicknessPx = 10f, lensAmountPx = 14f,
        dispersion = 0f, depthEffect = 0f, vibrancy = 1f
    )

    private fun sourceBitmap(color: Int) = Bitmap.createBitmap(256, 128, Bitmap.Config.ARGB_8888).apply {
        eraseColor(color)
    }

    @Test fun completedFrameResubmissionAndTenSecondsIdleProduceNoExtraFrames() {
        val source = GlassLensSource()
        val target = GlassLensTarget(source)
        val frameCount = AtomicInteger()
        val firstFrame = CountDownLatch(1)
        val request = params()
        try {
            target.onFrameReady = {
                frameCount.incrementAndGet()
                target.submit(request) // Simulate invalidateDraw submitting the same request again.
                firstFrame.countDown()
            }
            source.uploadSource(sourceBitmap(Color.RED), 1)
            target.submit(request)
            assertTrue("first frame missing", firstFrame.await(5, TimeUnit.SECONDS))
            repeat(100) { target.submit(request) }
            Thread.sleep(10_000)
            assertFalse(target.failed)
            assertEquals("static glass must not use completed frames as a clock", 1, frameCount.get())
        } finally {
            target.release()
            source.release()
        }
    }

    @Test fun finalBurstRequestAndNewSourceAreDeliveredWithoutAnotherUiSubmit() {
        val source = GlassLensSource()
        val target = GlassLensTarget(source)
        val finalFrame = CountDownLatch(1)
        val replacementFrame = CountDownLatch(1)
        try {
            target.onFrameReady = {
                target.latest?.let { bitmap ->
                    if (bitmap.width == 140) {
                        val pixel = bitmap.getPixel(70, 32)
                        if (Color.red(pixel) > 200) finalFrame.countDown()
                        if (Color.blue(pixel) > 200) replacementFrame.countDown()
                    }
                }
            }
            source.uploadSource(sourceBitmap(Color.RED), 1)
            repeat(40) { target.submit(params(100 + it)) }
            target.submit(params(140))
            assertTrue("last geometry update was lost", finalFrame.await(5, TimeUnit.SECONDS))
            source.uploadSource(sourceBitmap(Color.BLUE), 2)
            assertTrue("new source needs its own invalidation", replacementFrame.await(5, TimeUnit.SECONDS))
            assertFalse(target.failed)
        } finally {
            target.release()
            source.release()
        }
    }
}
