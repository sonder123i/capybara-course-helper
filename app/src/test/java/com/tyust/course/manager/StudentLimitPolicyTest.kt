package com.tyust.course.manager

import org.junit.Assert.*
import org.junit.Test

class StudentLimitPolicyTest {
    private fun record(school: String, id: String) =
        StudentLimitManager.BoundStudentRecord(school, "School $school", "Student $id", id)

    @Test fun aThirdStudentMayBelongToAnotherSchool() {
        val result = StudentLimitManager.evaluateBinding(listOf(record("a", "1"), record("b", "2")), "c", "Student 3", "3")
        assertTrue(result.allowed)
        assertFalse(result.alreadyBound)
        assertEquals(2, result.usedCount)
        assertTrue(result.usedNames.any { it.startsWith("School a") })
        assertTrue(result.usedNames.any { it.startsWith("School b") })
    }

    @Test fun aFourthStudentIsRejectedAcrossAllSchools() {
        val records = listOf(record("a", "1"), record("b", "2"), record("c", "3"))
        val result = StudentLimitManager.evaluateBinding(records, "d", "Student 4", "4")
        assertFalse(result.allowed)
        assertEquals(3, result.usedCount)
        assertTrue(result.reason.contains("所有学校合计最多 3 个"))
    }

    @Test fun aBoundStudentCanSignInAgainAtTheLimit() {
        val records = listOf(record("a", "1"), record("b", "2"), record("c", "3"))
        val result = StudentLimitManager.evaluateBinding(records, "b", "Updated name", "2")
        assertTrue(result.allowed)
        assertTrue(result.alreadyBound)
        assertEquals(3, result.usedCount)
    }

    @Test fun existingOverLimitRecordsRemainUsableWithoutAdmittingNewStudents() {
        val records = (1..4).map { record("school$it", "$it") }
        assertTrue(StudentLimitManager.evaluateBinding(records, "school4", "Student 4", "4").allowed)
        assertFalse(StudentLimitManager.evaluateBinding(records, "school5", "Student 5", "5").allowed)
    }

    @Test fun identicalStudentIdsAtDifferentSchoolsUseSeparatePlaces() {
        val records = listOf(record("a", "123"), record("b", "123"), record("c", "123"))
        val result = StudentLimitManager.evaluateBinding(records, "d", "Student 123", "123")
        assertEquals(3, result.usedCount)
        assertFalse(result.allowed)
    }

    @Test fun duplicateStoredRecordsDoNotUseExtraPlaces() {
        val existing = record("a", "1")
        val result = StudentLimitManager.evaluateBinding(listOf(existing, existing, existing), "b", "Student 2", "2")
        assertEquals(1, result.usedCount)
        assertTrue(result.allowed)
    }

    @Test fun missingSchoolOrIdentityCannotBeBound() {
        assertFalse(StudentLimitManager.evaluateBinding(emptyList(), "", "Student", "1").allowed)
        assertFalse(StudentLimitManager.evaluateBinding(emptyList(), "a", " ", "").allowed)
    }
}
