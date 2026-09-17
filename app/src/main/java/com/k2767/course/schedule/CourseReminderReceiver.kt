package com.k2767.course.schedule

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.k2767.course.manager.UserManager

class CourseReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        UserManager.getInstance().init(context)
        val scheduler = ScheduleReminderScheduler.get(context)
        if (intent.action == ScheduleReminderScheduler.ACTION) {
            scheduler.receive(intent.getStringExtra(ScheduleReminderScheduler.EXTRA_REMINDER_ID).orEmpty(),
                intent.getLongExtra(ScheduleReminderScheduler.EXTRA_REVISION, -1),
                intent.getLongExtra(ScheduleReminderScheduler.EXTRA_TRIGGER, -1))
        } else scheduler.reconcile(resetAlarms = true)
    }
}
