package com.k2767.course.academic

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.k2767.course.receiver.GrabAlarmReceiver

class AcademicGrabScheduler(context: Context) {
    private val context = context.applicationContext
    private val alarms = this.context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
    private val prefs = this.context.getSharedPreferences("grab_pro_prefs", Context.MODE_PRIVATE)

    fun canScheduleExactly(): Boolean = Build.VERSION.SDK_INT < 31 || alarms.canScheduleExactAlarms()

    fun schedule(account: String, accountStorage: String, triggerAt: Long, policy: GrabRunPolicy, parallel: Boolean) {
        require(account.isNotBlank() && accountStorage.isNotBlank()) { "请先登录任务账号" }
        require(triggerAt > System.currentTimeMillis()) { "开始时间必须在当前时间之后" }
        require(policy.intervalMillis in 500..Int.MAX_VALUE.toLong() && policy.maxAttempts in 1..1000)
        check(canScheduleExactly()) { "请先允许应用设置精确闹钟" }
        val intent = intent(accountStorage).apply {
            putExtra(GrabAlarmReceiver.EXTRA_ACCOUNT_KEY, account)
            putExtra(GrabAlarmReceiver.EXTRA_ACCOUNT_STORAGE_KEY, accountStorage)
            putExtra(GrabAlarmReceiver.EXTRA_INTERVAL, policy.intervalMillis.toInt())
            putExtra(GrabAlarmReceiver.EXTRA_MAX_RETRY, policy.maxAttempts)
            putExtra(GrabAlarmReceiver.EXTRA_PARALLEL_MODE, parallel)
        }
        val pending = PendingIntent.getBroadcast(context, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
        prefs.edit().putBoolean("has_scheduled_task_$accountStorage", true)
            .putLong("scheduled_trigger_$accountStorage", triggerAt).apply()
    }

    fun isScheduled(accountStorage: String): Boolean =
        prefs.getBoolean("has_scheduled_task_$accountStorage", false) &&
            PendingIntent.getBroadcast(context, 0, intent(accountStorage),
                PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) != null

    fun cancel(accountStorage: String) {
        PendingIntent.getBroadcast(context, 0, intent(accountStorage),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
            alarms.cancel(it)
            it.cancel()
        }
        prefs.edit().putBoolean("has_scheduled_task_$accountStorage", false)
            .remove("scheduled_trigger_$accountStorage").remove("scheduled_task_info_$accountStorage").apply()
    }

    private fun intent(accountStorage: String) = Intent(context, GrabAlarmReceiver::class.java).apply {
        action = GrabAlarmReceiver.ACTION_SCHEDULED_GRAB
        data = Uri.Builder().scheme("academic-grab").authority("schedule").appendPath(accountStorage).build()
    }
}
