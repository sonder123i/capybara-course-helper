package com.k2767.course.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * 给组件定一个「下课闹钟」。
 *
 * 系统定时刷新最短 30 分钟（updatePeriodMillis 的下限），只靠它，「上完的课让位给后面的课」
 * 会慢半拍。这里在每次渲染时算出今天最近的一次下课时间，到点自我刷新一次。
 * 用 set()（非精确闹钟）——不需要任何权限，也不会打扰 Doze，晚几分钟无所谓。
 */
object WidgetRefreshScheduler {
    private const val REQUEST_CODE = 0x5701

    fun schedule(context: Context, atMillis: Long) {
        val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as? AlarmManager ?: return
        val pendingIntent = pending(context)
        alarmManager.cancel(pendingIntent)
        if (atMillis <= System.currentTimeMillis()) return
        runCatching { alarmManager.set(AlarmManager.RTC, atMillis, pendingIntent) }
    }

    private fun pending(context: Context): PendingIntent = PendingIntent.getBroadcast(
        context,
        REQUEST_CODE,
        Intent(context, WidgetRefreshReceiver::class.java),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )
}

/** 下课闹钟到点时重画组件，并顺势定下一个。 */
class WidgetRefreshReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        val pending = goAsync()
        val appContext = context.applicationContext
        CoroutineScope(Dispatchers.Default).launch {
            try {
                val now = System.currentTimeMillis()
                val snapshot = CourseWidgetData.read(appContext)
                CourseWidgetData.requestUpdate(appContext)
                WidgetRefreshScheduler.schedule(appContext, WidgetToday.nextRefreshAt(snapshot, now))
            } finally {
                pending.finish()
            }
        }
    }
}