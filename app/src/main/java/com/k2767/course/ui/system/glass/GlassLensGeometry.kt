package com.k2767.course.ui.system.glass

/** Physical corners in clockwise order. An asymmetric corner may exceed half an edge. */
data class GlassLensCorners(
    val topLeft: Float,
    val topRight: Float,
    val bottomRight: Float,
    val bottomLeft: Float
) {
    val minimum: Float get() = minOf(topLeft, topRight, bottomRight, bottomLeft)

    fun fit(width: Float, height: Float): GlassLensCorners {
        fun clean(value: Float) = if (value.isFinite()) value.coerceAtLeast(0f) else 0f
        val tl = clean(topLeft)
        val tr = clean(topRight)
        val br = clean(bottomRight)
        val bl = clean(bottomLeft)
        fun ratio(edge: Float, sum: Float) = if (sum > 0f) (edge / sum).coerceIn(0f, 1f) else 1f
        val factor = minOf(ratio(width, tl + tr), ratio(width, bl + br),
            ratio(height, tl + bl), ratio(height, tr + br))
        return GlassLensCorners(tl * factor, tr * factor, br * factor, bl * factor)
    }

    fun gradient(width: Float, height: Float) = GlassLensCorners(
        topLeft * 1.5f, topRight * 1.5f, bottomRight * 1.5f, bottomLeft * 1.5f
    ).fit(width, height)

    companion object {
        fun uniform(radius: Float) = GlassLensCorners(radius, radius, radius, radius)
    }
}

/** Lens-local pixels to source pixels; translation stays in srcLeftPx/srcTopPx. */
internal data class GlassLensSourceAxes(
    val xx: Float = 1f,
    val xy: Float = 0f,
    val yx: Float = 0f,
    val yy: Float = 1f
)
