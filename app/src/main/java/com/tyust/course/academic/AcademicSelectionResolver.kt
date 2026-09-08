package com.tyust.course.academic

internal data class ResolvedAcademicSelection(val course: CourseOffer, val section: CourseSection)

/** Resolves an exact section from fresh rows, including old Zhengfang popup class lists. */
internal suspend fun AcademicProtocolAdapter.resolveSelection(
    context: CourseContext,
    courseId: String,
    sectionId: String,
    courseName: String,
    teacher: String = "",
    time: String = "",
    scopeId: String = ""
): ResolvedAcademicSelection? {
    return resolveCandidates(context, courseId, sectionId, courseName, teacher, time, scopeId).singleOrNull()
}

internal suspend fun AcademicProtocolAdapter.resolveCandidates(
    context: CourseContext, courseId: String, sectionId: String, courseName: String,
    teacher: String = "", time: String = "", scopeId: String = ""
): List<ResolvedAcademicSelection> {
    if (scopeId.isNotBlank() && context.scopes.none { it.id == scopeId })
        throw AcademicException(AcademicStatus.ROUND_CLOSED, "目标课程所在轮次尚未开放或已经结束")
    val offers = listCourses(context, CourseQuery(courseName, scopeId = scopeId)).filter {
        (scopeId.isBlank() || it.scopeId == scopeId) &&
            if (courseId.isNotBlank()) it.stableId == courseId else it.name == courseName
    }
    if (offers.map { it.scopeId to it.stableId }.distinct().size > 1)
        throw AcademicException(AcademicStatus.PAGE_CHANGED, "存在同名课程，请从课程列表指定目标课程和轮次")
    val matches = mutableListOf<ResolvedAcademicSelection>()
    for (offer in offers) {
        val rowSection = offer.raw["jx0404id"].orEmpty().ifBlank { offer.raw["sectionId"].orEmpty() }
        if (sectionId.isNotBlank() && rowSection.isNotBlank() && rowSection != sectionId && offer.raw["popupUrl"].isNullOrBlank()) continue
        for (section in listSections(offer)) {
            val exact = if (sectionId.isNotBlank()) section.stableId == sectionId
                else (teacher.isBlank() || section.teacher.ifBlank { offer.teacher }.contains(teacher, true)) &&
                    (time.isBlank() || section.time.ifBlank { offer.time }.contains(time, true))
            if (exact) matches += ResolvedAcademicSelection(offer, section)
        }
    }
    return matches.distinctBy { Triple(it.course.scopeId, it.course.stableId, it.section.stableId) }
}
