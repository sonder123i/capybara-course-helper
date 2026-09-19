package com.k2767.course.widget

import androidx.compose.ui.unit.dp
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class WidgetTodayTest {

    /** 2026-08-31 是周一 → 第 1 周；2026-09-17 是周四，落在第 3 周。 */
    private val firstWeek = "2026-08-31"

    private fun at(year: Int, month: Int, day: Int, hour: Int = 12, minute: Int = 0): Long =
        Calendar.getInstance().apply {
            clear()
            set(year, month - 1, day, hour, minute, 0)
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

    @Test fun stripsCampusPrefixButKeepsLocationReadable() {
        assertEquals("致远楼A519", WidgetToday.compactLocation("九龙湖校区 致远楼A519"))
        assertEquals("致远楼A519", WidgetToday.compactLocation("九龙湖校区·致远楼A519"))
        assertEquals("敏行楼B503-2", WidgetToday.compactLocation("九龙湖校区 敏行楼B503-2"))
        // 只有校区没有楼栋时保持原样，别把地点裁成空串
        assertEquals("九龙湖校区", WidgetToday.compactLocation("九龙湖校区"))
        assertEquals("", WidgetToday.compactLocation(""))
        // 没有校区前缀的地点原样保留
        assertEquals("创新楼A-319", WidgetToday.compactLocation("创新楼A-319"))
    }

    @Test fun skipEndedHandsTheSlotToLaterCourses() {
        val courses = listOf(
            WidgetCourse("a", "上午课", "", "致远楼A519", 4, 1, 2, "1-16周"),
            WidgetCourse("b", "下午课", "", "致远楼C510", 4, 3, 3, "1-16周")
        )
        val snap = snapshot(courses)

        // 早上 8:10：两节都还没下课，都能看到
        assertEquals(
            listOf("上午课", "下午课"),
            WidgetToday.rows(snap, now = at(2026, 9, 17, 8, 10), skipEnded = true).map { it.name }
        )
        // 10:30：上午课 09:45 就下课了 → 让位，只剩下午课
        assertEquals(
            listOf("下午课"),
            WidgetToday.rows(snap, now = at(2026, 9, 17, 10, 30), skipEnded = true).map { it.name }
        )
        // 12:30：全上完了 → 空（界面会显示「今天的课都上完啦」）
        assertTrue(WidgetToday.rows(snap, now = at(2026, 9, 17, 12, 30), skipEnded = true).isEmpty())
        // 不带 skipEnded 时仍然是全天课表（用来区分「今天没课」和「都上完了」）
        assertEquals(2, WidgetToday.rows(snap, now = at(2026, 9, 17, 12, 30)).size)
        // 明天那一栏不受影响
        val snapTomorrow = snapshot(courses, firstWeekDate = firstWeek)
        assertTrue(WidgetToday.rowsAt(snapTomorrow, dayOffset = 1, now = at(2026, 9, 17, 12, 30), skipEnded = true).isEmpty())
    }

    @Test fun nextRefreshFallsOnTheNearestClassEndOrMidnight() {
        val courses = listOf(
            WidgetCourse("a", "上午课", "", "", 4, 1, 2, "1-16周"),
            WidgetCourse("b", "下午课", "", "", 4, 3, 3, "1-16周")
        )
        val snap = snapshot(courses)
        // 08:10 时，最近的下课时刻是第 2 节 09:45
        assertEquals(at(2026, 9, 17, 9, 45), WidgetToday.nextRefreshAt(snap, now = at(2026, 9, 17, 8, 10)))
        // 10:30 时，最近的是第 3 节 12:00
        assertEquals(at(2026, 9, 17, 12, 0), WidgetToday.nextRefreshAt(snap, now = at(2026, 9, 17, 10, 30)))
        // 全部上完后，下一次刷新是明天 0 点
        assertEquals(at(2026, 9, 18, 0, 0), WidgetToday.nextRefreshAt(snap, now = at(2026, 9, 17, 13, 0)))
        // 没数据也不会崩，退回明天 0 点
        assertEquals(at(2026, 9, 18, 0, 0), WidgetToday.nextRefreshAt(null, now = at(2026, 9, 17, 13, 0)))
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

    /**
     * 「没填开学日期」和「今天真的没课」必须能分开：前者 rows 也是空的，
     * 组件若只看 rows 就会对着一张有课的课表说「今天没有课啦」。
     */
    @Test fun 缺锚点时与真的没课区分开() {
        val courses = listOf(WidgetCourse("c1", "高等数学", "张", "至善楼406", 4, 1, 2, "1-16周"))
        val now = at(2026, 9, 17)
        assertTrue(WidgetToday.hasWeekAnchor(snapshot(courses), now))
        assertFalse(WidgetToday.hasWeekAnchor(snapshot(courses, firstWeekDate = ""), now))
        // 非周一的锚点算不出周次（与 App 侧同规则），同样得报成缺锚点
        assertFalse(WidgetToday.hasWeekAnchor(snapshot(courses, firstWeekDate = "2026-09-01"), now))
        assertTrue(WidgetToday.rows(snapshot(courses, firstWeekDate = ""), now).isEmpty())
    }

    // ---------- 往后预告 ----------

    /**
     * 周末打开是最典型的场景：周六周日都没课，要提前摆出周一的课。
     * 2026-09-19 是周六（第 3 周），2026-09-21 周一仍在第 3 周。
     */
    @Test fun 周末向前预告到下一个有课的日子() {
        val courses = listOf(
            WidgetCourse("a", "周一高数", "", "至善楼406", 1, 1, 2, "1-16周"),
            WidgetCourse("b", "周四物理", "", "创新楼A-319", 4, 3, 3, "1-16周")
        )
        val snap = snapshot(courses)
        val saturday = at(2026, 9, 19)

        assertTrue(WidgetToday.rows(snap, now = saturday).isEmpty())

        val preview = WidgetToday.nextDayWithCourses(snap, fromOffset = 0, now = saturday)!!
        // 周日（offset=1）没课被跳过，直接落到周一（offset=2）
        assertEquals(2, preview.offset)
        assertEquals(1, preview.day)
        assertEquals(listOf("周一高数"), preview.rows.map { it.name })
        // 栏头靠这个写出「周一」
        assertEquals("周一", DayNames[preview.day - 1])
    }

    /**
     * 预告必须跨越周次。2026-09-19 周六属于第 3 周（单周），下周一 9-21 是第 4 周（双周）——
     * 若沿用「今天」的周次去过滤，双周课会被整片吞掉。这条专门把这个坑钉住。
     */
    @Test fun 预告跨周时周次跟着目标日期重算() {
        val courses = listOf(
            WidgetCourse("a", "单周周一", "", "", 1, 1, 1, "3-16周(单)"),
            WidgetCourse("b", "双周周一", "", "", 1, 3, 3, "4-16周(双)")
        )
        val snap = snapshot(courses)

        // 第 3 周（单周）的周六 → 下周一 9-21 已进第 4 周（双周）：轮到双周课
        val preview = WidgetToday.nextDayWithCourses(snap, fromOffset = 0, now = at(2026, 9, 19))!!
        assertEquals(2, preview.offset)
        assertEquals(listOf("双周周一"), preview.rows.map { it.name })

        // 第 4 周（双周）的周六 9-26 → 下周一 9-28 落回第 5 周（单周）：轮到单周课
        val nextWeek = WidgetToday.nextDayWithCourses(snap, fromOffset = 0, now = at(2026, 9, 26))!!
        assertEquals(2, nextWeek.offset)
        assertEquals(listOf("单周周一"), nextWeek.rows.map { it.name })
    }

    /** 只显示有课的日子：中间空着的日子不占栏位，offset 就会跳着走。 */
    @Test fun 跳过没课的日子而不是占位显示() {
        // 只有周一有课
        val courses = listOf(WidgetCourse("a", "周一课", "", "", 1, 1, 1, "1-16周"))
        val snap = snapshot(courses)
        // 2026-09-17 是周四：周五六日都没课，下一个有课日是下周一 9-21
        val preview = WidgetToday.nextDayWithCourses(snap, fromOffset = 0, now = at(2026, 9, 17))!!
        assertEquals(4, preview.offset)
        assertEquals(1, preview.day)
        assertEquals(listOf("周一课"), preview.rows.map { it.name })
    }

    /** 从第 8 天起才有课 → 超出 7 天上限，必须返回 null 让组件改说「接下来一周都没课」。 */
    @Test fun 超过一周没有课就返回空让组件换说法() {
        // 只有 9-23 那天有课（第 4 周周三）
        val courses = listOf(WidgetCourse("a", "下周三课", "", "", 3, 1, 1, "4-16周"))
        val snap = snapshot(courses)
        // 9-17 周四打开 → 最近的 9-23 在 6 天后，仍在上限内
        val inRange = WidgetToday.nextDayWithCourses(snap, fromOffset = 0, now = at(2026, 9, 17))!!
        assertEquals(6, inRange.offset)

        // 超过第 7 天才有课 → 9-16 周三提前 7 天正好是上限之外（窗口是 0..6）
        assertNull(WidgetToday.nextDayWithCourses(snap, fromOffset = 0, now = at(2026, 9, 16)))
        // 9-18 周五打开 → 9-23 在 5 天后，还是能提前看到
        assertEquals(5, WidgetToday.nextDayWithCourses(snap, fromOffset = 0, now = at(2026, 9, 18))!!.offset)
    }

    /** 上限窗口是「今天起共 7 天」：正好第 7 天（offset=6）算得到，第 8 天（offset=7）算不到。 */
    @Test fun 上限窗口含第七天不含第八天() {
        val courses = listOf(WidgetCourse("a", "周三课", "", "", 3, 1, 1, "4-16周"))
        val snap = snapshot(courses)
        // 9-17 周四 → 9-23 周三，offset = 6（第 7 天，认）
        assertEquals(6, WidgetToday.nextDayWithCourses(snap, fromOffset = 0, now = at(2026, 9, 17))!!.offset)
        // 9-16 周三 → 9-23 周三，offset = 7（第 8 天，不认）
        assertNull(WidgetToday.nextDayWithCourses(snap, fromOffset = 0, now = at(2026, 9, 16)))
    }

    /**
     * 两栏要能接上：右栏从「左栏那天的后一天」继续找，不能两栏撞同一天，
     * 也不能因为中间夹着没课的日子就断掉。
     */
    @Test fun 第二栏从左栏之后接着找() {
        val courses = listOf(
            WidgetCourse("a", "周一课", "", "", 1, 1, 1, "1-16周"),
            WidgetCourse("b", "周三课", "", "", 3, 1, 1, "1-16周"),
            WidgetCourse("c", "周五课", "", "", 5, 1, 1, "1-16周")
        )
        val snap = snapshot(courses)
        val saturday = at(2026, 9, 19)

        // 左栏：周六周日没课 → 周一（offset=2）
        val left = WidgetToday.nextDayWithCourses(snap, fromOffset = 0, now = saturday)!!
        assertEquals(2, left.offset)
        assertEquals(1, left.day)
        assertEquals(listOf("周一课"), left.rows.map { it.name })

        // 右栏从 offset=3 起找：周二没课被跳过 → 周三（offset=4）
        val right = WidgetToday.nextDayWithCourses(snap, fromOffset = left.offset + 1, now = saturday)!!
        assertEquals(4, right.offset)
        assertEquals(3, right.day)
        assertEquals(listOf("周三课"), right.rows.map { it.name })
        // 两栏不会同一天
        assertTrue(left.offset < right.offset)

        // 从右栏之后再找：周五在 offset=6，仍在上限内
        val third = WidgetToday.nextDayWithCourses(snap, fromOffset = right.offset + 1, now = saturday)!!
        assertEquals(6, third.offset)
        assertEquals(5, third.day)
    }

    /** 今天有课时，预告要从明天起算——不能把今天的课又摆一遍。 */
    @Test fun 今天有课时预告从明天开始() {
        val courses = listOf(
            WidgetCourse("a", "周四课", "", "", 4, 1, 1, "1-16周"),
            WidgetCourse("b", "周五课", "", "", 5, 1, 1, "1-16周")
        )
        val snap = snapshot(courses)
        val thursday = at(2026, 9, 17)

        val preview = WidgetToday.nextDayWithCourses(snap, fromOffset = 1, now = thursday)!!
        assertEquals(1, preview.offset)
        assertEquals(listOf("周五课"), preview.rows.map { it.name })
    }

    /**
     * 最危险的一条：课上完后 `rows(skipEnded = true)` 是空的，但今天**确实有课**。
     * 组件若拿 rows.isEmpty() 当「今天没课」用，周三 17:00 打开桌面就会看到明天/周四的课，
     * 像是今天被凭空跳过了。判断必须走 [WidgetToday.hasCoursesOn]。
     */
    @Test fun 课上完了不算今天没课() {
        val courses = listOf(
            WidgetCourse("a", "周四上午课", "", "", 4, 1, 2, "1-16周"),
            WidgetCourse("b", "周四下午课", "", "", 4, 3, 3, "1-16周")
        )
        val snap = snapshot(courses)
        val afterClass = at(2026, 9, 17, 12, 30)

        // 界面用的那一列确实是空的（该显示「今天的课都上完啦」）
        assertTrue(WidgetToday.rows(snap, now = afterClass, skipEnded = true).isEmpty())
        // 但「今天有课吗」必须是 true —— 组件就靠这个决定不预告
        assertTrue(WidgetToday.hasCoursesOn(snap, dayOffset = 0, now = afterClass))
        // 对照：真的没课的日子
        assertFalse(WidgetToday.hasCoursesOn(snapshot(listOf(WidgetCourse("c", "周五课", "", "", 5, 1, 1, "1-16周"))), 0, afterClass))
    }

    /** 预告的那一天永远不是今天，[skipEnded] 不该影响它。 */
    @Test fun 预告不受跳过已下课影响() {
        val courses = listOf(WidgetCourse("a", "周一课", "", "", 1, 1, 1, "1-16周"))
        val snap = snapshot(courses)
        // 周六晚上打开，周一的课当然还没上
        val preview = WidgetToday.nextDayWithCourses(snap, fromOffset = 0, now = at(2026, 9, 19, 22, 0))!!
        assertEquals(listOf("周一课"), preview.rows.map { it.name })
    }

    /** 上限小于 1 时不该返回东西（防御性：别让循环条件写反变成无限找）。 */
    @Test fun 上限为零时不返回预告() {
        val courses = listOf(WidgetCourse("a", "周一课", "", "", 1, 1, 1, "1-16周"))
        assertNull(WidgetToday.nextDayWithCourses(snapshot(courses), fromOffset = 0, maxAhead = 0, now = at(2026, 9, 19)))
    }

    /** 栏头：那天就是今天写「今天」，往后跳了就要写真实的星期几。 */
    @Test fun 栏头按真实那天写星期几() {
        assertEquals("今天", previewLabel(PreviewDay(0, 4, emptyList())))
        assertEquals("周一", previewLabel(PreviewDay(2, 1, emptyList())))
        assertEquals("周三", previewLabel(PreviewDay(4, 3, emptyList())))
        assertEquals("周日", previewLabel(PreviewDay(8, 7, emptyList())))
    }
}