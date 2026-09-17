package com.k2767.course.academic

import android.content.BroadcastReceiver
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.IntentFilter
import android.accessibilityservice.AccessibilityServiceInfo
import android.os.Build
import android.os.SystemClock
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.content.ContextCompat
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.uiautomator.By
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import com.k2767.course.AcademicWebViewActivity
import com.k2767.course.manager.StudentLimitManager
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.net.ServerSocket
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicInteger
import kotlin.concurrent.thread

@RunWith(AndroidJUnit4::class)
class AcademicCompatibilityTest {
    @Test fun studentLimitAllowsThreeAccountsAcrossSchoolsAndRejectsAFourth() {
        val base = InstrumentationRegistry.getInstrumentation().targetContext
        val isolated = object : ContextWrapper(base) {
            override fun getSharedPreferences(name: String, mode: Int) =
                base.getSharedPreferences("academic_student_limit_test_" + name, mode)
        }
        StudentLimitManager.clearRecords(isolated)
        try {
            for (index in 1..3) {
                assertTrue(StudentLimitManager.recordStudent(isolated, "school$index", "School $index", "Student $index", "S$index"))
            }
            assertEquals(3, StudentLimitManager.getUsedCount(isolated))
            assertFalse(StudentLimitManager.recordStudent(isolated, "school4", "School 4", "Student 4", "S4"))
            assertTrue(StudentLimitManager.checkCanUseStudent(isolated, "school2", "Student 2", "S2").alreadyBound)
            assertTrue(StudentLimitManager.recordStudent(isolated, "school2", "School 2", "Student 2", "S2"))
            assertEquals(3, StudentLimitManager.getUsedCount(isolated))
        } finally { StudentLimitManager.clearRecords(isolated) }
    }

    @Test fun qzScriptParsingWorksWithAndroidRegex() {
        val html = """
            <script>
            var table = { sAjaxSource: '/jsxsd/list'
            };
            function xsxkOper(jx0404id, kcid) {
                $.ajax({
                    url: '/jsxsd/select',
                    type: 'POST',
                    data: { jx0404id: jx0404id, kcid: kcid }
                });
            }
            function xstkOper(jx0404id) {
                $.ajax({
                    url: '/jsxsd/drop',
                    type: 'POST',
                    data: { jx0404id: jx0404id }
                });
            }
            </script>
        """.trimIndent()
        val parsed = QzScriptParser.parse(html, "https://jw.example.edu.cn/jsxsd/category")!!
        assertEquals("https://jw.example.edu.cn/jsxsd/list", parsed.listUrl)
        assertEquals("POST", parsed.submitMethod)
        assertEquals(setOf("jx0404id", "kcid"), parsed.bodyFields)
        assertEquals("S1", QzScriptParser.expand(parsed.submitFields.getValue("jx0404id"), mapOf("jx0404id" to "S1")))
        assertEquals(setOf("jx0404id"), parsed.dropFields.keys)
    }

    @Test fun privateStatusBroadcastWorksOnThisAndroidVersion() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val received = CountDownLatch(1)
        val action = context.packageName + ".ACADEMIC_COMPAT_TEST"
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) { if (intent?.action == action) received.countDown() }
        }
        ContextCompat.registerReceiver(context, receiver, IntentFilter(action), ContextCompat.RECEIVER_NOT_EXPORTED)
        try {
            context.sendBroadcast(Intent(action).setPackage(context.packageName))
            assertTrue("Private status broadcast was not delivered", received.await(5, TimeUnit.SECONDS))
        } finally { context.unregisterReceiver(receiver) }
    }

    @Test fun queueStorageIsScopedToTheAccount() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = AcademicGrabQueueStore(context)
        val a = "academic_compat_test_a"
        val b = "academic_compat_test_b"
        store.replace(a, emptyList()); store.replace(b, emptyList())
        try {
            assertTrue(store.add(AcademicGrabItem(a, "test", "Course", stableCourseId="C", stableSectionId="S")))
            assertEquals(1, AcademicGrabQueueStore(context).items(a).size)
            assertTrue(store.items(b).isEmpty())
        } finally { store.replace(a, emptyList()); store.replace(b, emptyList()) }
    }

    @Test fun manualQueueDeduplicatesWithinAnAccountAndKeepsDifferentRounds() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = AcademicGrabQueueStore(context)
        val account = "academic_manual_queue_test"
        try {
            store.replace(account, emptyList())
            val manual = AcademicGrabItem(account, "test", "Course", "Teacher", "Monday")
            assertTrue(store.add(manual))
            assertFalse(store.add(manual))
            val pinned = manual.copy(stableCourseId = "C", stableSectionId = "S", scopeId = "round-a")
            assertTrue(store.add(pinned))
            assertFalse(store.add(pinned))
            assertTrue(store.add(pinned.copy(scopeId = "round-b")))
            assertEquals(3, AcademicGrabQueueStore(context).items(account).size)
        } finally { store.replace(account, emptyList()) }
    }

    @Test fun targetsModesAndStatusesPersistWithoutMixingAccountsOrRounds() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = AcademicGrabQueueStore(context)
        val account = "academic_parity_queue_test"
        val other = "academic_parity_queue_other"
        val a = AcademicGrabItem(account, "test", "Course", stableCourseId = "C", stableSectionId = "S", scopeId = "a", useExactMatch = false)
        val b = a.copy(scopeId = "b", useExactMatch = true)
        try {
            store.replace(account, listOf(a, b)); store.setTarget(account, a)
            store.resetStatuses(account, listOf(a, b)); store.setStatus(account, a, "GRABBING")
            val reloaded = AcademicGrabQueueStore(context)
            assertEquals(listOf(a, b), reloaded.items(account))
            assertFalse(AcademicGrabItem(account, "test", "Manual course").useExactMatch)
            assertEquals(a, reloaded.target(account))
            assertNull(reloaded.target(other))
            assertEquals("GRABBING", reloaded.statuses(account)[a.key])
            assertEquals("WAITING", reloaded.statuses(account)[b.key])
            assertTrue(reloaded.statuses(other).isEmpty())
            try { store.setTarget(other, a); fail("A target cannot belong to another account") } catch (_: IllegalArgumentException) { }
        } finally {
            store.replace(account, emptyList()); store.setTarget(account, null); store.resetStatuses(account, emptyList())
        }
    }

    @Test fun manualTeachingClassesPersistAndHaveIndependentQueueStatuses() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val store = AcademicGrabQueueStore(context)
        val account = "academic_teaching_class_queue_test"
        val basketball = AcademicGrabItem(account, "test", "体育（一）", sectionName = "篮球0003")
        val football = basketball.copy(sectionName = "足球0003")
        val anyClass = basketball.copy(sectionName = "")
        try {
            store.replace(account, emptyList())
            assertTrue(store.add(basketball))
            assertFalse(store.add(basketball))
            assertTrue(store.add(football))
            assertTrue(store.add(anyClass))
            store.setTarget(account, basketball)
            store.resetStatuses(account, listOf(basketball, football, anyClass))
            store.setStatus(account, basketball, "SUCCESS")
            val reloaded = AcademicGrabQueueStore(context)
            assertEquals(listOf(basketball, football, anyClass), reloaded.items(account))
            assertEquals(basketball, reloaded.target(account))
            assertFalse(reloaded.add(football))
            assertEquals("SUCCESS", reloaded.statuses(account)[basketball.key])
            assertEquals("WAITING", reloaded.statuses(account)[football.key])
            assertEquals("WAITING", reloaded.statuses(account)[anyClass.key])
        } finally {
            store.replace(account, emptyList()); store.setTarget(account, null); store.resetStatuses(account, emptyList())
        }
    }

    @Test fun aLegacyQueueEntryKeepsItsStatusAndHasNoTeachingClassConstraint() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val prefs = context.getSharedPreferences("academic_grab_queue", Context.MODE_PRIVATE)
        val account = "academic_legacy_teaching_class_test"
        val legacyKey = "4:test|0:|0:|0:|6:Course|7:Teacher|6:Monday"
        try {
            prefs.edit()
                .putString(account, """[{"schoolId":"test","courseName":"Course","teacher":"Teacher","time":"Monday"}]""")
                .putString("statuses:$account", JSONObject(mapOf(legacyKey to "WAITING")).toString()).apply()
            val store = AcademicGrabQueueStore(context)
            val restored = store.items(account).single()
            assertEquals("", restored.sectionName)
            assertEquals(legacyKey, restored.key)
            assertEquals("WAITING", store.statuses(account)[restored.key])
            assertFalse(restored.useExactMatch)
        } finally { prefs.edit().remove(account).remove("statuses:$account").apply() }
    }

    @Test fun exactSchedulesPersistAndCancelIndependentlyForEachAccount() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        val scheduler = AcademicGrabScheduler(context)
        val a = "academic_schedule_test_a"
        val b = "academic_schedule_test_b"
        val future = System.currentTimeMillis() + TimeUnit.DAYS.toMillis(2)
        try {
            scheduler.cancel(a); scheduler.cancel(b)
            if (!scheduler.canScheduleExactly()) {
                try { scheduler.schedule(a, a, future, GrabRunPolicy(), false); fail("Missing exact-alarm permission must be surfaced") }
                catch (_: IllegalStateException) { assertFalse(scheduler.isScheduled(a)) }
                return
            }
            scheduler.schedule(a, a, future, GrabRunPolicy(750, 3), false)
            scheduler.schedule(b, b, future, GrabRunPolicy(1250, 5), false)
            assertTrue(AcademicGrabScheduler(context).isScheduled(a))
            assertTrue(AcademicGrabScheduler(context).isScheduled(b))
            scheduler.cancel(a)
            assertFalse(scheduler.isScheduled(a))
            assertTrue(scheduler.isScheduled(b))
        } finally { scheduler.cancel(a); scheduler.cancel(b) }
    }

    @Test fun loginBrowserLoadsCrossOriginResourcesAndAllowsManualNavigation() {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val context = instrumentation.targetContext
        val automation = instrumentation.uiAutomation
        val originalServiceFlags = automation.serviceInfo.flags
        automation.serviceInfo = automation.serviceInfo.apply {
            flags = flags or AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS
        }
        val pageRendered = CountDownLatch(1)
        val blockedRequests = AtomicInteger()
        val blocked = ServerSocket(0)
        val school = ServerSocket(0)
        val blockedWorker = thread(isDaemon = true) {
            try {
                while (!blocked.isClosed) blocked.accept().use { socket ->
                    socket.soTimeout = 5000
                    val input = socket.getInputStream().bufferedReader()
                    input.readLine()
                    while (!input.readLine().isNullOrEmpty()) {}
                    blockedRequests.incrementAndGet()
                    val body = "<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head><body><h1>ACADEMIC_SSO_READY</h1></body></html>".toByteArray()
                    socket.getOutputStream().write(("HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=UTF-8\r\nContent-Length: ${body.size}\r\nConnection: close\r\n\r\n").toByteArray() + body)
                }
            } catch (_: Exception) {}
        }
        val serverWorker = thread(isDaemon = true) {
            try {
                while (!school.isClosed) school.accept().use { socket ->
                    socket.soTimeout = 5000
                    val input = socket.getInputStream().bufferedReader()
                    val path = input.readLine()?.substringAfter(' ')?.substringBefore(' ')
                    while (!input.readLine().isNullOrEmpty()) {}
                    val body = if (path == "/rendered") {
                        pageRendered.countDown()
                        "ok".toByteArray()
                    } else ("<html><head><meta name='viewport' content='width=device-width,initial-scale=1'></head>" +
                        "<body><h1 style='font-size:18px'>ACADEMIC_WEBVIEW_READY</h1>" +
                        "<img src='http://127.0.0.1:" + blocked.localPort + "/blocked'>" +
                        "<script>window.addEventListener('load',function(){requestAnimationFrame(function(){" +
                        "requestAnimationFrame(function(){fetch('/rendered')})})})</script></body></html>").toByteArray()
                    val header = "HTTP/1.1 200 OK\r\nContent-Type: text/html; charset=UTF-8\r\nContent-Length: " + body.size + "\r\nConnection: close\r\n\r\n"
                    socket.getOutputStream().write(header.toByteArray() + body)
                }
            } catch (_: Exception) {}
        }
        try {
            val host = "127.0.0.1:" + school.localPort
            context.startActivity(Intent(context, AcademicWebViewActivity::class.java).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                putExtra(AcademicWebViewActivity.EXTRA_START_URL, "http://" + host + "/")
                putExtra(AcademicWebViewActivity.EXTRA_COOKIE_URL, "http://" + host + "/")
                putStringArrayListExtra(AcademicWebViewActivity.EXTRA_ALLOWED_HOSTS, arrayListOf(host))
            })
            val deadline = SystemClock.elapsedRealtime() + 30000
            var visible = false
            while (SystemClock.elapsedRealtime() < deadline) {
                // MuMu may place the Activity on a secondary display. WebView also
                // exposes text through virtual children rather than text search.
                val windows = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    val displays = automation.windowsOnAllDisplays
                    (0 until displays.size()).flatMap { displays.valueAt(it) }
                } else automation.windows
                visible = windows.any { containsText(it.root, "ACADEMIC_WEBVIEW_READY") } ||
                    containsText(automation.rootInActiveWindow, "ACADEMIC_WEBVIEW_READY")
                if (visible) break
                SystemClock.sleep(150)
            }
            assertTrue("The school page did not render in the login browser", visible)
            assertTrue("The page did not finish processing its subresources", pageRendered.await(5, TimeUnit.SECONDS))
            assertTrue("Interactive login must allow cross-origin school resources", blockedRequests.get() > 0)

            val device = UiDevice.getInstance(instrumentation)
            val address = device.wait(Until.findObject(By.clazz("android.widget.EditText")), 5000)
            assertNotNull("The browser needs an editable address/search bar", address)
            address.text = "http://127.0.0.2:${blocked.localPort}/login"
            device.findObject(By.desc("前往")).click()
            assertTrue("A user-entered SSO host must open", device.wait(Until.hasObject(By.text("ACADEMIC_SSO_READY")), 10000))
            val screenshot = java.io.File(context.getExternalFilesDir(null), "login-fixes/browser-manual-sso.png")
            screenshot.parentFile?.mkdirs()
            device.takeScreenshot(screenshot)
        } finally {
            automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            automation.performGlobalAction(android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_BACK)
            automation.serviceInfo = automation.serviceInfo.apply { flags = originalServiceFlags }
            school.close(); blocked.close()
            serverWorker.join(1000); blockedWorker.join(1000)
        }
    }

    @Suppress("DEPRECATION")
    private fun containsText(node: AccessibilityNodeInfo?, expected: String): Boolean {
        if (node == null) return false
        try {
            if (node.text?.contains(expected) == true || node.contentDescription?.contains(expected) == true) return true
            return (0 until node.childCount).any { containsText(node.getChild(it), expected) }
        } finally { node.recycle() }
    }
}
