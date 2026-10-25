package com.tyust.course.announcement

import android.content.Context
import android.os.SystemClock
import android.util.AtomicFile
import com.tyust.course.BuildConfig
import com.tyust.course.manager.UserManager
import com.tyust.course.update.UpdateManifestVerifier
import com.tyust.course.update.awaitResponse
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.io.File
import java.text.ParsePosition
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

/** Public App notices, independent of plugin installation and school credentials. */
object AnnouncementManager {
    // 公告源指向自建端点（GitHub Pages 主 + Gitee 备），见 SelfHostConfig；
    // ENABLE_REMOTE_ANNOUNCEMENT 关闭时不发起任何网络请求。
    private val PORTAL: String get() = com.tyust.course.SelfHostConfig.announcementFeedUrls.first()
    private const val PREFS = "announcement_prefs"
    private const val READ_IDS = "read_announcement_ids"
    private const val PORTAL_SEEN = "portal_feed_seen"
    private val legacySources: List<String> get() = com.tyust.course.SelfHostConfig.announcementFeedUrls.drop(1)
    val screens = setOf("schedule", "grades", "selection", "settings", "plugins", "services", "feedback", "updates", "announcements")
    data class Action(val label: String, val kind: String, val target: String)
    data class Announcement(
        val id: String, val title: String, val content: String, val type: String,
        val contentType: String = "text", val showOnce: Boolean = true,
        val revision: Int = 1, val summary: String = "", val category: String = "general",
        val display: String = if (type == "important") "popup" else "center", val pinned: Boolean = false,
        val startsAt: Long = 0, val endsAt: Long = 0, val minVersionCode: Int = 0, val maxVersionCode: Int = 0,
        val channels: Set<String> = emptySet(), val schoolIds: Set<String> = emptySet(),
        val coverUrl: String = "", val actions: List<Action> = emptyList()
    ) {
        val readKey get() = if (revision <= 1) id else "$id:$revision"
        fun applies(now: Long, version: Int, channel: String, school: String): Boolean =
            startsAt <= now && (endsAt == 0L || endsAt > now) && version >= minVersionCode &&
                (maxVersionCode == 0 || version <= maxVersionCode) &&
                (channels.isEmpty() || channel in channels) && (schoolIds.isEmpty() || school in schoolIds)
    }
    data class Feed(val items: List<Announcement> = emptyList(), val loading: Boolean = false,
        val cached: Boolean = false, val error: String = "", val readRevision: Long = 0)
    private val mutable = MutableStateFlow(Feed())
    val state = mutable.asStateFlow()
    private var appContext: Context? = null
    private var fetchedAt = -1L
    private val lock = Mutex()
    private val client = OkHttpClient.Builder().followRedirects(false).followSslRedirects(false).retryOnConnectionFailure(false)
        .connectTimeout(3, TimeUnit.SECONDS).readTimeout(4, TimeUnit.SECONDS).callTimeout(5, TimeUnit.SECONDS).build()
    fun initialize(context: Context) { appContext = context.applicationContext }
    fun visible(items: List<Announcement> = state.value.items, now: Long = System.currentTimeMillis()): List<Announcement> =
        items.filter { it.applies(now, BuildConfig.VERSION_CODE, if (BuildConfig.DEBUG || BuildConfig.UI_PREVIEW) "preview" else "stable", UserManager.getInstance().currentSchool?.id.orEmpty()) }
            .sortedWith(compareByDescending<Announcement> { it.pinned }.thenByDescending { it.startsAt })
    fun isRead(context: Context, announcement: Announcement) =
        announcement.readKey in context.getSharedPreferences(PREFS, 0).getStringSet(READ_IDS, emptySet()).orEmpty()
    fun unreadCount(context: Context) = visible().count { !isRead(context, it) }
    fun markAsRead(context: Context, announcement: Announcement) = markAsRead(context, announcement.readKey)
    @Synchronized fun markAsRead(context: Context, announcementId: String) {
        val prefs = context.getSharedPreferences(PREFS, 0)
        val read = prefs.getStringSet(READ_IDS, emptySet()).orEmpty().toMutableSet()
        read.remove(announcementId); read.add(announcementId)
        prefs.edit().putStringSet(READ_IDS, read.toList().takeLast(500).toSet()).apply()
        mutable.update { it.copy(readRevision = it.readRevision + 1) }
    }
    fun markAllRead(context: Context) { visible().forEach { markAsRead(context, it) } }
    suspend fun fetchAllAnnouncements(): List<Announcement> = refresh(force = true)
    suspend fun fetchUnreadAnnouncements(context: Context): List<Announcement> = visible(refresh()).filter { !isRead(context, it) }
    suspend fun fetchAnnouncement(context: Context) = fetchUnreadAnnouncements(context).firstOrNull { it.display == "popup" }
    suspend fun refresh(force: Boolean = false): List<Announcement> = lock.withLock {
        if (!com.tyust.course.SelfHostConfig.ENABLE_REMOTE_ANNOUNCEMENT) {
            return@withLock state.value.items
        }
        if (!force && fetchedAt >= 0 && SystemClock.elapsedRealtime() - fetchedAt < 600_000) return@withLock state.value.items
        fetchedAt = SystemClock.elapsedRealtime()
        mutable.update { it.copy(loading = true) }
        try {
            withContext(Dispatchers.IO) {
                val result = loadFeed(appContext)
                mutable.update { result.copy(readRevision = it.readRevision, loading = true) }
                result.items
            }
        } catch (cancelled: CancellationException) {
            fetchedAt = -1L
            throw cancelled
        } finally { mutable.update { it.copy(loading = false) } }
    }
    internal var documentLoader: suspend (String, Long, Int) -> String = ::download
    internal suspend fun loadFeed(app: Context?, loader: suspend (String, Long, Int) -> String = documentLoader): Feed {
        val cloudCache = app?.let { AtomicFile(File(it.filesDir, "app-announcements-v1.json")) }
        val prefs = app?.getSharedPreferences(PREFS, 0)
        try {
            val document = loader(PORTAL, SystemClock.elapsedRealtime() + 5000, 1024 * 1024)
            val root = JSONObject(document)
            require(root.getInt("schemaVersion") == 1 && root.has("announcements"))
            val items = validateDocument(document)
            save(cloudCache, document)
            prefs?.edit()?.putBoolean(PORTAL_SEEN, true)?.apply()
            return Feed(items)
        } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Use the last valid public feed. */ }
        // A failed portal request must never resurrect a withdrawn legacy notice.
        if (prefs?.getBoolean(PORTAL_SEEN, false) == true || cloudCache?.baseFile?.exists() == true)
            return Feed(cached(cloudCache), cached = true, error = "暂时无法更新公告，已保留本地记录")
        val cache = app?.let { AtomicFile(File(it.filesDir, "announcement-cache.json")) }
        val deadline = SystemClock.elapsedRealtime() + 8000
        for (source in legacySources) {
            try {
                val document = loader(source, deadline, 256 * 1024)
                val items = validateDocument(document)
                save(cache, document)
                return Feed(items)
            } catch (e: CancellationException) { throw e } catch (_: Exception) { /* Try the next legacy mirror. */ }
        }
        return Feed(cached(cache), cached = true, error = "暂时无法更新公告，稍后可重试")
    }
    private fun cached(file: AtomicFile?) = runCatching {
        file?.openRead()?.use { readDocument(it, 1024 * 1024) }?.let(::validateDocument)
    }.getOrNull().orEmpty()
    private fun save(file: AtomicFile?, document: String) {
        if (file == null) return
        val stream = runCatching { file.startWrite() }.getOrNull() ?: return
        try { stream.write(document.toByteArray()); file.finishWrite(stream) }
        catch (_: Exception) { file.failWrite(stream) }
    }
    private suspend fun download(source: String, deadline: Long, limit: Int): String {
        var url = source
        repeat(6) {
            val remaining = deadline - SystemClock.elapsedRealtime()
            require(remaining > 0 && validFeedUrl(url))
            val call = client.newCall(Request.Builder().url(url).header("Cache-Control", "no-cache").build())
            call.timeout().timeout(minOf(5000, remaining), TimeUnit.MILLISECONDS)
            call.awaitResponse().use { response ->
                if (response.code in setOf(301, 302, 303, 307, 308)) {
                    url = response.header("Location")?.let { response.request.url.resolve(it)?.toString() } ?: error("redirect")
                } else {
                    require(response.isSuccessful)
                    val body = response.body ?: error("empty")
                    return body.byteStream().use { readDocument(it, limit) }
                }
            }
        }
        error("redirect limit")
    }
    internal fun validFeedUrl(value: String): Boolean = value == PORTAL || UpdateManifestVerifier.validUrl(value)
    private fun readDocument(input: java.io.InputStream, limit: Int): String {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(8192)
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            require(output.size() + count <= limit)
            output.write(buffer, 0, count)
        }
        return output.toString("UTF-8")
    }
    internal fun parseDate(value: String): Long {
        if (value.isBlank()) return 0
        for (pattern in listOf("yyyy-MM-dd'T'HH:mm:ss.SSSXXX", "yyyy-MM-dd'T'HH:mm:ssXXX")) {
            val position = ParsePosition(0)
            val result = SimpleDateFormat(pattern, Locale.US).apply { isLenient = false }.parse(value, position)
            if (result != null && position.index == value.length) return result.time
        }
        error("Invalid announcement date")
    }
    fun validPublicUrl(value: String): Boolean = value.toHttpUrlOrNull()?.let {
        it.isHttps && it.username.isEmpty() && it.password.isEmpty() && it.port == 443 && it.host != "localhost" &&
            !it.host.endsWith(".local") && !it.host.matches(Regex("[0-9.]+")) && !it.host.contains(':')
    } == true
    internal fun validateDocument(json: String): List<Announcement> {
        require(json.toByteArray().size <= 1024 * 1024)
        val trimmed = json.trim()
        val root = if (trimmed.startsWith("[")) null else JSONObject(trimmed)
        if (root?.has("schemaVersion") == true) require(root.getInt("schemaVersion") == 1 && root.optJSONArray("announcements") != null)
        require(json.toByteArray().size <= if (root?.optInt("schemaVersion") == 1) 1024 * 1024 else 256 * 1024)
        val entries = if (root == null) JSONArray(trimmed) else root.optJSONArray("announcements") ?: JSONArray().put(root)
        require(entries.length() <= if (root?.optInt("schemaVersion") == 1) 50 else 100)
        val ids = mutableSetOf<String>()
        return (0 until entries.length()).map { index ->
            val item = entries.getJSONObject(index)
            val id = item.getString("id"); val title = item.getString("title"); val content = item.getString("content")
            require(id.isNotBlank() && id.length <= 128 && ids.add(id) && title.isNotBlank() && title.length <= 100 && content.isNotBlank() && content.length <= 16000)
            fun date(key: String) = parseDate(item.optString(key))
            fun strings(key: String, max: Int): Set<String> = item.optJSONArray(key)?.let { values ->
                require(values.length() <= max)
                (0 until values.length()).map { values.getString(it).also { text -> require(text.length <= 96) } }.toSet()
            }.orEmpty()
            val actions = item.optJSONArray("actions") ?: JSONArray()
            require(actions.length() <= 6)
            val parsedActions = (0 until actions.length()).map { n ->
                val action = actions.getJSONObject(n)
                val label = action.getString("label"); val kind = action.getString("kind"); val target = action.getString("target")
                require(label.isNotBlank() && label.length <= 24 && target.length <= 800)
                require(kind == "screen" && target in screens || kind == "url" && validPublicUrl(target))
                Action(label, kind, target)
            }
            val type = item.optString("type", "info")
            require(type in setOf("info", "warning", "important"))
            require(item.optString("contentType", "text") in setOf("text", "markdown"))
            val display = item.optString("display", if (type == "important") "popup" else "center")
            val cover = item.optString("coverUrl")
            val start = date("startsAt"); val end = date("endsAt")
            val min = item.optInt("minVersionCode"); val max = item.optInt("maxVersionCode")
            require(display in setOf("center", "popup") && (cover.isEmpty() || validPublicUrl(cover)) &&
                (end == 0L || end > start) && min >= 0 && max >= 0 && (max == 0 || max >= min))
            Announcement(id, title, content, type, item.optString("contentType", "text"), item.optBoolean("showOnce", true),
                item.optInt("revision", 1).also { require(it > 0) }, item.optString("summary").take(240), item.optString("category", "general"),
                display, item.optBoolean("pinned"), start, end, min, max, strings("channels", 2).also { require(it.all { v -> v in setOf("stable", "preview") }) },
                strings("schoolIds", 20), cover, parsedActions)
        }
    }
}
