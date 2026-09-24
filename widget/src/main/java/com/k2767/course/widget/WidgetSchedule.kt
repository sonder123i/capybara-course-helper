package com.k2767.course.widget

import org.json.JSONArray
import org.json.JSONObject
import java.text.Normalizer
import java.util.Calendar
import java.util.TimeZone

/** 与 App 内 ScheduleMaxWeeks 保持一致。 */
private const val MaxWeeks = 25

/**
 * 预告最多往后找几天。
 *
 * 一周足够覆盖「周末 + 没课的工作日」这类最常见的空档；再长的假期（国庆、寒暑假）本来就
 * 没什么可预告的，组件会改说「接下来一周都没课」，比翻到两周后翻出孤零零一节课更有用。
 */
const val MaxPreviewDays = 7

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
 * 往后预告的某一天。
 *
 * [offset] 是相对今天的天数（0=今天），[day] 是 1..7 的星期几，[rows] 是那天要塞进组件的课。
 * [day] 单独带着是为了让组件能写出正确的栏头——周末打开组件时左边那栏装的是周一的课，
 * 再写「今天」就是在撒谎，得写「周一」。
 */
data class PreviewDay(val offset: Int, val day: Int, val rows: List<WidgetCourseRow>)

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

    /**
     * 这份快照能不能算出周次。没填开学日期时 [weekAt] 返回 null，所有课都会被过滤掉——
     * 组件必须把这和「今天真的没课」区分开，否则会对着一张有课的课表说「今天没有课啦」。
     */
    fun hasWeekAnchor(
        snapshot: WidgetSnapshot,
        now: Long = System.currentTimeMillis(),
        zone: TimeZone = TimeZone.getDefault()
    ): Boolean = weekAt(snapshot.firstWeekDate, now, zone) != null

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
                else (timeOnDay(now, zone, ends[course.endPeriod]) ?: Long.MAX_VALUE) > now
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

    /**
     * 往后找「下一个有课的日子」：从 [fromOffset] 天起最多找 [maxAhead] 天。
     *
     * 这是「周末也能提前看到周一上什么」的实现。今天/本周没课时，组件不再干巴巴地说
     * 「今天没有课啦」，而是把后面第一天上什么课摆出来。
     *
     * 几条必须守住的规则：
     *
     * 1. **只认有课的日子，跳过没课的。** 周六查时周一有课就停下——中间空着的周日不出栏，
     *    所以返回的 offset 可能跳着走（0 → 2 是常态）。
     * 2. **周次按目标日期重新算**（走 [rowsAt]），所以预告跨到下一周时单双周不会错。
     * 3. **[maxAhead] 之外的整段假都找不到。** 国庆这类长假期会超过上限，调用方必须能拿到
     *    null 并换一句「接下来一周都没课」，而不是退回「今天没有课啦」——那样等于暗示
     *    只有今天空闲，是在说反话。
     * 4. **不受 [skipEnded] 影响。** 预告的日子永远不是「今天」，[rowsAt] 内部会忽略它；
     *    而 [fromOffset] = 0 查今天时调用方已经确认今天没课，跳过已下课不会改变结果。
     */
    fun nextDayWithCourses(
        snapshot: WidgetSnapshot,
        fromOffset: Int = 0,
        maxAhead: Int = MaxPreviewDays,
        now: Long = System.currentTimeMillis(),
        zone: TimeZone = TimeZone.getDefault(),
        max: Int = Int.MAX_VALUE
    ): PreviewDay? {
        if (maxAhead < 1) return null
        for (offset in fromOffset until fromOffset + maxAhead) {
            val rows = rowsAt(snapshot, offset, now = now, zone = zone, max = max, skipEnded = false)
            if (rows.isNotEmpty()) {
                val target = Calendar.getInstance(zone).apply {
                    timeInMillis = now
                    add(Calendar.DAY_OF_YEAR, offset)
                }.timeInMillis
                return PreviewDay(offset = offset, day = dayOfWeek(target, zone), rows = rows)
            }
        }
        return null
    }

    /**
     * [fromOffset] 那天到底有没有课（不看「上完没上完」）。
     *
     * 这是「今天/某天本来有课、只是都上完了」与「那天真的没课」的分界线。组件必须用它来
     * 决定该预告还是该报空——只看 [rowsAt] 加 `skipEnded` 的结果会分不清两者。
     */
    fun hasCoursesOn(
        snapshot: WidgetSnapshot,
        dayOffset: Int,
        now: Long = System.currentTimeMillis(),
        zone: TimeZone = TimeZone.getDefault()
    ): Boolean = rowsAt(snapshot, dayOffset, now = now, zone = zone).isNotEmpty()

    /**
     * 某一天、某个 `HH:mm` 对应的绝对时刻。
     *
     * [daySample] 只提供「哪一天」，时分秒被抹掉后换成给定的时间——所以传今天的样本时刻
     * 就算今天，传后天的样本时刻就算后天。时间缺失或写成非数字时返回 null，调用方必须
     * 把这节课整条丢掉：一节算不出起止的课排进时间轴，位置只能靠猜。
     */
    private fun timeOnDay(daySample: Long, zone: TimeZone, time: String?): Long? {
        val parts = time?.split(':') ?: return null
        val hour = parts.getOrNull(0)?.toIntOrNull() ?: return null
        val minute = parts.getOrNull(1)?.toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return Calendar.getInstance(zone).apply {
            timeInMillis = daySample
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
            .mapNotNull { course -> timeOnDay(now, zone, ends[course.endPeriod]) }
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
     * 时间轴：从今天起往后最多 [days] 天，按真实起止时刻排的一列课。
     *
     * 三条口径，缺一条就会骗人：
     *
     * 1. **今天只留还没下课的。** 桌面上这一列回答的是「接下来要上什么」，不是当天回顾；
     *    明天及以后不受这条影响。
     * 2. **排序看时刻，不看节次号。** 节次表完全可以第 5 节 14:00、第 6 节 14:55，
     *    但也能被用户改成不单调——按时刻排才不会把课排到已经过去的时段上。
     * 3. **算不出周次的那天整天跳过。** 没填开学日期时这里返回空列表，组件据此说
     *    「还没设开学日期」，而不是对着有课的课表说「今天没有课啦」。
     *
     * [WidgetTimelineRow.firstOfDay] 标在每天的第一节课上，供组件画日分隔：一列课
     * 不标日子，用户分不清哪节是今天的。
     */
    fun timeline(
        snapshot: WidgetSnapshot,
        now: Long = System.currentTimeMillis(),
        zone: TimeZone = TimeZone.getDefault(),
        days: Int = MaxPreviewDays
    ): List<WidgetTimelineRow> {
        val starts = snapshot.periods.associate { it.period to it.start }
        val ends = snapshot.periods.associate { it.period to it.end }
        val palette = WidgetPalette.colors.size
        val result = ArrayList<WidgetTimelineRow>()
        for (offset in 0 until days.coerceAtLeast(1)) {
            val daySample = Calendar.getInstance(zone).apply {
                timeInMillis = now
                add(Calendar.DAY_OF_YEAR, offset)
            }.timeInMillis
            val week = weekAt(snapshot.firstWeekDate, daySample, zone) ?: continue
            val day = dayOfWeek(daySample, zone)
            val occurrences = snapshot.courses
                .filter { it.day == day && visibleInWeek(it, week) }
                .mapNotNull { course ->
                    val start = timeOnDay(daySample, zone, starts[course.startPeriod]) ?: return@mapNotNull null
                    val end = timeOnDay(daySample, zone, ends[course.endPeriod]) ?: return@mapNotNull null
                    if (end <= start) null else Triple(course, start, end)
                }
                .filter { offset != 0 || it.third > now }
                .sortedWith(compareBy({ it.second }, { it.first.name }))
            occurrences.forEachIndexed { index, (course, start, end) ->
                result += WidgetTimelineRow(
                    dayOffset = offset,
                    day = day,
                    time = timeText(starts[course.startPeriod], ends[course.endPeriod]),
                    name = course.name,
                    location = compactLocation(course.location),
                    colorIndex = (course.id.hashCode().toLong() and 0x7fffffffL).rem(palette.toLong()).toInt(),
                    happening = now in start until end,
                    firstOfDay = index == 0
                )
            }
        }
        return result
    }
}

/** 时间轴上的一节课。[happening] = 此刻正在上；[firstOfDay] = 这一天的第一节，用来画日分隔。 */
data class WidgetTimelineRow(
    val dayOffset: Int,
    val day: Int,
    val time: String,
    val name: String,
    val location: String,
    val colorIndex: Int,
    val happening: Boolean,
    val firstOfDay: Boolean
)

/** 与 App 内课表同一套配色（ScheduleRoute 的 courseColors）。 */
object WidgetPalette {
    val colors = listOf(
        0xFF5C6BC0, 0xFF42A5F5, 0xFF66BB6A, 0xFFFFA726,
        0xFFAB47BC, 0xFFEF5350, 0xFF26C6DA, 0xFF8D6E63
    ).map { it.toInt() }
}