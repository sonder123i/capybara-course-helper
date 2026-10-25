package com.k2767.course.widget

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

/**
 * 「今日时间轴」排布规则的回归测试。
 *
 * 时间轴出错的方式全是「什么时候」而不是「多高」：周三下午打开时把上完的课还留在最上面、
 * 跨到明天却不标日子、单双周算错把不属于本周的课摆上来——这些肉眼试一次是试不出来的，
 * 因为组件在你眼前那一刻往往是对的。所以每条规则都钉一个时刻来跑。
 */
class WidgetTimelineTest {

    /** 2026-08-31 是周一 → 第 1 周；2026-09-17 是周四，落在第 3 周。 */
    private val firstWeek = "2026-08-31"

    private val periods = listOf(
        WidgetPeriod(1, "08:00", "08:45"),
        WidgetPeriod(2, "09:00", "09:45"),
        WidgetPeriod(3, "10:10", "12:00")
    )

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
        }.timeInMillis

    private fun snapshot(
        courses: List<WidgetCourse>,
        firstWeekDate: String = firstWeek
    ) = WidgetSnapshot("南昌工学院", firstWeekDate, periods, courses)

    private fun course(
        id: String,
        name: String,
        day: Int,
        start: Int,
        end: Int = start,
        weeks: String = "1-16周",
        location: String = "A101"
    ) = WidgetCourse(id, name, "教师", location, day, start, end, weeks)

    @Test fun `今天已经下课的不再占位`() {
        val snap = snapshot(listOf(
            course("a", "早上第一节的课", 4, 1),
            course("b", "上午最后一节", 4, 3),
            course("c", "明天的课", 5, 1)
        ))
        // 周四 12:30：今天三节里前两节早已下课
        val rows = WidgetToday.timeline(snap, now = at(2026, 9, 17, 12, 30), days = 3)
        assertEquals(listOf("明天的课"), rows.map { it.name })
    }

    @Test fun `正在上的那一节被标出来`() {
        val snap = snapshot(listOf(
            course("a", "正在上的课", 4, 1),
            course("b", "等下的课", 4, 3)
        ))
        val rows = WidgetToday.timeline(snap, now = at(2026, 9, 17, 8, 20), days = 1)
        assertEquals(listOf(true, false), rows.map { it.happening })
    }

    @Test fun `跨天按日期排并且每天只标一次日头`() {
        val snap = snapshot(listOf(
            course("a", "今天第一节", 4, 1),
            course("b", "今天第三节", 4, 3),
            course("c", "明天第一节", 5, 1),
            course("d", "明天第二节", 5, 2)
        ))
        val rows = WidgetToday.timeline(snap, now = at(2026, 9, 17, 7, 0), days = 2)
        assertEquals(listOf("今天第一节", "今天第三节", "明天第一节", "明天第二节"), rows.map { it.name })
        assertEquals(listOf(true, false, true, false), rows.map { it.firstOfDay })
        assertEquals(listOf(0, 0, 1, 1), rows.map { it.dayOffset })
        // 明天是周五：栏头必须写「周五」，写「明天」在跨周时就不成立了
        assertEquals(5, rows.last().day)
    }

    @Test fun `同一时刻多门课时顺序稳定不随渲染变化`() {
        // 排序键是「上课时刻 + 课名码点」，与 rowsAt 同一套；这里钉的是顺序【只由数据决定】，
        // 而不是甲一定在乙前面——中文按码点排，乙(U+4E59)本就排在甲(U+7532)之前。
        val early = course("z", "乙课", 4, 2)
        val later = course("a", "甲课", 4, 2)
        val expected = listOf("乙课", "甲课").sorted()
        val now = at(2026, 9, 17, 7, 0)
        val rows = WidgetToday.timeline(snapshot(listOf(early, later)), now = now, days = 1)
        assertEquals(expected, rows.map { it.name })
        // 换一种入参顺序，结果必须一模一样：否则组件每刷一次都可能换序
        val flipped = WidgetToday.timeline(snapshot(listOf(later, early)), now = now, days = 1)
        assertEquals(rows.map { it.name }, flipped.map { it.name })
    }

    @Test fun `没填开学日期时整列排不出来`() {
        val snap = snapshot(listOf(course("a", "有课的", 4, 1)), firstWeekDate = "")
        assertTrue(WidgetToday.timeline(snap, now = at(2026, 9, 17, 7, 0)).isEmpty())
    }

    @Test fun `单双周随周次奇偶切换`() {
        val snap = snapshot(listOf(
            course("a", "双周才上", 4, 1, weeks = "2-16周(双)"),
            course("b", "单周才上", 4, 2, weeks = "1-16周(单)"),
            course("c", "每周都上", 5, 1)
        ))
        // 2026-09-17 是第 3 周（单周）
        val oddWeek = WidgetToday.timeline(snap, now = at(2026, 9, 17, 7, 0), days = 2)
        assertEquals(listOf("单周才上", "每周都上"), oddWeek.map { it.name })
        // 2026-09-24 是第 4 周（双周）：换双周课，每周都上的那节两周都在。
        // 跨天预告时若按「今天」的周次算明天，单双周就会整排错位。
        val evenWeek = WidgetToday.timeline(snap, now = at(2026, 9, 24, 7, 0), days = 2)
        assertEquals(listOf("双周才上", "每周都上"), evenWeek.map { it.name })
    }

    @Test fun `节次时间缺失的课整条丢掉而不是排错位置`() {
        // periods 只有 1..3 节；第 9 节没有起止时间，排进时间轴只能靠猜
        val snap = snapshot(listOf(
            course("a", "时间正常的课", 4, 1),
            course("b", "没有节次时间的课", 4, 9)
        ))
        val rows = WidgetToday.timeline(snap, now = at(2026, 9, 17, 7, 0), days = 1)
        assertEquals(listOf("时间正常的课"), rows.map { it.name })
    }

    @Test fun `窗口内都没课就是空列表`() {
        // 只有周一的课，而现在是周四：往后三天（周四五六）都没有
        val snap = snapshot(listOf(course("a", "周一的课", 1, 1)))
        assertTrue(WidgetToday.timeline(snap, now = at(2026, 9, 17, 7, 0), days = 3).isEmpty())
    }

    @Test fun `教室前缀校区被裁掉但课名不动`() {
        val snap = snapshot(listOf(
            WidgetCourse("a", "数据结构", "教师", "前湖校区 博学楼A203", 4, 1, 1, "1-16周")
        ))
        val rows = WidgetToday.timeline(snap, now = at(2026, 9, 17, 7, 0), days = 1)
        assertEquals("博学楼A203", rows.single().location)
        assertFalse(rows.single().name.contains("校区"))
    }
}
