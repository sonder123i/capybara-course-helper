package com.k2767.course.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class WidgetTodayTest {

    /** 2026-08-31 是周一 → 第 1 周；2026-09-17 是周四，落在第 3 周。 */
    private val firstWeek = "2026-08-31"

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, 0, 0)
        }.timeInMillis

    private fun snapshot(
        courses: List<WidgetCourse>,
        periods: List<WidgetPeriod> = listOf(
            WidgetPeriod(1, "08:00", "08:45"),
            WidgetPeriod(2, "09:00", "09:45"),
            WidgetPeriod(3, "10:10", "12:00")
        ),
        firstWeekDate: String = firstWeek
    ) = WidgetSnapshot("南昌工学院", firstWeekDate, periods, courses)

    @Test fun parsesWeekExpressionsIncludingParity() {
        assertEquals(setOf(1, 3, 5, 7), WidgetToday.parseWeeks("1-7周(单)"))
        assertEquals(setOf(2, 4, 6), WidgetToday.parseWeeks("第2-6周(双)"))
        assertEquals(setOf(1, 3, 5), WidgetToday.parseWeeks("1,3,5"))
        assertEquals(setOf(18), WidgetToday.parseWeeks("18周"))
        assertNull(WidgetToday.parseWeeks(""))
        assertNull(WidgetToday.parseWeeks("待定"))
        assertNull(WidgetToday.parseWeeks("1-16周(单双)"))
        assertNull(WidgetToday.parseWeeks("30周"))
    }

    @Test fun computesWeekNumberFromSemesterStart() {
        assertEquals(1, WidgetToday.weekAt(firstWeek, at(2026, 9, 3)))
        assertEquals(3, WidgetToday.weekAt(firstWeek, at(2026, 9, 17)))
        assertEquals(4, WidgetToday.weekAt(firstWeek, at(2026, 9, 21)))
        // 起始日不是周一、或还没开学 → 算不出来
        assertNull(WidgetToday.weekAt("2026-09-02", at(2026, 9, 17)))
        assertNull(WidgetToday.weekAt(firstWeek, at(2026, 8, 20)))
        assertNull(WidgetToday.weekAt("", at(2026, 9, 17)))
    }

    @Test fun showsOnlyTodaysCoursesForTheCurrentWeek() {
        val courses = listOf(
            WidgetCourse("a", "模拟电子技术", "", "创新楼A-319", 4, 3, 3, "1-16周"),
            WidgetCourse("b", "大学物理", "", "至善楼406", 4, 1, 2, "2-16周(双)"),
            WidgetCourse("c", "线性代数", "", "致远楼A519", 4, 1, 2, "1-16周(单)"),
            WidgetCourse("d", "周五的课", "", "", 5, 1, 2, "1-16周")
        )
        val rows = WidgetToday.rows(snapshot(courses), now = at(2026, 9, 17), zone = TimeZone.getDefault())

        // 第 3 周是单周：双周课被过滤；周五的课不在今天；剩下的按起始节次排序
        assertEquals(listOf("线性代数", "模拟电子技术"), rows.map { it.name })
        assertEquals("08:00 - 09:45", rows[0].time)
        assertEquals("10:10 - 12:00", rows[1].time)
        assertEquals("创新楼A-319", rows[1].location)
    }

    @Test fun maxRowsTruncatesForSmallWidgets() {
        val courses = listOf(
            WidgetCourse("a", "第一门", "", "", 4, 1, 1, "1-16周"),
            WidgetCourse("b", "第二门", "", "", 4, 2, 2, "1-16周"),
            WidgetCourse("c", "第三门", "", "", 4, 3, 3, "1-16周")
        )
        val rows = WidgetToday.rows(snapshot(courses), now = at(2026, 9, 17), max = 2)
        assertEquals(listOf("第一门", "第二门"), rows.map { it.name })
    }

    @Test fun emptyWhenNoCourseToday() {
        val courses = listOf(WidgetCourse("a", "周五的课", "", "", 5, 1, 1, "1-16周"))
        assertTrue(WidgetToday.rows(snapshot(courses), now = at(2026, 9, 17)).isEmpty())
    }

    @Test fun parsesSnapshotJsonWithTheSharedSchema() {
        val raw = """
            {"school":"南昌工学院","firstWeekDate":"$firstWeek",
             "periods":[{"p":1,"s":"08:00","e":"08:45"}],
             "courses":[{"id":"x","name":"模拟电子技术","teacher":"","location":"创新楼A-319",
                         "day":4,"start":1,"end":1,"weeks":"1-16周"}]}
        """.trimIndent()
        val parsed = WidgetSnapshot.parse(raw)!!
        assertEquals("南昌工学院", parsed.school)
        assertEquals(1, parsed.periods.size)
        assertEquals("模拟电子技术", parsed.courses.single().name)
        assertEquals(4, parsed.courses.single().day)
        assertNull(WidgetSnapshot.parse("not json"))
    }
}