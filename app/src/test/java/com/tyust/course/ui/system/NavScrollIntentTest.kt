package com.tyust.course.ui.system

import org.junit.Assert.*
import org.junit.Test

class NavScrollIntentTest {
    @Test fun smallScrollsAccumulateAndReverseRequiresFreshTravel() {
        val scroll = NavScrollIntent()
        repeat(9) { assertNull(scroll.scroll(-4f)) }
        assertEquals(true, scroll.scroll(-4f))
        assertNull(scroll.scroll(12f))
        assertNull(scroll.scroll(-2f))
        assertNull(scroll.scroll(12f))
        assertEquals(false, scroll.scroll(8f))
    }

    @Test fun pageChangeDiscardsTravel() {
        val scroll = NavScrollIntent()
        scroll.scroll(-39f)
        scroll.reset()
        assertNull(scroll.scroll(-1f))
    }
}
