package com.k2767.course.ui.screen

import com.k2767.course.schedule.ScheduleDates
import com.k2767.course.schedule.ScheduleMaxWeeks
import com.k2767.course.schedule.ScheduleWeeks
import java.util.Calendar
import java.util.TimeZone

/** 一门课在某个真实时刻的一次发生。 */
data class ScheduleOccurrence(val course: ScheduleCourseUi, val startsAt: Long, val endsAt: Long)

/**
 * 按「今天」算出来的课表时刻表：正在上的课、下一节、还剩几堂。
 *
 * 时间口径全部是绝对毫秒，不是「第几节的第几分钟」——所以跨天、跨周、
 * 单双周都由日期本身决定，不需要调用方再判断当前是不是本周。
 * 节次时间用页面正在显示的 [periodTimes]，与网格同源：这里若另取一份基准，
 * 日视图说「还剩 2 堂」而网格上那节课已经画成下课，就会自相矛盾。
 */
data class ScheduleAgenda(
    val needsCalendar: Boolean,
    val today: List<ScheduleOccurrence> = emptyList(),
    val current: List<ScheduleOccurrence> = emptyList(),
    val next: ScheduleOccurrence? = null
) {
    /** 今天还没下课的堂数。全部上完时为 0，与「今天没有课」是两回事。 */
    fun remaining(now: Long) = today.count { it.endsAt > now }

    companion object {
        fun calculate(
            courses: List<ScheduleCourseUi>,
            periodTimes: List<PeriodTimeUi>,
            firstWeekDate: String?,
            now: Long,
            zone: TimeZone = TimeZone.getDefault()
        ): ScheduleAgenda {
            val starts = periodTimes.associate { it.period to it.startTime }
            val ends = periodTimes.associate { it.period to it.endTime }
            val day = Calendar.getInstance(zone).apply {
                timeInMillis = now
                set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            if (ScheduleDates.firstMonday(firstWeekDate, zone) == null) return ScheduleAgenda(true)

            fun minutes(value: String?): Pair<Int, Int>? {
                val parts = value?.split(':')?.map { it.toIntOrNull() } ?: return null
                return if (parts.size == 2 && parts[0] in 0..23 && parts[1] in 0..59) parts[0]!! to parts[1]!! else null
            }

            val upcoming = ArrayList<ScheduleOccurrence>()
            for (course in courses.distinctBy { it.id }) {
                val weeks = ScheduleWeeks.parse(course.weeks)
                val start = minutes(starts[course.startPeriod]) ?: continue
                val end = minutes(ends[course.endPeriod]) ?: continue
                if (!weeks.valid || course.day !in 1..7 || course.endPeriod < course.startPeriod) continue
                for (week in weeks.weeks) {
                    val date = ScheduleDates.date(firstWeekDate, week, course.day, zone) ?: continue
                    val startsAt = (date.clone() as Calendar).apply {
                        set(Calendar.HOUR_OF_DAY, start.first); set(Calendar.MINUTE, start.second)
                    }.timeInMillis
                    val endsAt = date.apply {
                        set(Calendar.HOUR_OF_DAY, end.first); set(Calendar.MINUTE, end.second)
                    }.timeInMillis
                    // 只关心「今天及以后」：过去几周的发生既不影响摘要，白算一遍也白排序。
                    if (endsAt <= startsAt || endsAt < day.timeInMillis) continue
                    upcoming += ScheduleOccurrence(course, startsAt, endsAt)
                }
            }
            upcoming.sortWith(compareBy({ it.startsAt }, { it.course.id }))
            val tomorrow = (day.clone() as Calendar).apply { add(Calendar.DATE, 1) }.timeInMillis
            val today = upcoming.filter { it.startsAt in day.timeInMillis until tomorrow }
            return ScheduleAgenda(
                needsCalendar = false,
                today = today,
                current = today.filter { now in it.startsAt until it.endsAt },
                next = upcoming.firstOrNull { it.startsAt > now }
            )
        }
    }
}

/** 周次写坏了（解析不出来的那些）的课有多少堂——摘要里要单独提醒，别混进「还剩」。 */
internal fun countUnknownWeeks(courses: List<ScheduleCourseUi>): Int =
    courses.count { !ScheduleWeeks.parse(it.weeks).valid }

/** 日视图只在 1..25 周之间有意义；越界时给一句人话，而不是空列表。 */
internal fun dayOutOfRange(week: Int): String? = when {
    week < 1 -> "尚未开学，当天没有课程"
    week > ScheduleMaxWeeks -> "本学期已结束"
    else -> null
}
