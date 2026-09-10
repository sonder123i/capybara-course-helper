package com.tyust.course.ui

import android.app.Activity
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.tyust.course.LoginActivity
import com.tyust.course.MainActivity
import com.tyust.course.manager.UserManager
import com.tyust.course.network.CourseApiClient
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionFlowDeviceTest {
    private val expiryBody = "登录状态已过期，重新登录后可继续查询和选课。"
    @Test fun cookieUpdateClearsAllExpirySurfacesAndRejectsLateBroadcast() {
        DemoUiDriver().use { ui ->
            val user = UserManager.getInstance()
            ui.onMain { user.saveCookie("demo-session=before-expiry") }
            val expired = user.sessionState.token
            ui.onMain { CourseApiClient.getInstance().notifyCookieExpired(expired) }
            ui.waitText(expiryBody)
            ui.waitText("已加载的内容仍可查看")
            ui.screenshot("expiry-first")
            ui.click("稍后")
            ui.waitText(expiryBody, false)
            ui.waitText("需要重新登录")
            ui.onMain { user.saveCookie("demo-session=recovered") }
            ui.waitText("重新登录", false)
            ui.waitText("需要重新登录", false)
            assertNotEquals(expired, user.sessionState.token)
            ui.onMain { CourseApiClient.getInstance().notifyCookieExpired(expired) }
            SystemClock.sleep(500)
            assertFalse(user.sessionState.state.value.expired)
            assertFalse(ui.hasText(expiryBody))
            ui.screenshot("expiry-recovered")
        }
    }

    @Test fun expiryWaitsForPickerAndRecoveryCancelsThePendingPrompt() {
        DemoUiDriver().use { ui ->
            val user = UserManager.getInstance()
            ui.onMain { user.saveCookie("demo-session=fresh") }
            ui.navigate("成绩")
            ui.click("2025-2026-2")
            ui.waitText("2024-2025-1")
            ui.onMain { CourseApiClient.getInstance().notifyCookieExpired(user.sessionState.token) }
            SystemClock.sleep(700)
            assertFalse(ui.hasText(expiryBody))
            ui.onMain { user.saveCookie("demo-session=updated-while-picker-open") }
            ui.back()
            ui.waitText("2024-2025-1", false)
            assertFalse(ui.hasText(expiryBody))
            assertFalse(user.sessionState.state.value.expired)
        }
    }

    @Test fun cancellingAndCompletingLoginReturnToTheOriginalPage() {
        DemoUiDriver().use { ui ->
            val user = UserManager.getInstance()
            ui.onMain { user.saveCookie("demo-session=before-login") }
            ui.navigate("成绩")
            val original = ui.main
            ui.onMain { CourseApiClient.getInstance().notifyCookieExpired(user.sessionState.token) }
            ui.waitText(expiryBody)
            ui.click("重新登录")
            ui.await("Login must open for a result") { ui.foreground is LoginActivity }
            ui.waitText("login-screen")
            ui.screenshot("reauthentication")
            ui.back()
            ui.await("Cancel must return to the existing page") { ui.foreground === original }
            assertTrue(user.sessionState.state.value.expired)
            ui.waitText("需要重新登录")
            if (ui.hasText("重新登录")) ui.click("重新登录") else ui.click("需要重新登录")
            if (ui.foreground is MainActivity) ui.click("重新登录")
            ui.await("Login must reopen") { ui.foreground is LoginActivity }
            ui.onMain {
                user.saveCookie("demo-session=login-result")
                requireNotNull(ui.foreground).setResult(Activity.RESULT_OK)
                requireNotNull(ui.foreground).finish()
            }
            ui.await("Success must retain the original activity") { ui.foreground === original }
            ui.waitText("重新登录", false)
            ui.waitText("2025-2026-2")
            assertFalse(user.sessionState.state.value.expired)
            ui.screenshot("reauthentication-return")
        }
    }
}
