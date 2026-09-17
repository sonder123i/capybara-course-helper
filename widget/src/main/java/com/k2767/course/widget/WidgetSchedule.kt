package com.k2767.course.widget

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Calendar
import java.util.TimeZone

/** 与 App 内 ScheduleMaxWeeks 保持一致。 */
private const val MaxWeeks = 25

data class WidgetPeriod(val period: Int, val start: String, val end: String)

data class WidgetCourse(
    val id: String,
    val name: String,
    val teacher: String,
    val location: String,
    val day: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weeks: String
)

data class WidgetSnapshot(
    val school: String,
    val firstWeekDate: String,
    val periods: List<WidgetPeriod>,
    val courses: List<WidgetCourse>
) {
    companion object {
        fun parse(raw: String): WidgetSnapshot? = runCatching {
            val json = JSONObject(raw)
            val periodArray = json.optJSONArray(CourseWidgetData.KEY_PERIODS) ?: JSONArray()
            val courseArray = json.optJSONArray(CourseWidgetData.KEY_COURSES) ?: JSONArray()
            val periods = ArrayList<WidgetPeriod>(periodArray.length())
            for (i in 0 until periodArray.length()) {
                val item = periodArray.optJSONObject(i) ?: continue
                val index = item.optInt(CourseWidgetData.KEY_PERIOD_INDEX, 0)
                if (index <= 0) continue
                periods.add(
                    WidgetPeriod(
                        index,
                        item.optString(CourseWidgetData.KEY_PERIOD_START),
                        item.optString(CourseWidgetData.KEY_PERIOD_END)
                    )
                )
            }
            val courses = ArrayList<WidgetCourse>(courseArray.length())
            for (i in 0 until courseArray.length()) {
                val item = courseArray.optJSONObject(i) ?: continue
                val name = item.optString(CourseWidgetData.KEY_COURSE_NAME).trim()
                if (name.isEmpty()) continue
                courses.add(
                    WidgetCourse(
                        id = item.optString(CourseWidgetData.KEY_COURSE_ID),
                        name = name,
                        teacher = item.optString(CourseWidgetData.KEY_COURSE_TEACHER).trim(),
                        location = item.optString(CourseWidgetData.KEY_COURSE_LOCATION).trim(),
                        day = item.optInt(CourseWidgetData.KEY_COURSE_DAY, 0),
                        startPeriod = item.optInt(CourseWidgetData.KEY_COURSE_START, 1),
                        endPeriod = item.optInt(CourseWidgetData.KEY_COURSE_END, 1),
                        weeks = item.optString(CourseWidgetData.KEY_COURSE_WEEKS)
                    )
                )
            }
            WidgetSnapshot(
                school = json.optString(CourseWidgetData.KEY_SCHOOL).trim(),
                firstWeekDate = json.optString(CourseWidgetData.KEY_FIRST_WEEK_DATE).trim(),
                periods = periods,
                courses = courses
            )
        }.getOrNull()
    }
}

/** 小组件里展示的一行课。 */
data class WidgetCourseRow(val name: String, val location: String, val time: String, val colorIndex: Int)

/**
 * 「今天有哪些课」的计算。这里刻意与 App 侧的 ScheduleWeeks / ScheduleDates 保持同样的规则，
 * 但不共享代码——小组件模块独立于 App 模块（同 CQUPT 课表小组件的骨架）。
 */
object WidgetToday {
    private val segment = Regex("^(\\d{1,2})(?:[-~—–至](\\d{1,2}))?$")

    /** 解析周次表达式；非法（或空）返回 null——与 App 一致：非法周次视为每周都上。 */
    fun parseWeeks(value: String?): Set<Int>? {
        if (value.isNullOrBlank()) return null
        val text = Normalizer.normalize(value, Normalizer.Form.NFKC).replace(Regex("\\s+"), "")
        val result = sortedSetOf<Int>()
        for (part in text.split(Regex("[,、;]+"))) {
            if (part.isEmpty()) return null
            val odd = part.contains('单')
            val even = part.contains('双')
            if (odd && even) return null
            val clean = part.replace(Regex("[第周单双()]"), "")
            val match = segment.matchEntire(clean) ?: return null
            val first = match.groupValues[1].toIntOrNull() ?: return null
            val last = match.groupValues[2].toIntOrNull() ?: first
            if (first !in 1..MaxWeeks || last !in first..MaxWeeks) return null
            (first..last).filterTo(result) { (!odd || it % 2 == 1) && (!even || it % 2 == 0) }
        }
        return result.ifEmpty { null }
    }

    fun visibleInWeek(course: WidgetCourse, week: Int): Boolean {
        val weeks = parseWeeks(course.weeks) ?: return true
        return week in weeks
    }

    /** 1=周一 … 7=周日。 */
    fun dayOfWeek(now: Long, zone: TimeZone = TimeZone.getDefault()): Int {
        val calendar = Calendar.getInstance(zone).apply { timeInMillis = now }
        return (calendar.get(Calendar.DAY_OF_WEEK) + 5) % 7 + 1
    }

    /** 与 App 的 ScheduleDates.weekAt 同规则：起始日必须是周一，且落在 1..25 周内。 */
    fun weekAt(firstWeekDate: String?, now: Long, zone: TimeZone = TimeZone.getDefault()): Int? {
        val start = monday(firstWeekDate) ?: return null
        val local = Calendar.getInstance(zone).apply { timeInMillis = now }
        val today = Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            set(local.get(Calendar.YEAR), local.get(Calendar.MONTH), local.get(Calendar.DAY_OF_MONTH))
        }
        val days = (today.timeInMillis - start.timeInMillis) / 86_400_000L
        return if (days < 0) null else (days / 7 + 1).toInt().takeIf { it in 1..MaxWeeks }
    }

    private fun monday(firstWeekDate: String?): Calendar? = runCatching {
        val parts = requireNotNull(firstWeekDate?.takeIf { it.isNotBlank() }).split('-').map(String::toInt)
        require(parts.size == 3)
        Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
            clear()
            isLenient = false
            set(parts[0], parts[1] - 1, parts[2])
            timeInMillis
        }.takeIf { it.get(Calendar.DAY_OF_WEEK) == Calendar.MONDAY }
    }.getOrNull()

    /** 当天要展示的课：按起始节次排序，`max` 用于按小组件尺寸截断。 */
    fun rows(
        snapshot: WidgetSnapshot,
        now: Long = System.currentTimeMillis(),
        zone: TimeZone = TimeZone.getDefault(),
        max: Int = Int.MAX_VALUE
    ): List<WidgetCourseRow> {
        val week = weekAt(snapshot.firstWeekDate, now, zone) ?: return emptyList()
        val today = dayOfWeek(now, zone)
        val starts = snapshot.periods.associate { it.period to it.start }
        val ends = snapshot.periods.associate { it.period to it.end }
        val palette = WidgetPalette.colors.size
        return snapshot.courses
            .filter { it.day == today && visibleInWeek(it, week) }
            .sortedWith(compareBy({ it.startPeriod }, { it.name }))
            .map { course ->
                WidgetCourseRow(
                    name = course.name,
                    location = course.location,
                    time = timeText(starts[course.startPeriod], ends[course.endPeriod]),
                    colorIndex = (course.id.hashCode().toLong() and 0x7fffffffL).rem(palette.toLong()).toInt()
                )
            }
            .take(max)
    }

    fun timeText(start: String?, end: String?): String = when {
        !start.isNullOrBlank() && !end.isNullOrBlank() -> "$start - $end"
        !start.isNullOrBlank() -> start
        else -> ""
    }
}

/** 与 App 内课表同一套配色（ScheduleRoute 的 courseColors）。 */
object WidgetPalette {
    val colors = listOf(
        0xFF5C6BC0, 0xFF42A5F5, 0xFF66BB6A, 0xFFFFA726,
        0xFFAB47BC, 0xFFEF5350, 0xFF26C6DA, 0xFF8D6E63
    ).map { it.toInt() }
}