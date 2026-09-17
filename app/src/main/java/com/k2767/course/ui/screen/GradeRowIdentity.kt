package com.k2767.course.ui.screen

/** Keep row state with the course across refreshes, including repeated attempts. */
internal fun gradeRowKeys(grades: List<GradeItemUi>): List<String> {
    val occurrences = mutableMapOf<String, Int>()
    return grades.map { grade ->
        val identity = listOf(grade.year, grade.term, grade.courseCode, grade.courseName,
            grade.jxbId, grade.teachingClass).joinToString("") { it.length.toString() + ":" + it }
        val occurrence = occurrences.getOrDefault(identity, 0)
        occurrences[identity] = occurrence + 1
        "$identity#$occurrence"
    }
}
