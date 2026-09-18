package com.k2767.course.update

import android.app.DownloadManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
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
    
    private val client = OkHttpClient.Builder()
        .connectTimeout(15, java.util.concurrent.TimeUnit.SECONDS)
        .readTimeout(30, java.util.concurrent.TimeUnit.SECONDS)
        .build()
    
    private var downloadId: Long = -1
    private var downloadReceiver: BroadcastReceiver? = null
    
    /**
     * 更新信息数据类
     */
    data class UpdateInfo(
        val versionCode: Int,
        val versionName: String,
        val releaseNotes: String,
        val downloadUrl: String,
        val forceUpdate: Boolean
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
                            forceUpdate = obj.optBoolean("forceUpdate", false)
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
     * 下载APK
     */
    fun downloadApk(
        downloadUrl: String,
        onProgress: (Int) -> Unit,
        onComplete: (File?) -> Unit,
        onFailure: (String) -> Unit = {}
    ) {
        Log.d(TAG, "开始下载: $downloadUrl")
        
        // 删除旧的APK文件
        val apkFile = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), APK_FILE_NAME)
        if (apkFile.exists()) {
            apkFile.delete()
        }
        
        val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
        
        val request = DownloadManager.Request(Uri.parse(downloadUrl)).apply {
            setTitle("正在下载更新")
            setDescription("正在下载新版本...")
            setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
            setDestinationInExternalFilesDir(context, Environment.DIRECTORY_DOWNLOADS, APK_FILE_NAME)
            setAllowedOverMetered(true)
            setAllowedOverRoaming(true)
        }
        
        downloadId = downloadManager.enqueue(request)
        
        // 监听下载进度
        Thread {
            val mainHandler = android.os.Handler(android.os.Looper.getMainLooper())
            val enqueuedAt = System.currentTimeMillis()
            // 只有真的拿到过字节数，才认为下载已经开始
            var started = false
            var downloading = true
            while (downloading) {
                val query = DownloadManager.Query().setFilterById(downloadId)
                val cursor = downloadManager.query(query)

                if (cursor.moveToFirst()) {
                    val statusIndex = cursor.getColumnIndex(DownloadManager.COLUMN_STATUS)
                    val bytesIndex = cursor.getColumnIndex(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)
                    val totalIndex = cursor.getColumnIndex(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)
                    val reasonIndex = cursor.getColumnIndex(DownloadManager.COLUMN_REASON)

                    if (statusIndex >= 0 && bytesIndex >= 0 && totalIndex >= 0) {
                        val status = cursor.getInt(statusIndex)
                        val bytesDownloaded = cursor.getLong(bytesIndex)
                        val bytesTotal = cursor.getLong(totalIndex)

                        // 「已经开始」的标志是收到了字节，而不是拿到了总大小——
                        // GitHub 分流下载常常不给 Content-Length，用它判断会误判成失败。
                        if (bytesDownloaded > 0) started = true

                        if (bytesTotal > 0) {
                            val progress = ((bytesDownloaded * 100) / bytesTotal).toInt()
                            mainHandler.post { onProgress(progress) }
                        } else if (status == DownloadManager.STATUS_RUNNING || status == DownloadManager.STATUS_PENDING) {
                            // 拿不到总大小（CDN 没给 Content-Length）：报 -1 表示「进度未知」，
                            // 界面显示滚动条 + “正在下载…”，而不是卡在 0%
                            mainHandler.post { onProgress(-1) }
                        }

                        when (status) {
                            DownloadManager.STATUS_SUCCESSFUL -> {
                                downloading = false
                                mainHandler.post { onComplete(apkFile) }
                            }
                            DownloadManager.STATUS_FAILED -> {
                                downloading = false
                                val code = if (reasonIndex >= 0) cursor.getInt(reasonIndex) else 0
                                mainHandler.post {
                                    onFailure(describeFailure(code))
                                    onComplete(null)
                                }
                            }
                        }
                    }
                }
                cursor.close()

                // 只在「一直排队、一个字节都没收到」时才算失败，并给足 2 分钟：
                // 裸连 GitHub 时连接建立本身就可能很慢，网络慢不等于下载坏掉。
                // 状态是 RUNNING 时一律继续等——系统下载器自己会报成功或失败。
                val stalled = !started && System.currentTimeMillis() - enqueuedAt > STALL_TIMEOUT_MILLIS
                if (downloading && stalled && !isRunning(downloadManager)) {
                    downloading = false
                    // 把系统下载器里的这条任务撤掉，免得通知栏留个永不动弹的条目
                    runCatching { downloadManager.remove(downloadId) }
                    mainHandler.post {
                        onFailure("下载一直没能开始，可能网络不通。可稍后重试，或到官网手动下载")
                        onComplete(null)
                    }
                }

                if (downloading) {
                    Thread.sleep(500)
                }
            }
        }.start()
    }

    /** 查一次系统下载器的状态，用于判断「卡住的到底是排队还是正在跑」。 */
    private fun isRunning(downloadManager: DownloadManager): Boolean {
        val cursor = downloadManager.query(DownloadManager.Query().setFilterById(downloadId))
        return cursor.use { if (it.moveToFirst()) it.getInt(it.getColumnIndex(DownloadManager.COLUMN_STATUS)) == DownloadManager.STATUS_RUNNING else false }
    }

    /** 把 DownloadManager 的错误码翻译成人话，方便用户判断该怎么办。 */
    private fun describeFailure(code: Int): String = when (code) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "存储空间不足，清理后重试"
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "找不到存储设备"
        DownloadManager.ERROR_HTTP_DATA_ERROR -> "下载数据出错，请重试"
        DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "服务器返回了异常响应（可能链接已失效）"
        DownloadManager.ERROR_FILE_ERROR -> "写入文件失败，请重试"
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "重定向次数过多，请到官网手动下载"
        DownloadManager.ERROR_CANNOT_RESUME -> "无法续传，请重试"
        else -> if (code in 400..599) "服务器返回 $code，可能链接已失效" else "下载失败（错误码 $code）"
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
     * 取消下载
     */
    fun cancelDownload() {
        if (downloadId != -1L) {
            val downloadManager = context.getSystemService(Context.DOWNLOAD_SERVICE) as DownloadManager
            downloadManager.remove(downloadId)
            downloadId = -1
        }
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

/** 一个字节都没收到多久算「卡住」。只对排队（PENDING）状态生效，RUNNING 不设上限。 */
private const val STALL_TIMEOUT_MILLIS = 120_000L

