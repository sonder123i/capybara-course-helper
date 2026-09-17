package com.k2767.course.ui.screen

import android.view.ViewTreeObserver
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.k2767.course.survey.Survey
import com.k2767.course.survey.SurveyRepository
import com.k2767.course.survey.SurveyVisitTracker
import com.k2767.course.ui.system.SystemActionButton
import com.k2767.course.ui.system.SystemCard
import com.k2767.course.ui.system.rememberGlassAccessibilityMode
import com.k2767.course.ui.theme.MotionDuration
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun SurveyReminder(repository: SurveyRepository, canPresent: Boolean, foreground: Boolean, onOpen: (String) -> Unit, modifier: Modifier = Modifier) {
    val state by repository.state.collectAsState()
    val visit by SurveyVisitTracker.visit.collectAsState()
    val view = LocalView.current
    val scope = rememberCoroutineScope()
    val reduced = rememberGlassAccessibilityMode().reduceMotion
    var focus by remember { mutableStateOf(view.hasWindowFocus()) }
    var visible by remember(repository) { mutableStateOf<Survey?>(null) }
    val candidate = remember(state, repository) { repository.reminderCandidate() }
    DisposableEffect(view) {
        val observer = ViewTreeObserver.OnWindowFocusChangeListener { focus = it }
        view.viewTreeObserver.addOnWindowFocusChangeListener(observer)
        onDispose { if (view.viewTreeObserver.isAlive) view.viewTreeObserver.removeOnWindowFocusChangeListener(observer) }
    }
    LaunchedEffect(visit, foreground) { if (!foreground) visible = null }
    LaunchedEffect(state.saved.surveys) { if (visible != null && state.saved.surveys.none { it.id == visible?.id }) visible = null }
    LaunchedEffect(repository, candidate?.id, canPresent, foreground, focus, visit) {
        if (!canPresent || !focus || !foreground || candidate == null || visible != null) return@LaunchedEffect
        delay(1200)
        if (SurveyVisitTracker.budget.claim()) {
            visible = candidate
            scope.launch {
                try { repository.markReminded(candidate) }
                catch (cancelled: CancellationException) { throw cancelled }
                catch (_: Exception) { /* Keep the visit budget even if local storage is unavailable. */ }
            }
        }
    }
    AnimatedVisibility(visible != null && canPresent && foreground && focus && state.saved.remindersEnabled,
        modifier, enter = fadeIn(tween(if (reduced) 0 else MotionDuration.Medium)), exit = fadeOut(tween(if (reduced) 0 else MotionDuration.Fast))) {
        visible?.let { survey ->
            SystemCard {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("有一份新问卷", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                    IconButton(onClick = { visible = null }) { Icon(Icons.Outlined.Close, "稍后查看问卷") }
                }
                Text(survey.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold,
                    maxLines = 2, overflow = TextOverflow.Ellipsis)
                Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    TextButton(onClick = { scope.launch { repository.setRemindersEnabled(false); visible = null } }) { Text("关闭轻提醒") }
                    Spacer(Modifier.weight(1f))
                    SystemActionButton("去看看", { visible = null; onOpen(survey.id) }, primary = true)
                }
            }
        }
    }
}
