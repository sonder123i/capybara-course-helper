package com.tyust.course.ui.theme

import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.ValueAnimator
import android.app.Activity
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.drawable.Drawable
import android.os.Build
import android.provider.Settings
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.LinearInterpolator
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.doOnPreDraw
import com.tyust.course.R
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/** The system splash waits only for the first app frame. Animation is a removable app overlay. */
object StartupLogoAnimation {
    private var played = false
    var contentProgress by androidx.compose.runtime.mutableFloatStateOf(1f)
        private set

    fun install(activity: Activity) {
        val splash = activity.installSplashScreen()
        splash.setOnExitAnimationListener { provider ->
            if (played || activity.isFinishing) { provider.remove(); return@setOnExitAnimationListener }
            played = true
            val animationsEnabled = if (Build.VERSION.SDK_INT >= 26) ValueAnimator.areAnimatorsEnabled()
                else Settings.Global.getFloat(activity.contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) > 0f
            if (!animationsEnabled) { provider.remove(); return@setOnExitAnimationListener }
            val root = activity.window.decorView as? ViewGroup
            if (root == null) { provider.remove(); return@setOnExitAnimationListener }
            val location = IntArray(2)
            provider.iconView.getLocationInWindow(location)
            val rootLocation = IntArray(2)
            root.getLocationInWindow(rootLocation)
            val width = provider.iconView.width.takeIf { it > 0 } ?: (240 * activity.resources.displayMetrics.density).toInt()
            val height = provider.iconView.height.takeIf { it > 0 } ?: width
            val bounds = Rect(location[0] - rootLocation[0], location[1] - rootLocation[1],
                location[0] - rootLocation[0] + width, location[1] - rootLocation[1] + height)
            val overlay = LogoOverlay(activity, bounds)
            root.addView(overlay, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
            overlay.doOnPreDraw { provider.remove(); overlay.start { root.removeView(overlay) } }
        }
    }

    private class LogoOverlay(context: Context, private val logoBounds: Rect) : View(context) {
        private val renderer = StartupLogoRenderer(context)
        private val paint = Paint().apply { color = ContextCompat.getColor(context, R.color.startup_background) }
        private var fraction = 0f
        private var animator: ValueAnimator? = null
        private var finish: (() -> Unit)? = null
        private var finished = false

        init { importantForAccessibility = IMPORTANT_FOR_ACCESSIBILITY_NO }

        fun start(onFinish: () -> Unit) {
            if (finished) { onFinish(); return }
            finish = onFinish
            contentProgress = 0f
            animator = ValueAnimator.ofFloat(0f, 1f).apply {
                duration = StartupChoreography.DurationMillis
                interpolator = LinearInterpolator()
                addUpdateListener {
                    fraction = it.animatedValue as Float
                    contentProgress = StartupChoreography.content(fraction * StartupChoreography.DurationMillis)
                    invalidate()
                }
                addListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) { complete() }
                })
                start()
            }
        }

        private fun complete() {
            if (finished) return
            finished = true
            contentProgress = 1f
            finish?.invoke()
            finish = null
        }

        override fun onDraw(canvas: Canvas) {
            val milliseconds = fraction * StartupChoreography.DurationMillis
            paint.alpha = ((1f - StartupChoreography.content(milliseconds)) * 255).toInt()
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            renderer.draw(canvas, logoBounds, milliseconds)
        }
        override fun onTouchEvent(event: MotionEvent): Boolean {
            if (event.actionMasked == MotionEvent.ACTION_DOWN) { animator?.cancel(); complete() }
            return false
        }

        override fun onDetachedFromWindow() {
            contentProgress = 1f
            finished = true
            finish = null
            animator?.removeAllListeners()
            animator?.cancel()
            animator = null
            super.onDetachedFromWindow()
        }
    }
}
