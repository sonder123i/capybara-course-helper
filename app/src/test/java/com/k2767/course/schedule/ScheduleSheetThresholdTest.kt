package com.k2767.course.schedule

import com.k2767.course.ui.system.shouldDismissScheduleSheet
import org.junit.Assert.*
import org.junit.Test

class ScheduleSheetThresholdTest {
    @Test fun shortDragReturnsWhileLongDragOrDownwardFlingDismisses() {
        assertFalse(shouldDismissScheduleSheet(90f, 400f, 0f, 1f))
        assertTrue(shouldDismissScheduleSheet(100f, 400f, 0f, 1f))
        assertTrue(shouldDismissScheduleSheet(30f, 800f, 2200f, 2f))
        assertFalse(shouldDismissScheduleSheet(300f, 800f, -2200f, 2f))
        assertFalse(shouldDismissScheduleSheet(30f, 0f, 3000f, 1f))
    }
}
