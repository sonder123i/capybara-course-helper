package com.tyust.course.ui.screen

import org.junit.Assert.assertEquals
import org.junit.Test

class GradeSummaryTest {
    private fun grade(credits: String, gpa: String) = GradeItemUi("课程", "85", credits, gpa, "")

    @Test fun missingSchoolGradePointsAreNotReportedAsZero() {
        assertEquals("--", semesterAverageGpa(listOf(grade("2", ""), grade("3", "--"))))
    }

    @Test fun missingGradePointsDoNotLowerTheWeightedAverage() {
        assertEquals("3.25", semesterAverageGpa(listOf(grade("1", "4"), grade("3", "3"), grade("8", ""))))
    }

    @Test fun aReportedZeroGradePointRemainsZero() {
        assertEquals("0.00", semesterAverageGpa(listOf(grade("2", "0"))))
    }
}
