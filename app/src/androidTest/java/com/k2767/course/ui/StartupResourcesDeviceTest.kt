package com.k2767.course.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.k2767.course.BuildConfig
import com.k2767.course.R
import com.k2767.course.ui.theme.StartupLogoRenderer
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

@RunWith(AndroidJUnit4::class)
class StartupResourcesDeviceTest {
    @Test fun startupHandoffUsesTheSameFullLogoAndEveryPhaseStaysInsideTheCanvas() {
        assumeTrue(BuildConfig.UI_PREVIEW)
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val renderer = StartupLogoRenderer(context)
        val bounds = Rect(0, 0, 1024, 1024)
        fun frame(time: Int) = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888).also {
            renderer.draw(Canvas(it), bounds, time.toFloat())
        }
        val native = Bitmap.createBitmap(1024, 1024, Bitmap.Config.ARGB_8888)
        requireNotNull(ContextCompat.getDrawable(context, R.drawable.ic_startup_logo)).apply {
            this.bounds = bounds; draw(Canvas(native))
        }
        val first = frame(0)
        assertTrue("System and app startup logo must share the exact first frame", native.sameAs(first))
        val pixels = IntArray(1024 * 1024)
        first.getPixels(pixels, 0, 1024, 0, 0, 1024, 1024)
        pixels.forEachIndexed { index, color ->
            if (color ushr 24 > 0) {
                val dx = index % 1024 - 512
                val dy = index / 1024 - 512
                assertTrue("Logo exceeds Android splash safe circle", dx * dx + dy * dy <= 341 * 341)
            }
        }
        val output = File(context.getExternalFilesDir(null), "redesign-validation").apply { mkdirs() }
        for (time in listOf(0, 90, 180, 360, 520, 650, 760, 850, 975, 1100)) {
            val bitmap = frame(time)
            repeat(1024) { i ->
                assertEquals(0, bitmap.getPixel(i, 0))
                assertEquals(0, bitmap.getPixel(i, 1023))
                assertEquals(0, bitmap.getPixel(0, i))
                assertEquals(0, bitmap.getPixel(1023, i))
            }
            File(output, "06-startup-" + time + ".png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            bitmap.recycle()
        }
        assertFalse(first.sameAs(frame(180)))
        native.recycle(); first.recycle()
    }
}
