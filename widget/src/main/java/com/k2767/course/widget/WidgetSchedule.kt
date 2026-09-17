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
        max: Int = Int.MAX_VALUE,
        skipEnded: Boolean = false
    ): List<WidgetCourseRow> = rowsAt(snapshot, dayOffset = 0, now = now, zone = zone, max = max, skipEnded = skipEnded)

    /**
     * 相对今天偏移 [dayOffset] 天（0=今天，1=明天）的课。偏移用 Calendar 走，跨夏令时不会错位。
     *
     * [skipEnded] 打开时会丢掉今天**已经下课**的课——组件位置有限，上完的课让位给后面的课，
     * 这样下午打开桌面就能直接看到下午的课（WakeUp 的这个细节）。明天那一栏不受影响。
     */
    fun rowsAt(
        snapshot: WidgetSnapshot,
        dayOffset: Int,
        now: Long = System.currentTimeMillis(),
        zone: TimeZone = TimeZone.getDefault(),
        max: Int = Int.MAX_VALUE,
        skipEnded: Boolean = false
    ): List<WidgetCourseRow> {
        val target = Calendar.getInstance(zone).apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, dayOffset)
        }.timeInMillis
        // 明天可能已经跨到下一周，所以周次要按目标日期重新算
        val week = weekAt(snapshot.firstWeekDate, target, zone) ?: return emptyList()
        val day = dayOfWeek(target, zone)
        val starts = snapshot.periods.associate { it.period to it.start }
        val ends = snapshot.periods.associate { it.period to it.end }
        val palette = WidgetPalette.colors.size
        return snapshot.courses
            .filter { it.day == day && visibleInWeek(it, week) }
            .filter { course ->
                if (!skipEnded || dayOffset != 0) true
                else (courseEndMillis(now, zone, ends[course.endPeriod]) ?: Long.MAX_VALUE) > now
            }
            .sortedWith(compareBy({ it.startPeriod }, { it.name }))
            .map { course ->
                WidgetCourseRow(
                    name = course.name,
                    location = compactLocation(course.location),
                    time = timeText(starts[course.startPeriod], ends[course.endPeriod]),
                    colorIndex = (course.id.hashCode().toLong() and 0x7fffffffL).rem(palette.toLong()).toInt()
                )
            }
            .take(max)
    }

    /** 某节课的下课时刻（今天的日期 + 结束时间）。时间缺失时返回 null。 */
    private fun courseEndMillis(now: Long, zone: TimeZone, end: String?): Long? {
        val parts = end?.split(':') ?: return null
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return Calendar.getInstance(zone).apply {
            timeInMillis = now
            set(Calendar.HOUR_OF_DAY, hour)
            set(Calendar.MINUTE, minute)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /**
     * 下一次「值得刷新组件」的时刻 = 今天最近的一次下课时间；今天的课都上完后 = 明天 0 点。
     *
     * 系统定时刷新最短 30 分钟，只靠它会让「下课 → 让位」慢半拍，所以组件每次渲染时
     * 都用这个时刻给自己定一个闹钟（见 WidgetRefreshScheduler）。
     */
    fun nextRefreshAt(
        snapshot: WidgetSnapshot?,
        now: Long = System.currentTimeMillis(),
        zone: TimeZone = TimeZone.getDefault()
    ): Long {
        val midnight = Calendar.getInstance(zone).apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, 1)
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
        val snap = snapshot ?: return midnight
        val week = weekAt(snap.firstWeekDate, now, zone) ?: return midnight
        val today = dayOfWeek(now, zone)
        val ends = snap.periods.associate { it.period to it.end }
        return snap.courses
            .filter { it.day == today && visibleInWeek(it, week) }
            .mapNotNull { course -> courseEndMillis(now, zone, ends[course.endPeriod]) }
            .filter { it > now }
            .minOrNull() ?: midnight
    }

    fun timeText(start: String?, end: String?): String = when {
        !start.isNullOrBlank() && !end.isNullOrBlank() -> "$start - $end"
        !start.isNullOrBlank() -> start
        else -> ""
    }

    /**
     * 裁掉「XX校区」前缀（与 App 内 ScheduleScreen.compactLocation 同规则）。
     *
     * 校区名几乎每门课都一样，而组件那一行最宝贵——裁掉之后「致远楼A519」才显示得下。
     * 只有校区、没有楼栋时保持原样，避免把地点变成空串。
     */
    fun compactLocation(location: String): String {
        if (location.isBlank()) return location
        val stripped = location.replace(Regex("^[^\\s]*校区[\\s·・,，、]*"), "").trim()
        return stripped.ifBlank { location }
    }

    /**
     * 一周网格：返回 (星期, 节次) → 格子。
     *
     * 跨多节的课只在起始节次写课名，后续节次画同色的延续块——Glance 没有 rowSpan，
     * 靠同色把一列拼成连续的竖条。同一格被多门课占用时先到先得（按节次与课名排序）。
     */
    fun cellsForWeek(
        snapshot: WidgetSnapshot,
        now: Long = System.currentTimeMillis(),
        zone: TimeZone = TimeZone.getDefault(),
        maxPeriods: Int
    ): Map<Pair<Int, Int>, WidgetCell> {
        val week = weekAt(snapshot.firstWeekDate, now, zone) ?: return emptyMap()
        val cells = HashMap<Pair<Int, Int>, WidgetCell>()
        val palette = WidgetPalette.colors.size
        snapshot.courses
            .filter { it.day in 1..7 && it.startPeriod in 1..maxPeriods && visibleInWeek(it, week) }
            .sortedWith(compareBy({ it.day }, { it.startPeriod }, { it.name }))
            .forEach { course ->
                val colorIndex = (course.id.hashCode().toLong() and 0x7fffffffL).rem(palette.toLong()).toInt()
                for (period in course.startPeriod..course.endPeriod.coerceAtMost(maxPeriods)) {
                    val key = course.day to period
                    if (cells.containsKey(key)) continue
                    cells[key] = if (period == course.startPeriod) {
                        WidgetCell(name = course.name, colorIndex = colorIndex, continued = false)
                    } else {
                        WidgetCell(name = "", colorIndex = colorIndex, continued = true)
                    }
                }
            }
        return cells
    }
}

/** 周视图网格里的一格：continued 表示它是上一格那门课的延续（不重复写课名）。 */
data class WidgetCell(val name: String, val colorIndex: Int, val continued: Boolean)

/** 与 App 内课表同一套配色（ScheduleRoute 的 courseColors）。 */
object WidgetPalette {
    val colors = listOf(
        0xFF5C6BC0, 0xFF42A5F5, 0xFF66BB6A, 0xFFFFA726,
        0xFFAB47BC, 0xFFEF5350, 0xFF26C6DA, 0xFF8D6E63
    ).map { it.toInt() }
}