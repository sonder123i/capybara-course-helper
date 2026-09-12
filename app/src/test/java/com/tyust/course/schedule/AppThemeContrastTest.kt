package com.tyust.course.schedule

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.tyust.course.ui.theme.DarkColorScheme
import com.tyust.course.ui.theme.LightColorScheme
import org.junit.Assert.assertTrue
import org.junit.Test

class AppThemeContrastTest {
    private fun contrast(a: Color, b: Color): Float = (maxOf(a.luminance(), b.luminance()) + 0.05f) /
        (minOf(a.luminance(), b.luminance()) + 0.05f)

    @Test fun semanticTextAndFilledButtonsMeetNormalTextContrast() {
        listOf(LightColorScheme, DarkColorScheme).forEach { colors ->
            listOf(colors.onSurface to colors.surface, colors.onSurfaceVariant to colors.surface,
                colors.onBackground to colors.background, colors.onPrimary to colors.primary,
                colors.onError to colors.error, colors.onPrimaryContainer to colors.primaryContainer,
                colors.onErrorContainer to colors.errorContainer).forEach { (text, background) ->
                assertTrue("Text contrast $text on $background", contrast(text, background) >= 4.5f)
            }
        }
    }
}
