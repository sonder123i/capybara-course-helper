package com.k2767.course.utils

import com.k2767.course.manager.SessionStateStore
import com.k2767.course.manager.SessionToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RecoveryFailure { NoPassword, VerificationRequired, CredentialsRejected, AccountUnavailable, Network, Cooldown, Storage }

/** 永久性失败：再试一次只会再失败一次，正方还会累加失败计数直到锁定账号。 */
internal val RecoveryFailure.terminal: Boolean
    get() = this == RecoveryFailure.CredentialsRejected || this == RecoveryFailure.AccountUnavailable

/**
 * 把教务返回的原文分级。只认账号状态类词（禁用/锁定/冻结/停用/注销）——
 * 「过期」一类词会撞上「会话已过期」，那是可恢复的，不能算永久失败。
 */
internal fun classifyRecoveryFailure(message: String): RecoveryFailure {
    val text = message.trim()
    return when {
        listOf("禁用", "锁定", "冻结", "停用", "注销").any(text::contains) -> RecoveryFailure.AccountUnavailable
        listOf("密码错误", "密码不正确", "用户名或密码", "账号或密码").any(text::contains) -> RecoveryFailure.CredentialsRejected
        text.contains("验证码") -> RecoveryFailure.VerificationRequired
        else -> RecoveryFailure.Network
    }
}

sealed interface SessionRecoveryResult {
    data class Recovered(val token: SessionToken) : SessionRecoveryResult
    data class NeedsLogin(val reason: RecoveryFailure, val message: String = "") : SessionRecoveryResult
    data object Superseded : SessionRecoveryResult
}
sealed interface LoginRecoveryOutcome {
    data class Cookie(val value: String) : LoginRecoveryOutcome
    data class Failure(val reason: RecoveryFailure, val message: String = "") : LoginRecoveryOutcome
}
enum class RecoveryPhase { Idle, Restoring, NeedsLogin }
data class SessionRecoveryState(val token: SessionToken, val phase: RecoveryPhase, val reason: RecoveryFailure? = null)

/** Session-scoped single flight. Dependencies are injectable without Android or a real account. */
class SessionRecoveryCoordinator(
    private val sessions: SessionStateStore,
    private val now: () -> Long,
    private val canRestore: () -> Boolean,
    private val login: (SessionToken, (LoginRecoveryOutcome) -> Unit) -> (() -> Unit),
    private val install: (SessionToken, String) -> SessionToken?,
    private val cooldownMs: Long = 60_000L
) {
    private class Operation(val listeners: MutableList<(SessionRecoveryResult) -> Unit>) {
        var cancel: (() -> Unit)? = null
    }
    private data class Attempt(val at: Long, val reason: RecoveryFailure, val message: String)
    private val pending = mutableMapOf<SessionToken, Operation>()
    private val failures = mutableMapOf<SessionToken, Attempt>()
    private val mutableState = MutableStateFlow(SessionRecoveryState(sessions.token, RecoveryPhase.Idle))
    val state = mutableState.asStateFlow()

    @Synchronized fun canAttempt(token: SessionToken): Boolean {
        if (!sessions.isCurrent(token)) return false
        if (pending.containsKey(token)) return true
        if (!canRestore()) return false
        val attempt = failures[token] ?: return true
        // 永久性失败对本会话不再重试；换账号/重新登录会清掉记录（见 sessionChanged）。
        if (attempt.reason.terminal) return false
        return now() - attempt.at >= cooldownMs
    }

    @Synchronized fun sessionChanged() {
        val current = sessions.token
        val obsolete = pending.keys.filter { it != current }
        obsolete.forEach { token ->
            val operation = pending.remove(token) ?: return@forEach
            operation.cancel?.invoke()
            operation.listeners.toList().forEach { it(SessionRecoveryResult.Superseded) }
        }
        failures.keys.retainAll(setOf(current))
        if (mutableState.value.token != current) mutableState.value = SessionRecoveryState(current, RecoveryPhase.Idle)
    }

    @Synchronized fun request(
        expected: SessionToken,
        manual: Boolean = false,
        onDone: (SessionRecoveryResult) -> Unit = {}
    ) {
        sessionChanged()
        if (!sessions.isCurrent(expected)) { onDone(SessionRecoveryResult.Superseded); return }
        pending[expected]?.let { it.listeners += onDone; return }
        val refusal: Pair<RecoveryFailure, String>? = when {
            !canRestore() -> RecoveryFailure.NoPassword to ""
            manual -> null
            else -> failures[expected]?.let { attempt -> when {
                attempt.reason.terminal -> attempt.reason to attempt.message
                now() - attempt.at < cooldownMs -> RecoveryFailure.Cooldown to ""
                else -> null
            } }
        }
        if (refusal != null) {
            val (reason, message) = refusal
            mutableState.value = SessionRecoveryState(expected, RecoveryPhase.NeedsLogin, reason)
            onDone(SessionRecoveryResult.NeedsLogin(reason, message))
            return
        }
        val operation = Operation(mutableListOf(onDone))
        pending[expected] = operation
        mutableState.value = SessionRecoveryState(expected, RecoveryPhase.Restoring)
        try {
            val cancel = login(expected) { complete(expected, operation, it) }
            if (pending[expected] === operation) operation.cancel = cancel else cancel()
        } catch (_: Exception) {
            complete(expected, operation, LoginRecoveryOutcome.Failure(RecoveryFailure.Network))
        }
    }

    @Synchronized private fun complete(expected: SessionToken, operation: Operation, outcome: LoginRecoveryOutcome) {
        if (pending[expected] !== operation) return
        pending.remove(expected)
        val result = if (!sessions.isCurrent(expected)) SessionRecoveryResult.Superseded else when (outcome) {
            is LoginRecoveryOutcome.Cookie -> {
                val installed = runCatching { install(expected, outcome.value) }.getOrNull()
                when {
                    installed != null && sessions.isCurrent(installed) -> SessionRecoveryResult.Recovered(installed)
                    !sessions.isCurrent(expected) -> SessionRecoveryResult.Superseded
                    else -> SessionRecoveryResult.NeedsLogin(RecoveryFailure.Storage)
                }
            }
            is LoginRecoveryOutcome.Failure -> SessionRecoveryResult.NeedsLogin(outcome.reason, outcome.message)
        }
        when (result) {
            is SessionRecoveryResult.Recovered -> {
                failures.remove(expected)
                mutableState.value = SessionRecoveryState(result.token, RecoveryPhase.Idle)
            }
            is SessionRecoveryResult.NeedsLogin -> {
                failures[expected] = Attempt(now(), result.reason, result.message)
                mutableState.value = SessionRecoveryState(expected, RecoveryPhase.NeedsLogin, result.reason)
            }
            SessionRecoveryResult.Superseded -> Unit
        }
        operation.listeners.toList().forEach { it(result) }
    }
}
