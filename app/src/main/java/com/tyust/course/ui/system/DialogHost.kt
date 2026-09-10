package com.tyust.course.ui.system

import androidx.activity.compose.BackHandler
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.EnterExitState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.snap
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.State
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
import androidx.compose.ui.semantics.hideFromAccessibility
import androidx.compose.ui.semantics.isTraversalGroup
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
    private val portalCount = androidx.compose.runtime.mutableIntStateOf(0)
    val hasBlockingSurface: Boolean get() = dialogs.isNotEmpty() || portalCount.intValue > 0
    internal fun beginPortal() { portalCount.intValue++ }
    internal fun endPortal() { portalCount.intValue = (portalCount.intValue - 1).coerceAtLeast(0) }
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
internal val LocalDialogProgress = compositionLocalOf<State<Float>?> { null }

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
            AnimatedVisibility(visibleState = visibility, enter = EnterTransition.None, exit = ExitTransition.None) {
                val progress = transition.animateFloat(transitionSpec = {
                    when {
                        reducedMotion -> snap()
                        targetState != EnterExitState.Visible -> tween(if (page) 240 else 190)
                        page -> tween(240)
                        else -> spring(0.80f, 420f)
                    }
                }, label = "dialog-presence") { if (it == EnterExitState.Visible) 1f else 0f }
                Box(
                    modifier.fillMaxSize().clickable(
                        interactionSource = remember { MutableInteractionSource() }, indication = null,
                        onClick = { state.dismiss(dialog.handle) }
                    ),
                    contentAlignment = if (bottom) Alignment.BottomCenter else Alignment.Center
                ) {
                    Box(Modifier.fillMaxSize().graphicsLayer { alpha = progress.value.coerceIn(0f, 1f) }
                        .background(Color.Black.copy(alpha = 0.22f)))
                    Box(
                        Modifier.graphicsLayer {
                                val presence = progress.value
                                val back = backProgress.value
                                translationX = if (page) size.width * ((1f - presence) / 4f + 0.18f * back) else 0f
                                translationY = if (bottom) size.height * ((1f - presence) / 3f + 0.18f * back) else 0f
                                scaleX = if (!page && !bottom) 0.94f + 0.06f * presence else 1f
                                scaleY = scaleX
                                alpha = presence.coerceIn(0f, 1f) * (1f - 0.12f * back)
                            }
                            .then(if (page) Modifier.fillMaxSize() else Modifier.windowInsetsPadding(WindowInsets.systemBars.union(WindowInsets.ime)).padding(vertical = 12.dp))
                            .semantics {
                                paneTitle = "对话框"
                                isTraversalGroup = true
                                if (!visibility.targetState || state.dialogs.lastOrNull() !== dialog) hideFromAccessibility()
                            }
                            .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = {})
                    ) {
                        CompositionLocalProvider(LocalDialogProgress provides progress) { dialog.content() }
                    }
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
