package com.tyust.course

import android.app.Activity
import android.app.Application
import android.content.ComponentName
import android.content.pm.ActivityInfo
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [32], application = Application::class)
class WindowOrientationTest {
    @Test
    @Config(qualifiers = "sw360dp-w360dp-h800dp")
    fun phoneKeepsPortraitPolicy() {
        val controller = Robolectric.buildActivity(Activity::class.java).create()
        try {
            controller.get().applyAdaptiveOrientation()
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, controller.get().requestedOrientation)
        } finally { controller.destroy() }
    }

    @Test
    @Config(qualifiers = "sw600dp-w600dp-h900dp")
    fun tabletReleasesAnExistingPortraitLock() {
        val controller = Robolectric.buildActivity(Activity::class.java).create()
        try {
            controller.get().requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_PORTRAIT
            controller.get().applyAdaptiveOrientation()
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, controller.get().requestedOrientation)
        } finally { controller.destroy() }
    }

    @Test
    @Config(qualifiers = "sw840dp-w840dp-h1000dp")
    fun recreatedWideWindowUsesTheCurrentResourceConfiguration() {
        val wide = Robolectric.buildActivity(Activity::class.java).create()
        wide.get().applyAdaptiveOrientation()
        assertEquals(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, wide.get().requestedOrientation)
        wide.destroy()
        RuntimeEnvironment.setQualifiers("sw411dp-w411dp-h900dp")
        val phone = Robolectric.buildActivity(Activity::class.java).create()
        try {
            phone.get().applyAdaptiveOrientation()
            assertEquals(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT, phone.get().requestedOrientation)
        } finally { phone.destroy() }
    }

    /** 问卷 WebView 已随问卷中心移除，当前全量清单是四个全屏 Activity。 */
    @Test
    fun manifestLeavesOrientationToRuntimeForAllFullscreenActivities() {
        val app = RuntimeEnvironment.getApplication()
        for (name in listOf("MainActivity", "LoginActivity", "CookieWebViewActivity",
                "AcademicWebViewActivity")) {
            val info = app.packageManager.getActivityInfo(ComponentName(app.packageName, "com.tyust.course.$name"), 0)
            assertEquals(name, ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED, info.screenOrientation)
        }
    }
}
