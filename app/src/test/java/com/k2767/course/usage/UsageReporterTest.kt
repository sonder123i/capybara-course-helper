package com.k2767.course.usage

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test
import java.io.IOException
import java.util.UUID

private class MemoryUsageStore(var record: UsageRecord = UsageRecord()) : UsageStore {
    override fun read() = record
    override fun write(record: UsageRecord) { this.record = record }
}

@OptIn(ExperimentalCoroutinesApi::class)
class UsageReporterTest {
    @Test fun waitsForTheNoticeAndOnlyCountsOncePerDayAcrossForegroundVisitsAndUpdates() = runTest {
        val store = MemoryUsageStore()
        val sent = mutableListOf<Pair<String, String>>()
        val base = 1_788_926_400_000L
        val clock = { base + testScheduler.currentTime }
        val transport = UsageTransport { id, version -> sent += id to version; UsageDay.at(clock()) }
        fun reporter(version: String) = UsageReporter(store, transport, version, true, { false }, backgroundScope, clock)
        val first = reporter("1.0.73")
        first.setForeground(true)
        runCurrent()
        assertTrue(sent.isEmpty())
        assertNull(store.record.installationId)
        first.acknowledgeNotice(true)
        runCurrent()
        assertEquals(1, sent.size)
        val installId = sent.single().first
        assertEquals(4, UUID.fromString(installId).version())
        first.setForeground(false)
        first.setForeground(true)
        runCurrent()
        assertEquals(1, sent.size)
        first.setForeground(false)
        val updated = reporter("1.0.74")
        updated.setForeground(true)
        runCurrent()
        assertEquals(1, sent.size)
        advanceTimeBy(UsageDay.DAY_MILLIS + 300)
        runCurrent()
        assertEquals(2, sent.size)
        assertEquals(installId to "1.0.74", sent.last())
    }

    @Test fun remainsOpenAcrossBeijingMidnightAndReportsTheNewDay() = runTest {
        // 2026-09-11 23:59:59 Beijing (timestamps do not follow the device's time zone).
        val base = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss").apply {
            timeZone = java.util.TimeZone.getTimeZone("UTC")
        }.parse("2026-09-11 15:59:59")!!.time
        val store = MemoryUsageStore(UsageRecord(UsagePreferences(true, true)))
        val days = mutableListOf<String>()
        val clock = { base + testScheduler.currentTime }
        val reporter = UsageReporter(store, UsageTransport { _, _ -> UsageDay.at(clock()).also(days::add) },
            "1.0.73", true, { false }, backgroundScope, clock)
        reporter.setForeground(true)
        runCurrent()
        assertEquals(listOf("2026-09-11"), days)
        advanceTimeBy(1300)
        runCurrent()
        assertEquals(listOf("2026-09-11", "2026-09-12"), days)
        assertEquals("2026-09-12", store.record.lastSuccessDay)
    }

    @Test fun failedRequestRetriesOnTheNextForegroundVisitWithTheSameId() = runTest {
        val store = MemoryUsageStore(UsageRecord(UsagePreferences(true, true)))
        val sent = mutableListOf<String>()
        val reporter = UsageReporter(store, UsageTransport { id, _ ->
            sent += id
            if (sent.size == 1) throw IOException("offline")
            "2026-09-11"
        }, "1.0.73", true, { false }, backgroundScope)
        reporter.setForeground(true)
        runCurrent()
        assertNull(store.record.lastSuccessDay)
        reporter.setForeground(false)
        reporter.setForeground(true)
        runCurrent()
        assertEquals(2, sent.size)
        assertEquals(sent[0], sent[1])
        assertEquals("2026-09-11", store.record.lastSuccessDay)
    }

    @Test fun disablingImmediatelyCancelsThePendingCallAndNeverRecordsItsAcknowledgement() = runTest {
        val store = MemoryUsageStore(UsageRecord(UsagePreferences(true, true)))
        val started = CompletableDeferred<Unit>()
        var cancelled = false
        var sent = 0
        val reporter = UsageReporter(store, UsageTransport { _, _ ->
            sent++
            started.complete(Unit)
            try { awaitCancellation() } finally { cancelled = true }
        }, "1.0.73", true, { false }, backgroundScope)
        reporter.setForeground(true)
        runCurrent()
        assertTrue(started.isCompleted)
        reporter.setEnabled(false)
        runCurrent()
        assertTrue(cancelled)
        assertFalse(store.record.preferences.enabled)
        assertNull(store.record.lastSuccessDay)
        reporter.setForeground(false)
        reporter.setForeground(true)
        advanceTimeBy(UsageDay.DAY_MILLIS + 300)
        runCurrent()
        assertEquals(1, sent)
    }

    @Test fun decliningTheNoticePersistsTheChoiceWithoutCreatingAnIdentifier() = runTest {
        val store = MemoryUsageStore()
        val reporter = UsageReporter(store, UsageTransport { _, _ -> error("Must not send") }, "1.0.73", true, { false }, backgroundScope)
        reporter.setForeground(true)
        reporter.acknowledgeNotice(false)
        runCurrent()
        assertEquals(UsagePreferences(noticeSeen = true, enabled = false), store.record.preferences)
        assertNull(store.record.installationId)
    }

    @Test fun debugAndDemoBuildsNeverGenerateAnIdOrSend() = runTest {
        for ((eligible, demo) in listOf(false to false, true to true, false to true)) {
            val store = MemoryUsageStore(UsageRecord(UsagePreferences(true, true)))
            val reporter = UsageReporter(store, UsageTransport { _, _ -> error("Must not send") },
                "1.0.73", eligible, { demo }, backgroundScope)
            reporter.setForeground(true)
            runCurrent()
            assertNull(store.record.installationId)
        }
    }

    @Test fun enteringDemoModeOrGoingToBackgroundCancelsAnInflightCall() = runTest {
        for (background in listOf(true, false)) {
            var demo = false
            var cancelled = false
            val store = MemoryUsageStore(UsageRecord(UsagePreferences(true, true)))
            val reporter = UsageReporter(store, UsageTransport { _, _ ->
                try { awaitCancellation() } finally { cancelled = true }
            }, "1.0.73", true, { demo }, backgroundScope)
            reporter.setForeground(true)
            runCurrent()
            if (background) reporter.setForeground(false) else { demo = true; reporter.refreshEligibility() }
            runCurrent()
            assertTrue(cancelled)
            assertNull(store.record.lastSuccessDay)
        }
    }

    @Test fun storageFailureDoesNotSendAnUnstableIdentifier() = runTest {
        val store = object : UsageStore {
            override fun read() = UsageRecord(UsagePreferences(true, true))
            override fun write(record: UsageRecord) { throw IOException("full") }
        }
        val reporter = UsageReporter(store, UsageTransport { _, _ -> error("Must not send") },
            "1.0.73", true, { false }, backgroundScope)
        reporter.setForeground(true)
        runCurrent()
    }

    @Test fun onlyValidCalendarDatesAreAccepted() {
        assertTrue(UsageDay.isValid("2028-02-29"))
        assertFalse(UsageDay.isValid("2026-02-29"))
        assertFalse(UsageDay.isValid("2026-09-11junk"))
        assertFalse(UsageDay.isValid("2026-9-11"))
    }
}
