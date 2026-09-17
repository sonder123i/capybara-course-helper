package com.k2767.course.ui.system

import androidx.compose.ui.geometry.Rect
import org.junit.Assert.*
import org.junit.Test

class GlassPortalPlacementTest {
    @Test fun menuUsesSpaceAboveWithoutMovingItsAnchor() {
        val result = resolvePortalPlacement(Rect(20f, 600f, 340f, 648f), Rect(12f, 36f, 348f, 760f), 320f, 48f, 380f, 12f)
        assertTrue(result.opensUp)
        assertEquals(600f, result.y + 380f - 48f, 0.001f)
        assertEquals(552f, result.bodySpace, 0.001f)
    }

    @Test fun smallWindowsClampWidthAndRemainFinite() {
        val result = resolvePortalPlacement(Rect(-20f, 40f, 500f, 88f), Rect(12f, 30f, 308f, 400f), 320f, 48f, 380f, 12f)
        assertFalse(result.opensUp)
        assertEquals(296f, result.width, 0.001f)
        assertEquals(12f, result.x, 0.001f)
        assertEquals(300f, result.bodySpace, 0.001f)
    }
}
