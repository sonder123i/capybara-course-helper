package com.tyust.course.schedule

import com.tyust.course.academic.AcademicScheduleEntry
import com.tyust.course.academic.AcademicStudyBridge
import org.junit.Assert.*
import org.junit.Test

class ScheduleJsonTest {
    @Test fun authoritativeEmptyResponseIsDistinctFromErrorOrLoginHtml() {
        assertEquals(emptyList<NetworkScheduleCourse>(), ScheduleJson.parse("""{"kbList":[]}"""))
        assertEquals(emptyList<NetworkScheduleCourse>(), ScheduleJson.parse("""{"data":{"kbList":[]}}"""))
        assertNull(ScheduleJson.parse("<html>用户登录</html>"))
        assertNull(ScheduleJson.parse("""{"success":false,"data":[]}"""))
        assertNull(ScheduleJson.parse("""{"code":500,"data":[]}"""))
        assertNull(ScheduleJson.parse("""{"message":"expired"}"""))
    }

    @Test fun bridgeCacheAndReorderingKeepSourceIdentityAndDropDuplicateRows() {
        val a = AcademicScheduleEntry("数学", "老师", "A101", 1, 1, 2, "1-16周", "source-a")
        val b = a.copy(name = "英语", day = 2, sourceId = "source-b")
        val parsed = ScheduleJson.parse(AcademicStudyBridge.scheduleJson(listOf(a, b)))!!
        val reloaded = ScheduleJson.parse(AcademicStudyBridge.scheduleJson(listOf(b, a, a)))!!
        assertEquals(parsed.map { it.course.id }.toSet(), reloaded.map { it.course.id }.toSet())
        assertEquals(setOf("source-a", "source-b"), reloaded.map { it.sourceId }.toSet())
        assertEquals(2, reloaded.size)
    }

    @Test fun absentRemoteWeeksNeverBecomeGuessedReminderOccurrences() {
        val parsed = ScheduleJson.parse("""{"kbList":[{"kcmc":"数学","xqj":1,"jcs":"1-2"}]}""")!!.single()
        assertFalse(ScheduleWeeks.parse(parsed.course.weeks).valid)
        assertTrue(ScheduleWeeks.parse(parsed.course.weeks).visibleIn(6))
    }
}
