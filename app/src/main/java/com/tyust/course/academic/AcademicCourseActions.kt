package com.tyust.course.academic

import com.tyust.course.model.Course
import kotlinx.coroutines.ensureActive

/** UI identities include the scope because different rounds can reuse course and class IDs. */
fun Course.academicSelectionKey(): String = listOf(completeParams["academic_scope_id"].orEmpty(),
    completeParams["academic_course_id"].orEmpty().ifBlank { courseId.orEmpty() }, classId.orEmpty()).joinToString("|") { "${it.length}:$it" }

fun Course.catalogSelectionKey(): String = if (completeParams["academic_system"].isNullOrBlank()) classId.orEmpty() else academicSelectionKey()
fun Course.catalogGroupKey(): String = if (completeParams["academic_system"].isNullOrBlank()) courseId.orEmpty()
    else listOf(completeParams["academic_scope_id"].orEmpty(), courseId.orEmpty(), name.orEmpty()).joinToString("|") { "${it.length}:$it" }

data class AcademicCourseFilter(
    val teacher: String = "", val time: String = "", val location: String = "", val credit: String = "", val availableOnly: Boolean = false
) {
    val active: Boolean get() = teacher.isNotBlank() || time.isNotBlank() || location.isNotBlank() || credit.isNotBlank() || availableOnly
    fun matches(course: Course): Boolean =
        (teacher.isBlank() || course.teacher.contains(teacher.trim(), true)) &&
        (time.isBlank() || course.time.contains(time.trim(), true)) &&
        (location.isBlank() || course.location.contains(location.trim(), true)) &&
        (credit.isBlank() || (credit.toDoubleOrNull() != null && course.credit.toDoubleOrNull() == credit.toDoubleOrNull())) &&
        (!availableOnly || (course.completeParams["academic_capacity_known"] == "true" &&
            course.completeParams["academic_selected_known"] == "true" && course.capacity > course.selected))
}

internal data class AcademicBatchResult(val succeeded: Int, val attempted: Int, val stopped: Boolean, val message: String)

/** A batch is an ordered set of individually confirmed selections, never a blind form replay. */
internal suspend fun runAcademicBatch(courses: List<Course>, select: suspend (Course) -> SelectionResult): AcademicBatchResult {
    var success = 0
    var attempts = 0
    for (course in courses.distinctBy { it.academicSelectionKey() }) {
        kotlinx.coroutines.currentCoroutineContext().ensureActive()
        val result = try { select(course) } catch (e: AcademicException) { SelectionResult(e.status, e.message.orEmpty()) }
        attempts++
        if (result.status in setOf(AcademicStatus.SUCCESS, AcademicStatus.ALREADY_SELECTED)) success++
        if (result.status.blocksFurtherSelections())
            return AcademicBatchResult(success, attempts, true, result.message.ifBlank { "需处理学校返回的状态后继续" })
    }
    return AcademicBatchResult(success, attempts, false, "")
}

internal fun AcademicStatus.blocksFurtherSelections(): Boolean = this in setOf(
    AcademicStatus.SESSION_EXPIRED, AcademicStatus.INVALID_CREDENTIALS, AcademicStatus.CAPTCHA_REQUIRED,
    AcademicStatus.HUMAN_VERIFICATION_REQUIRED, AcademicStatus.RESULT_UNKNOWN, AcademicStatus.UNTRUSTED_URL, AcademicStatus.PAGE_CHANGED
)
