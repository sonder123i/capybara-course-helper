package com.tyust.course.schedule

import com.tyust.course.academic.*
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Test

class ScheduleCacheStoreTest {
    private val current = AcademicTerm("2026-2027-1")
    private val json = """{"kbList":[{"kcmc":"数学","xqj":1,"jcs":"1-2","zcd":"1-16周"}]}"""
    private fun store(prefs: MemoryPreferences) = ScheduleCacheStore(prefs) { current }
    private fun saved(term: AcademicTerm = current, body: String = json) = CachedSchedule(current, term, body, false)
    private fun noNetwork(): AcademicStudyAdapter = error("A cached login must not open the network reader")

    @Test fun anotherLoginAndStoreInstanceReuseThePersistedTimetableWithoutNetwork() = runTest {
        val prefs = MemoryPreferences()
        store(prefs).save("account-a", "school", saved())
        val loaded = store(prefs).load("account-a", "school", false, false, ::noNetwork)
        assertTrue(loaded.fromCache)
        assertEquals(json, loaded.json)
        assertEquals(current, loaded.term)
    }

    @Test fun accountSchoolAndNextSemesterCannotBorrowOneAnothersCache() {
        val cache = store(MemoryPreferences())
        cache.save("a", "school", saved())
        assertNull(cache.selected("b", "school", false))
        assertNull(cache.selected("a", "other-school", false))
        assertNull(cache.selected("a", "school", true))
        cache.save("a", "school", saved(current.next(), """{"kbList":[]}"""))
        assertEquals(json, cache.selected("a", "school", false)?.json)
        assertEquals(current.next(), cache.selected("a", "school", true)?.term)
    }

    @Test fun legacyKeysAndAnAuthoritativeEmptyTimetableAreStillCacheHits() = runTest {
        val prefs = MemoryPreferences()
        prefs.edit().putString("schedule_a_school_2026_3", """{"kbList":[]}""").apply()
        val result = store(prefs).load("a", "school", false, false, ::noNetwork)
        assertTrue(result.fromCache)
        assertTrue(requireNotNull(ScheduleJson.parse(result.json)).isEmpty())
    }

    @Test fun summerTermCannotReuseTheSecondSemestersLegacyData() {
        val prefs = MemoryPreferences()
        prefs.edit().putString("schedule_a_school_2026_12", json).apply()
        assertNull(store(prefs).read("a", "school", AcademicTerm("2026-2027-3")))
    }

    @Test fun explicitSyncFetchesAgainAndAFailedSyncLeavesTheSavedTimetable() = runTest {
        val prefs = MemoryPreferences()
        val cache = store(prefs)
        cache.save("a", "school", saved())
        val remote = Reader(current)
        val fresh = cache.load("a", "school", false, true) { remote }
        assertFalse(fresh.fromCache)
        assertEquals(1, remote.catalogCalls)
        assertEquals(1, remote.scheduleCalls)
        remote.fail = true
        try {
            cache.load("a", "school", false, true) { remote }
            fail("A network error must be reported")
        } catch (_: java.io.IOException) { }
        assertEquals(json, store(prefs).selected("a", "school", false)?.json)
    }

    @Test fun serverSelectedTermPersistsButIsResolvedAgainAfterCalendarRollover() = runTest {
        val prefs = MemoryPreferences()
        var calendar = current
        val schoolTerm = AcademicTerm("2025-2026-2")
        val cache = ScheduleCacheStore(prefs) { calendar }
        cache.save("a", "school", CachedSchedule(schoolTerm, schoolTerm, json, false))
        assertEquals(schoolTerm, cache.load("a", "school", false, false, ::noNetwork).term)
        calendar = current.next()
        val remote = Reader(calendar)
        val loaded = cache.load("a", "school", false, false) { remote }
        assertEquals(calendar, loaded.term)
        assertEquals(1, remote.scheduleCalls)
    }

    @Test fun catalogCanResolveAnExistingCacheWithoutDownloadingTheTimetableAgain() = runTest {
        val prefs = MemoryPreferences()
        val schoolTerm = AcademicTerm("2025-2026-2")
        prefs.edit().putString("schedule_a_school_${schoolTerm.id}", json).apply()
        val remote = Reader(schoolTerm)
        val result = store(prefs).load("a", "school", false, false) { remote }
        assertTrue(result.fromCache)
        assertEquals(1, remote.catalogCalls)
        assertEquals(0, remote.scheduleCalls)
    }

    @Test fun invalidStoredResponseFallsThroughToNetworkAndCannotReplaceGoodData() = runTest {
        val prefs = MemoryPreferences()
        prefs.edit().putString("schedule_a_school_${current.id}", "<html>登录</html>").apply()
        val cache = store(prefs)
        val remote = Reader(current)
        val result = cache.load("a", "school", false, false) { remote }
        assertFalse(result.fromCache)
        assertEquals(1, remote.scheduleCalls)
        cache.save("a", "school", result)
        try { cache.save("a", "school", saved(body = "{}")); fail("Invalid response accepted") }
        catch (_: IllegalArgumentException) { }
        assertEquals(result.json, store(prefs).selected("a", "school", false)?.json)
    }

    private class Reader(private val term: AcademicTerm) : AcademicStudyAdapter {
        var catalogCalls = 0
        var scheduleCalls = 0
        var fail = false
        override suspend fun catalog(): AcademicStudyCatalog {
            catalogCalls++
            return AcademicStudyCatalog(listOf(term), term)
        }
        override suspend fun schedule(term: AcademicTerm): List<AcademicScheduleEntry> {
            scheduleCalls++
            if (fail) throw java.io.IOException("Offline")
            return listOf(AcademicScheduleEntry("更新后的课程", "", "", 1, 1, 2, "1-16周"))
        }
        override suspend fun grades(term: AcademicTerm?) = error("Unused")
        override suspend fun exams(term: AcademicTerm) = error("Unused")
    }
}
