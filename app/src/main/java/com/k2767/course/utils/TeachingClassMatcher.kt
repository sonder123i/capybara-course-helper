package com.k2767.course.utils

import com.k2767.course.model.Course
import org.json.JSONArray
import org.json.JSONObject

/** Shared by the legacy queue and service so a manual class cannot fall back to another class. */
object TeachingClassMatcher {
    @JvmStatic
    fun matchesName(row: JSONObject, requested: String?): Boolean =
        requested.isNullOrBlank() || normalize(row.optString("jxbmc")).contains(normalize(requested))

    @JvmStatic
    fun matchesRequest(row: JSONObject, course: Course): Boolean {
        if (!course.hasTeachingClassFilter()) return true
        return matchesName(row, course.teachingClassFilter) &&
            (course.teacher.isNullOrBlank() || teacher(row).contains(course.teacher.trim(), ignoreCase = true)) &&
            (course.time.isNullOrBlank() || normalizeTime(row.optString("sksj")).contains(normalizeTime(course.time)))
    }

    @JvmStatic
    fun canUseSavedClass(course: Course): Boolean = !course.hasTeachingClassFilter()

    @JvmStatic
    fun selectRow(rows: JSONArray, course: Course): JSONObject? {
        val candidates = (0 until rows.length()).mapNotNull(rows::optJSONObject)
            .filter { matchesRequest(it, course) }
        if (course.useExactMatch && !course.classId.isNullOrBlank()) {
            candidates.firstOrNull {
                it.optString("jxb_id") == course.classId || it.optString("do_jxb_id") == course.classId
            }?.let { return it }
        }
        if (!course.teacher.isNullOrBlank()) {
            candidates.firstOrNull { teacher(it).contains(course.teacher.trim(), ignoreCase = true) }
                ?.let { return it }
        }
        return candidates.firstOrNull()
    }

    @JvmStatic
    fun teacher(row: JSONObject): String = row.optString("jsxm").ifBlank {
        row.optString("jsxx").split(';').mapNotNull { it.split('/').getOrNull(1) }.joinToString("、")
    }

    private fun normalize(value: String) = CourseNameKit.normalizeBrackets(value).trim().lowercase(java.util.Locale.ROOT)
    private fun normalizeTime(value: String) = normalize(value).replace("星期", "周")
        .replace("礼拜", "周").replace("周天", "周日").replace(Regex("\\s+"), "")
}
