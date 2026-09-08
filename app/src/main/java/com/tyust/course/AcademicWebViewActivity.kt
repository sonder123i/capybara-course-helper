package com.tyust.course

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.os.Build
import android.webkit.CookieManager
import android.webkit.WebResourceRequest
import android.webkit.WebView
import android.webkit.WebViewClient
import android.webkit.WebResourceResponse
import android.webkit.WebResourceError
import android.webkit.WebChromeClient
import com.tyust.course.academic.AcademicUrlPolicy
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.tyust.course.ui.system.GlassPageScaffold
import com.tyust.course.ui.system.SystemIconButton
import com.tyust.course.ui.system.SystemPrimaryButton
import com.tyust.course.ui.theme.CourseSelectorTheme

/**
 * WebView fallback for captcha, slider and SSO pages. It deliberately has no
 * JavaScript bridge and only follows explicitly configured school hosts.
 */
class AcademicWebViewActivity : ComponentActivity() {
    companion object {
        const val EXTRA_START_URL = "academic_webview_start_url"
        const val EXTRA_ALLOWED_HOSTS = "academic_webview_allowed_hosts"
        const val EXTRA_COOKIE_RESULT = CookieWebViewActivity.EXTRA_COOKIE_RESULT
        const val EXTRA_COOKIE_URL = "academic_cookie_url"
        const val EXTRA_PAGE_URL = "academic_page_url"
        private var suffixConfigured = false
    }

    private var webView: WebView? = null
    private var startUrl = ""
    private var cookieUrl = ""
    private var allowedHosts: Set<String> = emptySet()
    private var loadingProgress by mutableFloatStateOf(0f)
    private var pageError by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        startUrl = intent.getStringExtra(EXTRA_START_URL).orEmpty()
        cookieUrl = intent.getStringExtra(EXTRA_COOKIE_URL).orEmpty().ifBlank { startUrl }
        allowedHosts = (intent.getStringArrayListExtra(EXTRA_ALLOWED_HOSTS).orEmpty())
            .map { normalizeHost(it) }
            .filter { it.isNotBlank() }
            .toSet()
        if (!isAllowed(Uri.parse(startUrl)) || !isAllowed(Uri.parse(cookieUrl))) {
            Toast.makeText(this, "教务地址不在允许范围内", Toast.LENGTH_LONG).show()
            setResult(Activity.RESULT_CANCELED)
            finish()
            return
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P && !suffixConfigured) {
            WebView.setDataDirectorySuffix("academic")
            suffixConfigured = true
        }
        val browser = createWebView()
        webView = browser
        setContent {
            CourseSelectorTheme {
                BackHandler { navigateBack() }
                GlassPageScaffold(
                    title = "教务网页登录",
                    subtitle = Uri.parse(startUrl).host,
                    modifier = Modifier.imePadding(),
                    onBack = ::navigateBack,
                    actions = {
                        SystemIconButton(Icons.Default.Refresh, "刷新网页", { browser.reload() })
                    }
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding).padding(horizontal = 12.dp)) {
                        if (loadingProgress < 1f) {
                            LinearProgressIndicator(progress = { loadingProgress }, modifier = Modifier.fillMaxWidth())
                        }
                        AndroidView(
                            factory = { browser },
                            modifier = Modifier.weight(1f).fillMaxWidth().clip(RoundedCornerShape(20.dp))
                        )
                        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                            Text(
                                text = pageError ?: "完成学校验证后，点“完成登录”返回应用",
                                style = MaterialTheme.typography.bodySmall,
                                color = if (pageError != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            SystemPrimaryButton("完成登录", ::finishWithCookie, Modifier.fillMaxWidth())
                        }
                    }
                }
            }
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            CookieManager.getInstance().removeAllCookies { browser.loadUrl(startUrl) }
        } else {
            // Before API 28 WebView has no per-process storage suffix. Clear only
            // the configured school cookies instead of unrelated browser sessions.
            for (host in allowedHosts) {
                val url = Uri.parse(startUrl).scheme + "://" + host + "/"
                CookieManager.getInstance().getCookie(url).orEmpty().split(';').forEach { part ->
                    val name = part.substringBefore('=').trim()
                    if (name.isNotEmpty()) CookieManager.getInstance().setCookie(url, name + "=; Max-Age=0; Path=/")
                }
            }
            browser.loadUrl(startUrl)
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    private fun createWebView(): WebView = WebView(this).apply {
        val webViewInstance = this
        settings.javaScriptEnabled = true
        settings.domStorageEnabled = true
        settings.allowFileAccess = false
        settings.allowContentAccess = false
        settings.mixedContentMode = android.webkit.WebSettings.MIXED_CONTENT_NEVER_ALLOW
        CookieManager.getInstance().apply {
            setAcceptCookie(true)
            setAcceptThirdPartyCookies(webViewInstance, false)
        }
        webChromeClient = object : WebChromeClient() {
            override fun onProgressChanged(view: WebView, newProgress: Int) {
                loadingProgress = newProgress / 100f
            }
        }
        webViewClient = object : WebViewClient() {
            override fun onPageStarted(view: WebView, url: String, favicon: android.graphics.Bitmap?) {
                pageError = null
                loadingProgress = 0f
            }
            override fun onPageFinished(view: WebView, url: String) {
                loadingProgress = 1f
            }
            override fun onReceivedError(view: WebView, request: WebResourceRequest, error: WebResourceError) {
                if (request.isForMainFrame) {
                    pageError = "网页暂时无法加载，请点击右上角刷新"
                    loadingProgress = 1f
                }
            }
            override fun shouldInterceptRequest(view: WebView, request: WebResourceRequest): WebResourceResponse? {
                if (request.url.scheme in setOf("data", "blob", "about") || isAllowed(request.url)) return null
                return WebResourceResponse("text/plain", "UTF-8", 403, "Blocked", emptyMap(), java.io.ByteArrayInputStream(ByteArray(0)))
            }
            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                if (!isAllowed(request.url)) {
                    Toast.makeText(this@AcademicWebViewActivity, "已阻止访问未授权域名", Toast.LENGTH_SHORT).show()
                    return true
                }
                return false
            }

            @Deprecated("API 21 compatibility")
            override fun shouldOverrideUrlLoading(view: WebView, url: String): Boolean {
                val uri = Uri.parse(url)
                if (!isAllowed(uri)) {
                    Toast.makeText(this@AcademicWebViewActivity, "已阻止访问未授权域名", Toast.LENGTH_SHORT).show()
                    return true
                }
                return false
            }
        }
    }

    private fun finishWithCookie() {
        val cookie = CookieManager.getInstance().getCookie(cookieUrl).orEmpty().trim()
        if (cookie.isBlank()) {
            Toast.makeText(this, "请先在网页中登录教务账号", Toast.LENGTH_SHORT).show()
            return
        }
        CookieManager.getInstance().flush()
        setResult(Activity.RESULT_OK, Intent().putExtra(EXTRA_COOKIE_RESULT, cookie).putExtra(EXTRA_PAGE_URL, webView?.url))
        finish()
    }

    private fun navigateBack() {
        if (webView?.canGoBack() == true) webView?.goBack()
        else { setResult(Activity.RESULT_CANCELED); finish() }
    }

    private fun isAllowed(uri: Uri): Boolean {
        return AcademicUrlPolicy.isAllowed(uri.toString(), Uri.parse(startUrl).scheme.orEmpty(), allowedHosts)
    }

    private fun normalizeHost(raw: String): String {
        val value = raw.trim().removePrefix("http://").removePrefix("https://").substringBefore('/')
        return value.lowercase().trim('.')
    }

    override fun onDestroy() {
        webView?.apply { stopLoading(); destroy() }
        webView = null
        super.onDestroy()
    }
}
