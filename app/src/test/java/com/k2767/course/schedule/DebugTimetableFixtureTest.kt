package com.k2767.course.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** 道具只许改输入，所以这些断言全部经过真实的 diff 引擎。 */
class DebugTimetableFixtureTest {
    private fun table(vararg rows: String) = "{\"kbList\":[${rows.joinToString(",")}]}"

    private fun row(name: String, room: String, id: String) =
        "{\"kcmc\":\"$name\",\"xm\":\"老师\",\"cdmc\":\"$room\",\"xqj\":1,\"jcs\":\"1-2\",\"zcd\":\"1-16周\",\"jxb_id\":\"$id\"}"

    @Test fun forgedBaselineFeedsTheRealEngineWithTwoChanges() {
        val current = scheduleRows(requireNotNull(ScheduleJson.parse(
            table(row("大学英语", "A302", "X1"), row("体育", "馆", "X2")))))
        val forged = requireNotNull(DebugTimetableFixture.previousVersion(
            table(row("大学英语", "A302", "X1"), row("体育", "馆", "X2"))))
        val seen = scheduleRows(requireNotNull(ScheduleJson.parse(forged)))
        assertEquals(1, seen.size)
        val report = requireNotNull(diffSchedule(seen, current))
        assertEquals(listOf("新增 体育", "大学英语 教室 验证用X999 → A302"), report.summary().drop(1))
        assertEquals(2, report.changes.size)
    }

    @Test fun aSingleRowTableCannotBeForged() {
        assertNull(DebugTimetableFixture.previousVersion(table(row("大学英语", "A302", "X1"))))
    }

    @Test fun nestedDataShapesAreWrittenBackWhereTheyWereFound() {
        val json = "{\"data\":{\"kbList\":[${row("大学英语", "A302", "X1")},${row("体育", "馆", "X2")}]}}"
        val forged = requireNotNull(DebugTimetableFixture.previousVersion(json))
        val rows = requireNotNull(ScheduleJson.parse(forged))
        assertEquals(1, rows.size)
        assertEquals("验证用X999", rows.single().course.location)
        assertTrue(requireNotNull(ScheduleJson.parse(json)).size > rows.size)
    }
}
