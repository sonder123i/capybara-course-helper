package com.k2767.course.schedule

import com.k2767.course.ui.screen.ExamItemUi
import com.k2767.course.ui.screen.examCalendarPlan
import com.k2767.course.utils.ICalExporter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar
import java.util.TimeZone

class ExamCalendarTest {
    private fun exam(name: String = "大学英语", time: String = "2026-06-30 09:00-10:30",
                     place: String = "一教101", seat: String = "12", kind: String = "期末") =
        ExamItemUi(name, time, place, seat, kind, "王老师")

    private fun timingOf(raw: String) = parseExamTiming(raw)!!

    @Test fun datedShapesWithYearAreAccepted() {
        listOf("2026-06-30 09:00", "2026年6月30日 09:00", "2026/6/30 09:00").forEach { raw ->
            val timing = timingOf(raw)
            assertFalse("$raw 应该带时刻", timing.allDay)
            assertEquals(2026, timing.startsAt.get(Calendar.YEAR))
            assertEquals(Calendar.JUNE, timing.startsAt.get(Calendar.MONTH))
            assertEquals(30, timing.startsAt.get(Calendar.DAY_OF_MONTH))
            assertEquals(9, timing.startsAt.get(Calendar.HOUR_OF_DAY))
        }
    }

    @Test fun aDateWithoutATimeBecomesAnAllDayEvent() {
        val timing = timingOf("2026年6月30日 第1-2节")
        assertTrue(timing.allDay)
        assertEquals(30, timing.startsAt.get(Calendar.DAY_OF_MONTH))
    }

    /** 猜错年份会把考试排到过去，用户带着一个不响的闹钟去考场。 */
    @Test fun anythingWithoutAYearIsRefused() {
        listOf("", "06-30 08:00", "第9周 周三 1-2节", "待定", "2026-13-40", "2026-02-30").forEach { raw ->
            assertNull("$raw 不该被认出来", parseExamTiming(raw))
        }
    }

    @Test fun unreadableExamsAreCountedAndEventsSortedByTime() {
        val plan = examCalendarPlan(listOf(exam(name = "线性代数", time = "2026-07-02 14:00"),
            exam(name = "大学英语"), exam(name = "体育", time = "第9周")))
        assertEquals(3, plan.total)
        assertEquals(1, plan.unreadable)
        assertEquals(listOf("大学英语 期末", "线性代数 期末"), plan.events.map { it.title })
        assertEquals(2, plan.timed)
        assertEquals(0, plan.allDay)
    }

    /** 换考场、改时间后重导要覆盖同一条，而不是在日历里留幽灵事件。 */
    @Test fun theUidSurvivesRoomAndTimeChangesButNotADifferentExam() {
        val moved = examCalendarPlan(listOf(exam(place = "二教205", time = "2026-07-01 09:00"))).events.single()
        val original = examCalendarPlan(listOf(exam())).events.single()
        assertEquals(original.uid, moved.uid)
        assertTrue(moved.uid.startsWith("exam-"))
        val other = examCalendarPlan(listOf(exam(name = "线性代数"))).events.single()
        assertFalse(original.uid == other.uid)
    }

    @Test fun timedEventsNeverInventAnEndTime() {
        val ics = ICalExporter.generateExamCalendarContent(
            examCalendarPlan(listOf(exam(), exam(name = "体育", time = "2026年7月3日"))).events)
        assertTrue(ics.contains("DTSTART;TZID=Asia/Shanghai:20260630T090000"))
        assertTrue(ics.contains("DTSTART;VALUE=DATE:20260703"))
        assertFalse("结束时间未知就不许反推时长", ics.contains("DTEND"))
        assertEquals(1, Regex("BEGIN:VALARM").findAll(ics).count())
        assertTrue(ics.contains("LOCATION:一教101"))
        assertTrue(ics.contains("DESCRIPTION:教务原文: 2026-06-30 09:00-10:30 · 座位 12 · 王老师"))
    }

    @Test fun theSummarySaysWhatWasDropped() {
        val mixed = examCalendarPlan(listOf(exam(), exam(name = "线性代数", time = "2026年7月3日"), exam(name = "体育", time = "第9周")))
        assertEquals("已导出 2 场考试：1 场定时，1 场只标了当天，另有 1 场时间看不懂",
            examExportSummary(mixed))
        assertEquals("3 场考试都没写能认出的日期，没有导出",
            examExportSummary(examCalendarPlan(listOf(exam(time = "待定"), exam(name = "B", time = "第9周"), exam(name = "C", time = "")))))
        assertEquals("还没有考试安排，没有导出", examExportSummary(examCalendarPlan(emptyList())))
    }

    @Test fun parsingUsesTheDeviceZoneAndNotAnInventedSemesterAnchor() {
        val zone = TimeZone.getDefault()
        val parsed = timingOf("2026-06-30 09:00").startsAt
        assertEquals(zone.getOffset(parsed.timeInMillis), parsed.timeZone.getOffset(parsed.timeInMillis))
        assertEquals(9, Calendar.getInstance(zone).apply { timeInMillis = parsed.timeInMillis }.get(Calendar.HOUR_OF_DAY))
    }
}
