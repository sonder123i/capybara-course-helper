package com.tyust.course.schedule

import com.tyust.course.manager.AppThemeMode
import com.tyust.course.manager.resolveDarkTheme
import org.junit.Assert.*
import org.junit.Test

class ScheduleDomainTest {
    @Test fun themeSelectionOverridesSystemAndUnknownPreferencesFollowSystem() {
        for (system in listOf(false, true)) {
            assertEquals(system, resolveDarkTheme(AppThemeMode.System, system))
            assertFalse(resolveDarkTheme(AppThemeMode.Light, system))
            assertTrue(resolveDarkTheme(AppThemeMode.Dark, system))
        }
        assertEquals(AppThemeMode.System, AppThemeMode.decode(null))
        assertEquals(AppThemeMode.System, AppThemeMode.decode("obsolete"))
        AppThemeMode.entries.forEach { assertEquals(it, AppThemeMode.decode(it.storageValue)) }
    }

    @Test fun mixedParityAndChineseSeparatorsAreSegmentLocal() {
        assertEquals(setOf(1, 2, 3, 4, 6, 8, 10, 12, 14, 15, 16), ScheduleWeeks.parse("1-4周，6-14周（双）、15-16周").weeks)
        assertEquals(setOf(1, 3, 5, 8), ScheduleWeeks.parse("１－５周(单), 8周").weeks)
        listOf("", "待定", "0-4", "4-1", "1-26", "1周,").forEach { assertFalse(it, ScheduleWeeks.parse(it).valid) }
        assertTrue(ScheduleWeeks.parse("待定").visibleIn(6))
    }

    @Test fun identitiesIgnoreListOrderAndEquivalentWeekFormatting() {
        val first = ScheduleIdentity.network("row1", "数学", "王老师", 1, 1, 2, "1-3周")
        assertEquals(first, ScheduleIdentity.network("row1", "数学", "王老师", 1, 1, 2, "1周,2周,3周"))
        assertNotEquals(first, ScheduleIdentity.network("row2", "数学", "王老师", 1, 1, 2, "1-3周"))
        assertNotEquals(first, ScheduleIdentity.network("row1", "数学", "王老师", 3, 1, 2, "1-3周"))
    }

    @Test fun conflictRequiresIntersectingDayPeriodsAndWeeksAndExcludesSelf() {
        val course = ScheduleCourseRecord("one", "数学", "", "", 1, 1, 2, "1-8周(单)")
        val conflicts = scheduleConflicts(course, listOf(course,
            course.copy(id = "touch", startPeriod = 2, endPeriod = 3, weeks = "3-5周"),
            course.copy(id = "later", startPeriod = 3, endPeriod = 4),
            course.copy(id = "even", weeks = "2-8周(双)"),
            course.copy(id = "other-day", day = 2)))
        assertEquals(listOf("touch"), conflicts.map { it.otherId })
        assertEquals(setOf(3, 5), conflicts.single().weeks)
        assertEquals(2, conflicts.single().startPeriod)
        assertEquals(2, conflicts.single().endPeriod)
    }

    @Test fun calendarWeekUsesLocalCivilDatesAcrossDaylightSavingBoundaries() {
        val zone = java.util.TimeZone.getTimeZone("Europe/Berlin")
        val later = com.tyust.course.schedule.ScheduleDates.date("2026-10-19", 2, zone = zone)!!
        assertEquals(2, ScheduleDates.weekAt("2026-10-19", later.timeInMillis, zone))
        assertNull(ScheduleDates.firstMonday("2026-02-30"))
        assertNull(ScheduleDates.firstMonday("2026-09-08"))
    }
}
