package com.tyust.course.utils

import com.tyust.course.manager.SessionStateStore
import com.tyust.course.manager.SessionToken

/** All entry points, including completion, run on the owner's thread. */
internal class SessionCheckLoop(
    private val sessions: SessionStateStore,
    private val schedule: (Long, () -> Unit) -> (() -> Unit),
    private val check: (SessionToken, (Boolean) -> Unit) -> (() -> Unit),
    private val onExpired: (SessionToken, Long) -> Unit
) {
    private class Run(val id: Long, val token: SessionToken, val interval: Long) {
        var timer: (() -> Unit)? = null
        var request: (() -> Unit)? = null
        var awaiting = false
        var checkId = 0L
    }
    private var nextId = 0L
    private var current: Run? = null

    fun start(token: SessionToken, interval: Long) {
        if (current?.token == token && current?.interval == interval) return
        stop()
        if (!sessions.isCurrent(token)) return
        Run(++nextId, token, interval).also { current = it; arm(it, 30_000L) }
    }

    fun stop() {
        val old = current ?: return
        current = null
        old.timer?.invoke()
        old.request?.invoke()
    }

    fun isActive(id: Long): Boolean = current?.id == id
    private fun owns(run: Run): Boolean = current === run && sessions.isCurrent(run.token)

    private fun arm(run: Run, delay: Long) {
        if (!owns(run)) return
        run.timer?.invoke()
        run.timer = schedule(delay) { perform(run) }
    }

    private fun perform(run: Run) {
        if (!owns(run) || run.awaiting) return
        run.timer = null
        run.awaiting = true
        val checkId = ++run.checkId
        val complete: (Boolean) -> Unit = { expired ->
            if (owns(run) && run.awaiting && run.checkId == checkId) {
                run.awaiting = false
                run.request = null
                if (expired) onExpired(run.token, run.id) else arm(run, run.interval)
            }
        }
        val cancel = try {
            check(run.token, complete)
        } catch (_: Exception) {
            // Request setup also runs on the main thread. A configuration or
            // network setup failure must neither crash it nor expire a session.
            complete(false)
            return
        }
        if (owns(run) && run.awaiting) run.request = cancel else cancel()
    }
}
