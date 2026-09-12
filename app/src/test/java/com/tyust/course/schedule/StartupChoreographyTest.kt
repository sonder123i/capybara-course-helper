package com.tyust.course.schedule

import com.tyust.course.ui.theme.StartupChoreography
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.abs

class StartupChoreographyTest {
    @Test fun sixCardsStartAndFinishAtTheStaticLogoWithoutAJump() {
        repeat(6) { index ->
            for (time in listOf(0f, 650f, 850f, 1100f)) {
                val frame = StartupChoreography.card(time, index)
                assertEquals(0f, frame.x, 0.001f)
                assertEquals(0f, frame.y, 0.001f)
                assertEquals(0f, frame.rotation, 0.001f)
                assertEquals(0f, frame.cardAmount, 0.001f)
            }
            assertEquals(1f, StartupChoreography.card(180f, index).cardAmount, 0f)
            for (boundary in listOf(180f, 650f)) {
                val before = StartupChoreography.card(boundary - 0.01f, index)
                val after = StartupChoreography.card(boundary + 0.01f, index)
                assertTrue(abs(before.x - after.x) < 0.1f)
                assertTrue(abs(before.y - after.y) < 0.1f)
            }
        }
    }

    @Test fun cardMotionStaysInsideTheFullCanvasAndContentOnlyEntersAfterTheBolt() {
        for (time in 0..1100 step 5) {
            repeat(6) { index ->
                val frame = StartupChoreography.card(time.toFloat(), index)
                val x = 316f + (index % 3) * 196f + frame.x
                val y = 390f + (index / 3) * 240f + frame.y
                // Includes the largest card and rotation, with breathing room on all sides.
                assertTrue(x - 110f > 0f && x + 110f < 1024f)
                assertTrue(y - 110f > 0f && y + 110f < 1024f)
            }
            assertTrue(StartupChoreography.content(time.toFloat()) in 0f..1f)
        }
        assertEquals(0f, StartupChoreography.content(850f), 0f)
        assertEquals(1f, StartupChoreography.content(1100f), 0f)
        assertEquals(0f, StartupChoreography.bolt(650f), 0f)
        assertEquals(1f, StartupChoreography.bolt(850f), 0f)
    }
}
