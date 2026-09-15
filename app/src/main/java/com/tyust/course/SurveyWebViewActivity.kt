package com.tyust.course

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import android.view.ViewGroup
import android.webkit.RenderProcessGoneDetail
import android.webkit.ValueCallback
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.tyust.course.survey.SurveyLinks
import com.tyust.course.ui.system.GlassPageScaffold
import com.tyust.course.ui.system.SystemEmptyState
import com.tyust.course.ui.system.SystemIconButton
import com.tyust.course.ui.system.SystemSecondaryButton
import com.tyust.course.ui.theme.CourseSelectorTheme

/** Questionnaire browsing has no academic bridge, cookie harvesting or shared timer control. */
class SurveyWebViewActivity : ComponentActivity() {
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(com.tyust.course.manager.AppThemeCoordinator.wrapContext(newBase))
    }

    private var browser by mutableStateOf<WebView?>(null)
    private var loadingProgress by mutableFloatStateOf(0f)
    private var error by mutableStateOf<String?>(null)
    private var currentUrl by mutableStateOf("")
    private var questionnaireUrl = ""
    private var surveyTitle = "问卷填写"
    private var fileCallback: ValueCallback<Array<Uri>>? = null
    private val filePicker = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        val callback = fileCallback
        fileCallback = null
        val clips = result.data?.clipData
        // Some document providers return both data (the first file) and ClipData (all files).
        val selected = if (result.resultCode == RESULT_OK && clips != null && clips.itemCount > 0)
            Array(clips.itemCount) { clips.getItemAt(it).uri }
        else WebChromeClient.FileChooserParams.parseResult(result.resultCode, result.data)
        callback?.onReceiveValue(selected)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        questionnaireUrl = intent.getStringExtra(EXTRA_URL).orEmpty()
        surveyTitle = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "问卷填写" }
        if (!SurveyLinks.isAllowedInitialUrl(questionnaireUrl)) {
            Toast.makeText(this, "问卷链接无效", Toast.LENGTH_LONG).show()
            finish()
            return
        }
        currentUrl = questionnaireUrl
        val web = createBrowser()
        browser = web
        val restored = savedInstanceState?.getBundle(STATE_WEB)?.let(web::restoreState)
        if (restored == null) web.loadUrl(questionnaireUrl) else currentUrl = web.url ?: questionnaireUrl
        setContent {
            CourseSelectorTheme {
                BackHandler { navigateBack() }
                var menu by rememberSaveable { mutableStateOf(false) }
                GlassPageScaffold(title = "问卷填写", subtitle = Uri.parse(currentUrl).host,
                    modifier = Modifier.imePadding(), onBack = ::navigateBack,
                    actions = {
                        SystemIconButton(Icons.Outlined.Close, "关闭问卷", { finish() })
                        SystemIconButton(Icons.Outlined.Refresh, "刷新网页", ::reload)
                        Box {
                            SystemIconButton(Icons.Outlined.MoreVert, "问卷网页菜单", { menu = true })
                            DropdownMenu(menu, { menu = false }) {
                                DropdownMenuItem(text = { Text("复制问卷链接") }, onClick = { menu = false; copyLink() },
                                    leadingIcon = { Icon(Icons.Outlined.ContentCopy, null) })
                                DropdownMenuItem(text = { Text("分享问卷") }, onClick = { menu = false; shareLink() },
                                    leadingIcon = { Icon(Icons.Outlined.Share, null) })
                                DropdownMenuItem(text = { Text("在浏览器打开") }, onClick = { menu = false; openBrowser() },
                                    leadingIcon = { Icon(Icons.Outlined.OpenInBrowser, null) })
                            }
                        }
                    }) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                        if (loadingProgress < 1f && error == null) LinearProgressIndicator(progress = { loadingProgress }, modifier = Modifier.fillMaxWidth())
                        if (error != null) {
                            SystemEmptyState("网页暂时无法加载", error.orEmpty(), icon = Icons.Outlined.CloudOff,
                                action = { Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    SystemSecondaryButton("重试", ::reload)
                                    SystemSecondaryButton("浏览器打开", ::openBrowser)
                                } })
                        }
                        val view = browser
                        if (view != null) key(view) { AndroidView(factory = { view }, modifier = Modifier.fillMaxWidth().weight(1f)) }
                    }
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createBrowser(): WebView = WebView(this).apply {
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = true // Picker-granted content URIs support web uploads.
        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
        settings.javaScriptCanOpenWindowsAutomatically = false
        settings.setSupportMultipleWindows(false)
        settings.mediaPlaybackRequiresUserGesture = true
        webViewClient = object : WebViewClient() {
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                val scheme = request.url.scheme?.lowercase()
                if (scheme == "http" || scheme == "https") return false // Preserve normal authentication/short-link redirects.
                if (request.isForMainFrame && request.hasGesture() && scheme in setOf("mailto", "tel", "sms", "weixin", "alipays", "mqq")) {
                    runCatching { startActivity(Intent(Intent.ACTION_VIEW, request.url).addCategory(Intent.CATEGORY_BROWSABLE)) }
                        .onFailure { Toast.makeText(this@SurveyWebViewActivity, "未找到可打开此链接的应用", Toast.LENGTH_SHORT).show() }
                }
                return true
            }
            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) {
                error = null; loadingProgress = 0f
                currentUrl = url ?: questionnaireUrl
            }
            override fun onPageFinished(view: WebView, url: String?) { currentUrl = url ?: questionnaireUrl; loadingProgress = 1f }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, failure: WebResourceError) {
                if (request.isForMainFrame) { error = "请检查网络后重试，也可以用系统浏览器打开。"; loadingProgress = 1f }
            }
            override fun onReceivedHttpError(view: WebView, request: WebResourceRequest, response: WebResourceResponse) {
                if (request.isForMainFrame && response.statusCode >= 400) { error = "问卷网页返回 ${response.statusCode}，请稍后重试。"; loadingProgress = 1f }
            }
            override fun onRenderProcessGone(view: WebView, detail: RenderProcessGoneDetail): Boolean {
                view.post {
                    if (browser === view) {
                        (view.parent as? ViewGroup)?.removeView(view)
                        view.destroy(); browser = null
                        error = "网页进程已结束，请点重试恢复问卷。"; loadingProgress = 1f
                    }
                }
                return true
            }
        }
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) { loadingProgress = newProgress / 100f }
            override fun onShowFileChooser(view: WebView, callback: ValueCallback<Array<Uri>>, params: FileChooserParams): Boolean {
                fileCallback?.onReceiveValue(null)
                fileCallback = callback
                return try {
                    filePicker.launch(params.createIntent().addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION))
                    true
                } catch (_: ActivityNotFoundException) {
                    fileCallback = null; callback.onReceiveValue(null)
                    Toast.makeText(this@SurveyWebViewActivity, "设备上没有可用的文件选择器", Toast.LENGTH_LONG).show()
                    true
                }
            }
        }
    }

    private fun reload() {
        error = null; loadingProgress = 0f
        val web = browser
        if (web == null) browser = createBrowser().also { it.loadUrl(questionnaireUrl) }
        else if (web.url.isNullOrBlank()) web.loadUrl(questionnaireUrl) else web.reload()
    }
    private fun navigateBack() {
        val web = browser
        if (web?.canGoBack() == true) { error = null; web.goBack() } else finish()
    }
    private fun copyLink() {
        getSystemService(ClipboardManager::class.java).setPrimaryClip(ClipData.newPlainText(surveyTitle, questionnaireUrl))
        Toast.makeText(this, "问卷链接已复制", Toast.LENGTH_SHORT).show()
    }
    private fun shareLink() {
        runCatching { startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"; putExtra(Intent.EXTRA_TEXT, "$surveyTitle\n$questionnaireUrl")
        }, "分享问卷")) }.onFailure { Toast.makeText(this, "暂时无法打开分享面板", Toast.LENGTH_SHORT).show() }
    }
    private fun openBrowser() {
        runCatching { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(questionnaireUrl))) }
            .onFailure { Toast.makeText(this, "设备上没有可用的浏览器", Toast.LENGTH_SHORT).show() }
    }
    override fun onSaveInstanceState(outState: Bundle) {
        browser?.let { web -> outState.putBundle(STATE_WEB, Bundle().also { web.saveState(it) }) }
        super.onSaveInstanceState(outState)
    }
    override fun onResume() { super.onResume(); browser?.onResume() }
    override fun onPause() { browser?.onPause(); super.onPause() }
    override fun onDestroy() {
        fileCallback?.onReceiveValue(null); fileCallback = null
        browser?.let { web ->
            (web.parent as? ViewGroup)?.removeView(web)
            web.stopLoading(); web.destroy()
        }
        browser = null
        super.onDestroy()
    }
    companion object {
        const val EXTRA_URL = "survey_url"
        const val EXTRA_TITLE = "survey_title"
        private const val STATE_WEB = "survey_web_state"
    }
}
