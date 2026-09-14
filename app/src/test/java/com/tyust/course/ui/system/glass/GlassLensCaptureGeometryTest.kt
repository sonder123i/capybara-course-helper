package com.tyust.course.ui.system.glass

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.unit.IntSize
import org.junit.Assert.*
import org.junit.Test

class GlassLensCaptureGeometryTest {
    @Test fun boundedCapturesPreserveTheWindowToTextureMapping() {
        val full = GlassLensCaptureGeometry(IntSize(800, 600), Offset(20f, 50f),
            Offset(0.8f, 0.2f), Offset(-0.1f, 1.1f))
        val reduced = full.fitPixelBudget(120_000)
        assertEquals(IntSize(400, 300), reduced.size)
        val point = full.origin + full.xAxis * 250f + full.yAxis * 160f
        assertEquals(Offset(125f, 80f).x, reduced.windowToLocal(point).x, 0.001f)
        assertEquals(Offset(125f, 80f).y, reduced.windowToLocal(point).y, 0.001f)
        assertSame(full, full.fitPixelBudget(800 * 600))
    }

    @Test fun recordedWindowTransformSurvivesLaterAnchorMovement() {
        val recorded = GlassLensCaptureGeometry(IntSize(200, 100), Offset(30f, 400f),
            Offset(0.8f, 0.2f), Offset(-0.1f, 1.1f))
        val local = Offset(40f, 20f)
        val window = recorded.origin + recorded.xAxis * local.x + recorded.yAxis * local.y
        val result = recorded.windowToLocal(window)
        assertEquals(local.x, result.x, 0.001f)
        assertEquals(local.y, result.y, 0.001f)
        val next = recorded.copy(origin = Offset(30f, 100f))
        assertNotEquals(next.windowToLocal(window), result)
    }

    @Test fun singularTransformsAreRejectedInsteadOfSamplingAnArbitraryEdge() {
        val collapsed = GlassLensCaptureGeometry(IntSize(200, 100), Offset.Zero, Offset.Zero, Offset.Zero)
        assertFalse(collapsed.windowToLocal(Offset(5f, 5f)).x.isFinite())
    }
}
