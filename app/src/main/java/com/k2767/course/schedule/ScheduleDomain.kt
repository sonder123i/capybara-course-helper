package com.k2767.course.schedule

import java.security.MessageDigest
import java.text.Normalizer
import java.util.Locale

const val ScheduleMaxWeeks = 25

data class ParsedWeeks(val weeks: Set<Int>, val valid: Boolean) {
    fun visibleIn(week: Int): Boolean = !valid || week in weeks
}

/** A parity constraint belongs to its segment, never to the entire expression. */
object ScheduleWeeks {
    private val range = Regex("^(\\d{1,2})(?:[-~—–至](\\d{1,2}))?$")
    fun parse(value: String?, maxWeeks: Int = ScheduleMaxWeeks): ParsedWeeks {
        if (value.isNullOrBlank()) return ParsedWeeks(emptySet(), false)
        val text = Normalizer.normalize(value, Normalizer.Form.NFKC).replace(Regex("\\s+"), "")
        val result = sortedSetOf<Int>()
        for (part in text.split(Regex("[,、;]+"))) {
            if (part.isBlank()) return ParsedWeeks(emptySet(), false)
            val odd = part.contains('单')
            val even = part.contains('双')
            if (odd && even) return ParsedWeeks(emptySet(), false)
            val clean = part.replace(Regex("[第周单双()]"), "")
            val match = range.matchEntire(clean) ?: return ParsedWeeks(emptySet(), false)
            val first = match.groupValues[1].toInt()
            val last = match.groupValues[2].toIntOrNull() ?: first
            if (first !in 1..maxWeeks || last !in first..maxWeeks) return ParsedWeeks(emptySet(), false)
            (first..last).filterTo(result) { (!odd || it % 2 == 1) && (!even || it % 2 == 0) }
        }
        return ParsedWeeks(result, result.isNotEmpty())
    }

    fun canonical(value: String): String = parse(value).takeIf { it.valid }?.weeks?.joinToString(",") ?: value.trim()
}

object ScheduleIdentity {
    fun network(sourceId: String, name: String, teacher: String, day: Int, start: Int, end: Int, weeks: String, location: String = ""): String {
        val fields = if (sourceId.isNotBlank()) listOf(sourceId, day.toString(), start.toString(), end.toString(), ScheduleWeeks.canonical(weeks))
            else listOf(name, teacher, location, day.toString(), start.toString(), end.toString(), ScheduleWeeks.canonical(weeks))
        val canonical = fields.joinToString("\u001f") { Normalizer.normalize(it.trim(), Normalizer.Form.NFKC).replace(Regex("\\s+"), " ") }
        return "network:" + digest(canonical)
    }

    fun digest(value: String): String = MessageDigest.getInstance("SHA-256")
        .digest(value.toByteArray(Charsets.UTF_8)).take(16).joinToString("") { "%02x".format(Locale.ROOT, it.toInt() and 0xff) }

    fun colorIndex(id: String, count: Int): Int = (id.hashCode().toLong() and 0x7fffffffL).rem(count).toInt()
}

data class ScheduleCourseRecord(
    val id: String,
    val name: String,
    val teacher: String,
    val location: String,
    val day: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weeks: String,
    val custom: Boolean = false
)

data class ScheduleConflict(val otherId: String, val otherName: String, val weeks: Set<Int>, val startPeriod: Int, val endPeriod: Int)

fun scheduleConflicts(course: ScheduleCourseRecord, all: List<ScheduleCourseRecord>): List<ScheduleConflict> {
    val weeks = ScheduleWeeks.parse(course.weeks)
    if (!weeks.valid) return emptyList()
    return all.mapNotNull { other ->
        if (course.id == other.id || course.day != other.day) return@mapNotNull null
        val first = maxOf(course.startPeriod, other.startPeriod)
        val last = minOf(course.endPeriod, other.endPeriod)
        val otherWeeks = ScheduleWeeks.parse(other.weeks)
        if (first > last || !otherWeeks.valid) return@mapNotNull null
        val overlap = weeks.weeks.intersect(otherWeeks.weeks)
        if (overlap.isEmpty()) null else ScheduleConflict(other.id, other.name, overlap, first, last)
    }
}

fun validateScheduleCourse(course: ScheduleCourseRecord, periodCount: Int): String? = when {
    course.name.isBlank() -> "请填写课程名称"
    course.day !in 1..7 -> "星期需在周一至周日之间"
    course.startPeriod !in 1..periodCount || course.endPeriod !in course.startPeriod..periodCount -> "请检查开始和结束节次"
    !ScheduleWeeks.parse(course.weeks).valid -> "请填写有效周次，例如 1-16周(单),18周"
    else -> null
}
