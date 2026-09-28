package com.k2767.course.schedule

import java.util.Calendar
import java.util.Locale

/**
 * 一场考试的墙上时间：教务写的是什么就是什么，不含任何时区换算。
 *
 * 这里刻意不放 Instant/Calendar——考试永远发生在学校那一边，
 * 日历文件也已经声明 TZID=Asia/Shanghai，中间任何一次按设备时区换算都会把时间推偏。
 */
data class ExamTiming(val date: String, val time: String?) {
    val allDay: Boolean get() = time == null
}

/** 一条能进日历的考试。字段全是中性类型，导出器不认识 UI。 */
data class ExamCalendarEvent(
    val uid: String,
    val title: String,
    val location: String,
    val description: String,
    val timing: ExamTiming,
)

/** [unreadable] 是为了让结果话术能把"没导出去的那些"说出来，不静默丢。 */
data class ExamCalendarPlan(val total: Int, val events: List<ExamCalendarEvent>, val unreadable: Int) {
    val timed get() = events.count { !it.timing.allDay }
    val allDay get() = events.count { it.timing.allDay }
}

private val dateShapes = listOf(
    Regex("""^(\d{4})-(\d{1,2})-(\d{1,2})"""),
    Regex("""^(\d{4})年(\d{1,2})月(\d{1,2})日"""),
    Regex("""^(\d{4})/(\d{1,2})/(\d{1,2})"""),
)
private val timeShape = Regex("""^\s*([01]\d|2[0-3]):([0-5]\d)""")

/**
 * 教务把考试时间写成自由文本，这里只认带年份的三种形状。
 *
 * 缺年份的 `06-30` 一律不猜——猜错会把考试排到过去，用户带着一个不响的闹钟去考场。
 * 时刻认不出没关系，那只是降级成全天事件，不影响"日历里有这场"。
 */
fun parseExamTiming(raw: String): ExamTiming? {
    val text = raw.trim()
    if (text.isEmpty()) return null
    for (shape in dateShapes) {
        val match = shape.find(text) ?: continue
        val parts = match.groupValues.drop(1).map { it.toInt() }
        if (parts[1] !in 1..12 || parts[2] !in 1..31) continue
        val calendar = Calendar.getInstance().apply {
            clear(); isLenient = false
            set(parts[0], parts[1] - 1, parts[2])
        }
        // 2月30日 这类日期只有在这里才暴露：Calendar 要到算时间时才校验。
        runCatching { calendar.timeInMillis }.getOrNull() ?: continue
        if (calendar.get(Calendar.MONTH) != parts[1] - 1 || calendar.get(Calendar.DAY_OF_MONTH) != parts[2]) continue

        val time = timeShape.find(text.substring(match.value.length))
        return ExamTiming("%04d-%02d-%02d".format(Locale.ROOT, parts[0], parts[1], parts[2]),
            time?.let { "${it.groupValues[1]}:${it.groupValues[2]}" })
    }
    return null
}

/** 一句人话，把降级和丢弃都说出来——静默少导几场比不导更让人抓狂。 */
fun examExportSummary(plan: ExamCalendarPlan): String = when {
    plan.total == 0 -> "还没有考试安排，没有导出"
    plan.events.isEmpty() -> "${plan.total} 场考试都没写能认出的日期，没有导出"
    else -> buildString {
        append("已导出 ${plan.events.size} 场考试：${plan.timed} 场定时，${plan.allDay} 场只标了当天")
        if (plan.unreadable > 0) append("，另有 ${plan.unreadable} 场时间看不懂")
    }
}

/** 同一场考试改期或换考场后重导，要覆盖同一条而不是留一条幽灵事件。 */
fun examEventUid(pairKey: String): String = "exam-" + ScheduleIdentity.digest(pairKey) + "@capybara"
