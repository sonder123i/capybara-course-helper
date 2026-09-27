package com.k2767.course.schedule

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ScheduleDiffTest {
    private fun kb(vararg rows: String) = ScheduleJson.parse("{\"kbList\":[${rows.joinToString(",")}]}")!!

    private fun rows(vararg rows: String) = scheduleRows(kb(*rows))

    private fun course(name: String = "大学英语", teacher: String = "王老师", room: String = "A302",
                       day: String = "3", periods: String = "3-4", weeks: String = "1-16周", id: String? = "X1") =
        buildString {
            append("{\"kcmc\":\"$name\",\"xm\":\"$teacher\",\"cdmc\":\"$room\",\"xqj\":\"$day\",\"jcs\":\"$periods\",\"zcd\":\"$weeks\"")
            if (id != null) append(",\"jxb_id\":\"$id\"")
            append("}")
        }

    private fun diff(before: List<DiffRow>, after: List<DiffRow>) = diffSchedule(before, after)!!

    @Test fun missingBaselineIsSilentInsteadOfClaimingEveryCourseIsNew() {
        assertNull(diffSchedule(null, rows(course())))
    }

    @Test fun identicalSnapshotsProduceNoChange() {
        val report = diff(rows(course()), rows(course()))
        assertTrue(report.changes.isEmpty())
        assertNull(report.headline())
        assertTrue(report.summary().isEmpty())
    }

    @Test fun stableSourceIdNamesTheRoomChange() {
        val current = rows(course(room = "B105"))
        val report = diff(rows(course(room = "A302")), current)
        assertEquals(listOf(ScheduleChange.Moved("大学英语", "location", "A302", "B105", setOf(current.single().id))), report.changes)
        assertEquals(DiffTrust.FieldLevel, report.trust)
        assertEquals(listOf("这次同步有 1 处调整", "大学英语 教室 A302 → B105"), report.summary())
    }

    @Test fun roomChangeKeepsTheRenderIdWhenSourceIdExists() {
        assertEquals(kb(course(room = "A302")).single().course.id, kb(course(room = "B105")).single().course.id)
    }

    @Test fun stableSourceIdNamesTheTeacherChange() {
        val current = rows(course(teacher = "李老师"))
        assertEquals(listOf(ScheduleChange.Moved("大学英语", "teacher", "王老师", "李老师", setOf(current.single().id))),
            diff(rows(course(teacher = "王老师")), current).changes)
    }

    @Test fun stableSourceIdNamesTheWeekRangeChange() {
        val current = rows(course(weeks = "1-14周"))
        assertEquals(listOf(ScheduleChange.Moved("大学英语", "weeks", "1-16周", "1-14周", setOf(current.single().id))),
            diff(rows(course(weeks = "1-16周")), current).changes)
    }

    @Test fun weekResplitOverTheSameSlotsIsNotAChange() {
        val report = diff(rows(course(weeks = "1-16周")), rows(course(weeks = "1-8周"), course(weeks = "9-16周")))
        assertTrue("重新划分行不该报变更", report.changes.isEmpty())
    }

    @Test fun lostWeekAfterResplitFallsBackInsteadOfClaimingDeletion() {
        val current = rows(course(weeks = "1-8周"), course(weeks = "10-16周"))
        val report = diff(rows(course(weeks = "1-16周")), current)
        assertEquals(listOf(ScheduleChange.ArrangementChanged("大学英语", current.map { it.id }.toSet())), report.changes)
        assertTrue(report.changes.none { it is ScheduleChange.Disappeared || it is ScheduleChange.Appeared })
    }

    @Test fun roomChangeWithoutSourceIdStillNamesTheRoom() {
        val current = rows(course(id = null, room = "B105"))
        val report = diff(rows(course(id = null, room = "A302")), current)
        assertEquals(DiffTrust.PresenceOnly, report.trust)
        assertEquals(listOf(ScheduleChange.Moved("大学英语", "location", "A302", "B105", setOf(current.single().id))), report.changes)
    }

    @Test fun courseWithoutSourceIdAppearsOnce() {
        val current = rows(course(name = "大学英语", id = null), course(name = "体育", teacher = "教练", id = null))
        val report = diff(rows(course(name = "大学英语", id = null)), current)
        val sports = current.last().id
        assertEquals(listOf(ScheduleChange.Appeared("体育", setOf(sports))), report.changes)
    }

    /** 同名不同教学班的两门课，只改其中一门时另一门不许被连坐点亮。 */
    @Test fun marksLandOnlyOnTheRowThatChanged() {
        val before = rows(course(name = "高等数学", id = "X1", room = "A302"), course(name = "高等数学", id = "X2", room = "B201"))
        val current = rows(course(name = "高等数学", id = "X1", room = "C105"), course(name = "高等数学", id = "X2", room = "B201"))
        val report = diff(before, current)
        val changed = current.first().id
        assertEquals(setOf(changed), report.changedIds())
        assertEquals(listOf("高等数学 教室 A302 → C105"), report.linesFor(changed))
        assertTrue(report.linesFor(current.last().id).isEmpty())
    }

    @Test fun emptiedTableIsOneChangeNotMassDeletion() {
        val report = diff(rows(course(), course(name = "体育", id = "X2")), rows())
        assertEquals(listOf<ScheduleChange>(ScheduleChange.Emptied), report.changes)
        assertNull(report.headline())
        assertEquals(listOf("教务返回了 0 门课程，请核对"), report.summary())
    }

    @Test fun widthAndSpacingJitterProducesNoChange() {
        val report = diff(rows(course(teacher = "王老师 ", room = " A302 ", weeks = "1-16周")),
            rows(course(teacher = "王老师", room = "A３０２", weeks = "1－16周")))
        assertTrue(report.changes.isEmpty())
    }

    @Test fun presenceOnlyAdmitsItCannotCompareTimeSegments() {
        val report = diff(rows(course(id = null)), rows(course(id = null, weeks = "1-8周"), course(id = null, weeks = "10-16周")))
        assertEquals(listOf("大学英语 的安排变了，请核对", "本次只能按课程名与教师比对，不逐段核对时间调整"),
            report.summary().drop(1))
    }

    /**
     * 钉住现状，不主张修复：无 sourceId 时派生 id 含教室，换教室会让 CourseReminderKey.courseId
     * 对不上新课表，ScheduleReminderScheduler.updateSnapshot 里的 mapNotNull 会直接丢掉那条提醒。
     */
    @Test fun roomChangeWithoutSourceIdMovesTheDerivedIdentity() {
        val moved = ScheduleIdentity.network("", "大学英语", "王老师", 3, 3, 4, "1-16周", "B105")
        val before = ScheduleIdentity.network("", "大学英语", "王老师", 3, 3, 4, "1-16周", "A302")
        assertTrue(moved != before)
        val stable = ScheduleIdentity.network("X1", "大学英语", "王老师", 3, 3, 4, "1-16周", "B105")
        assertEquals(ScheduleIdentity.network("X1", "大学英语", "王老师", 3, 3, 4, "1-16周", "A302"), stable)
    }
}
