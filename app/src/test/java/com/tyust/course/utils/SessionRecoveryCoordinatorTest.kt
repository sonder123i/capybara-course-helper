package com.tyust.course.utils

import com.tyust.course.manager.SessionStateStore
import com.tyust.course.manager.SessionToken
import org.junit.Assert.*
import org.junit.Test

class SessionRecoveryCoordinatorTest {
    private class Fixture {
        val sessions = SessionStateStore().apply { replace("A") }
        var clock = 0L
        var available = true
        var cookie = "original"
        var cancelled = 0
        val requests = mutableListOf<(LoginRecoveryOutcome) -> Unit>()
        val coordinator = SessionRecoveryCoordinator(sessions, { clock }, { available },
            login = { _, done -> requests += done; { cancelled++ } },
            install = { token, value ->
                if (sessions.isCurrent(token)) { cookie = value; sessions.replace(token.accountStorageKey) } else null
            })
        fun succeed(index: Int = requests.lastIndex, value: String = "fresh") = requests[index](LoginRecoveryOutcome.Cookie(value))
        fun fail(index: Int = requests.lastIndex) = requests[index](LoginRecoveryOutcome.Failure(RecoveryFailure.Network))
        fun failWith(reason: RecoveryFailure, message: String = "", index: Int = requests.lastIndex) =
            requests[index](LoginRecoveryOutcome.Failure(reason, message))
    }

    @Test fun aDisabledAccountStopsAutomaticRetryButStillHonoursAManualOne() {
        val f = Fixture()
        f.coordinator.request(f.sessions.token)
        f.failWith(RecoveryFailure.AccountUnavailable, "该用户已被禁用，请联系管理员")
        f.clock = 600_000
        assertFalse("永久性失败不能一直重试", f.coordinator.canAttempt(f.sessions.token))
        val results = mutableListOf<SessionRecoveryResult>()
        f.coordinator.request(f.sessions.token, onDone = results::add)
        assertEquals(1, f.requests.size)
        assertEquals(SessionRecoveryResult.NeedsLogin(RecoveryFailure.AccountUnavailable, "该用户已被禁用，请联系管理员"), results.single())
        f.coordinator.request(f.sessions.token, manual = true, onDone = results::add)
        assertEquals("用户主动点重试还是要放行", 2, f.requests.size)
    }

    @Test fun aRejectedStoredPasswordIsNotRetriedEither() {
        val f = Fixture()
        f.coordinator.request(f.sessions.token)
        f.failWith(RecoveryFailure.CredentialsRejected, "用户名或密码错误")
        f.clock = 600_000
        assertFalse(f.coordinator.canAttempt(f.sessions.token))
        f.coordinator.request(f.sessions.token) { result ->
            assertEquals(SessionRecoveryResult.NeedsLogin(RecoveryFailure.CredentialsRejected, "用户名或密码错误"), result)
        }
        assertEquals(1, f.requests.size)
    }

    @Test fun automaticAndManualRecoveryShareOneLoginAndPublishNewVersion() {
        val f = Fixture(); val old = f.sessions.token; f.sessions.expire(old)
        val results = mutableListOf<SessionRecoveryResult>()
        f.coordinator.request(old, onDone = results::add)
        f.coordinator.request(old, manual = true, onDone = results::add)
        assertEquals(1, f.requests.size)
        assertEquals(RecoveryPhase.Restoring, f.coordinator.state.value.phase)
        f.succeed()
        assertEquals("fresh", f.cookie)
        assertFalse(f.sessions.state.value.expired)
        assertNotEquals(old, f.sessions.token)
        assertEquals(List(2) { SessionRecoveryResult.Recovered(f.sessions.token) }, results)
    }

    @Test fun lateSuccessCannotOverwriteAnExternallyUpdatedCookie() {
        val f = Fixture(); val old = f.sessions.token
        val results = mutableListOf<SessionRecoveryResult>()
        f.coordinator.request(old, onDone = results::add)
        f.cookie = "manual"; f.sessions.replace("A")
        f.succeed(value = "obsolete")
        assertEquals("manual", f.cookie)
        assertEquals(listOf(SessionRecoveryResult.Superseded), results)
    }

    @Test fun switchingAwayAndBackCancelsOldOperationWithoutAffectingNewOne() {
        val f = Fixture(); val old = f.sessions.token
        val results = mutableListOf<SessionRecoveryResult>()
        f.coordinator.request(old, onDone = results::add)
        f.sessions.replace("B"); f.coordinator.sessionChanged()
        f.sessions.replace("A"); f.coordinator.request(f.sessions.token)
        f.fail(0)
        assertEquals(1, f.cancelled)
        assertEquals(listOf(SessionRecoveryResult.Superseded), results)
        assertEquals(RecoveryPhase.Restoring, f.coordinator.state.value.phase)
        f.succeed(1)
        assertEquals(RecoveryPhase.Idle, f.coordinator.state.value.phase)
    }

    @Test fun manualRetryBypassesCooldownButStillJoinsCurrentRequest() {
        val f = Fixture(); val token = f.sessions.token
        f.coordinator.request(token); f.fail()
        f.coordinator.request(token)
        assertEquals(1, f.requests.size)
        f.coordinator.request(token, manual = true)
        f.coordinator.request(token, manual = true)
        assertEquals(2, f.requests.size)
        f.succeed()
        assertEquals("fresh", f.cookie)
    }

    @Test fun failureCooldownDoesNotAffectAnotherSessionOrAccount() {
        val f = Fixture()
        f.coordinator.request(f.sessions.token); f.fail()
        assertFalse(f.coordinator.canAttempt(f.sessions.token))
        f.sessions.replace("A")
        assertTrue(f.coordinator.canAttempt(f.sessions.token))
        f.coordinator.request(f.sessions.token); f.fail()
        f.sessions.replace("B")
        assertTrue(f.coordinator.canAttempt(f.sessions.token))
        f.coordinator.request(f.sessions.token)
        assertEquals(3, f.requests.size)
    }

    @Test fun duplicateCallbackCannotFinishANewerRequestInTheSameSession() {
        val f = Fixture(); val token = f.sessions.token
        f.coordinator.request(token); f.fail(0)
        f.coordinator.request(token, manual = true)
        f.succeed(0, "late")
        assertEquals("original", f.cookie)
        assertEquals(RecoveryPhase.Restoring, f.coordinator.state.value.phase)
        f.succeed(1)
        assertEquals("fresh", f.cookie)
    }

    @Test fun oldTokenNeverStartsALoginForCurrentAccount() {
        val f = Fixture(); val old = f.sessions.token
        f.sessions.replace("B")
        var result: SessionRecoveryResult? = null
        f.coordinator.request(old) { result = it }
        assertTrue(f.requests.isEmpty())
        assertEquals(SessionRecoveryResult.Superseded, result)
    }

    @Test fun missingPasswordRequiresInteractionWithoutANetworkRequest() {
        val f = Fixture(); f.available = false
        f.coordinator.request(f.sessions.token)
        assertTrue(f.requests.isEmpty())
        assertEquals(RecoveryFailure.NoPassword, f.coordinator.state.value.reason)
    }

    @Test fun cooldownExpiresUsingMonotonicTime() {
        val f = Fixture(); f.coordinator.request(f.sessions.token); f.fail()
        f.clock = 60_001
        assertTrue(f.coordinator.canAttempt(f.sessions.token))
    }

    @Test fun cookieRefreshPreservesTheLoginFailureWithoutReplacingItWithANetworkError() {
        val f = Fixture()
        val results = mutableListOf<SessionRecoveryResult>()
        f.coordinator.request(f.sessions.token, manual = true, onDone = results::add)
        f.requests.single()(LoginRecoveryOutcome.Failure(RecoveryFailure.Login, "登录处理超时，请重试或使用网页登录"))
        assertEquals(SessionRecoveryResult.NeedsLogin(RecoveryFailure.Login, "登录处理超时，请重试或使用网页登录"), results.single())
        assertEquals("original", f.cookie)
        assertEquals(RecoveryFailure.Login, f.coordinator.state.value.reason)
    }
}
