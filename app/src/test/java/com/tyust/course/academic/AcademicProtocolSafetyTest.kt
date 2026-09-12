package com.tyust.course.academic

import kotlinx.coroutines.*
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.SocketPolicy
import org.junit.Assert.*
import org.junit.Test
import java.net.InetAddress
import java.security.KeyPairGenerator
import java.security.interfaces.RSAPublicKey
import javax.crypto.Cipher
import okio.ByteString.Companion.decodeBase64
import okio.ByteString.Companion.toByteString

class AcademicProtocolSafetyTest {
    @Test fun interruptedResponseBodiesRetryOnlyReadOnlyRequests() = runBlocking {
        val server = MockWebServer()
        // After a body disconnect OkHttp postpones the failed route. Dual-stack
        // localhost can then select an address family this server is not listening on.
        // Pin the listener and request to one endpoint so this isolates body retries.
        server.start(InetAddress.getByName("127.0.0.1"), 0)
        try {
            val school = AcademicCoreTest.testSchool(server, AcademicSystem.QZ).apply {
                domain = "127.0.0.1:${server.port}"
            }
            val session = AcademicSessionStore().session(school.id, "test", school.fullBasePath)
            val transport = AcademicHttpTransport(school, session)
            server.enqueue(MockResponse().setBody("partial data").setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
            server.enqueue(MockResponse().setBody("complete data"))
            assertEquals("complete data", transport.get(transport.appUrl("read")).text)
            assertEquals(2, server.requestCount)
            server.enqueue(MockResponse().setBody("partial write reply").setSocketPolicy(SocketPolicy.DISCONNECT_DURING_RESPONSE_BODY))
            try { transport.writeGet(transport.appUrl("write")); fail("An interrupted write body must stay unconfirmed") }
            catch (e: AcademicException) { assertEquals(AcademicStatus.RESULT_UNKNOWN, e.status) }
            assertEquals(3, server.requestCount)
        } finally { server.shutdown() }
    }

    private open class FakeAdapter(var result: AcademicStatus = AcademicStatus.RESULT_UNKNOWN) : AcademicProtocolAdapter {
        var writes = 0
        var offeredSection = "section"
        var enrolled = emptyList<SelectedCourse>()
        var failSelected = false
        override suspend fun login(credentials: Credentials) = LoginResult(AcademicStatus.SUCCESS)
        override suspend fun validateSession() = LoginResult(AcademicStatus.SUCCESS)
        override suspend fun loadCourseContext() = CourseContext(1, listOf(CourseScope("scope", "Scope")))
        override suspend fun listCourses(context: CourseContext, query: CourseQuery) = listOf(CourseOffer("course", "Course", scopeId="scope"))
        override suspend fun listSections(course: CourseOffer) = listOf(CourseSection(offeredSection, "course"))
        override suspend fun select(target: SelectionTarget): SelectionResult { writes++; return SelectionResult(result) }
        override suspend fun selected(context: CourseContext): List<SelectedCourse> {
            if (failSelected) throw AcademicException(AcademicStatus.PAGE_CHANGED, "invalid list")
            return enrolled
        }
        override suspend fun drop(target: SelectionTarget): OperationResult = error("A runner must never drop a course")
    }

    private val item = AcademicGrabItem("account", "school", "Course", stableCourseId="course", stableSectionId="section")

    @Test fun aClosedRoundWaitsWithoutSendingEnrollmentWrites() = runBlocking {
        val adapter = object : FakeAdapter() {
            override suspend fun loadCourseContext() = CourseContext(1, emptyList())
        }
        val events = mutableListOf<GrabRunEvent>()
        assertTrue(ProtocolGrabRunner(adapter).runUntilDone(item, GrabRunPolicy(maxAttempts = 1), events::add) is GrabRunEvent.Exhausted)
        assertTrue(events.any { it is GrabRunEvent.Waiting && it.status == AcademicStatus.ROUND_CLOSED })
        assertEquals(0, adapter.writes)
    }

    @Test fun aPinnedRoundWaitsForOpeningWhileOtherRoundsAreAvailable() = runBlocking {
        var contextReads = 0
        var courseReads = 0
        val adapter = object : FakeAdapter(AcademicStatus.SUCCESS) {
            override suspend fun loadCourseContext(): CourseContext {
                contextReads++
                val scopes = mutableListOf(CourseScope("other", "Other round"))
                if (contextReads >= 2) scopes += CourseScope("scope", "Requested round")
                return CourseContext(1, scopes)
            }
            override suspend fun listCourses(context: CourseContext, query: CourseQuery): List<CourseOffer> {
                courseReads++
                assertEquals("scope", query.scopeId)
                assertTrue(context.scopes.any { it.id == "scope" })
                return super.listCourses(context, query)
            }
        }
        val events = mutableListOf<GrabRunEvent>()
        val outcome = ProtocolGrabRunner(adapter).runUntilDone(item.copy(scopeId = "scope"),
            GrabRunPolicy(intervalMillis = 1, maxAttempts = 2), events::add)
        assertTrue(outcome is GrabRunEvent.Success)
        assertEquals(2, contextReads)
        assertEquals(1, courseReads)
        assertEquals(1, adapter.writes)
        assertTrue(events.any { it is GrabRunEvent.Waiting && it.status == AcademicStatus.ROUND_CLOSED })
        assertFalse(events.any { it is GrabRunEvent.Paused })
    }

    @Test fun aRoundClosingAtSubmissionWaitsAndReloadsBeforeRetrying() = runBlocking {
        var contextReads = 0
        val adapter = object : FakeAdapter() {
            override suspend fun loadCourseContext(): CourseContext {
                contextReads++
                return super.loadCourseContext()
            }
            override suspend fun select(target: SelectionTarget): SelectionResult {
                writes++
                return SelectionResult(if (writes == 1) AcademicStatus.ROUND_CLOSED else AcademicStatus.SUCCESS)
            }
        }
        val events = mutableListOf<GrabRunEvent>()
        val outcome = ProtocolGrabRunner(adapter).runUntilDone(item,
            GrabRunPolicy(intervalMillis = 1, maxAttempts = 2), events::add)
        assertTrue(outcome is GrabRunEvent.Success)
        assertEquals(2, contextReads)
        assertEquals(2, adapter.writes)
        assertTrue(events.any { it is GrabRunEvent.Waiting && it.status == AcademicStatus.ROUND_CLOSED })
        assertFalse(events.any { it is GrabRunEvent.Paused })
    }

    @Test fun anExpiredBackgroundSessionRenewsOnceBeforeResolvingFreshTargets() = runBlocking {
        var renewed = false
        var renewals = 0
        val adapter = object : FakeAdapter(AcademicStatus.SUCCESS) {
            override suspend fun loadCourseContext(): CourseContext {
                if (!renewed) throw AcademicException(AcademicStatus.SESSION_EXPIRED, "expired")
                return super.loadCourseContext()
            }
        }
        val runner = ProtocolGrabRunner(adapter) { renewals++; renewed = true; true }
        assertTrue(runner.runUntilDone(item, GrabRunPolicy(intervalMillis = 60_000, maxAttempts = 1)) {} is GrabRunEvent.Success)
        assertEquals(1, renewals)
        assertEquals(1, adapter.writes)
    }

    @Test fun aFailedSessionRenewalPausesWithoutRetryingThePassword() = runBlocking {
        var renewals = 0
        val adapter = object : FakeAdapter() {
            override suspend fun loadCourseContext(): CourseContext = throw AcademicException(AcademicStatus.SESSION_EXPIRED, "expired")
        }
        val runner = ProtocolGrabRunner(adapter) { renewals++; false }
        assertTrue(runner.runUntilDone(item, GrabRunPolicy(maxAttempts = 3)) {} is GrabRunEvent.Paused)
        assertEquals(1, renewals)
        assertEquals(0, adapter.writes)
    }

    @Test fun aMissingStableSectionCannotFallBackToAnotherClass() = runBlocking {
        val adapter = FakeAdapter().apply { offeredSection = "other" }
        assertTrue(ProtocolGrabRunner(adapter).runOnce(item, true) is GrabRunEvent.Paused)
        assertEquals(0, adapter.writes)
    }

    @Test fun anUnknownWriteIsCheckedAgainstTheExactSection() = runBlocking {
        val adapter = FakeAdapter().apply { enrolled = listOf(SelectedCourse("section", "Course", courseId="course", sectionId="section")) }
        assertTrue(ProtocolGrabRunner(adapter).runOnce(item, true) is GrabRunEvent.Success)
        assertEquals(1, adapter.writes)
        adapter.enrolled = listOf(SelectedCourse("other", "Course", courseId="course", sectionId="other"))
        assertTrue(ProtocolGrabRunner(adapter).runOnce(item, true) is GrabRunEvent.Paused)
        adapter.enrolled = listOf(SelectedCourse("course", "Course", courseId="course"))
        assertTrue(ProtocolGrabRunner(adapter).runOnce(item, true) is GrabRunEvent.Paused)
        adapter.failSelected = true
        assertTrue(ProtocolGrabRunner(adapter).runOnce(item, true) is GrabRunEvent.Paused)
    }

    @Test fun conflictsPauseAndAlreadySelectedCompletes() = runBlocking {
        assertTrue(ProtocolGrabRunner(FakeAdapter(AcademicStatus.CONFLICT)).runOnce(item, true) is GrabRunEvent.Paused)
        assertTrue(ProtocolGrabRunner(FakeAdapter(AcademicStatus.ALREADY_SELECTED)).runOnce(item, true) is GrabRunEvent.Success)
    }

    @Test fun oldZfQueueResolvesThePinnedClassAmongMultipleRows() = runBlocking {
        val adapter = object : FakeAdapter(AcademicStatus.SUCCESS) {
            override suspend fun listCourses(context: CourseContext, query: CourseQuery) = listOf("other", "section").map {
                CourseOffer("course", "Course", scopeId = "scope", raw = mapOf("sectionId" to it))
            }
            override suspend fun listSections(course: CourseOffer) = listOf(CourseSection(course.raw.getValue("sectionId"), course.stableId))
            override suspend fun select(target: SelectionTarget): SelectionResult {
                assertEquals("section", target.section.stableId)
                return super.select(target)
            }
        }
        assertTrue(ProtocolGrabRunner(adapter).runOnce(item, true) is GrabRunEvent.Success)
        assertEquals(1, adapter.writes)
    }

    @Test fun aPopupClassCanDifferFromItsParentCourseRow() = runBlocking {
        val adapter = object : FakeAdapter(AcademicStatus.SUCCESS) {
            override suspend fun listCourses(context: CourseContext, query: CourseQuery) =
                listOf(CourseOffer("course", "Course", scopeId = "scope", raw = mapOf("sectionId" to "parent", "popupUrl" to "https://school.example/classes")))
        }
        assertTrue(ProtocolGrabRunner(adapter).runOnce(item, true) is GrabRunEvent.Success)
        assertEquals(1, adapter.writes)
    }

    @Test fun aManualCourseRequiresExactlyOneMatchingTeacherAndTime() = runBlocking {
        val adapter = object : FakeAdapter(AcademicStatus.SUCCESS) {
            override suspend fun listSections(course: CourseOffer) = listOf(
                CourseSection("first", "course", teacher = "Teacher A", time = "Monday"),
                CourseSection("second", "course", teacher = "Teacher B", time = "Tuesday"))
        }
        val manual = item.copy(stableCourseId = "", stableSectionId = "")
        assertTrue(ProtocolGrabRunner(adapter).runOnce(manual, true) is GrabRunEvent.Paused)
        assertEquals(0, adapter.writes)
        assertTrue(ProtocolGrabRunner(adapter).runOnce(manual.copy(teacher = "Teacher B", time = "Tuesday"), true) is GrabRunEvent.Success)
        assertEquals(1, adapter.writes)
    }

    @Test fun smartModeTriesAnotherClassAfterCapacityOrConflictWithoutChangingTheCourse() = runBlocking {
        val submitted = mutableListOf<String>()
        val adapter = object : FakeAdapter() {
            override suspend fun listSections(course: CourseOffer) = listOf("full", "conflict", "available").map {
                CourseSection(it, "course", teacher = "Different teacher", time = "Different time")
            }
            override suspend fun select(target: SelectionTarget): SelectionResult {
                assertEquals("scope", target.course.scopeId)
                assertEquals("course", target.course.stableId)
                submitted += target.section.stableId
                return SelectionResult(when (target.section.stableId) {
                    "full" -> AcademicStatus.NO_CAPACITY; "conflict" -> AcademicStatus.CONFLICT; else -> AcademicStatus.SUCCESS
                })
            }
        }
        val result = ProtocolGrabRunner(adapter).runOnce(item.copy(scopeId = "scope", useExactMatch = false, teacher = "Previous teacher"), true)
        assertTrue(result is GrabRunEvent.Success)
        assertEquals(listOf("full", "conflict", "available"), submitted)
    }

    @Test fun smartModeDoesNotTryAnotherClassAfterAnUnknownWriteOrCaptcha() = runBlocking {
        for (status in listOf(AcademicStatus.RESULT_UNKNOWN, AcademicStatus.CAPTCHA_REQUIRED, AcademicStatus.SESSION_EXPIRED)) {
            val adapter = object : FakeAdapter(status) {
                override suspend fun listSections(course: CourseOffer) = listOf(CourseSection("first", "course"), CourseSection("second", "course"))
            }
            assertTrue(ProtocolGrabRunner(adapter).runOnce(item.copy(useExactMatch = false), true) is GrabRunEvent.Paused)
            assertEquals(1, adapter.writes)
        }
    }

    @Test fun pausingTheQueuePreventsTheNextSmartCandidateFromSubmitting() = runBlocking {
        var active = true
        val adapter = object : FakeAdapter(AcademicStatus.NO_CAPACITY) {
            override suspend fun listSections(course: CourseOffer) = listOf(CourseSection("first", "course"), CourseSection("second", "course"))
            override suspend fun select(target: SelectionTarget): SelectionResult { active = false; return super.select(target) }
        }
        try { ProtocolGrabRunner(adapter, canContinue = { active }).runOnce(item.copy(useExactMatch = false), true); fail() }
        catch (_: CancellationException) { assertEquals(1, adapter.writes) }
    }

    @Test fun smartModeFiltersAServerResponseThatContainsAnotherRound() = runBlocking {
        val adapter = object : FakeAdapter(AcademicStatus.SUCCESS) {
            override suspend fun listCourses(context: CourseContext, query: CourseQuery) = listOf(
                CourseOffer("course", "Course", scopeId = "other"), CourseOffer("course", "Course", scopeId = "scope"))
            override suspend fun select(target: SelectionTarget): SelectionResult {
                assertEquals("scope", target.course.scopeId)
                return super.select(target)
            }
        }
        assertTrue(ProtocolGrabRunner(adapter).runOnce(item.copy(scopeId = "scope", useExactMatch = false), true) is GrabRunEvent.Success)
        assertEquals(1, adapter.writes)
        val ambiguous = ProtocolGrabRunner(adapter).runUntilDone(item.copy(useExactMatch = false), GrabRunPolicy(maxAttempts = 1)) { }
        assertTrue(ambiguous is GrabRunEvent.Paused)
        assertEquals(1, adapter.writes)
    }

    @Test fun aManualSmartCourseRetainsItsTeacherAndTimeConstraints() = runBlocking {
        val adapter = object : FakeAdapter(AcademicStatus.SUCCESS) {
            override suspend fun listSections(course: CourseOffer) = listOf(
                CourseSection("first", "course", teacher = "Teacher A", time = "Monday"),
                CourseSection("second", "course", teacher = "Teacher B", time = "Tuesday"))
            override suspend fun select(target: SelectionTarget): SelectionResult {
                assertEquals("second", target.section.stableId)
                return super.select(target)
            }
        }
        assertTrue(ProtocolGrabRunner(adapter).runOnce(item.copy(stableSectionId = "", stableCourseId = "", teacher = "Teacher B", time = "Tuesday", useExactMatch = false), true) is GrabRunEvent.Success)
        assertEquals(1, adapter.writes)
    }

    @Test fun accountTransactionsAreReentrantAndSerial() = runBlocking {
        val session = AcademicSessionStore().session("school", "account", "https://school.example/")
        var active = 0
        var maximum = 0
        coroutineScope {
            (0..2).map { async {
                session.withProtocolLock {
                    session.withProtocolLock {
                        active++
                        maximum = maxOf(maximum, active)
                        delay(10)
                        active--
                    }
                }
            } }.awaitAll()
        }
        assertEquals(1, maximum)
    }

    @Test fun base64AndHexRsaBothUsePkcs1AndUtf8() {
        val key = KeyPairGenerator.getInstance("RSA").apply { initialize(1024) }.generateKeyPair()
        val public = key.public as RSAPublicKey
        val text = "Test-中文-123"
        val b64 = AcademicCrypto.rsaBase64(public.modulus.toByteArray().toByteString().base64(), public.publicExponent.toByteArray().toByteString().base64(), text)
        val hex = AcademicCrypto.rsaHex(public.modulus.toString(16), public.publicExponent.toString(16), text)
        val decrypt = Cipher.getInstance("RSA/ECB/PKCS1Padding").apply { init(Cipher.DECRYPT_MODE, key.private) }
        assertEquals(text, decrypt.doFinal(b64.decodeBase64()!!.toByteArray()).toString(Charsets.UTF_8))
        assertEquals(text, decrypt.doFinal(hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray()).toString(Charsets.UTF_8))
    }

    @Test fun aspNetSelectionReloadsViewstateAndTheActualRowControl() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val school = AcademicCoreTest.testSchool(server, AcademicSystem.ZF_OLD)
            val session = AcademicSessionStore().session(school.id, "a", school.fullBasePath)
            val adapter = ZfOldAcademicAdapter(school, session, AcademicHttpTransport(school, session))
            val url = server.url("/jsxsd/xsxk.aspx").toString()
            server.enqueue(MockResponse().setBody(aspPage("view-one", "row-one")))
            server.enqueue(MockResponse().setBody(aspPage("view-two", "row-two")))
            server.enqueue(MockResponse().setBody("""{"success":true}"""))
            val context = CourseContext(session.epoch, listOf(CourseScope("scope", "选课", listUrl=url)))
            val course = adapter.listCourses(context, CourseQuery()).single()
            val section = adapter.listSections(course).single()
            assertEquals(AcademicStatus.SUCCESS, adapter.select(SelectionTarget(course, section, true)).status)
            server.takeRequest()
            server.takeRequest()
            val body = server.takeRequest().body.readUtf8()
            assertTrue(body.contains("__VIEWSTATE=view-two"))
            assertTrue(body.contains("row-two=on"))
            assertFalse(body.contains("row-one"))
            assertTrue(body.contains("Button1="))
            assertFalse(body.contains("Button2="))
        } finally { server.shutdown() }
    }

    @Test fun anonymousAjaxErrorCannotBeAcceptedAsLogin() = runBlocking {
        val server = MockWebServer()
        server.start()
        try {
            val school = AcademicCoreTest.testSchool(server, AcademicSystem.QZ)
            val session = AcademicSessionStore().session(school.id, "a", school.fullBasePath)
            val adapter = QzAcademicAdapter(school, session, AcademicHttpTransport(school, session))
            server.enqueue(MockResponse().setBody("""<form><input type="password"></form>"""))
            assertEquals(AcademicStatus.SESSION_EXPIRED, adapter.validateSession().status)
            assertNull(server.takeRequest().getHeader("X-Requested-With"))
        } finally { server.shutdown() }
    }

    private fun aspPage(viewstate: String, row: String) = """
        <form action="xsxk.aspx" method="post">
        <input type="hidden" name="__VIEWSTATE" value="$viewstate">
        <table><tr><th>课程代码</th><th>课程名称</th><th>教师姓名</th><th>上课时间</th><th>操作</th></tr>
        <tr><td>C1</td><td>测试课程</td><td>测试教师</td><td>周一</td><td><input type="checkbox" name="$row" value="on"></td></tr></table>
        <input type="submit" name="Button1" value=" 立即提交 ">
        <input type="submit" name="Button2" value="其他按钮">
        </form>
    """
}
