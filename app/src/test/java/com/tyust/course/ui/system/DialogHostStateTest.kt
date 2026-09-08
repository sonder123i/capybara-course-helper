package com.tyust.course.ui.system

import org.junit.Assert.*
import org.junit.Test

class DialogHostStateTest {
    @Test fun disposingOldOwnerCannotDismissNewDialog() {
        val state = DialogHostState()
        var dismissed = 0
        val first = state.show(onDismiss = { dismissed++ }) {}
        state.dismiss(first)
        val second = state.show(onDismiss = { dismissed++ }) {}
        state.finishDismissal(first)
        state.dismiss(first, notify = false)
        assertTrue(state.isVisible)
        assertSame(second, state.dialogs.single().handle)
        assertEquals(1, dismissed)
        state.finishDismissal(first)
        assertEquals(1, dismissed)
    }

    @Test fun dismissingNestedDialogKeepsParentAndCallbackRunsOnce() {
        val state = DialogHostState()
        var dismissed = 0
        val parent = state.show(onDismiss = {}) {}
        val child = state.show(onDismiss = { dismissed++ }) {}
        state.dismiss(child)
        state.finishDismissal(child)
        state.finishDismissal(child)
        assertEquals(1, dismissed)
        assertSame(parent, state.dialogs.single().handle)
        assertTrue(state.isVisible)
    }
}
