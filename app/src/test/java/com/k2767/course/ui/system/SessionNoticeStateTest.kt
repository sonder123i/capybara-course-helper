package com.k2767.course.ui.system

import com.k2767.course.manager.SessionStateStore
import org.junit.Assert.*
import org.junit.Test

class SessionNoticeStateTest {
    @Test fun cookieUpdateRemovesTheVisibleAndPendingExpirationNotice() {
        val sessions = SessionStateStore(); val notice = SessionNoticeState()
        val old = sessions.replace("A"); sessions.expire(old)
        notice.update(sessions.state.value, true, true)
        assertTrue(notice.state.value.visible)
        sessions.replace("A")
        notice.update(sessions.state.value, false, true)
        assertFalse(notice.state.value.visible)
        assertFalse(sessions.expire(old))
        notice.update(sessions.state.value, true, true)
        assertFalse(notice.state.value.visible)
    }

    @Test fun deferredNoticeIsCancelledByRecoveryBeforeAnotherModalCloses() {
        val sessions = SessionStateStore(); val notice = SessionNoticeState()
        sessions.expire(sessions.replace("A"))
        notice.update(sessions.state.value, true, false)
        assertFalse(notice.state.value.shown)
        sessions.replace("A")
        notice.update(sessions.state.value, false, true)
        assertFalse(notice.state.value.visible)
    }

    @Test fun dismissalAndPageSwitchDoNotRepeatTheSameEpisode() {
        val sessions = SessionStateStore(); val notice = SessionNoticeState()
        sessions.expire(sessions.replace("A"))
        notice.update(sessions.state.value, true, true)
        notice.dismiss(sessions.token)
        repeat(5) { notice.update(sessions.state.value, true, true) }
        assertFalse(notice.state.value.visible)
        assertTrue(notice.state.value.shown)
        sessions.expire(sessions.replace("A"))
        notice.update(sessions.state.value, true, true)
        assertTrue(notice.state.value.visible)
    }

    @Test fun closingOldDialogCannotCloseANewAccountsDialog() {
        val sessions = SessionStateStore(); val notice = SessionNoticeState()
        val old = sessions.replace("A"); sessions.expire(old)
        notice.update(sessions.state.value, true, true)
        sessions.expire(sessions.replace("B"))
        notice.update(sessions.state.value, true, true)
        notice.dismiss(old)
        assertTrue(notice.state.value.visible)
    }

    @Test fun restoringNeverShowsAnExpirationDialog() {
        val sessions = SessionStateStore(); val notice = SessionNoticeState()
        sessions.expire(sessions.replace("A"))
        notice.update(sessions.state.value, false, true)
        assertFalse(notice.state.value.shown)
    }
}
