package com.k2767.course.ui.system.glass

import kotlin.math.abs
import kotlin.math.pow

internal fun decayGlideVelocity(velocity: Float, elapsedSeconds: Float): Float {
    if (!velocity.isFinite()) return 0f
    val decayed = velocity * 0.78f.pow(elapsedSeconds.coerceAtLeast(0f) * 60f)
    return if (abs(decayed) < 0.006f) 0f else decayed
}
