package com.tyust.course.academic.plugin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import java.util.concurrent.TimeUnit

internal object PluginLocationSearch {
    class Failure(message: String) : java.io.IOException(message)
    data class Place(val name: String, val point: PluginCoordinates.Point, val title: String, val address: String, val exact: Boolean = true)
    val USER_AGENT = "CapybaraCourse/${com.tyust.course.BuildConfig.VERSION_NAME} (https://github.com/sonder123i/capybara-course-helper)"
    private val client = OkHttpClient.Builder().callTimeout(30, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()
    private val mutex = Mutex()
    private var requestedAt = 0L
    private val cache = linkedMapOf<String, List<Place>>()

    // Search is an explicit button action, never autocomplete or background polling.
    suspend fun search(query: String): List<Place> = mutex.withLock {
        if (!com.tyust.course.SelfHostConfig.ENABLE_MAP_SERVICE) throw Failure("地图服务未启用，可用地图选点或坐标")
        val text = query.trim().take(120)
        require(text.length >= 2)
        val connection = PluginMapRequestAuth.currentPersonalAccess()
        val cacheKey = (connection?.let { PluginJson.sha256(it.toByteArray(Charsets.UTF_8)) } ?: "public") + ":" + text
        cache[cacheKey]?.let { return@withLock it }
        val wait = 1100 - (android.os.SystemClock.elapsedRealtime() - requestedAt)
        if (wait > 0) delay(wait)
        requestedAt = android.os.SystemClock.elapsedRealtime()
        val found = withContext(Dispatchers.IO) {
            val request = PluginMapRequestAuth.authorize(Request.Builder().url(searchUrl(text)).header("User-Agent", USER_AGENT), personalAccess = { connection }).build()
            client.newCall(request).execute().use { response ->
                val raw = response.body?.byteStream()?.use { String(it.readBytesBounded(128 * 1024), Charsets.UTF_8) }
                    ?: throw Failure("地址搜索未返回结果，请稍后重试")
                if (!response.isSuccessful) {
                    val message = runCatching { org.json.JSONObject(raw).optString("error") }.getOrDefault("")
                    throw Failure(if (message.startsWith("个人地图")) message.take(180)
                        else if (message.startsWith("国内搜索免费额度")) "国内搜索免费额度已用完或尚未启用，可用地图选点或坐标" else PluginMapService.searchFailure(response.code))
                }
                parseResults(raw)
            }
        }
        if (cache.size >= 16) cache.remove(cache.keys.first())
        if (found.isNotEmpty()) cache[cacheKey] = found
        found
    }

    internal fun searchUrl(query: String) = PluginMapService.SEARCH_URL.toHttpUrl().newBuilder()
        .addQueryParameter("q", query.trim().take(120)).build()

    internal fun parseResults(raw: String): List<Place> = PluginJson.objects(JSONArray(raw)).mapNotNull { entry ->
        val latitude = entry.optString("lat").toDoubleOrNull() ?: return@mapNotNull null
        val longitude = entry.optString("lon").toDoubleOrNull() ?: return@mapNotNull null
        val point = runCatching { PluginCoordinates.convert(PluginCoordinates.Point(latitude, longitude), "WGS84", "WGS84") }.getOrNull() ?: return@mapNotNull null
        val address = entry.optString("display_name").take(500)
        val title = entry.optString("name").ifBlank { address.substringBefore(',') }.take(120)
        if (title.isBlank()) null else Place(address.ifBlank { title }, point, title, address)
    }.distinctBy { Triple(it.title, it.point.latitude, it.point.longitude) }.take(6)
}
