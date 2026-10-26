package com.tyust.course.academic.plugin

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * 反馈落地在仓库的 Issue 新建页。这几条断言守的是「别再指回上游那个已切断的站点」：
 * 一旦有人把 ISSUES_URL 改回去，或者把参数从 query 挪进路径，测试会先红。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], application = android.app.Application::class)
class PluginFeedbackTest {

    @Test fun issueUrlPointsAtThisRepository() {
        val url = PluginFeedback.url()
        assertEquals("github.com", url.host)
        assertEquals("/sonder123i/capybara-course-helper/issues/new", url.path)
        assertEquals("[反馈]", url.getQueryParameter("title"))
        assertTrue(url.getQueryParameter("body")!!.contains("### 环境"))
    }

    @Test fun errorCodeLandsInTitleAndBody() {
        val url = PluginFeedback.url(errorCode = "APP_RUNTIME_ERROR")
        assertEquals("[Bug] APP_RUNTIME_ERROR", url.getQueryParameter("title"))
        val body = url.getQueryParameter("body")!!
        assertTrue(body.contains("错误码：APP_RUNTIME_ERROR"))
    }

    @Test fun bodyCarriesVersionAndLeavesRoomForTheUser() {
        val body = PluginFeedback.body()
        assertTrue(body.contains("App："))
        assertTrue(body.contains("系统：Android"))
        assertTrue(body.contains("### 问题描述"))
        assertTrue(body.contains("### 复现步骤"))
    }

    /** 没有插件、没有错误码时不能出现空行占位，否则用户提交的正文里全是「插件：null」。 */
    @Test fun plainFeedbackOmitsPluginAndErrorLines() {
        val body = PluginFeedback.body()
        assertFalse(body.contains("插件："))
        assertFalse(body.contains("错误码："))
        assertFalse(body.contains("null"))
    }
}
