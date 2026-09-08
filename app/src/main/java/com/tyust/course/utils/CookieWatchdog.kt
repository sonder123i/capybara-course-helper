package com.tyust.course.utils

import android.content.Context
import android.os.Handler
import android.os.Looper
import com.tyust.course.academic.*
import com.tyust.course.manager.SessionToken
import com.tyust.course.manager.UserManager
import com.tyust.course.model.SchoolConfig
import com.tyust.course.network.CourseApiClient
import kotlinx.coroutines.*
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException

/** One cancellable check and one timer per session; lifecycle state belongs to the main thread. */
object CookieWatchdog {
    private const val DEFAULT_INTERVAL_MS = 5 * 60 * 1000L
    private val handler = Handler(Looper.getMainLooper())
    private val academicScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var academicCheck: Job? = null
    private var legacyCheck: Call? = null
    private var watchedSession: SessionToken? = null
    private var watchedSchool: SchoolConfig? = null
    private var context: Context? = null
    private var intervalMs = DEFAULT_INTERVAL_MS
    private var runId = 0L

    private fun onMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) block() else handler.post(block)
    }

    @JvmStatic
    fun start(ctx: Context, intervalMs: Long = DEFAULT_INTERVAL_MS): Unit = onMain {
        val user = UserManager.getInstance()
        val token = user.sessionState.token
        if (watchedSession == token) return@onMain
        stop()
        watchedSchool = user.currentSchool ?: return@onMain
        watchedSession = token
        context = ctx.applicationContext
        this.intervalMs = intervalMs
        handler.postDelayed(checkRunnable, 30_000L)
    }

    @JvmStatic
    fun stop(): Unit = onMain {
        runId++
        handler.removeCallbacks(checkRunnable)
        academicCheck?.cancel()
        legacyCheck?.cancel()
        academicCheck = null
        legacyCheck = null
        watchedSession = null
        watchedSchool = null
    }

    private fun ownsCheck(id: Long, token: SessionToken): Boolean =
        runId == id && watchedSession == token && UserManager.getInstance().sessionState.isCurrent(token)

    private fun scheduleNext(id: Long, token: SessionToken): Unit {
        if (!ownsCheck(id, token)) return
        handler.removeCallbacks(checkRunnable)
        handler.postDelayed(checkRunnable, intervalMs)
    }

    private fun complete(id: Long, token: SessionToken, expired: Boolean): Unit = onMain {
        if (!ownsCheck(id, token)) return@onMain
        if (!expired) { scheduleNext(id, token); return@onMain }
        val ctx = context
        if (ctx != null && SessionRenewer.canRenew()) {
            SessionRenewer.renew(ctx) { renewed ->
                if (renewed && runId == id && UserManager.getInstance().currentAccountStorageKey == token.accountStorageKey) {
                    start(ctx, intervalMs)
                } else if (ownsCheck(id, token)) {
                    CourseApiClient.getInstance().notifyCookieExpired(token)
                    stop()
                }
            }
        } else {
            CourseApiClient.getInstance().notifyCookieExpired(token)
            stop()
        }
    }

    private val checkRunnable: Runnable = Runnable {
        val token = watchedSession ?: return@Runnable
        val school = watchedSchool ?: return@Runnable
        val id = runId
        if (!ownsCheck(id, token)) return@Runnable
        if (AcademicGatewayFactory.supports(school)) {
            val user = UserManager.getInstance()
            val cookie = user.savedCookie
            val username = user.username.ifBlank { user.studentId.orEmpty() }
            academicCheck = academicScope.launch {
                val result = try {
                    AcademicGatewayFactory.importCookie(school, token.accountStorageKey, cookie, replace = false, username = username)
                    AcademicGatewayFactory.create(school, token.accountStorageKey).validateSession().status
                } catch (e: CancellationException) { throw e }
                catch (e: AcademicException) { e.status }
                catch (_: Exception) { AcademicStatus.NETWORK_RETRYABLE }
                complete(id, token, result == AcademicStatus.SESSION_EXPIRED)
            }
        } else {
            legacyCheck = CourseApiClient.getInstance().validateCookie(school, token.accountStorageKey, object : Callback {
                override fun onFailure(call: Call, e: IOException) = complete(id, token, false)
                override fun onResponse(call: Call, response: Response) {
                    val expired = response.use {
                        runCatching {
                            val html = it.body?.string().orEmpty()
                            html.contains("用户登录") || html.contains("登 录") || html.contains("slogin.html") ||
                                html.contains("notLogin") || html.contains("name=\"yhm\"")
                        }.getOrDefault(false)
                    }
                    complete(id, token, expired)
                }
            })
        }
    }
}
