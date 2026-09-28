package com.k2767.course.schedule

import android.content.Intent
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/**
 * 考试提醒的落地：点开通知要停在成绩页的考试那一栏。
 *
 * 与 [CourseReminderNavigation] 分开的理由是那个键位：它读的是 EXTRA_REMINDER_ID，
 * 复用同一把键会把考试通知误判成"一条已失效的课程提醒"。
 */
object ExamReminderNavigation {
    var requestedDate by mutableStateOf<String?>(null)
        private set

    fun accept(intent: Intent?) {
        intent?.getStringExtra(ScheduleReminderScheduler.EXTRA_EXAM_DATE)?.takeIf { it.isNotBlank() }?.let { requestedDate = it }
    }

    /** 考试通知不需要校验"这场还在不在"：快照在响过之后就摘了，校验只会把好通知判成失效。 */
    fun consume() { requestedDate = null }
}
