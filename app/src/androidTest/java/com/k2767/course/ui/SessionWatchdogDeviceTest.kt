package com.k2767.course.ui

import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.k2767.course.manager.UserManager
import com.k2767.course.model.SchoolConfig
import com.k2767.course.utils.CookieWatchdog
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class SessionWatchdogDeviceTest {
    @Test fun unresolvedSchoolSurvivesTheThirtySecondMainThreadCheck() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val isolated = object : ContextWrapper(context) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) =
                baseContext.getSharedPreferences("watchdog_idle_test_$name", mode)
        }
        val user = UserManager.getInstance()
        val school = SchoolConfig("idle-test", "Pending school", "unused.invalid", "https").apply { academicSystem = "auto" }
        try {
            instrumentation.runOnMainSync {
                user.init(isolated)
                user.currentSchool = school
                CookieWatchdog.start(isolated, 1000L)
            }
            // Exercise the real Handler timer responsible for the reported crash.
            SystemClock.sleep(34_000)
            instrumentation.runOnMainSync {
                assertFalse(user.sessionState.state.value.expired)
                school.academicSystem = "unknown"
            }
            SystemClock.sleep(2200)
            instrumentation.runOnMainSync { assertFalse(user.sessionState.state.value.expired) }
            println("Thirty-second watchdog and unknown configuration survived on API ${Build.VERSION.SDK_INT}")
        } finally {
            instrumentation.runOnMainSync {
                CookieWatchdog.stop()
                user.init(context)
            }
            isolated.getSharedPreferences("course_selector_prefs", Context.MODE_PRIVATE).edit().clear().commit()
        }
    }
}
