package com.tyust.course.ui.system.glass

import org.junit.Assert.assertEquals
import org.junit.Test

class GlideDecayTest {
    @Test fun elapsedTimeControlsDecayAtEveryRefreshRate() {
        fun at(rate: Int): Float {
            var velocity = 4f
            repeat(rate / 10) { velocity = decayGlideVelocity(velocity, 1f / rate) }
            return velocity
        }
        assertEquals(at(60), at(90), 0.00001f)
        assertEquals(at(60), at(120), 0.00001f)
    }

    @Test fun stalledFrameAndReverseVelocityRemainStable() {
        assertEquals(-decayGlideVelocity(2f, 0.1f), decayGlideVelocity(-2f, 0.1f), 0.00001f)
        assertEquals(0f, decayGlideVelocity(2f, 10f), 0f)
        assertEquals(0f, decayGlideVelocity(Float.NaN, 0.1f), 0f)
    }
}
