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
fun AppSymbol(spec: AppSymbolSpec, progress: Float, tint: Color, modifier: Modifier = Modifier, reduceMotion: Boolean = false) {
    AnimatedLineIcon(
        spec = when (spec) {
            AppSymbolSpec.Courses -> AnimatedIconSpec.Courses
            AppSymbolSpec.Schedule -> AnimatedIconSpec.Calendar
            AppSymbolSpec.Grab -> AnimatedIconSpec.Target
            AppSymbolSpec.Grades -> AnimatedIconSpec.Grades
            AppSymbolSpec.Settings -> AnimatedIconSpec.Settings
        }, modifier = modifier, tint = tint,
        sharedProgress = if (reduceMotion) if (progress >= 0.5f) 1f else 0f else progress
    )
}
