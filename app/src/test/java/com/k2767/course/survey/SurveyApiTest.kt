package com.k2767.course.survey

import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.util.UUID

class SurveyApiTest {
    @Test fun publicRequestsSendOnlyTheSchoolHostAndPreserveLinkParameters() = runBlocking {
        MockWebServer().use { server ->
            val survey = JSONObject().put("id", "survey-1").put("title", "校园调查").put("url", "https://wjx.cn/vm/a?q=x%2By#end")
            server.enqueue(MockResponse().setBody(JSONObject().put("serverTime", "2026-09-15T12:00:00.000Z").put("surveys", org.json.JSONArray().put(survey)).toString()))
            val api = SurveyApi(server.url("/").toString().trimEnd('/'))
            assertEquals("https://wjx.cn/vm/a?q=x%2By#end", api.list("jw.example.edu.cn").surveys.single().url)
            val request = server.takeRequest()
            assertEquals("jw.example.edu.cn", request.requestUrl!!.queryParameter("schoolHost"))
            assertEquals("all", request.requestUrl!!.queryParameter("status"))
            assertNull(request.getHeader("Cookie")); assertNull(request.getHeader("Authorization"))
        }
    }
    @Test fun clicksHaveNoStableDeviceOrAccountIdentifierAndUnavailableDetailsFailClosed() = runBlocking {
        MockWebServer().use { server ->
            val api = SurveyApi(server.url("/").toString().trimEnd('/')); val id = UUID.randomUUID().toString()
            server.enqueue(MockResponse().setBody("{\"ok\":true,\"counted\":true}"))
            api.click("survey-1", "jw.example.edu.cn", id)
            val body = JSONObject(server.takeRequest().body.readUtf8())
            assertEquals(setOf("consent", "requestId", "schoolHost"), body.keys().asSequence().toSet())
            assertEquals(id, body.getString("requestId")); assertTrue(body.getBoolean("consent"))
            server.enqueue(MockResponse().setResponseCode(404).setBody("{}"))
            try { api.detail("survey-1", "jw.example.edu.cn"); fail("invisible survey opened") } catch (_: SurveyUnavailableException) { }
        }
    }
}
