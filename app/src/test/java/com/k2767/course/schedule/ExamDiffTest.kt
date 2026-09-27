package com.k2767.course.schedule

import com.k2767.course.ui.screen.ExamItemUi
import com.k2767.course.ui.screen.examDiffRows
import com.k2767.course.ui.screen.examRowId
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExamDiffTest {
    private fun exam(name: String = "大学英语", kind: String = "期末", time: String = "2026-06-30 09:00",
                     place: String = "一教101", seat: String = "12", teacher: String = "王老师") =
        ExamItemUi(name, time, place, seat, kind, teacher)

    private fun rows(vararg items: ExamItemUi) = examDiffRows(items.toList())

    private fun diff(before: List<ExamItemUi>, after: List<ExamItemUi>) =
        requireNotNull(diffSchedule(examDiffRows(before), examDiffRows(after), ExamTable))

    @Test fun examPlaceChangeIsAttributable() {
        val current = exam(place = "二教205")
        val report = diff(listOf(exam(place = "一教101")), listOf(current))
        assertEquals(listOf(ScheduleChange.Moved("大学英语", "place", "一教101", "二教205", setOf(examRowId(current)))),
            report.changes)
        assertEquals(DiffTrust.PresenceOnly, report.trust)
        assertEquals(listOf("这次同步有 1 处调整", "大学英语 考场 一教101 → 二教205"), report.summary())
    }

    @Test fun twoExamsWithTheSameNameNeverCollapseIntoOne() {
        val before = listOf(exam(seat = "12"), exam(seat = "34"))
        assertEquals(2, examDiffRows(before).size)
        assertTrue(diff(before, before).changes.isEmpty())
    }

    @Test fun aSeatChangeOnTwinExamsFallsBackInsteadOfStayingSilent() {
        val before = listOf(exam(seat = "12"), exam(seat = "34"))
        val after = listOf(exam(seat = "12"), exam(seat = "99"))
        val report = diff(before, after)
        assertEquals(1, report.changes.size)
        assertTrue(report.changes.single() is ScheduleChange.ArrangementChanged)
    }

    @Test fun anEmptyExamTableIsNotAnAuthoritativeChange() {
        val report = diff(listOf(exam()), emptyList())
        assertTrue("排考前没有考试是常态，不该演成整表清空", report.changes.isEmpty())
    }

    @Test fun aNewExamAppearsOnce() {
        val report = diff(listOf(exam()), listOf(exam(), exam(name = "线性代数")))
        assertEquals(listOf("新增 线性代数"), report.summary().drop(1))
    }

    @Test fun widthAndSpacingJitterProducesNoChange() {
        val report = diff(listOf(exam(time = "2026-06-30 09:00", place = " 一教101 ")),
            listOf(exam(time = "2026-06-30 09:00", place = "一教101")))
        assertTrue(report.changes.isEmpty())
    }

    @Test fun missingBaselineEstablishesSilently() {
        assertEquals(ScheduleBaseline.Establish, scheduleBaseline(null, rows(exam()), comparable = true, table = ExamTable))
    }

    @Test fun rowsSurviveTheRoundTripThroughStorage() {
        val stored = encodeRows(rows(exam(), exam(name = "线性代数")))
        assertEquals(rows(exam(), exam(name = "线性代数")), decodeRows(stored))
        assertNull(decodeRows("<html>登录</html>"))
    }
}
