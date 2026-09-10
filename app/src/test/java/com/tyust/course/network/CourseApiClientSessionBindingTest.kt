package com.tyust.course.network

import com.tyust.course.manager.SessionStateStore
import com.tyust.course.manager.UserManager
import com.tyust.course.model.SchoolConfig
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicReference

class CourseApiClientSessionBindingTest {
    @Test fun aRequestStartedForAnOldSessionCannotBorrowTheReplacement() = withServer { api, sessions, school, server ->
        val old = sessions.replace("session-binding-test")
        server.enqueue(MockResponse().setBody("current response"))
        val result = api.runWithSession(old) {
            sessions.replace(old.accountStorageKey)
            api.fetchPageHiddenParamsSync(school)
        }
        assertNull(result)
        assertEquals(0, server.requestCount)
        assertEquals("current response", api.runWithSession(sessions.token) { api.fetchPageHiddenParamsSync(school) })
        assertEquals(1, server.requestCount)
    }

    @Test fun aCallbackFollowUpKeepsItsOriginalSessionAfterCookieReplacement() = withServer { api, sessions, school, server ->
        val old = sessions.replace("session-binding-test")
        server.enqueue(MockResponse().setBody("first response"))
        server.enqueue(MockResponse().setBody("must not be fetched by the old callback"))
        val done = CountDownLatch(1)
        val followUp = AtomicReference<String?>()
        val error = AtomicReference<Throwable?>()
        val call = api.runWithSession(old) {
            api.validateCookie(school, object : Callback {
                override fun onFailure(call: Call, e: IOException) { error.set(e); done.countDown() }
                override fun onResponse(call: Call, response: Response) {
                    try {
                        response.close()
                        sessions.replace(old.accountStorageKey)
                        followUp.set(api.fetchPageHiddenParamsSync(school))
                    } catch (failure: Throwable) { error.set(failure) }
                    finally { done.countDown() }
                }
            })
        }
        try {
            assertTrue("The first callback must complete", done.await(5, TimeUnit.SECONDS))
            assertNull(error.get())
            assertNull(followUp.get())
            assertEquals(1, server.requestCount)
        } finally { call.cancel() }
    }

    private fun withServer(block: (CourseApiClient, SessionStateStore, SchoolConfig, MockWebServer) -> Unit) {
        val server = MockWebServer()
        val sessions = UserManager.getInstance().sessionState
        val originalAccount = sessions.token.accountStorageKey
        server.start()
        try {
            val url = server.url("/")
            val school = SchoolConfig("session-test", "Session test", "${url.host}:${url.port}", "http")
            block(CourseApiClient.getInstance(), sessions, school, server)
        } finally {
            sessions.replace(originalAccount)
            server.shutdown()
        }
    }
}
