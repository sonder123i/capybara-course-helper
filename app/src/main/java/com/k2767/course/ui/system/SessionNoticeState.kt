package com.k2767.course.ui.system

import androidx.lifecycle.ViewModel
import com.k2767.course.manager.SessionState
import com.k2767.course.manager.SessionToken
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class SessionNoticeSnapshot(val token: SessionToken? = null, val shown: Boolean = false, val visible: Boolean = false)

/** Presentation belongs to an expiration episode, not to a page or a network callback. */
class SessionNoticeState {
    private val mutableState = MutableStateFlow(SessionNoticeSnapshot())
    val state = mutableState.asStateFlow()

    fun update(session: SessionState, needsLogin: Boolean, canPresent: Boolean) {
        var next = mutableState.value.takeIf { it.token == session.token } ?: SessionNoticeSnapshot(session.token)
        next = when {
            !session.expired -> SessionNoticeSnapshot(session.token)
            !needsLogin -> next.copy(visible = false)
            canPresent && !next.shown -> next.copy(shown = true, visible = true)
            else -> next
        }
        mutableState.value = next
    }

    fun dismiss(expected: SessionToken) {
        if (mutableState.value.token == expected) mutableState.value = mutableState.value.copy(visible = false)
    }
}

class SessionNoticeViewModel : ViewModel() { val notices = SessionNoticeState() }
