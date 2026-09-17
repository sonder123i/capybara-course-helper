package com.k2767.course.academic

import kotlinx.coroutines.*
import okhttp3.Cookie
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.TimeUnit

class AcademicSessionReplacementTest {
    @Test fun lateResponseCannotRepopulateReplacedCookieOrReturnData() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val school = AcademicCoreTest.testSchool(server, AcademicSystem.QZ)
            val store = AcademicSessionStore()
            val old = store.session(school.id, "a", school.fullBasePath)
            val http = AcademicHttpTransport(school, old)
            server.enqueue(MockResponse().setHeadersDelay(300, TimeUnit.MILLISECONDS)
                .addHeader("Set-Cookie", "sid=obsolete; Path=/jsxsd").setBody("old result"))
            val request = async(Dispatchers.IO) { http.get(http.appUrl("index")) }
            assertNotNull(server.takeRequest(2, TimeUnit.SECONDS))
            val current = store.replace(school.id, "a", school.fullBasePath)
            val url = server.url("/jsxsd/")
            current.cookies.saveFromResponse(url, listOf(Cookie.parse(url, "sid=new; Path=/jsxsd")!!))
            try { request.await(); fail("Replaced response must be discarded") }
            catch (_: CancellationException) { }
            assertEquals("sid=new", current.cookieHeader())
            assertTrue(old.cookieHeader().isEmpty())
            assertNotSame(old, current)
        } finally { server.shutdown() }
    }

    @Test fun replacementAndLogoutRetireOnlyTheirAccount() {
        val store = AcademicSessionStore()
        val a = store.session("school", "a", "https://example.edu/")
        val b = store.session("school", "b", "https://example.edu/")
        val nextA = store.replace("school", "a", "https://example.edu/")
        assertTrue(a.retired)
        assertFalse(b.retired)
        assertFalse(nextA.retired)
        store.invalidate("school", "a")
        assertTrue(nextA.retired)
        assertFalse(store.session("school", "a", "https://example.edu/").retired)
    }
}
