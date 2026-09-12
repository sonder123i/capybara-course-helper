package com.tyust.course.usage

import kotlinx.coroutines.suspendCancellableCoroutine
import okhttp3.Call
import okhttp3.Callback
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

internal class HttpUsageTransport(
    private val client: OkHttpClient = OkHttpClient.Builder()
        .callTimeout(15, TimeUnit.SECONDS).followRedirects(false).followSslRedirects(false).build(),
    private val endpoint: String = "https://usage.hidisiwa.xyz/v1/usage"
) : UsageTransport {
    override suspend fun report(installationId: String, version: String): String = suspendCancellableCoroutine { continuation ->
        // This client has no school cookies, account headers, interceptors or persisted request queue.
        val body = JSONObject().put("installationId", installationId).put("version", version)
            .toString().toRequestBody("application/json; charset=utf-8".toMediaType())
        val call = client.newCall(Request.Builder().url(endpoint).post(body).build())
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWithException(e)
            }
            override fun onResponse(call: Call, response: Response) {
                val result = runCatching {
                    response.use {
                        if (it.code != 200) throw IOException("Usage acknowledgement unavailable")
                        // Bound the acknowledgement too; an error page must never become a saved day.
                        val source = it.body?.source() ?: throw IOException("Missing acknowledgement")
                        if (source.request(1025)) throw IOException("Acknowledgement too large")
                        val data = JSONObject(source.readUtf8())
                        val day = data.optString("day")
                        if (!data.optBoolean("accepted") || !UsageDay.isValid(day)) throw IOException("Invalid acknowledgement")
                        day
                    }
                }
                if (continuation.isActive) result.fold(continuation::resume, continuation::resumeWithException)
            }
        })
    }
}
