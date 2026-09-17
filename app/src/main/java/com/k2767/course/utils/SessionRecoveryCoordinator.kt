package com.k2767.course.utils

import com.k2767.course.manager.SessionStateStore
import com.k2767.course.manager.SessionToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class RecoveryFailure { NoPassword, VerificationRequired, CredentialsRejected, Network, Cooldown, Storage }
sealed interface SessionRecoveryResult {
    data class Recovered(val token: SessionToken) : SessionRecoveryResult
    data class NeedsLogin(val reason: RecoveryFailure) : SessionRecoveryResult
    data object Superseded : SessionRecoveryResult
}
sealed interface LoginRecoveryOutcome {
    data class Cookie(val value: String) : LoginRecoveryOutcome
    data class Failure(val reason: RecoveryFailure) : LoginRecoveryOutcome
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
    private val pending = mutableMapOf<SessionToken, Operation>()
    private val failures = mutableMapOf<SessionToken, Long>()
    private val mutableState = MutableStateFlow(SessionRecoveryState(sessions.token, RecoveryPhase.Idle))
    val state = mutableState.asStateFlow()

    @Synchronized fun canAttempt(token: SessionToken): Boolean =
        sessions.isCurrent(token) && (pending.containsKey(token) ||
            (canRestore() && failures[token]?.let { now() - it < cooldownMs } != true))

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
        val unavailable = when {
            !canRestore() -> RecoveryFailure.NoPassword
            !manual && failures[expected]?.let { now() - it < cooldownMs } == true -> RecoveryFailure.Cooldown
            else -> null
        }
        if (unavailable != null) {
            mutableState.value = SessionRecoveryState(expected, RecoveryPhase.NeedsLogin, unavailable)
            onDone(SessionRecoveryResult.NeedsLogin(unavailable))
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
            is LoginRecoveryOutcome.Failure -> SessionRecoveryResult.NeedsLogin(outcome.reason)
        }
        when (result) {
            is SessionRecoveryResult.Recovered -> {
                failures.remove(expected)
                mutableState.value = SessionRecoveryState(result.token, RecoveryPhase.Idle)
            }
            is SessionRecoveryResult.NeedsLogin -> {
                failures[expected] = now()
                mutableState.value = SessionRecoveryState(expected, RecoveryPhase.NeedsLogin, result.reason)
            }
            SessionRecoveryResult.Superseded -> Unit
        }
        operation.listeners.toList().forEach { it(result) }
    }
}
