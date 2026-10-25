package com.tyust.course.announcement

import android.app.Application
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.RuntimeEnvironment
import org.commonmark.node.AbstractVisitor
import org.commonmark.node.HtmlBlock
import org.commonmark.node.HtmlInline
import org.commonmark.node.Image
import org.commonmark.parser.Parser
import java.io.File
import java.io.IOException

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = Application::class)
class AnnouncementManagerTest {
    private fun notice(extra: String = "") = """{"id":"notice","title":"功能更新","content":"## 正文","contentType":"markdown"$extra}"""
    private fun feed(vararg notices: String) = """{"schemaVersion":1,"announcements":[${notices.joinToString(",") }]}"""
    @Test fun preservesExistingIdsAndSupportsHistoryEnvelope() {
        val result = AnnouncementManager.validateDocument("""{"announcements":[{"id":"old","title":"旧公告","content":"正文","showOnce":true},{"id":"new","title":"新公告","content":"正文"}]}""")
        assertEquals(listOf("old", "new"), result.map { it.id })
    }
    @Test fun acceptsLegacySingleAnnouncement() {
        assertEquals("legacy", AnnouncementManager.validateDocument("""{"id":"legacy","title":"标题","content":"正文"}""").single().id)
    }
    @Test fun rejectsErrorDocumentInsteadOfClearingHistory() {
        assertThrows(Exception::class.java) { AnnouncementManager.validateDocument("""{"error":"unavailable"}""") }
        assertThrows(Exception::class.java) { AnnouncementManager.validateDocument("<html>bad gateway</html>") }
    }
    @Test fun rejectsOversizedDocument() {
        assertThrows(Exception::class.java) { AnnouncementManager.validateDocument(" ".repeat(256 * 1024 + 1)) }
    }
    @Test fun onlyTheAnnouncementFeedGetsTheNewHostAllowance() {
        assertTrue(AnnouncementManager.validFeedUrl("https://sonder123i.github.io/capybara-course-helper/announcement.json"))
        assertTrue(AnnouncementManager.validFeedUrl("https://gitee.com/sonder123i/capybara-course-helper/raw/main/docs/announcement.json"))
        assertFalse(AnnouncementManager.validFeedUrl("https://sonder123i.github.io.attacker.test/capybara-course-helper/announcement.json"))
        assertFalse(AnnouncementManager.validFeedUrl("http://sonder123i.github.io/capybara-course-helper/announcement.json"))
        assertFalse(AnnouncementManager.validFeedUrl("https://dl.hidisiwa.xyz/announcement.json"))
    }
    @Test fun defaultDisplayAndReadRevisionPreserveTheUsersNotificationPreference() {
        assertEquals("center", AnnouncementManager.validateDocument(notice()).single().display)
        val important = AnnouncementManager.validateDocument(notice(",\"type\":\"important\",\"revision\":4")).single()
        assertEquals("popup", important.display)
        assertEquals("notice:4", important.readKey)
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("announcement_prefs", 0).edit().clear().commit()
        AnnouncementManager.markAsRead(context, important)
        assertTrue(AnnouncementManager.isRead(context, important))
        assertFalse(AnnouncementManager.isRead(context, important.copy(revision = 5)))
    }
    @Test fun timeVersionChannelAndSchoolAllConstrainVisibility() {
        val notice = AnnouncementManager.Announcement("a", "title", "text", "info", startsAt = 1000, endsAt = 3000,
            minVersionCode = 119, maxVersionCode = 125, channels = setOf("stable"), schoolIds = setOf("test-school"))
        assertTrue(notice.applies(1000, 119, "stable", "test-school"))
        assertTrue(notice.applies(2999, 125, "stable", "test-school"))
        assertFalse(notice.applies(999, 119, "stable", "test-school"))
        assertFalse(notice.applies(3000, 119, "stable", "test-school"))
        assertFalse(notice.applies(2000, 118, "stable", "test-school"))
        assertFalse(notice.applies(2000, 126, "stable", "test-school"))
        assertFalse(notice.applies(2000, 119, "preview", "test-school"))
        assertFalse(notice.applies(2000, 119, "stable", "another-school"))
    }
    @Test fun acceptsSixIndependentNavigationActionsButRejectsHiddenOperations() {
        val actions = JSONArray()
        listOf("schedule", "grades", "selection", "plugins", "updates").forEach { actions.put(JSONObject().put("label", it).put("kind", "screen").put("target", it)) }
        actions.put(JSONObject().put("label", "指南").put("kind", "url").put("target", "https://example.com/guide"))
        val item = JSONObject(notice()).put("actions", actions)
        assertEquals(6, AnnouncementManager.validateDocument(feed(item.toString())).single().actions.size)
        actions.getJSONObject(0).put("target", "delete-account")
        assertThrows(Exception::class.java) { AnnouncementManager.validateDocument(feed(item.toString())) }
        for (url in listOf("javascript:alert(1)", "file:///private", "http://example.com", "https://localhost", "https://127.0.0.1/", "https://user:password@example.com")) {
            assertFalse(url, AnnouncementManager.validPublicUrl(url))
        }
    }
    @Test fun rejectsFutureSchemasDuplicateIdsAndInvalidSchedule() {
        for (document in listOf("""{"schemaVersion":2,"announcements":[]}""", feed(notice(), notice()),
            notice(",\"startsAt\":\"2026-02-30T00:00:00Z\""), notice(",\"revision\":0"), notice(",\"minVersionCode\":125,\"maxVersionCode\":119"))) {
            assertThrows(Exception::class.java) { AnnouncementManager.validateDocument(document) }
        }
        assertEquals(0L, AnnouncementManager.parseDate("1970-01-01T08:00:00.000+08:00"))
        assertEquals(1000L, AnnouncementManager.parseDate("1970-01-01T00:00:01Z"))
    }
    @Test fun publishedFeedCanExceedLegacyLimitButCannotExceedFiftyNotices() {
        val items = (1..40).map { JSONObject(notice()).put("id", "item$it").put("content", "正文".repeat(1800)).toString() }
        assertTrue(feed(*items.toTypedArray()).toByteArray().size > 256 * 1024)
        assertEquals(40, AnnouncementManager.validateDocument(feed(*items.toTypedArray())).size)
        assertThrows(Exception::class.java) { AnnouncementManager.validateDocument(feed(*(1..51).map { JSONObject(notice()).put("id", "$it").toString() }.toTypedArray())) }
    }
    @Test fun emptyAuthoritativeFeedDoesNotResurrectWithdrawnLegacyNoticesWhenOffline() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        context.getSharedPreferences("announcement_prefs", 0).edit().clear().commit()
        File(context.filesDir, "announcement-cache.json").writeText(notice())
        val empty = AnnouncementManager.loadFeed(context) { _, _, _ -> feed() }
        assertTrue(empty.items.isEmpty())
        val requests = mutableListOf<String>()
        val cached = AnnouncementManager.loadFeed(context) { url, _, _ -> requests += url; throw IOException("offline fixture") }
        assertTrue(cached.cached)
        assertTrue(cached.items.isEmpty())
        assertEquals(1, requests.size)
        assertTrue(requests.single().endsWith("/announcement.json"))
    }
    @Test fun invalidNetworkDocumentKeepsTheLastValidCloudSnapshot() = runBlocking {
        val context = RuntimeEnvironment.getApplication()
        AnnouncementManager.loadFeed(context) { _, _, _ -> feed(notice()) }
        val cached = AnnouncementManager.loadFeed(context) { _, _, _ -> "{\"error\":\"unavailable\"}" }
        assertEquals("notice", cached.items.single().id)
        assertTrue(cached.cached)
    }
    @Test fun cancellationDoesNotBecomeAnOfflineResult() {
        assertThrows(CancellationException::class.java) {
            runBlocking { AnnouncementManager.loadFeed(RuntimeEnvironment.getApplication()) { _, _, _ -> throw CancellationException("cancelled fixture") } }
        }
    }
    @Test fun markdownPreservesTablesCodeAndPublicImagesWhileRemovingHtmlAndPrivateImages() {
        val markdown = """## 指南

| 功能 | 说明 |
| --- | --- |
| 课表 | 查看课程 |

![正常](https://example.com/image.png)
![本地](file:///private)
![引用][private]

[private]: http://127.0.0.1/image

<script>alert(1)</script>

说明 <img src="http://127.0.0.1/image"> 后续

```html
<button>示例代码</button>
```
"""
        val safe = safeAnnouncementMarkdown(markdown)
        var html = 0
        val images = mutableListOf<String>()
        Parser.builder().build().parse(safe).accept(object : AbstractVisitor() {
            override fun visit(block: HtmlBlock) { html++ }
            override fun visit(inline: HtmlInline) { html++ }
            override fun visit(image: Image) { images += image.destination }
        })
        assertEquals(0, html)
        assertEquals(listOf("https://example.com/image.png"), images)
        assertTrue(safe.contains("| 课表 | 查看课程 |"))
        assertTrue(safe.contains("<button>示例代码</button>"))
        assertTrue(safe.contains("后续"))
    }
}
