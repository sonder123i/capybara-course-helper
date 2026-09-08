package com.tyust.course.ui.system

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.DateRange
import androidx.compose.material.icons.automirrored.outlined.FormatListBulleted
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.PI
import kotlin.math.sin

enum class AppSymbolSpec(val outline: ImageVector) {
    Courses(Icons.AutoMirrored.Outlined.FormatListBulleted),
    Schedule(Icons.Outlined.DateRange),
    Grab(Icons.Outlined.MyLocation),
    Grades(Icons.Outlined.BarChart),
    Settings(Icons.Outlined.Settings)
}

/** Animation state is owned once by the navigation, including its optical sampling copy. */
@Composable
fun AppSymbol(spec: AppSymbolSpec, progress: Float, tint: Color, modifier: Modifier = Modifier) {
    val fill = progress.coerceIn(0f, 1f)
    val pulse = sin(PI.toFloat() * fill)
    Box(modifier.graphicsLayer {
        if (spec == AppSymbolSpec.Settings) rotationZ = 12f * pulse
        if (spec == AppSymbolSpec.Schedule) scaleY = 1f - 0.06f * pulse
        if (spec == AppSymbolSpec.Grab) { scaleX = 1f - 0.08f * pulse; scaleY = scaleX }
        if (spec == AppSymbolSpec.Grades) {
            transformOrigin = TransformOrigin(0.5f, 1f)
            scaleY = 0.88f + 0.12f * fill
        }
    }) {
        if (spec != AppSymbolSpec.Grades) {
            Icon(spec.outline, contentDescription = null, tint = tint, modifier = Modifier.matchParentSize())
        }
        Canvas(Modifier.matchParentSize()) {
            val unit = size.width / 24f
            when (spec) {
                AppSymbolSpec.Courses -> repeat(3) { row ->
                    val reveal = (fill * 1.4f - row * 0.2f).coerceIn(0f, 1f)
                    drawCircle(tint.copy(alpha = tint.alpha * reveal), 1.7f * unit, Offset(4f * unit, (6f + row * 6f) * unit))
                }
                AppSymbolSpec.Schedule -> repeat(3) { cell ->
                    val reveal = (fill * 1.4f - cell * 0.2f).coerceIn(0f, 1f)
                    drawRoundRect(tint.copy(alpha = tint.alpha * reveal), Offset((6f + cell * 4f) * unit, 14f * unit), Size(3f * unit, 3f * unit), CornerRadius(0.6f * unit))
                }
                AppSymbolSpec.Grab -> drawCircle(tint.copy(alpha = tint.alpha * fill), 3.1f * unit, center)
                AppSymbolSpec.Settings -> drawCircle(tint.copy(alpha = tint.alpha * fill), 2f * unit, center)
                AppSymbolSpec.Grades -> {
                    val heights = floatArrayOf(7f, 14f, 10f)
                    heights.forEachIndexed { bar, height ->
                        val left = (4f + bar * 6f) * unit
                        val top = (20f - height) * unit
                        drawRoundRect(tint, Offset(left, top), Size(4f * unit, height * unit),
                            CornerRadius(unit), style = Stroke(1.5f * unit))
                        val reveal = (fill * 1.4f - bar * 0.2f).coerceIn(0f, 1f)
                        val fillHeight = (height - 1.5f) * unit * reveal
                        if (fillHeight > 0f) {
                            drawRoundRect(tint, Offset(left + 0.75f * unit, 19.25f * unit - fillHeight),
                                Size(2.5f * unit, fillHeight), CornerRadius(0.4f * unit))
                        }
                    }
                }
            }
        }
    }
}
