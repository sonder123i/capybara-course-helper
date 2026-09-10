package com.tyust.course.ui.screen

import org.junit.Assert.*
import org.junit.Test

class GradeRowIdentityTest {
    private val maths = GradeItemUi("高等数学", "80", "4", "3.0", "必修", year = "2025", term = "1", courseCode = "MATH")
    private val english = maths.copy(courseName = "大学英语", courseCode = "ENGLISH")

    @Test fun insertingAndReorderingCoursesKeepsExistingRowState() {
        val before = gradeRowKeys(listOf(maths, english))
        val after = gradeRowKeys(listOf(english, maths.copy(courseCode = "NEW"), maths))
        assertEquals(before[0], after[2])
        assertEquals(before[1], after[0])
    }

    @Test fun scoreAndDetailRefreshDoesNotReplaceTheRowIdentity() {
        val refreshed = maths.copy(grade = "95", gpa = "4.0", detail = "平时: 90; 期末: 98")
        assertEquals(gradeRowKeys(listOf(maths)), gradeRowKeys(listOf(refreshed)))
    }

    @Test fun retakesAndDuplicateRecordsHaveDistinctKeys() {
        val keys = gradeRowKeys(listOf(maths, maths.copy(term = "2"), maths))
        assertEquals(keys.size, keys.distinct().size)
    }
}
