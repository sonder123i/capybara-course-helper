package com.tyust.course.usage

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException

class HttpUsageTransportTest {
    @Test fun sendsExactlyAnAnonymousIdAndVersionWithoutCredentials() = runBlocking {
        MockWebServer().use { server ->
            server.enqueue(MockResponse().setBody("{\"accepted\":true,\"day\":\"2026-09-11\"}"))
            val transport = HttpUsageTransport(endpoint = server.url("/v1/usage").toString())
            assertEquals("2026-09-11", transport.report("43b531bc-96e0-463d-9e2f-86f672a1b88b", "1.0.73"))
            val request = server.takeRequest()
            val json = JSONObject(request.body.readUtf8())
            assertEquals(setOf("installationId", "version"), json.keys().asSequence().toSet())
            assertEquals("POST", request.method)
            assertNull(request.getHeader("Cookie"))
            assertNull(request.getHeader("Authorization"))
        }
    }

    @Test fun rejectsRedirectsErrorsOversizedBodiesAndFalseAcknowledgements() = runBlocking {
        MockWebServer().use { server ->
            val transport = HttpUsageTransport(endpoint = server.url("/v1/usage").toString())
            for (response in listOf(
                MockResponse().setResponseCode(302).setHeader("Location", "/unexpected"),
                MockResponse().setResponseCode(503),
                MockResponse().setBody("x".repeat(1025)),
                MockResponse().setBody("{\"accepted\":false,\"day\":\"2026-09-11\"}"),
                MockResponse().setBody("{\"accepted\":true,\"day\":\"2026-02-30\"}")
            )) {
                server.enqueue(response)
                try { transport.report("43b531bc-96e0-463d-9e2f-86f672a1b88b", "1.0.73"); fail("Invalid acknowledgement accepted") }
                catch (_: IOException) { }
            }
            assertEquals(5, server.requestCount)
        }
    }
}
