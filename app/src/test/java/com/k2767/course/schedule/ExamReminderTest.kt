package com.k2767.course.schedule

import com.k2767.course.ui.screen.ExamItemUi
import com.k2767.course.ui.screen.examCalendarPlan
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

class ExamReminderTest {
    private val zone: TimeZone = TimeZone.getDefault()
    private fun millis(text: String) = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT).apply { timeZone = zone }
        .parse(text)!!.time

    private fun exam(name: String, time: String, place: String = "一教101") =
        ExamItemUi(name, time, place, "12", "期末", "王老师")

    private fun alarms(now: String, vararg items: ExamItemUi) =
        examDayAlarms("a", "2026-2027-2", examCalendarPlan(items.toList()).events, millis(now), zone)

    private fun hourOf(trigger: Long) = Calendar.getInstance(zone).apply { timeInMillis = trigger }.get(Calendar.HOUR_OF_DAY)

    @Test fun twoExamsOnTheSameDayShareOneAlarm() {
        val alarm = alarms("2026-06-29 10:00", exam("大学英语", "2026-06-30 09:00"), exam("线性代数", "2026-06-30 14:00")).single()
        assertEquals("2026-06-30", alarm.date)
        assertEquals(2, alarm.lines.size)
        assertEquals(20, hourOf(alarm.triggerAt))
        assertEquals(millis("2026-06-29 20:00"), alarm.triggerAt)
    }

    /** 一个立刻响的或永不响的闹钟，都比不响更糟。 */
    @Test fun aTriggerTimeAlreadyPastIsSkippedNotBackfilled() {
        assertTrue(alarms("2026-06-29 21:00", exam("大学英语", "2026-06-30 09:00")).isEmpty())
        assertEquals(1, alarms("2026-06-29 10:00", exam("大学英语", "2026-06-30 09:00")).size)
    }

    @Test fun aDateOnlyExamStillGetsTheEveningReminder() {
        val alarm = alarms("2026-07-02 10:00", exam("体育", "2026年7月3日")).single()
        assertEquals(listOf("体育 期末 时间见教务 · 一教101"), alarm.lines)
        assertTrue("前一天 20:00 才响", alarm.triggerAt > millis("2026-07-02 10:00"))
    }

    @Test fun revisionIsStableUntilTheLinesChange() {
        val before = alarms("2026-06-29 10:00", exam("大学英语", "2026-06-30 09:00")).single()
        val same = alarms("2026-06-29 09:00", exam("大学英语", "2026-06-30 09:00")).single()
        val moved = alarms("2026-06-29 10:00", exam("大学英语", "2026-06-30 09:00", place = "二教205")).single()
        assertEquals(before.revision, same.revision)
        assertNotEquals(before.revision, moved.revision)
        assertEquals(before.storageId, moved.storageId)
    }

    @Test fun oneAlarmPerExamDay() {
        val days = alarms("2026-06-29 10:00",
            exam("大学英语", "2026-06-30 09:00"), exam("线性代数", "2026-07-02 14:00"), exam("体育", "2026年7月3日"))
        assertEquals(listOf("2026-06-30", "2026-07-02", "2026-07-03"), days.map { it.date })
    }

    @Test fun notificationSaysOneLineForOneExamAndCountsTheRest() {
        assertEquals("大学英语 期末 09:00 · 一教101", examDayNotification(listOf("大学英语 期末 09:00 · 一教101")))
        assertEquals("2 场考试：A；B", examDayNotification(listOf("A", "B")))
        assertEquals("明天有考试", examDayNotification(emptyList()))
    }
}
