package com.tyust.course.academic.plugin

import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import com.amap.api.maps.MapsInitializer
import com.tyust.course.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest
import java.util.concurrent.TimeUnit

internal object PluginAmap {
    const val PRIVACY_URL = "https://lbs.amap.com/pages/privacy/"
    private val configMutex = Mutex()
    private var configuration: PluginAmapConfiguration? = null
    private var initializedKey: String? = null
    private val client = OkHttpClient.Builder().callTimeout(10, TimeUnit.SECONDS)
        .followRedirects(false).followSslRedirects(false).build()

    fun hasConsent(context: Context) = context.getSharedPreferences("amap-privacy", Context.MODE_PRIVATE).getBoolean("consent-v1", false)
    fun consent(context: Context) { context.getSharedPreferences("amap-privacy", Context.MODE_PRIVATE).edit().putBoolean("consent-v1", true).apply() }

    suspend fun configuration(context: Context): PluginAmapConfiguration = configMutex.withLock {
        if (!com.tyust.course.SelfHostConfig.ENABLE_MAP_SERVICE) throw IOException("地图服务未启用")
        val now = System.currentTimeMillis() / 1000
        configuration?.takeIf { it.expiresAt > now + 30 }?.let { return@withLock it }
        // Debug/preview credentials are a local developer setting, never a production fallback.
        if (BuildConfig.DEBUG && BuildConfig.AMAP_ANDROID_SDK_KEY.matches(Regex("[a-fA-F0-9]{32}"))) {
            return@withLock PluginAmapConfiguration(BuildConfig.AMAP_ANDROID_SDK_KEY, now + 3600)
        }
        if (BuildConfig.MAP_CONFIG_CLIENT_SECRET.isEmpty()) throw IOException("此版本尚未配置高德授权，可继续使用基础地图")
        withContext(Dispatchers.IO) {
            val exchange = PluginMapConfigCrypto.create(BuildConfig.MAP_CONFIG_CLIENT_SECRET, context.packageName, BuildConfig.VERSION_CODE, signingCertificate(context))
            val request = Request.Builder().url(PluginMapService.ORIGIN + "/api/maps/v1/client-config")
                .header("User-Agent", PluginLocationSearch.USER_AGENT)
                .post(exchange.body.toString().toRequestBody("application/json".toMediaType())).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) throw IOException(if (response.code == 401) "高德授权验证失败，请检查设备时间并使用正式版本或更新 App" else "高德配置暂时不可用，可重试或使用基础地图")
                val bytes = response.body?.byteStream()?.use { it.readBytesBounded(16 * 1024) } ?: throw IOException("地图配置未返回结果")
                exchange.open(JSONObject(String(bytes, Charsets.UTF_8))).also { configuration = it }
            }
        }
    }

    @Synchronized fun initialize(context: Context, key: String) {
        check(hasConsent(context)) { "请先同意高德地图隐私说明" }
        if (initializedKey == key) return
        val app = context.applicationContext
        MapsInitializer.updatePrivacyShow(app, true, true)
        MapsInitializer.updatePrivacyAgree(app, true)
        MapsInitializer.setApiKey(key)
        MapsInitializer.setProtocol(MapsInitializer.HTTPS)
        MapsInitializer.initialize(app)
        initializedKey = key
    }

    @Suppress("DEPRECATION")
    internal fun signingCertificate(context: Context): String {
        val flags = if (Build.VERSION.SDK_INT >= 28) PackageManager.GET_SIGNING_CERTIFICATES else PackageManager.GET_SIGNATURES
        val info = context.packageManager.getPackageInfo(context.packageName, flags)
        val signatures = if (Build.VERSION.SDK_INT >= 28) info.signingInfo?.apkContentsSigners else info.signatures
        val bytes = signatures?.singleOrNull()?.toByteArray() ?: throw IOException("无法验证应用签名")
        return MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    }
}
