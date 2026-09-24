package com.k2767.course.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.util.Log
import androidx.core.content.FileProvider
import okhttp3.Call
import okhttp3.Callback
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.Response
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * 应用更新管理器
 * 负责检查更新、下载APK、安装APK
 */
class UpdateManager(private val context: Context) {
    
    companion object {
        private const val TAG = "UpdateManager"
        
        // 自用：版本信息挂在自己的 GitHub Pages 上，不再指向上游仓库
        private const val VERSION_URL = "https://sonder123i.github.io/capybara-course-helper/version.json"
        
        // 下载文件名
        private const val APK_FILE_NAME = "zhengfang_update.apk"
        
        @Volatile
        private var instance: UpdateManager? = null
        
        fun getInstance(context: Context): UpdateManager {
            return instance ?: synchronized(this) {
                instance ?: UpdateManager(context.applicationContext).also { instance = it }
            }
        }
    }
    
    // 检查版本与下载安装包共用。读超时 60 秒：下载时「连接还在但不吐数据」就靠它抛错，
    // 这是替代系统下载器之后唯一的停滞保护。
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(60, java.util.concurrent.TimeUnit.SECONDS)
        .build()

    /** 下载纯逻辑：不碰 Android，便于用 MockWebServer 单测。 */
    private val downloader = ApkDownloader(client)

    /**
     * 更新信息数据类
     */
    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val releaseNotes: String,
        val downloadUrl: String,
        val forceUpdate: Boolean,
        /** 备用下载地址（另一个下载源）。version.json 没写就是空串。 */
        val mirrorUrl: String = ""
    )
    
    /**
     * 获取本地版本信息
     */
    private fun getPackageInfo(): PackageInfo? {
        return try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                context.packageManager.getPackageInfo(
                    context.packageName,
                    android.content.pm.PackageManager.PackageInfoFlags.of(0)
                )
            } else {
                @Suppress("DEPRECATION")
                context.packageManager.getPackageInfo(context.packageName, 0)
            }
        } catch (e: Exception) {
            Log.e(TAG, "获取版本信息失败: ${e.message}")
            null
        }
    }
    
    private fun getLocalVersionCode(): Long {
        val packageInfo = getPackageInfo() ?: return 0
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            packageInfo.longVersionCode
        } else {
            @Suppress("DEPRECATION")
            packageInfo.versionCode.toLong()
        }
    }
    
    private fun getLocalVersionName(): String {
        return getPackageInfo()?.versionName ?: "1.0.0"
    }
    
    /**
     * 检查更新
     */
    fun checkForUpdate(callback: (UpdateInfo?) -> Unit) {
        // 自用改造：默认不与作者仓库通信，直接按「已是最新」返回。
        if (!com.k2767.course.SelfHostConfig.ENABLE_APP_UPDATE_CHECK) {
            Log.d(TAG, "应用内更新检查已由 SelfHostConfig 关闭")
            callback(null)
            return
        }
        Log.d(TAG, "检查更新: $VERSION_URL")
        
        val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
        
        val request = Request.Builder()
            .url(VERSION_URL)
            .header("Cache-Control", "no-cache")
            .build()
        
        client.newCall(request).enqueue(object : Callback {
            override fun onFailure(call: Call, e: IOException) {
                Log.e(TAG, "检查更新失败: ${e.message}")
                mainHandler.post { callback(null) }
            }
            
            override fun onResponse(call: Call, response: Response) {
                try {
                    val json = response.body?.string() ?: ""
                    Log.d(TAG, "版本信息: $json")
                    
                    val obj = JSONObject(json)
                    val serverVersionCode = obj.optInt("versionCode", 0)
                    val localVersionCode = getLocalVersionCode()
                    
                    Log.d(TAG, "本地版本: $localVersionCode, 服务器版本: $serverVersionCode")
                    
                    if (serverVersionCode > localVersionCode) {
                        val updateInfo = UpdateInfo(
                            versionCode = serverVersionCode,
                            versionName = obj.optString("versionName", ""),
                            releaseNotes = obj.optString("releaseNotes", ""),
                            downloadUrl = obj.optString("downloadUrl", ""),
                            forceUpdate = obj.optBoolean("forceUpdate", false),
                            mirrorUrl = obj.optString("mirrorUrl", "")
                        )
                        mainHandler.post { callback(updateInfo) }
                    } else {
                        mainHandler.post { callback(null) }
                    }
                } catch (e: Exception) {
                    Log.e(TAG, "解析版本信息失败: ${e.message}")
                    mainHandler.post { callback(null) }
                }
            }
        })
    }
    
/**
     * 下载安装包，流式写入应用私有目录。
     *
     * **不再用系统 DownloadManager。** 它在部分 ROM 上把任务排进队列后永远停在 PENDING：
     * 一个字节不传、也不报 FAILED，用户看到的就是「正在下载…」挂住，两分钟后我们只能
     * 报「没能开始」（v1.2.0 现场，OriginOS）。改由应用自己跟随重定向下载，进度、超时、
     * 重试都握在自己手里。
     *
     * 下载本身在 [ApkDownloader] 里（不碰 Android，可单测），这里只负责线程与主线程回调。
     * 失败时按序再试 [mirrorUrl]（另一个下载源），两个都失败才报错。
     */
    fun downloadApk(
        downloadUrl: String,
        mirrorUrl: String = "",
        onProgress: (Int) -> Unit,
        onComplete: (File?) -> Unit,
        onFailure: (String) -> Unit = {}
    ) {
        val apkFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_FILE_NAME)
        if (apkFile.exists()) apkFile.delete()

        val sources = listOf(downloadUrl, mirrorUrl).filter { it.isNotBlank() }.distinct()
        if (sources.isEmpty()) {
            onFailure("没有可用的下载地址")
            onComplete(null)
            return
        }

        Thread {
            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
            try {
                downloader.download(sources, apkFile) { progress ->
                    mainHandler.post { onProgress(progress) }
                }
                mainHandler.post {
                    onProgress(100)
                    onComplete(apkFile)
                }
            } catch (e: Exception) {
                Log.e(TAG, "下载失败（已试 ${sources.size} 个源）: ${e.message}")
                mainHandler.post {
                    onFailure(ApkDownloader.describe(e))
                    onComplete(null)
                }
            }
        }.start()
    }

    /**
     * 安装APK
     */
    fun installApk(apkFile: File) {
        Log.d(TAG, "安装APK: ${apkFile.absolutePath}")
        
        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
                
                val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    FileProvider.getUriForFile(
                        context,
                        "${context.packageName}.fileprovider",
                        apkFile
                    )
                } else {
                    Uri.fromFile(apkFile)
                }
                
                setDataAndType(uri, "application/vnd.android.package-archive")
            }
            
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e(TAG, "安装APK失败: ${e.message}")
        }
    }
    
    /**
     * 用系统浏览器打开下载地址。
     *
     * 应用内下载（Gitee 与镜像）都不通时的保底出口——浏览器跟随重定向的兼容性
     * 远好于任何应用内实现，用户在浏览器里下完再点安装即可。
     */
    fun openDownloadInBrowser(url: String) {
        if (url.isBlank()) return
        runCatching {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }.onFailure { Log.e(TAG, "打不开浏览器: ${it.message}") }
    }

    /**
     * 获取当前版本名
     */
    fun getCurrentVersionName(): String {
        return getLocalVersionName()
    }
    
    /**
     * 获取当前版本号
     */
    fun getCurrentVersionCode(): Int {
        return getLocalVersionCode().toInt()
    }
}

