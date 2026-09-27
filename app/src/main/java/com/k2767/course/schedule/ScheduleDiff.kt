package com.k2767.course.schedule

import org.json.JSONArray
import org.json.JSONObject

/** 能点名变了哪个字段，还是只能回答「这门课的安排不一样了」。 */
enum class DiffTrust { FieldLevel, PresenceOnly }

/**
 * 待比对的一行。[cover] 是这门课实际占掉的时间点，[fields] 只负责显示——
 * 分开之后，配对才不依赖学校把那行写成了什么字样。[pairKey] 是无 sourceId 时的配对键，由每张表自己定。
 */
data class DiffRow(
    val id: String,
    val sourceId: String,
    val label: String,
    val pairKey: String,
    val fields: Map<String, String>,
    val cover: Set<String>,
)

/** 一张可比较的表的脾气：空表是不是权威结果、哪些字段由 cover 承担、降级时怎么说。 */
data class DiffTable(
    val emptyIsAuthoritative: Boolean,
    val presenceNote: String,
    val coveredFields: Set<String> = setOf("day", "periods", "weeks"),
)

val ScheduleTable = DiffTable(true, "本次只能按课程名与教师比对，不逐段核对时间调整")

/** 排考前考试表为空是常态，所以空表不算权威变更；一场考试的时间点就是它的考试时间。 */
val ExamTable = DiffTable(false, "本次只能按课程名与考试名称比对，不逐字段核对时间与座位", coveredFields = setOf("time"))

sealed class ScheduleChange {
    abstract val label: String

    /** 当前表里受这条变更影响的渲染 id；消失的行已经不在表里，所以为空。 */
    open val ids: Set<String> get() = emptySet()

    data class Moved(override val label: String, val field: String, val from: String, val to: String,
                     override val ids: Set<String>) : ScheduleChange()
    data class Appeared(override val label: String, override val ids: Set<String>) : ScheduleChange()
    data class Disappeared(override val label: String) : ScheduleChange()
    data class ArrangementChanged(override val label: String, override val ids: Set<String>) : ScheduleChange()

    /** 整表变空单独一条，绝不摊成「删除了 N 门课」。 */
    data object Emptied : ScheduleChange() {
        override val label = ""
    }
}

data class ScheduleChangeReport(val trust: DiffTrust, val table: DiffTable, val changes: List<ScheduleChange>) {
    /** 卡片标记只看这个集合：哪一格被碰过，就只点亮哪一格。 */
    fun changedIds(): Set<String> = changes.flatMap { it.ids }.toSet()

    /** 详情卡按行取明细，措辞仍然出自 wording()。 */
    fun linesFor(id: String): List<String> = changes.filter { id in it.ids }.map { wording(it) }

    /** 列表页一次要问所有行，给它一张表而不是让调用方自己遍历。 */
    fun linesById(): Map<String, List<String>> =
        changedIds().map { id -> id to linesFor(id) }.filter { it.second.isNotEmpty() }.toMap()

    fun headline(): String? = when {
        changes.isEmpty() -> null
        changes.singleOrNull() is ScheduleChange.Emptied -> null
        else -> "这次同步有 ${changes.size} 处调整"
    }

    /** 展示用的文本只在这里生成一次，UI 不再造句。 */
    fun summary(): List<String> = listOfNotNull(headline()) + changes.map { wording(it) } + limitation()

    private fun limitation(): List<String> =
        if (trust == DiffTrust.PresenceOnly && changes.any { it is ScheduleChange.ArrangementChanged })
            listOf(table.presenceNote) else emptyList()
}

/** Null 表示本机还没有基线：调用方应当静默建立，而不是报「新增了全部课程」。 */
fun diffSchedule(previous: List<DiffRow>?, current: List<DiffRow>, table: DiffTable = ScheduleTable): ScheduleChangeReport? {
    if (previous == null) return null
    val trust = if ((previous + current).all { it.sourceId.isNotBlank() }) DiffTrust.FieldLevel else DiffTrust.PresenceOnly
    if (current.isEmpty()) {
        if (!table.emptyIsAuthoritative || previous.isEmpty()) return ScheduleChangeReport(trust, table, emptyList())
        return ScheduleChangeReport(trust, table, listOf(ScheduleChange.Emptied))
    }

    // 配对键刻意不用渲染用的派生 id：无 sourceId 时那个键含教室，换教室会被看成换了一门课。
    val key: (DiffRow) -> String = if (trust == DiffTrust.FieldLevel) { row -> row.sourceId } else { row -> row.pairKey }
    val before = previous.groupBy(key)
    val after = current.groupBy(key)
    val changes = buildList {
        before.keys.filter { it !in after }.forEach { add(ScheduleChange.Disappeared(before.getValue(it).first().label)) }
        after.keys.filter { it !in before }.forEach {
            val rows = after.getValue(it)
            add(ScheduleChange.Appeared(rows.first().label, rows.map { row -> row.id }.toSet()))
        }
        before.keys.filter { it in after }.forEach { addAll(compareBucket(before.getValue(it), after.getValue(it), table)) }
    }
    return ScheduleChangeReport(trust, table, changes)
}

/** 基线状态机的输出，调用方据此决定建基线、静默、还是挂提示。 */
sealed class ScheduleBaseline {
    /** 本机还没有基线：静默把 current 记为已读，一条都不报。 */
    data object Establish : ScheduleBaseline()

    /** 离线查看或演示模式：既不比较，也不许推进基线。 */
    data object Bypassed : ScheduleBaseline()

    data object InSync : ScheduleBaseline()

    data class Pending(val report: ScheduleChangeReport) : ScheduleBaseline()
}

/**
 * [seen] 是用户上次看过的那份表，只有在"看过"时才对齐——所以中间同步三次也只报一条累计。
 * 报告一律现算、不落盘，基线不动则变更不会丢。
 */
fun scheduleBaseline(seen: List<DiffRow>?, current: List<DiffRow>, comparable: Boolean,
                     table: DiffTable = ScheduleTable): ScheduleBaseline {
    if (!comparable) return ScheduleBaseline.Bypassed
    if (seen == null) return ScheduleBaseline.Establish
    val report = requireNotNull(diffSchedule(seen, current, table))
    return if (report.changes.isEmpty()) ScheduleBaseline.InSync else ScheduleBaseline.Pending(report)
}

fun scheduleRows(rows: List<NetworkScheduleCourse>): List<DiffRow> = rows.map { entry ->
    val course = entry.course
    val parsed = ScheduleWeeks.parse(course.weeks)
    val slot = "${course.day}|${course.startPeriod}-${course.endPeriod}"
    val teacher = ScheduleIdentity.normalize(course.teacher)
    DiffRow(
        id = course.id,
        sourceId = ScheduleIdentity.normalize(entry.sourceId),
        label = ScheduleIdentity.normalize(course.name),
        pairKey = "${ScheduleIdentity.normalize(course.name)}\u001f$teacher",
        fields = mapOf(
            "teacher" to ScheduleIdentity.normalize(course.teacher),
            "location" to ScheduleIdentity.normalize(course.location),
            "day" to weekdayLabel(course.day),
            "periods" to periodsLabel(course.startPeriod, course.endPeriod),
            "weeks" to if (parsed.valid) weeksLabel(parsed.weeks) else ScheduleIdentity.normalize(course.weeks),
        ),
        cover = if (parsed.valid) parsed.weeks.map { "$slot|$it" }.toSet() else setOf("$slot|~${course.weeks.trim()}"),
    )
}

/** 基线要落盘，但存储层不该认识各张表自己的类型，所以 DiffRow 自带序列化形状。 */
fun encodeRows(rows: List<DiffRow>): String = JSONArray().apply {
    rows.forEach { row ->
        put(JSONObject().put("id", row.id).put("sourceId", row.sourceId).put("label", row.label).put("pairKey", row.pairKey)
            .put("fields", JSONObject(row.fields as Map<*, *>)).put("cover", JSONArray(row.cover.toList())))
    }
}.toString()

/** 解析不出来就当作没有基线，绝不拿半截数据去比。 */
fun decodeRows(json: String): List<DiffRow>? = runCatching {
    val array = JSONArray(json)
    (0 until array.length()).map { index ->
        val row = array.getJSONObject(index)
        val fields = row.getJSONObject("fields")
        val cover = row.getJSONArray("cover")
        DiffRow(
            id = row.getString("id"),
            sourceId = row.getString("sourceId"),
            label = row.getString("label"),
            pairKey = row.getString("pairKey"),
            fields = fields.keys().asSequence().associateWith { fields.getString(it) },
            cover = (0 until cover.length()).map { cover.getString(it) }.toSet(),
        )
    }
}.getOrNull()

private fun compareBucket(before: List<DiffRow>, after: List<DiffRow>, table: DiffTable): List<ScheduleChange> {
    val label = after.first().label
    val ids = after.map { it.id }.toSet()
    if (before.size == 1 && after.size == 1) return fieldDiff(before.single(), after.single())
    if (before.flatMap { it.cover }.toSet() != after.flatMap { it.cover }.toSet())
        return listOf(ScheduleChange.ArrangementChanged(label, ids))
    // 时间点完全一致，只是行被重新划分了：能定位的就定位，定位不了的整桶退回「安排变了」。
    val result = mutableListOf<ScheduleChange>()
    for (field in bucketFields) {
        val from = bucketValues(before, field)
        val to = bucketValues(after, field)
        if (from == to) continue
        if (from.size == 1 && to.size == 1) result.add(ScheduleChange.Moved(label, field, from.single(), to.single(), ids))
        else return listOf(ScheduleChange.ArrangementChanged(label, ids))
    }
    if (result.isEmpty() && residualDiffers(before, after, table)) return listOf(ScheduleChange.ArrangementChanged(label, ids))
    return result
}

/** cover 与整桶字段都没管到的那些列——静默吞掉就是漏报。 */
private fun residualDiffers(before: List<DiffRow>, after: List<DiffRow>, table: DiffTable): Boolean =
    (before + after).flatMap { it.fields.keys }.distinct()
        .filter { it !in bucketFields && it !in table.coveredFields }
        .any { field -> bucketValues(before, field) != bucketValues(after, field) }

private fun bucketValues(rows: List<DiffRow>, field: String) = rows.map { fieldValue(it, field) }.filter(String::isNotBlank).toSet()

private fun fieldDiff(before: DiffRow, after: DiffRow): List<ScheduleChange> {
    val fields = (before.fields.keys + after.fields.keys).sorted()
    return buildList {
        if (before.label != after.label) add(ScheduleChange.Moved(after.label, "name", before.label, after.label, setOf(after.id)))
        fields.forEach { field ->
            val from = before.fields[field].orEmpty()
            val to = after.fields[field].orEmpty()
            if (from != to) add(ScheduleChange.Moved(after.label, field, from, to, setOf(after.id)))
        }
    }
}

private fun fieldValue(row: DiffRow, field: String) = if (field == "name") row.label else row.fields[field].orEmpty()

/** 多行桶里只能定位到"整门课"这一层，比的是与时间点无关的那几个字段。 */
private val bucketFields = listOf("name", "teacher", "location", "place")

private fun wording(change: ScheduleChange): String = when (change) {
    is ScheduleChange.Moved -> if (change.from.isBlank() || change.to.isBlank())
        "${change.label}的${fieldLabel(change.field)}有调整，请核对"
    else "${change.label} ${fieldLabel(change.field)} ${change.from} → ${change.to}"
    is ScheduleChange.Appeared -> "新增 ${change.label}"
    is ScheduleChange.Disappeared -> "${change.label} 不在这次的表里了，请核对"
    is ScheduleChange.ArrangementChanged -> "${change.label} 的安排变了，请核对"
    is ScheduleChange.Emptied -> "教务返回了 0 门课程，请核对"
}

private fun fieldLabel(field: String) = when (field) {
    "name" -> "课程名"
    "teacher" -> "教师"
    "location" -> "教室"
    "place" -> "考场"
    "time" -> "考试时间"
    "seat" -> "座位号"
    "examName" -> "考试名称"
    "day" -> "星期"
    "periods" -> "节次"
    "weeks" -> "周次"
    else -> field
}

private val weekdayNames = "一二三四五六日"

private fun weekdayLabel(day: Int) = if (day in 1..7) "周${weekdayNames[day - 1]}" else ""

private fun periodsLabel(start: Int, end: Int) = when {
    start <= 0 -> ""
    end <= start -> "${start}节"
    else -> "$start-${end}节"
}

private fun weeksLabel(weeks: Set<Int>): String {
    val sorted = weeks.sorted()
    if (sorted.isEmpty()) return ""
    val runs = mutableListOf<IntRange>()
    var first = sorted.first()
    var last = first
    sorted.drop(1).forEach { value ->
        if (value == last + 1) last = value
        else { runs.add(first..last); first = value; last = value }
    }
    runs.add(first..last)
    return runs.joinToString(",") { if (it.first == it.last) "${it.first}" else "${it.first}-${it.last}" } + "周"
}
