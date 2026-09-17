package com.k2767.course.network

import com.k2767.course.manager.SessionStateStore
import com.k2767.course.manager.SessionToken
import okhttp3.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.concurrent.TimeUnit

class SessionBoundCallbackTest {
    @Test fun lateResponseAndFailureCannotReachTheNewSessionEvenAfterReturningToTheAccount() {
        val sessions = SessionStateStore()
        val token = sessions.replace("a")
        val server = MockWebServer()
        server.start()
        val client = OkHttpClient()
        try {
            server.enqueue(MockResponse().setBody("obsolete"))
            val call = client.newCall(Request.Builder().url(server.url("/"))
                .tag(SessionToken::class.java, token).build())
            var responses = 0
            var failures = 0
            val callback = SessionBoundCallback(sessions, object : Callback {
                override fun onFailure(call: Call, e: IOException) { failures++ }
                override fun onResponse(call: Call, response: Response) { responses++; response.close() }
            })
            val response = call.execute()
            assertNotNull(server.takeRequest(1, TimeUnit.SECONDS))
            sessions.replace("b")
            sessions.replace("a")
            callback.onResponse(call, response)
            callback.onFailure(call, IOException("late disconnect"))
            assertEquals(0, responses)
            assertEquals(0, failures)
            try { response.body!!.string(); fail("Obsolete body must be closed") }
            catch (_: IllegalStateException) { }
        } finally {
            client.connectionPool.evictAll()
            client.dispatcher.executorService.shutdown()
            server.shutdown()
        }
    }

    @Test fun currentFailureStillReachesTheCaller() {
        val sessions = SessionStateStore()
        val token = sessions.replace("a")
        val call = OkHttpClient().newCall(Request.Builder().url("https://example.invalid/")
            .tag(SessionToken::class.java, token).build())
        var failures = 0
        SessionBoundCallback(sessions, object : Callback {
            override fun onFailure(call: Call, e: IOException) { failures++ }
            override fun onResponse(call: Call, response: Response) { fail("No network request expected") }
        }).onFailure(call, IOException("offline"))
        assertEquals(1, failures)
    }
}
