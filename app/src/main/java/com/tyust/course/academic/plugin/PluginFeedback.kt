package com.tyust.course.academic.plugin

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import com.tyust.course.BuildConfig

/**
 * 反馈入口 = 本仓库的 GitHub Issue 新建页。
 *
 * 上游这里跳的是作者自建站点的 /feedback 网页表单；本发行版没有反馈后端
 * （见 SelfHostConfig.ENABLE_REMOTE_FEEDBACK），改成把版本、机型、插件、错误码
 * 直接预填进 Issue 正文，用户点一下就能提，不需要 App 自己维持任何服务。
 *
 * 不上传设备标识，不落地本地缓存；用户看得到自己提交了什么。
 */
object PluginFeedback {

    /**
     * GitHub 的 new-issue 页。`title` / `body` 两个 query 参数会被它读出来预填，
     * 其余参数（labels、template）依赖仓库配置，这里不用。
     */
    private const val ISSUES_URL = "https://github.com/sonder123i/capybara-course-helper/issues/new"

    fun url(pkg: PluginPackage? = null, errorCode: String? = null): Uri =
        Uri.parse(ISSUES_URL).buildUpon()
            .appendQueryParameter("title", title(pkg, errorCode))
            .appendQueryParameter("body", body(pkg, errorCode))
            .build()

    internal fun title(pkg: PluginPackage?, errorCode: String?): String = when {
        pkg != null -> "[插件反馈] ${pkg.manifest.name}"
        !errorCode.isNullOrBlank() -> "[Bug] $errorCode"
        else -> "[反馈]"
    }

    /**
     * 预填正文。刻意分成「环境」「正文」两段：环境由 App 填，正文留空给用户，
     * 否则用户往往写一句「用不了」就提交，缺机型版本根本没法查。
     */
    internal fun body(pkg: PluginPackage? = null, errorCode: String? = null): String = buildString {
        appendLine("### 环境")
        appendLine()
        appendLine("- App：${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})")
        appendLine("- 系统：Android ${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）")
        appendLine("- 机型：${Build.MANUFACTURER} ${Build.MODEL}")
        if (pkg != null) {
            appendLine("- 插件：${pkg.manifest.name} ${pkg.manifest.version}（${pkg.manifest.id}）")
        }
        if (!errorCode.isNullOrBlank()) {
            appendLine("- 错误码：$errorCode")
        }
        appendLine()
        appendLine("### 问题描述")
        appendLine()
        appendLine("<!-- 发生了什么、你期望它怎样 -->")
        appendLine()
        appendLine("### 复现步骤")
        appendLine()
        appendLine("1. ")
        appendLine("2. ")
    }

    fun open(context: Context, pkg: PluginPackage? = null, errorCode: String? = null): Boolean =
        runCatching {
            context.startActivity(Intent(Intent.ACTION_VIEW, url(pkg, errorCode)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        }.isSuccess
}
