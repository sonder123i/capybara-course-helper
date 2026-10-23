package com.tyust.course.schedule

import java.util.Calendar
import java.util.TimeZone

/** 一次倒计时的目标：at 是要倒计时的瞬间，label 是组件上显示的说明。 */
internal data class ScheduleWidgetCountdownTarget(
    val at: Long,
    val label: String,
    /** 课间中：这次倒计时指向下一小节上课，而不是整门课结束。 */
    val inBreak: Boolean = false,
    /** 本小节开始时刻；课中用来画进度，整门课倒计时或课间为空。 */
    val segmentStart: Long? = null
)

/**
 * 倒计时目标选择。
 *
 * 默认目标是整门课的下一个边界（上课或下课）。打开“大课按小节倒计时”后，把连续节次拆开：
 * 课中倒计时到本小节下课，最后一节才显示“距下课”，前面的小节显示“距课间”；
 * 课间倒计时到下一小节上课，并明确标成课间休息，避免用户误以为整门课已经结束。
 *
 * 任何信息不完整（缺少节次时间、单次调课改过这门课的时间、节次区间倒序）时都退回整门课
 * 倒计时：宁可少分段，也不显示一个猜出来的时间。
 */
internal object ScheduleWidgetCountdown {
    const val TO_CLASS = "距上课"
    const val TO_END = "距下课"
    const val TO_BREAK = "距课间"
    const val BREAK_TO_CLASS = "课间休息 · 距上课"
    const val TOMORROW_CLASS = "距明日上课"

    /** 状态自身的整门课倒计时目标（状态构造时已经算好）。 */
    fun wholeCourse(state: ScheduleWidgetState): ScheduleWidgetCountdownTarget? = state.countdownAt?.let {
        ScheduleWidgetCountdownTarget(it, state.countdownLabel.ifBlank { TO_CLASS })
    }

    fun resolve(state: ScheduleWidgetState, segmented: Boolean,
                zone: TimeZone = TimeZone.getDefault()): ScheduleWidgetCountdownTarget? {
        val item = state.primary ?: return null
        val whole = wholeCourse(state) ?: return null
        if (!segmented || item.status != "正在上课") return whole
        val base = state.snapshot?.timeBase ?: return whole
        val course = item.course
        val startPeriod = course.startPeriod
        val endPeriod = course.endPeriod
        if (endPeriod <= startPeriod) return whole
        val day = Calendar.getInstance(zone).apply {
            timeInMillis = state.now
            set(Calendar.HOUR_OF_DAY, 0); set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
        }
        fun at(period: Int, source: Map<Int, String>): Long? {
            val minutes = source[period]?.let(::parseMinutes) ?: return null
            return (day.clone() as Calendar).apply {
                set(Calendar.HOUR_OF_DAY, minutes / 60)
                set(Calendar.MINUTE, minutes % 60)
            }.timeInMillis
        }
        // 先完整校验每一节的起止时间，再决定落在哪一段：后面的节次缺时间或区间倒序时，
        // 不能因为“已经落在第一节里”就给出一个没校验过的倒计时。
        val segments = ArrayList<Pair<Long, Long>>(endPeriod - startPeriod + 1)
        for (period in startPeriod..endPeriod) {
            val start = at(period, base.periodStarts) ?: return whole
            val end = at(period, base.periodEnds) ?: return whole
            segments += start to end
        }
        // 单次调课可能改过这门课的时间；此时逐节推演不可信，退回整门课倒计时。
        if (segments.first().first != item.occurrence.startsAt ||
            segments.last().second != item.occurrence.endsAt) return whole
        for ((index, segment) in segments.withIndex()) {
            val (start, end) = segment
            if (end <= start) return whole
            if (index > 0 && start < segments[index - 1].second) return whole
        }
        for ((index, segment) in segments.withIndex()) {
            val (start, end) = segment
            if (state.now >= start && state.now < end) {
                val last = index == segments.lastIndex
                return ScheduleWidgetCountdownTarget(end, if (last) TO_END else TO_BREAK, segmentStart = start)
            }
            val nextStart = segments.getOrNull(index + 1)?.first ?: continue
            if (state.now >= end && state.now < nextStart) {
                return ScheduleWidgetCountdownTarget(nextStart, BREAK_TO_CLASS, inBreak = true)
            }
        }
        return whole
    }

    /**
     * 下一次必须刷新的最早时刻。课程起止由 agenda 给出；打开按小节倒计时后，小节边界（课间结束、
     * 下一小节开始）也要刷新，否则边界闹钟会跳过课间，Chronometer 越过 0 继续走成负数。
     */
    fun nextRefreshAt(state: ScheduleWidgetState, segmented: Boolean,
                      zone: TimeZone = TimeZone.getDefault()): Long? = listOfNotNull(
        state.agenda?.nextChangeAt,
        if (segmented) resolve(state, segmented = true, zone = zone)?.at else null
    ).minOrNull()

    /** 0..1 的剩余时间比例：满格开始、倒计时走到 0 时归零；课间、整门课倒计时或没有小节信息时返回 null（不画进度条）。 */
    fun progress(state: ScheduleWidgetState, target: ScheduleWidgetCountdownTarget?): Float? {
        val start = target?.segmentStart ?: return null
        if (target.inBreak) return null
        val span = target.at - start
        if (span <= 0L) return null
        return ((target.at - state.now).toFloat() / span.toFloat()).coerceIn(0f, 1f)
    }

    private fun parseMinutes(value: String): Int? {
        val parts = value.trim().split(':')
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return hour * 60 + minute
    }
}
