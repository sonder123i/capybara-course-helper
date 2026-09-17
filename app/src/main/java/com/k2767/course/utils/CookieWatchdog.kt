package com.k2767.course.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.k2767.course.academic.*
import com.k2767.course.manager.SessionToken
import com.k2767.course.manager.UserManager
import com.k2767.course.model.SchoolConfig
import com.k2767.course.network.CourseApiClient
import kotlinx.coroutines.*
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException

object CookieWatchdog {
    private const val DEFAULT_INTERVAL_MS = 5 * 60 * 1000L
    private val handler = Handler(Looper.getMainLooper())
    private val academicScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var watchedSchool: SchoolConfig? = null
    private var context: Context? = null
    private var intervalMs = DEFAULT_INTERVAL_MS
    private val loop by lazy {
        SessionCheckLoop(UserManager.getInstance().sessionState,
            schedule = { delay, action ->
                val task = Runnable(action)
                handler.postDelayed(task, delay)
                val cancel: () -> Unit = { handler.removeCallbacks(task) }
                cancel
            }, check = ::check, onExpired = ::recover)
    }

    private fun onMain(block: () -> Unit): Unit {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else handler.post(block)
    }

    @JvmStatic
    fun start(ctx: Context, intervalMs: Long = DEFAULT_INTERVAL_MS): Unit = onMain {
        val user = UserManager.getInstance()
        watchedSchool = user.currentSchool
        if (watchedSchool == null) {
            loop.stop()
            return@onMain
        }
        context = ctx.applicationContext
        this.intervalMs = intervalMs
        loop.start(user.sessionState.token, intervalMs)
    }

    @JvmStatic
    fun stop(): Unit = onMain { loop.stop() }

    private fun recover(token: SessionToken, runId: Long): Unit {
        val ctx = context ?: return
        if (SessionRenewer.canRenew()) {
            SessionRenewer.request(token) { result ->
                if (loop.isActive(runId)) when (result) {
                    is SessionRecoveryResult.Recovered -> {
                        if (UserManager.getInstance().sessionState.isCurrent(result.token)) start(ctx, intervalMs)
                    }
                    is SessionRecoveryResult.NeedsLogin -> {
                        CourseApiClient.getInstance().notifyCookieExpired(token)
                        stop()
                    }
                    SessionRecoveryResult.Superseded -> Unit
                }
            }
        } else {
            CourseApiClient.getInstance().notifyCookieExpired(token)
            stop()
        }
    }

    private fun check(token: SessionToken, complete: (Boolean) -> Unit): () -> Unit {
        val school = watchedSchool
        if (school == null || (AcademicGatewayFactory.supports(school) && !AcademicGatewayFactory.hasSelectedAdapter(school))) {
            // An unresolved/unknown configuration is not evidence of expiry.
            complete(false)
            return { }
        }
        if (AcademicGatewayFactory.supports(school)) {
            val job = academicScope.launch {
                val result = try {
                    val gateway = AcademicGatewayFactory.create(school, token.accountStorageKey)
                    gateway.validateSession().status
                } catch (e: CancellationException) { throw e }
                catch (e: AcademicException) { e.status }
                catch (_: Exception) { AcademicStatus.NETWORK_RETRYABLE }
                onMain { complete(result == AcademicStatus.SESSION_EXPIRED) }
            }
            return { job.cancel() }
        }
        val call = CourseApiClient.getInstance().validateCookie(school, token.accountStorageKey, object : Callback {
            override fun onFailure(call: Call, e: IOException): Unit = onMain { complete(false) }
            override fun onResponse(call: Call, response: Response) {
                val expired = response.use {
                    runCatching {
                        val html = it.body?.string().orEmpty()
                        html.contains("用户登录") || html.contains("登 录") || html.contains("slogin.html") ||
                            html.contains("notLogin") || html.contains("name=\"yhm\"")
                    }.getOrDefault(false)
                }
                onMain { complete(expired) }
            }
        })
        return { call.cancel() }
    }
}
