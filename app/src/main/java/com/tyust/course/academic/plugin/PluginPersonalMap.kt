package com.tyust.course.academic.plugin

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.crypto.SecretKey

/** The host stores only a scoped connection code, never the user's Web service key. */
internal class PluginPersonalMapStore(context: Context, keyProvider: (() -> SecretKey)? = null) {
    private val vault = NativePluginVault(context.applicationContext, "host:personal-map:v1", keyProvider = keyProvider)
    fun read(): String? = try {
        vault.get("connection")?.getString("token")?.also {
            if (!valid(it)) throw IOException("个人地图连接需要重新配置")
        }
    } catch (_: Exception) { throw IOException("个人地图连接无法恢复，请重新连接或切换公共服务") }

    fun save(token: String) {
        require(valid(token)) { "请填写插件站生成的个人地图连接码" }
        vault.put("connection", JSONObject().put("token", token))
        check(read() == token) { "个人地图连接保存失败" }
    }

    fun clear() {
        vault.remove("connection")
        check(read() == null) { "个人地图连接移除失败" }
    }

    companion object {
        const val HEADER = "X-Zhengfang-Map-Personal"
        const val MANAGE_URL = "https://plugins.hidisiwa.xyz/maps/personal"
        fun valid(token: String) = token.matches(Regex("zfmap1_[A-Za-z0-9_-]{43}"))
    }
}

internal object PluginPersonalMapClient {
    private val client = OkHttpClient.Builder().callTimeout(12, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false).build()

    suspend fun status(token: String): JSONObject = withContext(Dispatchers.IO) {
        if (!com.tyust.course.SelfHostConfig.ENABLE_MAP_SERVICE) throw IOException("地图服务未启用")
        require(PluginPersonalMapStore.valid(token)) { "请填写插件站生成的个人地图连接码，不是高德 Key 原值" }
        val request = Request.Builder().url(PluginMapService.ORIGIN + "/api/maps/v1/personal-status")
            .header(PluginPersonalMapStore.HEADER, token).header("User-Agent", PluginLocationSearch.USER_AGENT).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException(if (response.code == 401) "连接码已失效，请在插件站重新生成" else "个人地图服务暂时不可用，请稍后重试")
            val raw = response.body?.byteStream()?.use { String(it.readBytesBounded(32 * 1024), Charsets.UTF_8) }
                ?: throw IOException("个人地图服务未返回状态")
            JSONObject(raw).also { if (!it.optBoolean("connected")) throw IOException("个人地图连接尚未就绪") }
        }
    }
}
