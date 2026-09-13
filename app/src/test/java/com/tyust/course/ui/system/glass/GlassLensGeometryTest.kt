package com.tyust.course.ui.system.glass

import org.junit.Assert.*
import org.junit.Test

class GlassLensGeometryTest {
    @Test fun unmeasuredBackdropEffectsHaveSafeEmptyCorners() {
        val density = androidx.compose.ui.unit.Density(3f)
        for ((width, height) in listOf(0f to 0f, Float.NaN to Float.NaN, 100f to 0f)) {
            assertEquals(GlassLensCorners.uniform(0f), lensCornerRadiiPx(
                androidx.compose.foundation.shape.CircleShape, width, height, density, androidx.compose.ui.unit.LayoutDirection.Ltr))
        }
    }
    @Test fun asymmetricFanKeepsItsLargeCornerInsteadOfBecomingACircle() {
        val corners = GlassLensCorners(220f, 28f, 36f, 28f)
        assertEquals(corners, corners.fit(270f, 270f))
        assertTrue(corners.fit(270f, 270f).topLeft > 135f)
    }

    @Test fun fittedAdjacentCornersNeverOverlapAnEdge() {
        val original = GlassLensCorners(220f, 28f, 36f, 28f)
        for (width in listOf(64f, 180f, 270f)) for (height in listOf(32f, 120f, 270f)) {
            for (corners in listOf(original.fit(width, height), original.gradient(width, height))) {
                assertTrue(corners.topLeft + corners.topRight <= width + 0.001f)
                assertTrue(corners.bottomLeft + corners.bottomRight <= width + 0.001f)
                assertTrue(corners.topLeft + corners.bottomLeft <= height + 0.001f)
                assertTrue(corners.topRight + corners.bottomRight <= height + 0.001f)
                assertEquals(original.topLeft / original.topRight, corners.topLeft / corners.topRight, 0.001f)
            }
        }
    }

    @Test fun symmetricCapsuleStillFitsItsShortEdge() {
        assertEquals(GlassLensCorners.uniform(32f), GlassLensCorners.uniform(100f).fit(200f, 64f))
        assertEquals(GlassLensCorners.uniform(0f), GlassLensCorners.uniform(100f).fit(0f, 64f))
    }

    @Test fun invalidRadiiCannotPoisonShaderUniforms() {
        assertEquals(GlassLensCorners(0f, 0f, 0f, 12f),
            GlassLensCorners(Float.NaN, Float.POSITIVE_INFINITY, -3f, 12f).fit(100f, 100f))
    }
}
