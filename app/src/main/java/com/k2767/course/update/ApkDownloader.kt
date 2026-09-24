package com.k2767.course.update

import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.io.IOException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * 安装包的流式下载。
 *
 * 刻意不碰任何 Android API（Context / Handler / Looper 一概不用），进度用回调同步抛给调用方，
 * 切主线程由调用方负责。这样这一层能在 JVM 上用 MockWebServer 直接测——「跟随重定向」
 * 「不给总长度」「中途断流换源」「返回的其实是错误页」这四种情况都出过问题或极容易出错，
 * 只靠真机试是试不完的。
 */
internal class ApkDownloader(private val client: OkHttpClient) {

    /**
     * 依次尝试 [sources]，成功返回写入的字节数；全部失败抛最后一个异常。
     *
     * 每轮失败都要把半截文件删掉：留着残骸，下一轮的 [looksLikeApk] 可能被上一轮的文件头骗过。
     */
    fun download(sources: List<String>, target: File, onProgress: (Int) -> Unit): Long {
        require(sources.isNotEmpty()) { "没有可用的下载地址" }
        var last: Exception = IOException("没有可用的下载地址")
        for (url in sources) {
            try {
                val written = stream(url, target, onProgress)
                if (!looksLikeApk(target)) throw IOException("下载到的不是安装包（$written 字节）")
                return written
            } catch (e: Exception) {
                last = e
                runCatching { target.delete() }
            }
        }
        throw last
    }

    /** 把 [url] 的响应体写进 [target]。总长度未知时进度报 -1（界面显示滚动条）。 */
    internal fun stream(url: String, target: File, onProgress: (Int) -> Unit): Long {
        val request = Request.Builder().url(url).build()
        client.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("服务器返回 ${response.code}")
            val body = response.body ?: throw IOException("服务器没有返回内容")
            val total = body.contentLength()
            // 声明长度小到装不下一个安装包，多半是错误页：宁可判失败，
            // 也别把它写进文件再交给安装器去解析。
            if (total in 1..MIN_APK_BYTES) throw IOException("服务器返回的不是安装包")
            var written = 0L
            body.byteStream().use { input ->
                target.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        output.write(buffer, 0, read)
                        written += read
                        onProgress(if (total > 0) ((written * 100) / total).toInt() else -1)
                    }
                }
            }
            if (total > 0 && written != total) throw IOException("下载不完整（$written/$total）")
            return written
        }
    }

    companion object {
        /** 比这还小的响应体不可能是安装包，当作错误页直接判失败。 */
        const val MIN_APK_BYTES = 100_000L

        /** 装之前先看一眼文件头：APK 是 zip，头两字节必须是 PK。 */
        fun looksLikeApk(file: File): Boolean {
            if (!file.exists() || file.length() < MIN_APK_BYTES) return false
            return runCatching {
                file.inputStream().use { input ->
                    val head = ByteArray(2)
                    input.read(head) == 2 &&
                        head[0] == 'P'.code.toByte() && head[1] == 'K'.code.toByte()
                }
            }.getOrDefault(false)
        }

        /** 把下载异常翻译成人话，并说清「还能怎么办」。 */
        fun describe(error: Throwable): String = when (error) {
            is SocketTimeoutException -> "下载中途断了数据，可能网络不稳。可重试，或到官网手动下载"
            is UnknownHostException -> "连不上下载服务器，检查网络后重试"
            is SSLException -> "安全连接失败，可稍后重试，或到官网手动下载"
            else -> error.message ?: "下载失败，请重试"
        }
    }
}