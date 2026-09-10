package com.tyust.course.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import com.tyust.course.login.PasswordLoginCallback
import com.tyust.course.login.PasswordLoginGatewayFactory
import com.tyust.course.manager.SessionToken
import com.tyust.course.manager.UserManager

/** All entry points share one recovery operation; Android callbacks are serialized on main. */
object SessionRenewer {
    private val handler = Handler(Looper.getMainLooper())
    private fun onMain(action: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) action() else handler.post(action)
    }
    private val coordinator by lazy {
        val user = UserManager.getInstance()
        SessionRecoveryCoordinator(
            sessions = user.sessionState,
            now = SystemClock::elapsedRealtime,
            canRestore = { user.currentSchool != null && user.canAutoRelogin() },
            install = { expected, cookie -> user.saveCookieIfCurrent(expected, cookie) },
            login = { _, done ->
                val school = requireNotNull(user.currentSchool)
                val gateway = PasswordLoginGatewayFactory.create(school)
                fun complete(outcome: LoginRecoveryOutcome) {
                    gateway.clearSensitiveState()
                    onMain { done(outcome) }
                }
                gateway.login(school, user.username, user.accountPassword, object : PasswordLoginCallback {
                    override fun onSuccess(cookie: String) = complete(LoginRecoveryOutcome.Cookie(cookie))
                    override fun onCaptchaRequired(imageBytes: ByteArray) = complete(LoginRecoveryOutcome.Failure(RecoveryFailure.VerificationRequired))
                    override fun onCaptchaInvalid() = complete(LoginRecoveryOutcome.Failure(RecoveryFailure.VerificationRequired))
                    override fun onInvalidCredentials() = complete(LoginRecoveryOutcome.Failure(RecoveryFailure.CredentialsRejected))
                    override fun onError(message: String) = complete(LoginRecoveryOutcome.Failure(RecoveryFailure.Network))
                })
                val cancel: () -> Unit = { gateway.clearSensitiveState() }
                cancel
            }
        )
    }

    val state get() = coordinator.state

    @JvmStatic fun canRenew(): Boolean = coordinator.canAttempt(UserManager.getInstance().sessionState.token)

    fun sessionChanged() = onMain { coordinator.sessionChanged() }

    fun request(
        expected: SessionToken,
        manual: Boolean = false,
        onDone: (SessionRecoveryResult) -> Unit = {}
    ) = onMain { coordinator.request(expected, manual, onDone) }

    /** Compatibility for the existing background service; UI uses the typed result above. */
    @JvmStatic fun renew(context: Context, onDone: (Boolean) -> Unit) {
        request(UserManager.getInstance().sessionState.token) { onDone(it is SessionRecoveryResult.Recovered) }
    }
}
