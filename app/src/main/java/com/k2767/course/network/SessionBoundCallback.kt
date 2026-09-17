package com.k2767.course.network

import com.k2767.course.manager.SessionStateStore
import com.k2767.course.manager.SessionToken
import okhttp3.Call
import okhttp3.Callback
import okhttp3.Response
import java.io.IOException

/** The tag belongs to request creation, never to the moment its response arrives. */
internal class SessionBoundCallback(
    private val sessions: SessionStateStore,
    private val delegate: Callback,
    private val inSession: (SessionToken?, () -> Unit) -> Unit = { _, action -> action() }
) : Callback {
    private fun accepts(call: Call): Boolean =
        call.request().tag(SessionToken::class.java)?.let(sessions::isCurrent) ?: true

    override fun onFailure(call: Call, e: IOException) {
        if (accepts(call)) inSession(call.request().tag(SessionToken::class.java)) { delegate.onFailure(call, e) }
    }

    override fun onResponse(call: Call, response: Response) {
        if (accepts(call)) inSession(call.request().tag(SessionToken::class.java)) { delegate.onResponse(call, response) }
        else response.close()
    }
}
