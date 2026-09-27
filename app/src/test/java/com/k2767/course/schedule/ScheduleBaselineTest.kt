package com.k2767.course.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleBaselineTest {
    private fun table(vararg names: String): List<DiffRow> = scheduleRows(requireNotNull(
        ScheduleJson.parse("{\"kbList\":[${names.joinToString(",") {
            "{\"kcmc\":\"$it\",\"xqj\":1,\"jcs\":\"1-2\",\"zcd\":\"1-16周\",\"jxb_id\":\"$it\"}"
        }}]}")))

    private fun pending(seen: List<DiffRow>?, current: List<DiffRow>) =
        scheduleBaseline(seen, current, comparable = true) as ScheduleBaseline.Pending

    @Test fun firstSnapshotBecomesTheBaselineWithoutAnnouncingEveryCourse() {
        assertEquals(ScheduleBaseline.Establish, scheduleBaseline(null, table("数学"), comparable = true))
    }

    @Test fun offlineViewIsBypassedEvenWhenTheTableChanged() {
        val bypassed = scheduleBaseline(table("数学"), table("英语"), comparable = false)
        assertEquals(ScheduleBaseline.Bypassed, bypassed)
    }

    @Test fun anEmptySeenTableIsABaselineRatherThanAMissingOne() {
        val current = table("数学")
        val report = pending(emptyList(), current).report
        assertEquals(listOf(ScheduleChange.Appeared("数学", setOf(current.single().id))), report.changes)
    }

    @Test fun theSameTableIsInSync() {
        assertEquals(ScheduleBaseline.InSync, scheduleBaseline(table("数学"), table("数学"), comparable = true))
    }

    @Test fun unreadSyncsAccumulateIntoOneReportInsteadOfOneNoticeEach() {
        val seen = table("数学")
        assertEquals(listOf("英语"), pending(seen, table("数学", "英语")).report.changes.map(ScheduleChange::label))
        assertEquals(listOf("英语", "体育"), pending(seen, table("数学", "英语", "体育")).report.changes.map(ScheduleChange::label))
        assertEquals("这次同步有 2 处调整", pending(seen, table("数学", "英语", "体育")).report.summary().first())
    }

    @Test fun anEmptiedTimetableStillReportsOnceAgainstTheSeenBaseline() {
        val report = pending(table("数学", "体育"), emptyList()).report
        assertTrue(report.changes.single() is ScheduleChange.Emptied)
        assertEquals(listOf("教务返回了 0 门课程，请核对"), report.summary())
    }
}
