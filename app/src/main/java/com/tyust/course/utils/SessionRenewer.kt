package com.tyust.course.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import com.tyust.course.login.PasswordLoginCallback
import com.tyust.course.login.PasswordLoginGatewayFactory
import com.tyust.course.manager.UserManager

/** Shared renewal entry point. Callbacks and session changes are serialized on the UI thread. */
object SessionRenewer {
    private const val TAG = "SessionRenewer"
    private val handler = Handler(Looper.getMainLooper())
    private val gate = SessionRenewalGate()

    @JvmStatic
    fun canRenew(): Boolean {
        val user = UserManager.getInstance()
        return synchronized(gate) { gate.canAttempt(user.sessionState.token, SystemClock.elapsedRealtime()) } &&
            user.canAutoRelogin()
    }

    @JvmStatic
    fun renew(context: Context, onDone: (Boolean) -> Unit) {
        val expected = UserManager.getInstance().sessionState.token
        handler.post start@{
            val user = UserManager.getInstance()
            if (!user.sessionState.isCurrent(expected) || !canRenew()) {
                onDone(false)
                return@start
            }
            if (!synchronized(gate) { gate.join(expected, onDone) }) return@start
            val school = user.currentSchool
            fun finish(success: Boolean) {
                synchronized(gate) {
                    gate.finish(expected, success, user.sessionState.isCurrent(expected), SystemClock.elapsedRealtime())
                }
            }
            if (school == null) { finish(false); return@start }
            val username = user.username
            val gateway = PasswordLoginGatewayFactory.create(school)
            fun complete(cookie: String? = null) {
                gateway.clearSensitiveState()
                handler.post result@{
                    if (!user.sessionState.isCurrent(expected)) { finish(false); return@result }
                    if (cookie != null) user.saveCookie(cookie)
                    finish(cookie != null)
                }
            }
            gateway.login(school, username, user.accountPassword, object : PasswordLoginCallback {
                override fun onSuccess(cookie: String) = complete(cookie)
                override fun onCaptchaRequired(imageBytes: ByteArray) = complete()
                override fun onCaptchaInvalid() = complete()
                override fun onInvalidCredentials() = complete()
                override fun onError(message: String) {
                    Log.w(TAG, "续期失败: $message")
                    complete()
                }
            })
        }
    }
}
