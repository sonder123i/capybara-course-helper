package com.tyust.course.academic

data class AcademicTerm(val id: String, val name: String = id) {
    val year: Int get() = id.substringBefore('-').toInt()
    val semester: Int get() = id.substringAfterLast('-').toInt()
    fun next(): AcademicTerm = if (semester == 1) AcademicTerm("$year-${year + 1}-2")
        else AcademicTerm("${year + 1}-${year + 2}-1")
}

data class AcademicStudyCatalog(val terms: List<AcademicTerm>, val currentTerm: AcademicTerm)

data class AcademicScheduleEntry(
    val name: String, val teacher: String, val location: String,
    val day: Int, val startPeriod: Int, val endPeriod: Int, val weeks: String
)

data class AcademicGrade(
    val name: String, val score: String, val credits: String, val gradePoint: String,
    val type: String = "", val term: String = "", val code: String = "",
    val college: String = "", val sectionId: String = "", val detail: String = ""
)

data class AcademicGradeReport(
    val grades: List<AcademicGrade>, val gradePointAverage: String = "", val totalCredits: String = ""
)

data class AcademicExam(
    val name: String, val time: String, val location: String,
    val seat: String = "", val examName: String = "", val teacher: String = ""
)

interface AcademicStudyAdapter {
    suspend fun catalog(): AcademicStudyCatalog
    suspend fun schedule(term: AcademicTerm): List<AcademicScheduleEntry>
    suspend fun grades(term: AcademicTerm? = null): AcademicGradeReport
    suspend fun exams(term: AcademicTerm): List<AcademicExam>
}
