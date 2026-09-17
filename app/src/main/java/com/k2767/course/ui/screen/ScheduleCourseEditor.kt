package com.k2767.course.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.k2767.course.manager.ScheduleSettingsManager.CustomCourse
import com.k2767.course.schedule.*
import com.k2767.course.ui.system.*

@Composable
fun ScheduleCourseEditor(initial: CustomCourse, existing: List<ScheduleCourseUi>, periodCount: Int,
    isNew: Boolean, onClose: () -> Unit, onSave: (CustomCourse) -> Unit) {
    var name by rememberSaveable(initial.id) { mutableStateOf(initial.name) }
    var teacher by rememberSaveable(initial.id) { mutableStateOf(initial.teacher) }
    var location by rememberSaveable(initial.id) { mutableStateOf(initial.location) }
    var day by rememberSaveable(initial.id) { mutableStateOf(initial.day.toString()) }
    var start by rememberSaveable(initial.id) { mutableStateOf(initial.startPeriod.toString()) }
    var end by rememberSaveable(initial.id) { mutableStateOf(initial.endPeriod.toString()) }
    var weeks by rememberSaveable(initial.id) { mutableStateOf(initial.weeks) }
    var attempted by rememberSaveable { mutableStateOf(false) }
    val draft = ScheduleCourseRecord("custom:${initial.id}", name.trim(), teacher.trim(), location.trim(),
        day.toIntOrNull() ?: 0, start.toIntOrNull() ?: 0, end.toIntOrNull() ?: 0, weeks.trim(), true)
    val error = validateScheduleCourse(draft, periodCount)
    val conflicts = remember(draft, existing) { scheduleConflicts(draft, existing.map { it.record() }) }
    Scaffold(containerColor = androidx.compose.ui.graphics.Color.Transparent,
        topBar = { SystemTopBar(title = if (isNew) "添加课程" else "编辑课程", navigationIcon = {
            SystemIconButton(Icons.Default.Close, "关闭课程编辑", onClose)
        }) },
        bottomBar = {
            SystemPrimaryButton(text = "保存课程", modifier = Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(16.dp), onClick = {
                attempted = true
                if (error == null) onSave(CustomCourse(initial.id, draft.name, draft.location, draft.teacher,
                    draft.day, draft.startPeriod, draft.endPeriod, draft.weeks))
            })
        }) { padding ->
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(padding).padding(horizontal = 20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("课程名称") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(teacher, { teacher = it }, label = { Text("教师（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(location, { location = it }, label = { Text("地点（可选）") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(day, { day = it }, label = { Text("星期（1 为周一，7 为周日）") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(start, { start = it }, label = { Text("开始节次") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
                OutlinedTextField(end, { end = it }, label = { Text("结束节次") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.weight(1f))
            }
            OutlinedTextField(weeks, { weeks = it }, label = { Text("周次") }, supportingText = { Text("例如：1-16周(单),18周") }, modifier = Modifier.fillMaxWidth())
            androidx.compose.animation.AnimatedVisibility(attempted && error != null) {
                Text(error.orEmpty(), color = MaterialTheme.colorScheme.error)
            }
            if (conflicts.isNotEmpty()) {
                Text("时间重叠，仍可保存", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.error)
                conflicts.forEach { Text("${it.otherName} · 第 ${it.weeks.joinToString("、")} 周 · ${it.startPeriod}-${it.endPeriod} 节",
                    color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
            }
            Spacer(Modifier.height(16.dp))
        }
    }
}
