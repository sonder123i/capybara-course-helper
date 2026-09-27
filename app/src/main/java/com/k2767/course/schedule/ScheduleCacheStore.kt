package com.k2767.course.schedule

import android.content.SharedPreferences
import com.k2767.course.academic.AcademicStudyAdapter
import com.k2767.course.academic.AcademicStudyBridge
import com.k2767.course.academic.AcademicStudyParser
import com.k2767.course.academic.AcademicStudyReader
import com.k2767.course.academic.AcademicTerm
import org.json.JSONObject

internal data class CachedSchedule(
    val currentTerm: AcademicTerm,
    val term: AcademicTerm,
    val json: String,
    val fromCache: Boolean
)

/** Account/term data survives login sessions; only an explicit sync bypasses a valid cache. */
internal class ScheduleCacheStore(
    private val preferences: SharedPreferences,
    private val calendarTerm: () -> AcademicTerm = { AcademicStudyReader.calendarTerm() }
) {
    private fun prefix(account: String, school: String) = "schedule_${account}_${school}"

    fun currentTerm(account: String, school: String): AcademicTerm {
        val calendar = calendarTerm()
        val known = runCatching {
            JSONObject(preferences.getString("${prefix(account, school)}_current", null) ?: return@runCatching null)
        }.getOrNull()
        // Resolve the school's term again when the local academic half-year changes.
        // A school can legitimately start later than the calendar fallback.
        return known?.takeIf { it.optString("calendar") == calendar.id }
            ?.optString("term")?.let(AcademicStudyParser::term) ?: calendar
    }

    fun read(account: String, school: String, term: AcademicTerm): String? {
        val base = prefix(account, school)
        val keys = buildList {
            add("${base}_${term.id}")
            if (term.semester in 1..2) add("${base}_${term.year}_${if (term.semester == 1) 3 else 12}")
        }
        return keys.firstNotNullOfOrNull { key ->
            runCatching { preferences.getString(key, null) }.getOrNull()
                ?.takeIf { ScheduleJson.parse(it) != null }
        }
    }

    fun selected(account: String, school: String, nextSemester: Boolean): CachedSchedule? {
        val current = currentTerm(account, school)
        val term = if (nextSemester) current.next() else current
        return read(account, school, term)?.let { CachedSchedule(current, term, it, true) }
    }

    suspend fun load(
        account: String,
        school: String,
        nextSemester: Boolean,
        forceRefresh: Boolean,
        reader: () -> AcademicStudyAdapter
    ): CachedSchedule {
        if (!forceRefresh) selected(account, school, nextSemester)?.let { return it }
        val remote = reader()
        val current = remote.catalog().currentTerm
        val term = if (nextSemester) current.next() else current
        // Existing installations may have data but no persisted current-term metadata yet.
        if (!forceRefresh) read(account, school, term)?.let { return CachedSchedule(current, term, it, true) }
        return CachedSchedule(current, term, AcademicStudyBridge.scheduleJson(remote.schedule(term)), false)
    }

    fun save(account: String, school: String, schedule: CachedSchedule) {
        require(ScheduleJson.parse(schedule.json) != null) { "Invalid timetable must not replace the cache" }
        val base = prefix(account, school)
        preferences.edit()
            .putString("${base}_${schedule.term.id}", schedule.json)
            .putLong("${base}_${schedule.term.id}_time", System.currentTimeMillis())
            .putString("${base}_current", JSONObject().put("term", schedule.currentTerm.id)
                .put("calendar", calendarTerm().id).toString())
            .apply()
    }

    /**
     * 「用户上次看过的那份课表」。它与课表本身分开存放：课表可以随每次同步前进，
     * 这条基线只在用户看过时前进，未读期间的多次同步才会累计成一条变更。
     */
    fun seenJson(account: String, school: String, term: AcademicTerm): String? =
        runCatching { preferences.getString("${prefix(account, school)}_${term.id}_seen", null) }.getOrNull()
            ?.takeIf { ScheduleJson.parse(it) != null }

    fun markSeen(account: String, school: String, term: AcademicTerm, json: String) {
        require(ScheduleJson.parse(json) != null) { "A failed sync must not become the seen baseline" }
        val base = "${prefix(account, school)}_${term.id}"
        preferences.edit().putString("${base}_seen", json).putLong("${base}_seen_time", System.currentTimeMillis()).apply()
    }

    /** 比较方向要的是可配对的行；推进方向要的才是原始 json。 */
    fun seenRows(account: String, school: String, term: AcademicTerm): List<DiffRow>? =
        seenJson(account, school, term)?.let { scheduleRows(requireNotNull(ScheduleJson.parse(it))) }

    /**
     * 考试安排的「上次看过」。与课表基线分键存放——同一门课的考试换了考场，
     * 不该被读成课表变了，反之也一样。
     */
    fun examSeenRows(account: String, school: String, term: AcademicTerm): List<DiffRow>? =
        runCatching { preferences.getString(examSeenKey(account, school, term), null) }.getOrNull()
            ?.let { decodeRows(it) }

    fun markExamSeen(account: String, school: String, term: AcademicTerm, rows: List<DiffRow>) {
        val encoded = encodeRows(rows)
        require(decodeRows(encoded) != null) { "An unparsable exam snapshot must not become the seen baseline" }
        preferences.edit().putString(examSeenKey(account, school, term), encoded)
            .putLong("${examSeenKey(account, school, term)}_time", System.currentTimeMillis()).apply()
    }

    private fun examSeenKey(account: String, school: String, term: AcademicTerm) =
        "exam_seen_${account}_${school}_${term.id}"
}
