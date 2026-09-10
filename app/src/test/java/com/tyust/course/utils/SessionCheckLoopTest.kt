package com.tyust.course.utils

import com.tyust.course.manager.SessionStateStore
import org.junit.Assert.*
import org.junit.Test

class SessionCheckLoopTest {
    private class Fixture {
        val sessions = SessionStateStore()
        val timers = mutableListOf<() -> Unit>()
        val replies = mutableListOf<(Boolean) -> Unit>()
        var cancelled = 0
        var expired = 0
        val loop = SessionCheckLoop(sessions, { _, action ->
            timers.add(action)
            val cancel: () -> Unit = { timers.remove(action) }
            cancel
        }, { _, reply ->
            replies.add(reply)
            val cancel: () -> Unit = { cancelled++ }
            cancel
        }, { _, _ -> expired++ })
        fun tick() { timers.removeAt(0).invoke() }
    }

    @Test fun repeatedStartRetainsOneTimerAndOneInFlightCheck() = with(Fixture()) {
        val token = sessions.replace("a")
        repeat(4) { loop.start(token, 300_000) }
        assertEquals(1, timers.size)
        tick()
        repeat(4) { loop.start(token, 300_000) }
        assertEquals(1, replies.size)
        assertTrue(timers.isEmpty())
        replies.single()(false)
        replies.single()(false)
        assertEquals(1, timers.size)
        loop.stop()
        assertTrue(timers.isEmpty())
    }

    @Test fun replacementCancelsRequestAndDiscardsOldReplyAcrossAccountRoundTrip() = with(Fixture()) {
        loop.start(sessions.replace("a"), 300_000)
        tick()
        loop.start(sessions.replace("b"), 300_000)
        assertEquals(1, cancelled)
        loop.start(sessions.replace("a"), 300_000)
        replies.first()(true)
        assertEquals(0, expired)
        assertEquals(1, timers.size)
        tick()
        replies.last()(false)
        assertEquals(1, timers.size)
    }

    @Test fun stopInvalidatesEvenAnAlreadyDequeuedTimerAndLateCompletion() = with(Fixture()) {
        loop.start(sessions.replace("a"), 300_000)
        val oldTimer = timers.first()
        loop.stop()
        oldTimer()
        assertTrue(replies.isEmpty())
        loop.start(sessions.token, 300_000)
        tick()
        loop.stop()
        loop.stop()
        replies.single()(true)
        assertEquals(1, cancelled)
        assertEquals(0, expired)
        assertTrue(timers.isEmpty())
    }

    @Test fun confirmedExpiryRunsOnceAndWaitsForRecovery() = with(Fixture()) {
        loop.start(sessions.replace("a"), 300_000)
        tick()
        replies.single()(true)
        replies.single()(true)
        assertEquals(1, expired)
        assertTrue(timers.isEmpty())
        loop.start(sessions.replace("a"), 300_000)
        assertEquals(1, timers.size)
    }
}
