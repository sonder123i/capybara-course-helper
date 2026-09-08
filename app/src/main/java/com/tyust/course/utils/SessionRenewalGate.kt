package com.tyust.course.utils

import com.tyust.course.manager.SessionToken

/** Called on the main thread by SessionRenewer. Failures belong to one session generation. */
internal class SessionRenewalGate(private val cooldownMs: Long = 60_000) {
    private val pending = mutableMapOf<SessionToken, MutableList<(Boolean) -> Unit>>()
    private var failure: Pair<SessionToken, Long>? = null

    fun canAttempt(token: SessionToken, now: Long): Boolean =
        failure?.let { (failedToken, at) -> failedToken != token || now - at >= cooldownMs } ?: true

    fun join(token: SessionToken, callback: (Boolean) -> Unit): Boolean {
        pending[token]?.let { it += callback; return false }
        pending[token] = mutableListOf(callback)
        return true
    }

    fun finish(token: SessionToken, success: Boolean, current: Boolean, now: Long) {
        val callbacks = pending.remove(token) ?: return
        if (current) failure = if (success) null else token to now
        callbacks.forEach { it(success) }
    }
}
