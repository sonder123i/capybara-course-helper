package com.k2767.course.academic

import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.assertTrue
import org.junit.Test

class AcademicRequestPacingTest {
    @Test fun concurrentReadersShareTheSessionRequestInterval() = runBlocking {
        val session = AcademicSession(AcademicSessionKey("fixture", "account"), "https://school.test/")
        try {
            val starts = (1..3).map { async { session.paceRequest(40); System.nanoTime() } }.awaitAll().sorted()
            assertTrue(starts.zipWithNext().all { (previous, next) -> next - previous >= 35_000_000 })
        } finally { session.retire() }
    }

    @Test fun anUnpacedSchoolIsNotHeldBack() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val school = AcademicCoreTest.testSchool(server, AcademicSystem.ZF)
            val session = AcademicSessionStore().session(school.id, "a", school.fullBasePath)
            val http = AcademicHttpTransport(school, session)
            repeat(3) { server.enqueue(MockResponse().setBody("ok")) }
            val started = System.nanoTime()
            repeat(3) { http.get(http.appUrl("form")) }
            assertTrue("Default pacing must not slow a school down",
                System.nanoTime() - started < 1_000_000_000)
        } finally { server.shutdown() }
    }

    @Test fun theTransportPacesEveryRequestForAPacedSchool() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val school = AcademicCoreTest.testSchool(server, AcademicSystem.ZF).apply { requestPacingMillis = 60 }
            val session = AcademicSessionStore().session(school.id, "a", school.fullBasePath)
            val http = AcademicHttpTransport(school, session)
            repeat(3) { server.enqueue(MockResponse().setBody("ok")) }
            val started = System.nanoTime()
            repeat(3) { http.get(http.appUrl("form")) }
            assertTrue("Three requests at 60ms apart cannot finish under 100ms",
                System.nanoTime() - started >= 100_000_000)
        } finally { server.shutdown() }
    }
}
