package com.tyust.course.ui.system

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.launch

enum class DialogPresentation { Center, Bottom, Page }
class DialogHandle internal constructor()

internal class HostedDialog(
    val handle: DialogHandle,
    val onDismiss: () -> Unit,
    val presentation: DialogPresentation,
    val content: @Composable () -> Unit
) {
    val visibility = MutableTransitionState(false).apply { targetState = true }
    var notifyOnClose = true
}

class DialogHostState {
    internal val dialogs = mutableStateListOf<HostedDialog>()
    val currentDialog: (@Composable () -> Unit)? get() = dialogs.lastOrNull()?.content
    val isVisible: Boolean get() = dialogs.lastOrNull()?.visibility?.targetState == true

    fun show(
        onDismiss: () -> Unit,
        presentation: DialogPresentation = DialogPresentation.Center,
        content: @Composable () -> Unit
    ): DialogHandle = DialogHandle().also { dialogs.add(HostedDialog(it, onDismiss, presentation, content)) }

    fun dismiss(handle: DialogHandle? = dialogs.lastOrNull()?.handle, notify: Boolean = true) {
        val dialog = dialogs.firstOrNull { it.handle === handle } ?: return
        dialog.notifyOnClose = dialog.notifyOnClose && notify
        dialog.visibility.targetState = false
    }

    internal fun finishDismissal(handle: DialogHandle) {
        val dialog = dialogs.firstOrNull { it.handle === handle } ?: return
        if (dialog.visibility.targetState) return
        dialogs.remove(dialog)
        if (dialog.notifyOnClose) dialog.onDismiss()
    }
}

@Composable
fun rememberDialogHostState(): DialogHostState = remember { DialogHostState() }

val LocalDialogHost = compositionLocalOf<DialogHostState?> { null }

@Composable
fun DialogHost(state: DialogHostState, modifier: Modifier = Modifier) {
    val reducedMotion = rememberGlassAccessibilityMode().reduceMotion
    state.dialogs.forEach { dialog ->
        key(dialog.handle) {
            val visibility = dialog.visibility
            LaunchedEffect(visibility.isIdle, visibility.currentState, visibility.targetState) {
                if (visibility.isIdle && !visibility.currentState && !visibility.targetState) {
                    state.finishDismissal(dialog.handle)
                }
            }
            val bottom = dialog.presentation == DialogPresentation.Bottom
            val page = dialog.presentation == DialogPresentation.Page
            val backProgress = remember { Animatable(0f) }
            val scope = rememberCoroutineScope()
            PredictiveBackHandler(enabled = state.dialogs.lastOrNull() === dialog && visibility.targetState) { events ->
                try {
                    events.collect { if (!reducedMotion && (page || bottom)) backProgress.snapTo(it.progress) }
                    state.dismiss(dialog.handle)
                } catch (_: CancellationException) {
                    scope.launch { backProgress.animateTo(0f, spring(0.9f, 500f)) }
                }
            }
            val enter = when {
                reducedMotion -> EnterTransition.None
                page -> slideInHorizontally(tween(240)) { it / 4 } + fadeIn(tween(180))
                bottom -> fadeIn(tween(120)) + slideInVertically(spring(0.80f, 420f)) { it / 3 }
                else -> fadeIn(tween(120)) + scaleIn(initialScale = 0.94f, animationSpec = spring(0.80f, 420f))
            }
            val exit = when {
                reducedMotion -> ExitTransition.None
                page -> slideOutHorizontally(tween(240)) { it / 4 } + fadeOut(tween(180))
                bottom -> fadeOut(tween(190)) + slideOutVertically(tween(190)) { it / 3 }
                else -> fadeOut(tween(190)) + scaleOut(targetScale = 0.94f, animationSpec = tween(190))
            }
            AnimatedVisibility(visibleState = visibility, enter = EnterTransition.None, exit = ExitTransition.None) {
                Box(
                    modifier.fillMaxSize().clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null,
                        onClick = { state.dismiss(dialog.handle) }
                    ),
                    contentAlignment = if (bottom) Alignment.BottomCenter else Alignment.Center
                ) {
                    Box(Modifier.fillMaxSize().animateEnterExit(
                        enter = if (reducedMotion) EnterTransition.None else fadeIn(tween(180)),
                        exit = if (reducedMotion) ExitTransition.None else fadeOut(tween(190))
                    ).background(Color.Black.copy(alpha = 0.22f)))
                    Box(
                        Modifier.animateEnterExit(enter = enter, exit = exit)
                            .graphicsLayer {
                                val progress = backProgress.value
                                translationX = if (page) size.width * 0.18f * progress else 0f
                                translationY = if (bottom) size.height * 0.18f * progress else 0f
                                alpha = 1f - 0.12f * progress
                            }
                            .then(if (page) Modifier.fillMaxSize() else Modifier.windowInsetsPadding(WindowInsets.systemBars).padding(vertical = 12.dp))
                            .semantics { paneTitle = "对话框" }
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                    ) { dialog.content() }
                    if (!visibility.targetState) {
                        Box(Modifier.fillMaxSize().clickable(
                            interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {}
                        ).clearAndSetSemantics {})
                    }
                }
            }
        }
    }
}
