package com.k2767.course.update

import okhttp3.OkHttpClient
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File
import java.io.IOException
import java.util.concurrent.TimeUnit

/**
 * 更新包下载的回归测试。
 *
 * 这四种情况都是真出过事或极容易出事的：重定向跟丢、CDN 不给总长度、传到一半断流、
 * 服务器 200 但返回的其实是错误页。它们在真机上要凑齐网络状态才能碰到，在这里可以逐个钉死。
 */
class ApkDownloaderTest {

    @get:Rule val folder = TemporaryFolder()

    private lateinit var server: MockWebServer
    private lateinit var downloader: ApkDownloader

    /** 下载 6MB 的包在测试里没必要，只要超过 MIN_APK_BYTES 即可。 */
    private fun apkBytes(size: Int = 150_000): ByteArray {
        val bytes = ByteArray(size)
        bytes[0] = 'P'.code.toByte()
        bytes[1] = 'K'.code.toByte()
        for (i in 2 until size) bytes[i] = (i % 251).toByte()
        return bytes
    }

    private fun target(name: String = "update.apk"): File = File(folder.root, name)

    @Before fun setUp() {
        server = MockWebServer().apply { start() }
        downloader = ApkDownloader(
            OkHttpClient.Builder()
                .connectTimeout(5, TimeUnit.SECONDS)
                .readTimeout(5, TimeUnit.SECONDS)
                .build()
        )
    }

    @After fun tearDown() = server.shutdown()

    @Test fun `跟随重定向拿到完整包`() {
        val body = apkBytes()
        server.enqueue(MockResponse().setResponseCode(302).setHeader("Location", server.url("/real")))
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(body)))

        val file = target()
        val written = downloader.download(listOf(server.url("/redirect").toString()), file) {}

        assertEquals(body.size.toLong(), written)
        assertEquals(body.size.toLong(), file.length())
        assertTrue(ApkDownloader.looksLikeApk(file))
    }

    @Test fun `不给总长度时进度报未知但仍能下完`() {
        val body = apkBytes()
        server.enqueue(MockResponse().setResponseCode(200).setChunkedBody(Buffer().write(body), 8192))

        val file = target()
        val progresses = mutableListOf<Int>()
        val written = downloader.download(listOf(server.url("/chunked").toString()), file) { progresses += it }

        assertEquals(body.size.toLong(), written)
        assertEquals(body.size.toLong(), file.length())
        // 拿不到总长度时全程报 -1：界面据此显示滚动条而不是卡在 0%。
        // 收尾的 100% 由调用方在下完之后自己补（UpdateManager 里 post(100)）——
        // 这一层手里没有总长度，本来就算不出百分比。
        assertTrue("进度必须是未知（-1），实际: $progresses", progresses.isNotEmpty() && progresses.all { it == -1 })
        assertTrue(ApkDownloader.looksLikeApk(file))
    }

    @Test fun `传到一半断流时换下一个源`() {
        val good = apkBytes()
        // 第一个源：声明了长度，但响应体写到一半就断
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY)
                .setBody(Buffer().write(good))
        )
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(good)))

        val file = target()
        val written = downloader.download(
            listOf(server.url("/broken").toString(), server.url("/mirror").toString()), file
        ) {}

        assertEquals("必须换到第二个源并下完", good.size.toLong(), written)
        assertTrue(ApkDownloader.looksLikeApk(file))
        assertEquals(2, server.requestCount)
    }

    @Test fun `返回的是错误页时判失败且不留残文件`() {
        server.enqueue(
            MockResponse().setResponseCode(200)
                .setBody("<html><body>404 not found</body></html>")
        )

        val file = target()
        val error = runCatching {
            downloader.download(listOf(server.url("/page").toString()), file) {}
        }.exceptionOrNull()

        assertTrue("错误页必须判成失败，实际: $error", error is IOException)
        assertFalse("失败的半截文件必须删掉，别让下一次误以为是包", file.exists())
    }

    @Test fun `体积够大但根本不是安装包时也判失败`() {
        // 够大、也返回 200，但头两字节不是 PK —— 有些网关会塞一个很大的 HTML
        val notApk = ByteArray(150_000) { 'x'.code.toByte() }
        server.enqueue(MockResponse().setResponseCode(200).setBody(Buffer().write(notApk)))

        val file = target()
        val error = runCatching {
            downloader.download(listOf(server.url("/big").toString()), file) {}
        }.exceptionOrNull()

        assertTrue(error is IOException)
        assertFalse(file.exists())
    }

    @Test fun `HTTP 错误码直接判失败`() {
        server.enqueue(MockResponse().setResponseCode(404).setBody("nope"))

        val file = target()
        val error = runCatching {
            downloader.download(listOf(server.url("/missing").toString()), file) {}
        }.exceptionOrNull()

        assertTrue(error is IOException)
        assertTrue("错误信息里要带上状态码，便于用户判断", error!!.message!!.contains("404"))
    }

    @Test fun `全部源都失败时抛出最后一个异常`() {
        server.enqueue(MockResponse().setResponseCode(500))
        server.enqueue(MockResponse().setResponseCode(503))

        val file = target()
        val error = runCatching {
            downloader.download(
                listOf(server.url("/a").toString(), server.url("/b").toString()), file
            ) {}
        }.exceptionOrNull()

        assertTrue(error is IOException)
        assertTrue(error!!.message!!.contains("503"))
    }
}