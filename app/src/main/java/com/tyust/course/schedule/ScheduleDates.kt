package com.tyust.course.schedule

import java.util.Calendar
import java.util.TimeZone

object ScheduleDates {
    fun firstMonday(value: String?, zone: TimeZone = TimeZone.getDefault()): Calendar? = runCatching {
        val parts = requireNotNull(value).split('-').map(String::toInt)
        require(parts.size == 3)
        Calendar.getInstance(zone).apply {
            clear(); isLenient = false
            set(parts[0], parts[1] - 1, parts[2])
            timeInMillis
            require(get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY)
        }
    }.getOrNull()

    fun date(value: String?, week: Int, day: Int = 1, zone: TimeZone = TimeZone.getDefault()): Calendar? =
        firstMonday(value, zone)?.apply { add(Calendar.DAY_OF_YEAR, (week - 1) * 7 + day - 1) }

    fun weekAt(value: String?, now: Long, zone: TimeZone = TimeZone.getDefault()): Int? {
        val start = firstMonday(value, TimeZone.getTimeZone("UTC")) ?: return null
        val local = Calendar.getInstance(zone).apply { timeInMillis = now }
        val today = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear(); set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
        }
        val days = (today.timeInMillis - start.timeInMillis) / 86_400_000L
        return if (days < 0) null else (days / 7 + 1).toInt().takeIf { it in 1..ScheduleMaxWeeks }
    }
}
