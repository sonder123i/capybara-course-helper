package com.tyust.course.schedule

import com.tyust.course.ui.screen.gradesActionReservation
import org.junit.Assert.*
import org.junit.Test

class HeaderReservationTest {
    @Test fun railIsFullyReservedBeforeTheMovingRowsTouchIncludingLargeText() {
        for (titleHeight in listOf(48f, 78f, 112f, 160f)) for (step in 0..100) {
            val p = step / 100f
            val actionsBottom = (titleHeight - 48f) / 2f * (1f - p) + 48f
            val segmentTop = (titleHeight + 10f) * (1f - p)
            if (actionsBottom >= segmentTop) {
                assertEquals("Moving action rail needs full reservation", 1f,
                    gradesActionReservation(p, titleHeight, 10f, 48f), 0f)
            }
        }
        assertEquals(0f, gradesActionReservation(0f, 78f, 10f, 48f), 0f)
    }
}
