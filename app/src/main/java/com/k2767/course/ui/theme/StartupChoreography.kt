package com.k2767.course.ui.theme

import kotlin.math.PI
import kotlin.math.sin

/** Pure trajectory in the splash's 1024-unit canvas, shared by rendering and boundary tests. */
object StartupChoreography {
    const val DurationMillis = 1100L
    const val SafeScale = 0.70f
    data class CardFrame(val x: Float, val y: Float, val rotation: Float, val cardAmount: Float)
    private fun smooth(value: Float): Float {
        val p = value.coerceIn(0f, 1f)
        return p * p * (3f - 2f * p)
    }
    fun card(milliseconds: Float, index: Int): CardFrame {
        require(index in 0..5)
        val column = index % 3 - 1
        val row = index / 3
        val separation = if (milliseconds <= 180f) smooth(milliseconds / 180f)
            else 1f - smooth((milliseconds - 180f - index * 12f) / (470f - index * 12f))
        val arc = if (milliseconds in 180f..650f)
            sin(((milliseconds - 180f) / 470f) * PI.toFloat()) else 0f
        return CardFrame(
            x = column * 76f * separation + (if (row == 0) 1f else -1f) * 18f * arc,
            y = (if (row == 0) -84f else 78f) * separation - 24f * arc,
            rotation = (column * 8f + if (row == 0) -3f else 3f) * separation,
            cardAmount = separation
        )
    }
    fun bolt(milliseconds: Float): Float = when {
        milliseconds < 150f -> 1f - smooth(milliseconds / 150f)
        else -> smooth((milliseconds - 650f) / 200f)
    }
    fun content(milliseconds: Float): Float = smooth((milliseconds - 850f) / 250f)
    fun settleScale(milliseconds: Float): Float {
        val p = ((milliseconds - 850f) / 250f).coerceIn(0f, 1f)
        return 1f + 0.045f * sin(p * PI.toFloat()) * (1f - p)
    }
}
