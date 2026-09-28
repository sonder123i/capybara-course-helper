package com.k2767.course.schedule

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale
import java.util.TimeZone

/** 考前一晚提醒的钟点：晚自习结束前，还来得及查路线和收证件。 */
const val ExamReminderHour = 20

/**
 * 一天一条。同一天考两场就合并成一条通知——按场次弹是两次打扰，
 * 而"明天有考试"这件事本身是按天成立的。
 */
data class ExamAlarm(
    val account: String,
    val term: String,
    val date: String,
    val lines: List<String>,
    val triggerAt: Long,
) {
    val storageId: String get() = ScheduleIdentity.digest(listOf(account, term, date).joinToString("\u001f"))

    /** 内容变了闹钟才重排；没变时这个值稳定，否则每次 reconcile 都会重写 PendingIntent。 */
    val revision: Long get() = lines.joinToString("\u001e").hashCode().toLong()
}

/**
 * 提醒时间是**绝对时刻**（前一天 20:00），不能用课程的 `leadMinutes` 反推——
 * 那套要从"考试开始时刻"往回减，而只给了日期的考试根本没有开始时刻。
 *
 * 算出来已经过去的提醒整条跳过，不补发：一个立刻响的或永不响的闹钟，都比不响更糟。
 */
fun examDayAlarms(account: String, term: String, events: List<ExamCalendarEvent>, now: Long,
                  zone: TimeZone = TimeZone.getDefault()): List<ExamAlarm> {
    val dayFormat = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = zone }
    val timeFormat = SimpleDateFormat("HH:mm", Locale.ROOT).apply { timeZone = zone }
    val grouped = events.map { event ->
        val start = event.timing.startsAt
        val line = buildString {
            append(event.title)
            append(if (event.timing.allDay) " 时间见教务" else " ${timeFormat.format(start.time)}")
            if (event.location.isNotBlank()) append(" · ${event.location}")
        }
        dayFormat.format(start.time) to line
    }.groupBy({ it.first }, { it.second })

    return grouped.mapNotNull { (date, lines) ->
        examEveTrigger(date, zone)?.takeIf { it > now }?.let {
            ExamAlarm(account, term, date, lines.distinct().sorted(), it)
        }
    }.sortedBy { it.triggerAt }
}

/** 这场考试前一天的 [ExamReminderHour] 点。 */
fun examEveTrigger(date: String, zone: TimeZone = TimeZone.getDefault()): Long? {
    val parts = date.split('-').mapNotNull { it.toIntOrNull() }
    if (parts.size != 3) return null
    return runCatching {
        Calendar.getInstance(zone).apply {
            clear(); isLenient = false
            set(parts[0], parts[1] - 1, parts[2])
            add(Calendar.DAY_OF_YEAR, -1)
            set(Calendar.HOUR_OF_DAY, ExamReminderHour)
            set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }.getOrNull()
}

/** 通知正文。标题固定是"明天有考试"，这里只负责内容那一行。 */
fun examDayNotification(lines: List<String>): String = when (lines.size) {
    0 -> "明天有考试"
    1 -> lines.single()
    else -> "${lines.size} 场考试：" + lines.joinToString("；")
}
