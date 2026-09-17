package com.k2767.course.widget

import androidx.compose.ui.unit.dp
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

    @Test fun tomorrowUsesNextDayAndAdvancesWeek() {
        val courses = listOf(
            WidgetCourse("a", "周四的课", "", "", 4, 1, 1, "1-16周"),
            WidgetCourse("b", "周五的课", "", "", 5, 1, 1, "1-3周"),
            WidgetCourse("c", "双周周五", "", "", 5, 3, 3, "3-16周(双)")
        )
        val snap = snapshot(courses)
        // 2026-09-17 是第 3 周（单周）的周四 → 明天 9-18 周五仍在第 3 周：双周课不出现
        assertEquals(
            listOf("周五的课"),
            WidgetToday.rowsAt(snap, dayOffset = 1, now = at(2026, 9, 17)).map { it.name }
        )
        // 2026-09-24 是第 4 周（双周）的周四 → 明天 9-25 落在第 4 周：轮到双周课
        assertEquals(
            listOf("双周周五"),
            WidgetToday.rowsAt(snap, dayOffset = 1, now = at(2026, 9, 24)).map { it.name }
        )
        // 今天这一栏不受影响
        assertEquals(
            listOf("周四的课"),
            WidgetToday.rowsAt(snap, dayOffset = 0, now = at(2026, 9, 17)).map { it.name }
        )
    }

    @Test fun weekGridFillsContinuationCellsAndFiltersByWeek() {
        val courses = listOf(
            WidgetCourse("a", "模拟电子技术", "", "", 4, 2, 3, "1-16周"),
            WidgetCourse("b", "双周课", "", "", 5, 1, 1, "2-16周(双)"),
            WidgetCourse("c", "第七节以后", "", "", 1, 7, 8, "1-16周")
        )
        val snap = snapshot(courses)

        // 第 3 周（单周）：周四 2-3 节落格，第 3 节是延续块；双周课不出现
        val oddWeek = WidgetToday.cellsForWeek(snap, now = at(2026, 9, 17), maxPeriods = 6)
        assertEquals("模拟电子技术", oddWeek[4 to 2]!!.name)
        assertTrue(oddWeek[4 to 3]!!.continued)
        assertEquals("", oddWeek[4 to 3]!!.name)
        assertNull(oddWeek[5 to 1])
        // 起始节次超出网格高度（maxPeriods）的课不进图
        assertNull(oddWeek[1 to 7])

        // 第 4 周（双周）：轮到双周课；每周都上的课仍在
        val evenWeek = WidgetToday.cellsForWeek(snap, now = at(2026, 9, 24), maxPeriods = 6)
        assertEquals("双周课", evenWeek[5 to 1]!!.name)
        assertEquals("模拟电子技术", evenWeek[4 to 2]!!.name)
    }

    @Test fun rowsThatFitUsesRealHeightInsteadOfSizeBuckets() {
        // 顶栏占 44dp、每行 46dp：4 行需要 228dp，给到 230dp 就该出 4 行
        assertEquals(4, rowsThatFit(230.dp, chromeHeight = 44.dp, rowHeight = 46.dp, max = 10))
        // 拉大到 300dp → 5 行（这正是「拉大了还是三行」要修掉的行为）
        assertEquals(5, rowsThatFit(300.dp, chromeHeight = 44.dp, rowHeight = 46.dp, max = 10))
        assertEquals(2, rowsThatFit(150.dp, chromeHeight = 44.dp, rowHeight = 46.dp, max = 10))
        // 再矮也至少给一行，再高也不超过上限
        assertEquals(1, rowsThatFit(30.dp, chromeHeight = 44.dp, rowHeight = 46.dp, max = 10))
        assertEquals(10, rowsThatFit(2000.dp, chromeHeight = 44.dp, rowHeight = 46.dp, max = 10))
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