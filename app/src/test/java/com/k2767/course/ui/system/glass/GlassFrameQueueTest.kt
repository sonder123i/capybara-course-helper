package com.k2767.course.ui.system.glass

import org.junit.Assert.*
import org.junit.Test

class GlassFrameQueueTest {
    @Test fun completedOutputDoesNotScheduleItself() {
        val queue = GlassFrameQueue<String>()
        assertTrue(queue.request("still"))
        val frame = queue.next()!!
        assertFalse(queue.finish(frame, true))
        repeat(100) { assertFalse(queue.request("still")) }
    }

    @Test fun sourceChangeRendersUnchangedGeometry() {
        val queue = GlassFrameQueue<String>()
        queue.request("still")
        queue.finish(queue.next()!!, true)
        assertTrue(queue.sourceChanged(1))
        assertEquals(1L, queue.next()!!.sourceRevision)
        assertFalse(queue.finish(queue.next()!!, true))
    }

    @Test fun updatesDuringRenderDeliverLatestEvenWithoutAnotherDraw() {
        val queue = GlassFrameQueue<Int>()
        queue.request(0)
        val rendering = queue.next()!!
        repeat(30) { assertFalse(queue.request(it + 1)) }
        assertFalse(queue.sourceChanged(4))
        assertTrue(queue.finish(rendering, true))
        assertEquals(GlassFrameRequest(30, 4), queue.next())
        assertFalse(queue.finish(queue.next()!!, true))
        assertFalse(queue.request(30))
    }

    @Test fun failedFrameCanRetryAfterSourceArrives() {
        val queue = GlassFrameQueue<Int>()
        queue.request(1)
        assertFalse(queue.finish(queue.next()!!, false))
        assertTrue(queue.sourceChanged(1))
    }

    @Test fun closingDropsPendingWorkAndLateSourceCallbacks() {
        val queue = GlassFrameQueue<Int>()
        queue.request(1)
        val rendering = queue.next()!!
        queue.request(2)
        queue.close()
        assertFalse(queue.finish(rendering, true))
        assertFalse(queue.sourceChanged(1))
        assertFalse(queue.request(3))
        assertNull(queue.next())
    }
}
