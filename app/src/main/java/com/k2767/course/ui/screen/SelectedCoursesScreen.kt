package com.k2767.course.ui.screen

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Place
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.graphics.vector.ImageVector
import com.k2767.course.ui.system.SystemIconButton
import com.k2767.course.ui.system.LocalAppOverlayBottomInset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.k2767.course.model.Course
import com.k2767.course.ui.theme.*
import com.k2767.course.ui.system.GlassPullRefreshBox
import com.k2767.course.ui.system.SystemCard
import com.k2767.course.ui.system.SystemStatusBadge
import com.k2767.course.ui.system.SystemTone
import com.k2767.course.ui.system.SystemDestructiveButton
import com.k2767.course.ui.system.SystemDialog
import com.k2767.course.ui.system.SystemSecondaryButton
import com.k2767.course.ui.system.neumorphicShadow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectedCoursesScreen(
    courses: List<Course>,
    isLoading: Boolean,
    isDropping: Boolean = false,
    onRefresh: () -> Unit,
    onDropCourse: (Course) -> Unit = {}
) {

    // 透明容器：让 Aurora 壁纸透出，与"可选"页一致
    Box(
        modifier = Modifier.fillMaxSize()
    ) {
        // 退课进度条
        if (isDropping) {
            LinearProgressIndicator(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
                color = SemanticDanger,
                trackColor = SemanticDanger.copy(alpha = 0.2f)
            )
        }

        GlassPullRefreshBox(
            isRefreshing = isLoading,
            onRefresh = onRefresh,
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                isLoading && courses.isEmpty() -> {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = LocalAppOverlayBottomInset.current + 20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        items(6) {
                            CourseSkeletonItem()
                        }
                    }
                }
                courses.isEmpty() -> {
                    Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = "暂无已选课程\n下拉刷新获取数据",
                            style = MaterialTheme.typography.bodyLarge,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            color = Neutral500
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 12.dp, bottom = LocalAppOverlayBottomInset.current + 20.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        item {
                            Column(Modifier.padding(horizontal = 4.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("已选 ${courses.size} 门课程", style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurface)
                                if (courses.all { it.completeParams["canDrop"] == "false" }) {
                                    Text("上课安排可在课表查看；退课请在教务网页办理。",
                                        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }

                        items(courses) { course ->
                            SelectedCourseItem(
                                course = course,
                                isDropping = isDropping,
                                onDrop = { onDropCourse(course) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SelectedCourseItem(
    course: Course,
    isDropping: Boolean = false,
    onDrop: () -> Unit = {}
) {
    var showDropConfirmDialog by remember { mutableStateOf(false) }
    
    if (showDropConfirmDialog) {
        SystemDialog(
            onDismissRequest = { showDropConfirmDialog = false },
            title = {
                Text(
                    text = "确认退课",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = SemanticDanger
                )
            },
            dismissButton = {
                SystemSecondaryButton(
                    text = "取消",
                    onClick = { showDropConfirmDialog = false },
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                SystemDestructiveButton(
                    text = "确认退课",
                    onClick = {
                        showDropConfirmDialog = false
                        onDrop()
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        ) {
            Text(
                text = "确定要退选「${course.name ?: "未知课程"}」吗？\n\n退课后可能无法再次选上此课程。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
    
    val canDrop = course.completeParams["canDrop"] != "false"
    SystemCard(modifier = Modifier.fillMaxWidth(), contentPadding = PaddingValues(16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(course.name.orEmpty().ifBlank { "未命名课程" }, modifier = Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold,
                maxLines = 3, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurface)
            if (!course.credit.isNullOrBlank()) {
                SystemStatusBadge("${course.credit} 学分", tone = SystemTone.Info)
            }
        }
        if (!course.teacher.isNullOrBlank()) SelectedCourseMetadata(Icons.Default.Person, course.teacher)
        if (!course.time.isNullOrBlank()) SelectedCourseMetadata(Icons.Default.Schedule, course.time)
        if (!course.location.isNullOrBlank()) SelectedCourseMetadata(Icons.Default.Place, course.location)
        if (!course.jxbmc.isNullOrBlank() && course.jxbmc != course.name) SelectedCourseMetadata(Icons.Default.Group, course.jxbmc)
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(
                text = course.courseId.orEmpty().let { if (it.isBlank()) "已选课程" else "课程号 $it" },
                modifier = Modifier.weight(1f), style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis
            )
            if (canDrop) {
                SystemIconButton(Icons.Default.Delete, "退课", { showDropConfirmDialog = true },
                    tint = SemanticDanger, enabled = !isDropping, chip = false)
            } else SystemStatusBadge("已选", tone = SystemTone.Success)
        }
    }
}

@Composable
private fun SelectedCourseMetadata(icon: ImageVector, text: String) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Icon(icon, contentDescription = null, modifier = Modifier.padding(top = 2.dp).size(16.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(text, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun CourseSkeletonItem() {
    SystemCard(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Box(modifier = Modifier.width(150.dp).height(20.dp).background(Neutral200.copy(alpha = 0.5f), RoundedCornerShape(4.dp)))
                Box(modifier = Modifier.width(50.dp).height(20.dp).background(Neutral200.copy(alpha = 0.5f), RoundedCornerShape(4.dp)))
            }
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.3f), thickness = 0.5.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(24.dp)) {
                Box(modifier = Modifier.width(60.dp).height(30.dp).background(Neutral200.copy(alpha = 0.5f), RoundedCornerShape(4.dp)))
                Box(modifier = Modifier.width(60.dp).height(30.dp).background(Neutral200.copy(alpha = 0.5f), RoundedCornerShape(4.dp)))
            }
            Box(modifier = Modifier.fillMaxWidth().height(40.dp).background(Neutral200.copy(alpha = 0.3f), RoundedCornerShape(8.dp)))
        }
    }
}
