package com.tyust.course.manager

import org.junit.Assert.*
import org.junit.Test

class SessionRequestGateTest {
    @Test fun anOlderLoadCannotCompleteTheNewerSpinner() {
        val sessions = SessionStateStore(); val gate = SessionRequestGate(sessions)
        val first = gate.begin("grades"); val second = gate.begin("grades")
        assertFalse(gate.isCurrent(first)); assertTrue(gate.isCurrent(second))
    }
    @Test fun independentSemestersCanFinishIntoTheirOwnCaches() {
        val sessions = SessionStateStore(); val gate = SessionRequestGate(sessions)
        val a = gate.begin("semester:1"); val b = gate.begin("semester:2")
        assertTrue(gate.isCurrent(a)); assertTrue(gate.isCurrent(b))
        sessions.replace("A")
        assertFalse(gate.isCurrent(a)); assertFalse(gate.isCurrent(b))
    }
    @Test fun sameAccountWithANewGenerationRejectsOldWork() {
        val sessions = SessionStateStore().apply { replace("A") }; val gate = SessionRequestGate(sessions)
        val request = gate.begin("grades")
        sessions.replace("B"); sessions.replace("A")
        assertFalse(gate.isCurrent(request))
    }
}
