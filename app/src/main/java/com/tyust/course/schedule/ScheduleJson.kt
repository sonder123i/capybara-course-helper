package com.tyust.course.schedule

import org.json.JSONArray
import org.json.JSONObject

data class NetworkScheduleCourse(val course: ScheduleCourseRecord, val sourceId: String)

/** Null means an invalid/error response; an empty list is an authoritative empty timetable. */
object ScheduleJson {
    fun parse(json: String): List<NetworkScheduleCourse>? = runCatching {
        val response = JSONObject(json)
        if (response.has("success") && !response.optBoolean("success", true)) return null
        if (response.has("code") && response.optString("code") !in setOf("0", "200", "") && !response.optBoolean("success")) return null
        if (response.optString("status").lowercase() in setOf("error", "failed", "failure")) return null
        val data = response.opt("data")
        val rows = response.optJSONArray("kbList") ?: when (data) {
            is JSONObject -> data.optJSONArray("kbList")
            is JSONArray -> data
            else -> null
        } ?: return null
        (0 until rows.length()).map { index ->
            val row = rows.getJSONObject(index)
            fun text(vararg keys: String): String = keys.firstNotNullOfOrNull {
                row.optString(it).takeIf { value -> value.isNotBlank() && value != "null" }
            }.orEmpty()
            val name = text("kcmc", "KCMC").ifBlank { "未知课程" }
            val teacher = text("xm", "XM")
            val campus = text("xqmc", "cdxqmc")
            val building = text("cdlmc", "jxlmc")
            val room = text("cdmc", "CDMC")
            val location = when {
                campus.isNotBlank() && room.isNotBlank() -> "$campus $room"
                building.isNotBlank() && room.isNotBlank() -> "$building $room"
                room.isNotBlank() -> room
                else -> text("jxcdmc", "JXCDMC")
            }
            val day = text("xqj", "XQJ").toIntOrNull() ?: 0
            val weeks = text("zcd", "ZCD")
            val periods = text("jcs", "JCS", "jcor", "JCOR").replace(',', '-').split('-')
            val start = periods.firstOrNull()?.trim()?.toIntOrNull() ?: 0
            val end = periods.lastOrNull()?.trim()?.toIntOrNull() ?: start
            val sourceId = text("schedule_source_id", "kcb_id", "kb_id", "jxb_id", "jx0404id", "kch_id")
            val id = ScheduleIdentity.network(sourceId, name, teacher, day, start, end, weeks, location)
            NetworkScheduleCourse(ScheduleCourseRecord(id, name, teacher, location, day, start, end, weeks), sourceId)
        }.distinctBy { it.course.id }
    }.getOrNull()
}
