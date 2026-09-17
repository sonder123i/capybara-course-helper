package com.k2767.course.ui

import android.app.Activity
import android.app.Instrumentation
import android.content.ClipData
import android.content.ClipboardManager
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import android.provider.MediaStore
import android.provider.Settings
import android.view.View
import android.view.ViewGroup
import android.webkit.WebView
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.Lifecycle
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.UiDevice
import com.k2767.course.BuildConfig
import com.k2767.course.SurveyWebViewActivity
import com.k2767.course.demo.DemoData
import com.k2767.course.manager.AppearanceSettingsManager
import com.k2767.course.manager.AppThemeMode
import com.k2767.course.manager.UserManager
import com.k2767.course.survey.*
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.IOException
import java.io.File
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import java.util.concurrent.atomic.AtomicReference

/** Uses the real local Worker via adb reverse tcp:8791 and the isolated preview APK. */
@RunWith(AndroidJUnit4::class)
class SurveyCenterDeviceTest {
    private val instrumentation = InstrumentationRegistry.getInstrumentation()
    private val context get() = instrumentation.targetContext
    private val device get() = UiDevice.getInstance(instrumentation)
    private class RecordingApi : SurveyTransport {
        private val api = SurveyApi("http://127.0.0.1:8791")
        val lists = AtomicInteger()
        @Volatile var offline = false
        override suspend fun list(schoolHost: String): SurveyFeedResult { lists.incrementAndGet(); if (offline) throw IOException("test offline"); return api.list(schoolHost) }
        override suspend fun detail(id: String, schoolHost: String): SurveyDetailResult { if (offline) throw IOException("test offline"); return api.detail(id, schoolHost) }
        override suspend fun click(id: String, schoolHost: String, requestId: String) = error("Preview builds must not send telemetry")
    }

    private fun withSurveys(reminders: Boolean = false, block: (DemoUiDriver, RecordingApi, SurveyRepository) -> Unit) {
        assumeTrue("Use the separate UI preview variant", BuildConfig.UI_PREVIEW)
        instrumentation.runOnMainSync { UserManager.getInstance().init(context); UserManager.getInstance().startDemoSession(DemoData.school()) }
        val user = UserManager.getInstance()
        val host = SurveyLinks.schoolHost(user.currentSchool?.baseUrl)
        val file = SurveyFileStore(context, user.sessionState.token.accountStorageKey + "\n" + host)
        val previous = runBlocking { file.read() }
        runBlocking { file.write(SurveySavedData(schoolHost = host, remindersEnabled = reminders)) }
        val factory = SurveyViewModel.transportFactory
        val api = RecordingApi()
        SurveyViewModel.transportFactory = { api }
        val oldTheme = AppearanceSettingsManager.themeMode
        val oldGlass = AppearanceSettingsManager.glassEffectEnabled
        val oldMotion = Settings.Global.getString(context.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE)
        try {
            DemoUiDriver().use { ui ->
                lateinit var repository: SurveyRepository
                ui.onMain { repository = ViewModelProvider(requireNotNull(ui.main))[SurveyViewModel::class.java]
                    .forAccount(user.sessionState.token.accountStorageKey, user.currentSchool?.baseUrl) }
                ui.await("Local survey feed did not load", 25000) { repository.state.value.saved.surveys.size >= 18 && !repository.state.value.refreshing }
                block(ui, api, repository)
            }
        } finally {
            SurveyViewModel.transportFactory = factory
            device.executeShellCommand(if (oldMotion == null) "settings delete global animator_duration_scale" else "settings put global animator_duration_scale $oldMotion")
            instrumentation.runOnMainSync { AppearanceSettingsManager.updateThemeMode(oldTheme); AppearanceSettingsManager.updateGlassEffect(oldGlass) }
            runBlocking { file.write(previous) }
        }
    }

    private fun openCenter(ui: DemoUiDriver) {
        ui.await("Main window did not resume") { var focused = false; ui.onMain { focused = ui.main?.hasWindowFocus() == true }; focused }
        ui.navigate("设置")
        SystemClock.sleep(800)
        for (attempt in 0..8) {
            val activity = requireNotNull(ui.main); val w = activity.window.decorView.width; val h = activity.window.decorView.height
            if (ui.hasText("问卷中心") && ui.boundsOf("问卷中心").bottom < h * 4 / 5) break
            ui.shell("input -d ${activity.display!!.displayId} swipe ${w / 2} ${h * 3 / 4} ${w / 2} ${h / 3} 300")
            SystemClock.sleep(450)
        }
        ui.click("问卷中心"); ui.waitText("刷新问卷")
    }

    @Test fun recordsFiltersScrollPositionThemeAndRecreation() = withSurveys { ui, _, repo ->
        openCenter(ui)
        val first = repo.state.value.saved.surveys.first { it.pinned }
        ui.click(first.title); ui.waitText("问卷详情"); ui.click("收藏问卷"); ui.click("我已填写")
        ui.waitText("撤销填写标记")
        ui.screenshot("survey-detail")
        ui.back(); ui.waitText("校园问卷与参与记录")
        ui.click("全部问卷"); ui.click("我的收藏"); ui.waitText("1 份问卷")
        ui.screenshot("survey-favorites")
        val original = requireNotNull(ui.main)
        ui.onMain { original.recreate() }
        ui.await("Main activity did not recreate") { ui.main !== original && ui.foreground === ui.main }
        ui.waitText("我的收藏"); ui.waitText(first.title)
        ui.click(first.title); ui.waitText("撤销填写标记"); ui.click("撤销填写标记"); ui.waitText("我已填写"); ui.back()
        assertNull(repo.state.value.local(first.id).completedAt)
        ui.click("我的收藏"); ui.click("全部问卷"); ui.waitText("进行中")
        val activity = requireNotNull(ui.main); val w = activity.window.decorView.width; val h = activity.window.decorView.height
        repeat(2) { ui.shell("input -d ${activity.display!!.displayId} swipe ${w / 2} ${h * 4 / 5} ${w / 2} ${h * 3 / 5} 350"); SystemClock.sleep(400) }
        val visible = repo.state.value.saved.surveys.first { it.id != first.id && ui.hasText(it.title) }
        val before = ui.boundsOf(visible.title).top
        ui.click(visible.title); ui.waitText("问卷详情"); ui.back(); ui.waitText(visible.title)
        assertTrue("List scroll position changed", kotlin.math.abs(before - ui.boundsOf(visible.title).top) < 40)
        ui.onMain { AppearanceSettingsManager.updateThemeMode(AppThemeMode.Dark) }; SystemClock.sleep(900)
        ui.screenshot("survey-dark")
        ui.onMain { AppearanceSettingsManager.updateGlassEffect(false) }; SystemClock.sleep(500)
        ui.screenshot("survey-no-glass")
        ui.shell("settings put global animator_duration_scale 0"); SystemClock.sleep(500)
        ui.screenshot("survey-reduced-motion")
        ui.click("问卷偏好"); ui.waitText("问卷偏好"); ui.click("清空浏览历史"); ui.click("清空历史")
        ui.await("Browsing history was not cleared") { repo.state.value.saved.local.values.none { it.lastViewedAt != null } }
        ui.click("完成")
        assertTrue(repo.state.value.local(first.id).favorite)
        assertTrue(repo.state.value.saved.local.values.none { it.lastViewedAt != null })
        ui.screenshot("survey-history-cleared")
    }

    @Test fun importantRemindersRespectVisitBudgetAndOfflineRetryKeepsCachedContent() = withSurveys(reminders = true) { ui, api, repo ->
        ui.waitText("有一份新问卷"); ui.screenshot("survey-important-reminder")
        val first = repo.state.value.saved.surveys.first { it.important }
        ui.click("稍后查看问卷")
        ui.await("Reminder was not recorded") { repo.state.value.local(first.id).reminded }
        val calls = api.lists.get()
        SystemClock.sleep(1700); assertFalse(ui.hasText("有一份新问卷"))
        device.pressHome()
        ui.await("Main activity did not stop before starting a new visit") {
            var stopped = false
            ui.onMain { stopped = ui.main?.lifecycle?.currentState == Lifecycle.State.CREATED }
            stopped
        }
        ui.onMain { requireNotNull(ui.main).startActivity(Intent(ui.main, com.k2767.course.MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) }
        ui.waitText("有一份新问卷"); ui.click("关闭轻提醒")
        ui.await("Reminder opt-out was not saved") { !repo.state.value.saved.remindersEnabled }
        assertEquals("Foreground resumed within 5 minutes must not fetch again", calls, api.lists.get())
        api.offline = true; openCenter(ui); ui.waitText("正在显示本机缓存")
        ui.screenshot("survey-offline-cache")
        ui.click(first.title); ui.click("开始填写"); ui.waitText("暂时无法确认问卷状态，请联网后重试")
        assertFalse(ui.foreground is SurveyWebViewActivity)
        ui.back(); api.offline = false; ui.click("刷新问卷")
        ui.await("Retry did not clear the offline state") { repo.state.value.error == null && !repo.state.value.refreshing }
        ui.screenshot("survey-retry-success")
    }

    @Test fun webViewLoadsRestoresUploadsAndRemainsIdleWithoutPolling() = withSurveys { ui, api, repo ->
        openCenter(ui)
        val survey = repo.state.value.saved.surveys.first { it.pinned }
        ui.click(survey.title); ui.click("开始填写")
        ui.await("Survey WebView did not open", 25000) { ui.foreground is SurveyWebViewActivity }
        var activity = ui.foreground as SurveyWebViewActivity
        var web = findWebView(ui, activity)
        ui.await("Official WJX page did not load", 45000) {
            js(ui, web, "document.readyState === 'complete' && document.body.innerText.length > 30") == "true" &&
                !ui.hasText("网页暂时无法加载")
        }
        assertEquals(survey.url, activity.intent.getStringExtra(SurveyWebViewActivity.EXTRA_URL))
        assertTrue(js(ui, web, "location.hostname").contains("wjx."))
        ui.screenshot("survey-web-wjx")
        ui.onMain { activity.recreate() }
        ui.await("Survey WebView activity did not recreate") { ui.foreground is SurveyWebViewActivity && ui.foreground !== activity }
        activity = ui.foreground as SurveyWebViewActivity
        web = findWebView(ui, activity)
        ui.await("WebView state did not restore", 45000) { js(ui, web, "document.readyState === 'complete' && document.body.innerText.length > 30") == "true" }
        val restoredUrl = js(ui, web, "location.href")
        device.pressHome(); SystemClock.sleep(1000)
        ui.onMain { activity.startActivity(Intent(activity, SurveyWebViewActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_REORDER_TO_FRONT)) }
        ui.await("WebView did not regain window focus") { var focused = false; ui.onMain { focused = activity.hasWindowFocus() }; focused }
        assertEquals(restoredUrl, js(ui, web, "location.href"))
        if (android.os.Build.VERSION.SDK_INT >= 33) {
            val requests = api.lists.get()
            val start = SystemClock.elapsedRealtime()
            repeat(4) {
                SystemClock.sleep(30_250)
                android.util.Log.i("SurveyValidation", "WebView idle ${SystemClock.elapsedRealtime() - start} ms; list calls=${api.lists.get()}")
            }
            assertTrue(SystemClock.elapsedRealtime() - start >= 120_000)
            assertEquals("Survey list was polled while idle", requests, api.lists.get())
            assertEquals(restoredUrl, js(ui, web, "location.href"))
        }
        ui.screenshot("survey-web-restored-idle")

        val clipboard = context.getSystemService(ClipboardManager::class.java)
        val previousClip = clipboard.primaryClip
        try {
            ui.click("问卷网页菜单"); ui.click("复制问卷链接")
            ui.onMain { assertEquals(survey.url, clipboard.primaryClip?.getItemAt(0)?.text?.toString()) }
        } finally { ui.onMain { if (previousClip != null) clipboard.setPrimaryClip(previousClip) else clipboard.clearPrimaryClip() } }
        val shared = captureIntent(Intent.ACTION_CHOOSER) { ui.click("问卷网页菜单"); ui.click("分享问卷") }
        @Suppress("DEPRECATION") val send = shared.getParcelableExtra<Intent>(Intent.EXTRA_INTENT)
        assertEquals(Intent.ACTION_SEND, send?.action)
        assertEquals("text/plain", send?.type)
        assertEquals("${survey.title}\n${survey.url}", send?.getStringExtra(Intent.EXTRA_TEXT))
        val browserIntent = captureIntent(Intent.ACTION_VIEW) { ui.click("问卷网页菜单"); ui.click("在浏览器打开") }
        assertEquals(survey.url, browserIntent.dataString)

        // Exercise the actual WebChromeClient and Activity Result callback using a test-only form.
        ui.onMain { web.loadDataWithBaseURL("https://www.wjx.cn/survey-validation/", """
            <!doctype html><html lang="zh"><meta name="viewport" content="width=device-width,initial-scale=1">
            <style>body{font:18px sans-serif;padding:24px}button{padding:18px;font:inherit}</style>
            <h1>附件上传验证</h1><p>本页仅用于本机验证，不提交问卷答案。</p>
            <input id="files" type="file" multiple accept="text/plain" style="display:none">
            <button onclick="document.getElementById('files').click()">选择验证文件</button>
            <p id="result">等待选择</p>
            <script>document.getElementById('files').onchange=async function(){
              const files=Array.from(this.files);const texts=await Promise.all(files.map(f=>f.text()));
              document.getElementById('result').textContent=files.length+' files: '+texts.join('|');
            };</script></html>
        """.trimIndent(), "text/html", "UTF-8", null) }
        ui.waitText("附件上传验证")
        val chooserObserved = AtomicReference<Intent>()
        val observer = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action == Intent.ACTION_GET_CONTENT) chooserObserved.set(Intent(intent))
                return null
            }
        }
        instrumentation.addMonitor(observer)
        try {
            ui.click("选择验证文件")
            ui.await("System file picker was not launched") { chooserObserved.get() != null }
            SystemClock.sleep(1000)
            device.takeScreenshot(File(context.getExternalFilesDir(null), "flow-validation/survey-web-file-picker.png"))
            ui.shell("input -d ${activity.display!!.displayId} keyevent KEYCODE_BACK")
            ui.waitText("附件上传验证")
            assertEquals("true", js(ui, web, "document.getElementById('files').files.length === 0"))
        } finally { instrumentation.removeMonitor(observer) }

        val uris = mutableListOf<Uri>()
        try {
            listOf("survey-one", "survey-two").forEachIndexed { index, text ->
                val values = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "survey-validation-${SystemClock.uptimeMillis()}-$index.txt")
                    put(MediaStore.MediaColumns.MIME_TYPE, "text/plain")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Download/SurveyValidation")
                }
                val uri = requireNotNull(context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values))
                uris += uri
                context.contentResolver.openOutputStream(uri)!!.use { it.write(text.toByteArray()) }
            }
            val selected = Intent().apply {
                data = uris.first()
                clipData = ClipData.newUri(context.contentResolver, "Survey validation", uris.first()).apply { addItem(ClipData.Item(uris.last())) }
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            val upload = captureIntent(Intent.ACTION_GET_CONTENT, Instrumentation.ActivityResult(Activity.RESULT_OK, selected)) {
                ui.click("选择验证文件")
            }
            assertTrue("Multi-file selection was not requested", upload.getBooleanExtra(Intent.EXTRA_ALLOW_MULTIPLE, false))
            ui.await("Selected content URIs were not delivered to the WebView", 20000) {
                js(ui, web, "document.getElementById('result').textContent") == "\"2 files: survey-one|survey-two\""
            }
            ui.screenshot("survey-web-upload-complete")
            js(ui, web, "history.pushState({}, '', '#second-page'); true")
            ui.back()
            ui.await("Web back did not restore the previous page") { js(ui, web, "location.hash") != "\"#second-page\"" }
            assertSame(activity, ui.foreground)
            ui.click("关闭问卷"); ui.waitText("问卷详情")
        } finally { uris.forEach { context.contentResolver.delete(it, null, null) } }
    }

    private fun findWebView(ui: DemoUiDriver, activity: Activity): WebView {
        var found: WebView? = null
        fun find(view: View): WebView? = if (view is WebView) view else
            (view as? ViewGroup)?.let { group -> (0 until group.childCount).firstNotNullOfOrNull { find(group.getChildAt(it)) } }
        ui.await("No WebView attached") { ui.onMain { found = find(activity.window.decorView) }; found != null }
        return requireNotNull(found)
    }

    private fun js(ui: DemoUiDriver, web: WebView, script: String): String {
        val result = AtomicReference<String>()
        val latch = CountDownLatch(1)
        ui.onMain { web.evaluateJavascript(script) { result.set(it); latch.countDown() } }
        assertTrue("JavaScript callback timed out", latch.await(5, TimeUnit.SECONDS))
        return result.get()
    }

    private fun captureIntent(action: String, result: Instrumentation.ActivityResult = Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), block: () -> Unit): Intent {
        val captured = AtomicReference<Intent>()
        val monitor = object : Instrumentation.ActivityMonitor() {
            override fun onStartActivity(intent: Intent): Instrumentation.ActivityResult? {
                if (intent.action != action) return null
                captured.set(Intent(intent))
                return result
            }
        }
        instrumentation.addMonitor(monitor)
        try { block(); return requireNotNull(captured.get()) { "Intent $action was not launched" } }
        finally { instrumentation.removeMonitor(monitor) }
    }
}
