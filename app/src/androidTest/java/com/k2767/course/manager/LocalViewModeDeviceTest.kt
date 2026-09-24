package com.k2767.course.manager

import android.content.Context
import android.content.ContextWrapper
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.k2767.course.academic.AcademicTerm
import com.k2767.course.model.SchoolConfig
import com.k2767.course.schedule.CachedSchedule
import com.k2767.course.schedule.ScheduleCacheStore
import com.k2767.course.utils.RecoveryFailure
import com.k2767.course.utils.SessionRecoveryResult
import com.k2767.course.utils.SessionRenewer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * 离线查看的状态机。
 *
 * 跑在 debug 包上：uiPreview 一启动就自己 `startDemoSession`，两个模式互斥会被它先抢走。
 * 所有偏好都写在带前缀的隔离文件里，不碰手机上真实的登录态。
 */
@RunWith(AndroidJUnit4::class)
class LocalViewModeDeviceTest {
    private val instrumentation get() = InstrumentationRegistry.getInstrumentation()
    private val user get() = UserManager.getInstance()
    private val school = SchoolConfig("localview-test", "离线测试学校", "unused.invalid", "https")
        .apply { academicSystem = "auto" }
    private val term = AcademicTerm("2026-2027-1")
    private val timetable = """{"kbList":[{"kcmc":"高等数学","xqj":1,"jcs":"1-2","zcd":"1-16周"}]}"""
    private lateinit var isolated: ContextWrapper

    @Before
    fun isolatePreferencesAndResetSession() {
        val base = instrumentation.targetContext
        isolated = object : ContextWrapper(base) {
            override fun getApplicationContext(): Context = this
            override fun getSharedPreferences(name: String, mode: Int) =
                base.getSharedPreferences("local_view_test_$name", mode)
        }
        listOf("course_selector_prefs", "local_view_prefs", "schedule_cache").forEach { name ->
            isolated.getSharedPreferences(name, Context.MODE_PRIVATE).edit().clear().commit()
        }
        instrumentation.runOnMainSync {
            user.init(isolated)
            user.clearLoginState()
        }
    }

    @Test
    fun logoutKeepsTheCachedTimetableReachable() {
        val storageKey = signInWithCachedTimetable()
        user.clearLoginState()

        assertEquals("$OWNED_ACCOUNT_KEY", user.lastViewAccountKey)
        assertFalse("登出之后不许自称已登录", user.isLoggedIn)
        assertEquals(storageKey, user.localViewStorageKey)
        assertNotNull("登出把缓存变成了读不到的孤儿数据", cache().selected(user.localViewStorageKey, school.id, false))
    }

    @Test
    fun enteringLocalViewRestoresOwnershipButNotLogin() {
        val storageKey = signInWithCachedTimetable()
        user.clearLoginState()

        assertTrue(user.enterLocalView())

        assertTrue(user.isLocalViewMode)
        assertFalse("离线查看不是登录态", user.isLoggedIn)
        assertEquals("离线测试", user.studentName)
        assertEquals(storageKey, user.currentAccountStorageKey)
        assertFalse("离线查看不许留着教务 Cookie", user.hasSavedCookie())
        assertNull(isolated.getSharedPreferences("course_selector_prefs", Context.MODE_PRIVATE)
            .getString("is_logged_in", null))
    }

    @Test
    fun plainLogoutThenRestartStillLandsOnTheLoginScreen() {
        signInWithCachedTimetable()
        user.clearLoginState()
        restart()

        assertFalse("退出登录后重开不该自动回到登录态", user.isLoggedIn)
        assertFalse("没点过离线条目就不该在离线态", user.isLocalViewMode)
    }

    @Test
    fun localViewSurvivesARebootAndKeepsTheShardKey() {
        val storageKey = signInWithCachedTimetable()
        user.clearLoginState()
        assertTrue(user.enterLocalView())
        restart()

        assertTrue(user.isLocalViewMode)
        assertFalse(user.isLoggedIn)
        assertEquals(storageKey, user.currentAccountStorageKey)
    }

    @Test
    fun deletingTheOwnedAccountClosesTheOfflineDoor() {
        signInWithCachedTimetable()
        user.clearLoginState()
        assertTrue(user.enterLocalView())

        user.deleteAccount(OWNED_ACCOUNT_KEY)

        assertEquals("", user.lastViewAccountKey)
        assertFalse(user.isLocalViewMode)
        assertFalse(user.enterLocalView())
    }

    @Test
    fun offlineViewRefusesToKnockOnTheAcademicServer() {
        signInWithCachedTimetable()
        user.clearLoginState()
        assertTrue(user.enterLocalView())

        assertFalse("自动续期在离线态必须直接拒绝", SessionRenewer.canRenew())

        val results = CountDownLatch(1)
        var reason: RecoveryFailure? = null
        SessionRenewer.request(user.sessionState.token) { result ->
            if (result is SessionRecoveryResult.NeedsLogin) reason = result.reason
            results.countDown()
        }
        assertTrue(results.await(5, TimeUnit.SECONDS))
        assertEquals(RecoveryFailure.NoPassword, reason)
    }

    /** 建一个有缓存课表的登录会话，返回它的 storage key。 */
    private fun signInWithCachedTimetable(): String {
        user.currentSchool = school
        user.studentId = "25011040229"
        user.studentName = "离线测试"
        user.saveCookieLogin("JSESSIONID=offline-test")
        val storageKey = user.currentAccountStorageKey
        cache().save(storageKey, school.id, CachedSchedule(term, term, timetable, false))
        return storageKey
    }

    private fun restart() = instrumentation.runOnMainSync { user.init(isolated) }

    private fun cache() = ScheduleCacheStore(
        isolated.getSharedPreferences("schedule_cache", Context.MODE_PRIVATE)
    ) { term }

    companion object {
        private const val OWNED_ACCOUNT_KEY = "localview-test::25011040229"
    }
}
