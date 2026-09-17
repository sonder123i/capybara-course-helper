package com.k2767.course.survey

import com.k2767.course.network.SchoolServiceEndpoints
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit

/** Deliberately independent of academic clients, interceptors and CookieManager. */
class SurveyApi(private val baseUrl: String = BASE_URL) : SurveyTransport {
    private val client = OkHttpClient.Builder().connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS).callTimeout(20, TimeUnit.SECONDS).build()

    private fun url(path: String, schoolHost: String) = (baseUrl + path).toHttpUrl().newBuilder()
        .apply { if (schoolHost.isNotBlank()) addQueryParameter("schoolHost", schoolHost) }

    private fun execute(request: Request): JSONObject = client.newCall(request).execute().use { response ->
        if (response.code == 404 || response.code == 409) throw SurveyUnavailableException("问卷已结束、下架或不在当前学校的投放范围内")
        if (!response.isSuccessful) throw IOException("Survey HTTP ${response.code}")
        JSONObject(response.body?.string() ?: throw IOException("Empty survey response"))
    }

    override suspend fun list(schoolHost: String): SurveyFeedResult = withContext(Dispatchers.IO) {
        val json = execute(Request.Builder().url(url("/v1/surveys", schoolHost).addQueryParameter("status", "all").build()).build())
        val items = json.getJSONArray("surveys")
        SurveyFeedResult(List(items.length()) { SurveyJson.fromApi(items.getJSONObject(it)) },
            parseSurveyTime(json.getString("serverTime")) ?: throw IOException("Missing survey server time"))
    }

    override suspend fun detail(id: String, schoolHost: String): SurveyDetailResult = withContext(Dispatchers.IO) {
        require(id.matches(Regex("[A-Za-z0-9-]{1,100}")))
        val json = execute(Request.Builder().url(url("/v1/surveys/$id", schoolHost).build()).build())
        SurveyDetailResult(SurveyJson.fromApi(json.getJSONObject("survey")),
            parseSurveyTime(json.getString("serverTime")) ?: throw IOException("Missing survey server time"))
    }

    override suspend fun click(id: String, schoolHost: String, requestId: String) = withContext(Dispatchers.IO) {
        require(id.matches(Regex("[A-Za-z0-9-]{1,100}")))
        val json = JSONObject().put("consent", true).put("requestId", requestId).put("schoolHost", schoolHost)
        execute(Request.Builder().url((baseUrl + "/v1/surveys/$id/clicks").toHttpUrl())
            .post(json.toString().toRequestBody("application/json; charset=utf-8".toMediaType())).build())
        Unit
    }

    companion object { const val BASE_URL = SchoolServiceEndpoints.BASE_URL }
}
