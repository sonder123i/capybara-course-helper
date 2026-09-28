package com.k2767.course.schedule

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.k2767.course.MainActivity
import com.k2767.course.R
import com.k2767.course.manager.UserManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject

class ScheduleReminderScheduler private constructor(private val context: Context) : Application.ActivityLifecycleCallbacks {
    private val preferences = context.getSharedPreferences("course_reminders", Context.MODE_PRIVATE)
    private val calendars = ScheduleCalendarStore(preferences)
    private val alarmManager = context.getSystemService(AlarmManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var started = false
    var revision by mutableIntStateOf(0)
        private set

    companion object {
        const val ACTION = "com.k2767.course.action.COURSE_REMINDER"
        const val ACTION_EXAM = "com.k2767.course.action.EXAM_REMINDER"
        const val CHANNEL = "course_reminders"
        const val EXTRA_REMINDER_ID = "course_reminder_id"
        const val EXTRA_REVISION = "course_reminder_revision"
        const val EXTRA_TRIGGER = "course_reminder_trigger"
        @Volatile private var instance: ScheduleReminderScheduler? = null
        @JvmStatic fun get(context: Context): ScheduleReminderScheduler = instance ?: synchronized(this) {
            instance ?: ScheduleReminderScheduler(context.applicationContext).also { instance = it }
        }
    }

    fun start(application: Application) {
        if (started) return
        started = true
        // Application is created before an alarm Receiver. Restore the account before any
        // reconciliation; the default, uninitialized session would cancel every alarm.
        UserManager.getInstance().init(application)
        application.registerActivityLifecycleCallbacks(this)
        scope.launch {
            UserManager.getInstance().sessionState.state.map { it.token.accountStorageKey to it.expired }
                .distinctUntilChanged().collect { reconcile() }
        }
    }

    private fun activeAccount(): String = UserManager.getInstance().let {
        // 课程提醒是本地闹钟，不碰教务：离线查看不是登录态，但也没理由因为"登不上教务"
        // 就把提醒整个撤掉——那正好是在教务最不稳的时候把人唯一的用处拿走。
        if ((it.isLoggedIn || it.isLocalViewMode) && !it.isDemoMode) it.currentAccountStorageKey else ""
    }

    private fun records() = ReminderJson.decode(preferences.getString("records", null))
    private fun save(records: List<CourseReminder>) {
        preferences.edit().putString("records", ReminderJson.encode(records)).apply()
        revision++
    }

    fun find(key: CourseReminderKey): CourseReminder? = records().firstOrNull { it.key == key }
    fun encodeUndo(records: List<CourseReminder>) = ReminderJson.encode(records)
    fun restoreUndo(value: String) {
        val restore = ReminderJson.decode(value)
        val ids = restore.map { it.key.storageId }.toSet()
        save(records().filter { it.key.storageId !in ids } + restore.map { it.copy(revision = it.revision + 1) })
        reconcile()
    }

    @Synchronized
    fun setEnabled(key: CourseReminderKey, course: ScheduleCourseRecord, enabled: Boolean) {
        val current = records()
        val old = current.firstOrNull { it.key == key }
        val updated = CourseReminder(key, course, enabled, old?.leadMinutes ?: 15, (old?.revision ?: 0) + 1)
        save(current.filter { it.key != key } + updated)
        reconcile()
    }

    @Synchronized
    fun updateSnapshot(account: String, term: String, courses: List<ScheduleCourseRecord>) {
        if (account.isBlank() || term.isBlank()) return
        val byId = courses.associateBy { it.id }
        val old = records()
        val updated = old.mapNotNull { reminder ->
            if (reminder.key.account != account || reminder.key.term != term) reminder else {
                byId[reminder.key.courseId]?.let { course ->
                    if (reminder.course == course) reminder else reminder.copy(course = course, revision = reminder.revision + 1)
                }
            }
        }
        if (updated != old) save(updated)
        reconcile()
    }

    @Synchronized
    fun updateCustomCourse(account: String, course: ScheduleCourseRecord) {
        val old = records()
        val updated = old.map { if (it.key.account == account && it.key.courseId == course.id && it.course != course)
            it.copy(course = course, revision = it.revision + 1) else it }
        if (old != updated) save(updated)
        reconcile()
    }

    @Synchronized
    fun removeCourse(account: String, courseId: String): List<CourseReminder> {
        val old = records()
        val removed = old.filter { it.key.account == account && it.key.courseId == courseId }
        save(old - removed.toSet())
        reconcile()
        return removed
    }

    @Synchronized
    fun clearAccount(account: String) {
        save(records().filter { it.key.account != account })
        val editor = preferences.edit()
        preferences.all.keys.filter { it.startsWith("calendar:$account|") }.forEach { editor.remove(it) }
        editor.remove("legacy-calendar-migrated:$account")
        editor.apply()
        reconcile()
    }

    fun timeBase(account: String, term: String): ScheduleTimeBase? = calendars.read(account, term)

    fun updateTimeBase(account: String, term: String, value: ScheduleTimeBase) {
        if (!calendars.write(account, term, value)) return
        revision++
        reconcile()
    }

    fun migrateLegacyTimeBase(account: String, currentTerm: String, value: ScheduleTimeBase) {
        if (calendars.migrateLegacy(account, currentTerm, value)) { revision++; reconcile() }
    }

    fun permissions(): ReminderPermissions {
        val notifications = NotificationManagerCompat.from(context).areNotificationsEnabled() &&
            (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            (Build.VERSION.SDK_INT < 26 || context.getSystemService(NotificationManager::class.java).getNotificationChannel(CHANNEL)?.importance != NotificationManager.IMPORTANCE_NONE)
        return ReminderPermissions(notifications, Build.VERSION.SDK_INT < 31 || alarmManager.canScheduleExactAlarms())
    }

    fun status(key: CourseReminderKey): ReminderStatus = find(key)?.let {
        CourseReminderPlanner.plan(it, timeBase(key.account, key.term), System.currentTimeMillis(), permissions(), activeAccount())
    } ?: ReminderStatus(ReminderAvailability.Off)

    private fun alarmIntent(key: CourseReminderKey) = Intent(context, CourseReminderReceiver::class.java).apply {
        action = ACTION
        data = Uri.Builder().scheme("course-reminder").authority("alarm").appendPath(key.storageId).build()
    }

    private fun scheduledPlans(): Map<String, PlannedReminder> = runCatching {
        val json = JSONArray(preferences.getString("scheduled", "[]"))
        (0 until json.length()).mapNotNull { index -> runCatching {
            val item = json.getJSONObject(index)
            val plan = PlannedReminder(ReminderJson.reminder(item.getJSONObject("reminder")), item.getLong("trigger"), item.getLong("start"))
            plan.reminder.key.storageId to plan
        }.getOrNull() }.toMap()
    }.getOrDefault(emptyMap())

    private fun savePlans(plans: Map<String, PlannedReminder>) {
        preferences.edit().putString("scheduled", JSONArray().apply {
            plans.values.forEach { plan -> put(JSONObject().apply {
                put("reminder", ReminderJson.reminder(plan.reminder)); put("trigger", plan.triggerAt); put("start", plan.startsAt)
            }) }
        }.toString()).apply()
    }

    private val alarms = object : ReminderAlarmPort {
        override fun scheduled() = scheduledPlans()
        override fun exists(key: CourseReminderKey): Boolean = PendingIntent.getBroadcast(context, 0, alarmIntent(key),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) != null
        override fun schedule(plan: PlannedReminder) {
            val key = plan.reminder.key
            val intent = alarmIntent(key).putExtra(EXTRA_REMINDER_ID, key.storageId)
                .putExtra(EXTRA_REVISION, plan.reminder.revision).putExtra(EXTRA_TRIGGER, plan.triggerAt)
            val pending = PendingIntent.getBroadcast(context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, plan.triggerAt, pending)
            savePlans(scheduledPlans() + (key.storageId to plan))
        }
        override fun cancel(key: CourseReminderKey) {
            PendingIntent.getBroadcast(context, 0, alarmIntent(key), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
                alarmManager.cancel(it); it.cancel()
            }
            savePlans(scheduledPlans() - key.storageId)
            NotificationManagerCompat.from(context).cancel(key.storageId, 1)
        }
    }

    @Synchronized
    fun reconcile(resetAlarms: Boolean = false) {
        if (resetAlarms) {
            scheduledPlans().values.forEach { alarms.cancel(it.reminder.key) }
            examSnapshots().forEach { cancelExamAlarm(it) }
        }
        val permission = permissions()
        try {
            reconcileCourseReminders(records(), activeAccount(), { timeBase(it.account, it.term) }, permission, System.currentTimeMillis(), alarms)
            reconcileExam(permission)
        } catch (_: SecurityException) {
            scheduledPlans().values.forEach { alarms.cancel(it.reminder.key) }
            examSnapshots().forEach { cancelExamAlarm(it) }
        }
        revision++
    }

    @Synchronized
    fun receive(id: String, expectedRevision: Long, expectedTrigger: Long) {
        val plan = scheduledPlans()[id] ?: return
        val record = records().firstOrNull { it.key.storageId == id } ?: return
        if (record.revision != expectedRevision || plan.triggerAt != expectedTrigger) return
        if (!isCurrentReminderOccurrence(plan, record, timeBase(record.key.account, record.key.term), permissions(), activeAccount())) {
            reconcile(); return
        }
        val now = System.currentTimeMillis()
        if (now < plan.triggerAt) return
        // An old broadcast must never replace or advance a newer pending occurrence.
        savePlans(scheduledPlans() - id)
        if (now >= plan.triggerAt && now < plan.startsAt) {
            val manager = context.getSystemService(NotificationManager::class.java)
            if (Build.VERSION.SDK_INT >= 26) manager.createNotificationChannel(NotificationChannel(CHANNEL, "课程提醒", NotificationManager.IMPORTANCE_DEFAULT))
            val open = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
                data = Uri.Builder().scheme("course-reminder").authority("open").appendPath(id).build()
                putExtra(EXTRA_REMINDER_ID, id)
            }
            val content = PendingIntent.getActivity(context, 0, open, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_course_reminder)
                .setContentTitle("${record.course.name} · 即将上课")
                .setContentText(listOf(record.course.location, "第 ${record.course.startPeriod}-${record.course.endPeriod} 节").filter { it.isNotBlank() }.joinToString(" · "))
                .setContentIntent(content).setAutoCancel(true).setOnlyAlertOnce(true).build()
            try { NotificationManagerCompat.from(context).notify(id, 1, notification) } catch (_: SecurityException) { }
        }
        reconcile()
    }

    fun findById(id: String): CourseReminder? = records().firstOrNull { it.key.storageId == id && it.key.account == activeAccount() }

    // ============ 考试提醒：一天一条，前一天晚上 ============
    /** 快照落本地：开机重排时没有网络，也不能靠成绩页再去拉一次教务。 */
    private fun examSnapshots(): List<ExamAlarm> = runCatching {
        val array = JSONArray(preferences.getString("exam_alarms", "[]"))
        (0 until array.length()).mapNotNull { index ->
            val item = array.optJSONObject(index) ?: return@mapNotNull null
            val lines = item.optJSONArray("lines") ?: return@mapNotNull null
            ExamAlarm(item.getString("account"), item.getString("term"), item.getString("date"),
                (0 until lines.length()).map { lines.getString(it) }, item.getLong("trigger"))
        }
    }.getOrDefault(emptyList())

    private fun saveExamSnapshots(alarms: List<ExamAlarm>) {
        preferences.edit().putString("exam_alarms", JSONArray().apply {
            alarms.forEach { put(JSONObject().put("account", it.account).put("term", it.term).put("date", it.date)
                .put("lines", JSONArray(it.lines)).put("trigger", it.triggerAt)) }
        }.toString()).apply()
        revision++
    }

    fun examRemindersEnabled(account: String): Boolean =
        account.isNotBlank() && preferences.getBoolean("exam_reminders_on_$account", false)

    /** 关掉开关要连已排的闹钟一起撤，不能只把快照留下次再算。 */
    @Synchronized
    fun setExamRemindersEnabled(account: String, term: String, enabled: Boolean, events: List<ExamCalendarEvent>) {
        if (account.isBlank() || term.isBlank()) return
        preferences.edit().putBoolean("exam_reminders_on_$account", enabled).apply()
        val scoped = examSnapshots().filter { it.account == account && it.term == term }
        scoped.forEach { cancelExamAlarm(it) }
        saveExamSnapshots(examSnapshots().filterNot { it in scoped } +
            if (enabled) examDayAlarms(account, term, events, System.currentTimeMillis()) else emptyList())
        reconcile()
    }

    private fun examIntent(alarm: ExamAlarm) = Intent(context, CourseReminderReceiver::class.java).apply {
        action = ACTION_EXAM
        data = Uri.Builder().scheme("exam-reminder").authority("alarm").appendPath(alarm.storageId).build()
        putExtra(EXTRA_REMINDER_ID, alarm.storageId)
        putExtra(EXTRA_REVISION, alarm.revision)
        putExtra(EXTRA_TRIGGER, alarm.triggerAt)
    }

    private fun cancelExamAlarm(alarm: ExamAlarm) {
        PendingIntent.getBroadcast(context, 0, examIntent(alarm), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE)?.let {
            alarmManager.cancel(it); it.cancel()
        }
    }

    @Synchronized
    fun reconcileExam(permission: ReminderPermissions) {
        val account = activeAccount()
        val now = System.currentTimeMillis()
        val stored = examSnapshots()
        // 过期的就地丢弃，否则它会一直躺在表里被每次 reconcile 重看一遍。
        val live = stored.filter { it.triggerAt > now }
        if (live != stored) saveExamSnapshots(live)
        stored.filter { it.triggerAt <= now }.forEach { cancelExamAlarm(it) }
        val mine = live.filter { it.account == account && it.lines.isNotEmpty() }
        if (account.isBlank() || !permission.available || !examRemindersEnabled(account)) {
            mine.forEach { cancelExamAlarm(it) }; return
        }
        mine.forEach { alarm ->
            // 内容变了 revision 就变了：只判"排着没排"会留下一个带着旧 revision 的闹钟，
            // 触发时被 staleness 校验挡掉，于是永远不响。
            if (armedRevision(alarm) == alarm.revision && examAlarmPending(alarm)) return@forEach
            val pending = PendingIntent.getBroadcast(context, 0, examIntent(alarm),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, alarm.triggerAt, pending)
            rememberArmed(alarm)
        }
    }

    private fun armedExams(): JSONObject = runCatching {
        JSONObject(preferences.getString("exam_armed", "{}"))
    }.getOrDefault(JSONObject())

    private fun armedRevision(alarm: ExamAlarm): Long =
        runCatching { armedExams().getLong(alarm.storageId) }.getOrDefault(-1L)

    private fun rememberArmed(alarm: ExamAlarm) {
        preferences.edit().putString("exam_armed", armedExams().put(alarm.storageId, alarm.revision).toString()).apply()
    }

    private fun examAlarmPending(alarm: ExamAlarm) =
        PendingIntent.getBroadcast(context, 0, examIntent(alarm), PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE) != null

    @Synchronized
    fun receiveExam(id: String, expectedRevision: Long, expectedTrigger: Long) {
        val alarm = examSnapshots().firstOrNull { it.storageId == id } ?: return
        if (alarm.revision != expectedRevision || alarm.triggerAt != expectedTrigger) return
        if (System.currentTimeMillis() < alarm.triggerAt) return
        // 响过就从快照摘掉：重启后不该把同一句"明天有考试"再排一遍。
        saveExamSnapshots(examSnapshots().filterNot { it.storageId == id })
        if (alarm.account != activeAccount()) { reconcile(); return }
        val manager = context.getSystemService(NotificationManager::class.java)
        if (Build.VERSION.SDK_INT >= 26)
            manager.createNotificationChannel(NotificationChannel(CHANNEL, "课程与考试提醒", NotificationManager.IMPORTANCE_DEFAULT))
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val notification = NotificationCompat.Builder(context, CHANNEL).setSmallIcon(R.drawable.ic_course_reminder)
            .setContentTitle("明天有考试")
            .setContentText(examDayNotification(alarm.lines))
            .setStyle(NotificationCompat.InboxStyle().also { style -> alarm.lines.forEach { style.addLine(it) } })
            .setContentIntent(open).setAutoCancel(true).setOnlyAlertOnce(true).build()
        try { NotificationManagerCompat.from(context).notify(id, 2, notification) } catch (_: SecurityException) { }
        reconcile()
    }
    override fun onActivityResumed(activity: Activity) { reconcile() }
    override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) = Unit
    override fun onActivityStarted(activity: Activity) = Unit
    override fun onActivityPaused(activity: Activity) = Unit
    override fun onActivityStopped(activity: Activity) = Unit
    override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) = Unit
    override fun onActivityDestroyed(activity: Activity) = Unit
}
