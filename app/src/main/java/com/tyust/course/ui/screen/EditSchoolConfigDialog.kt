package com.tyust.course.ui.screen

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.tyust.course.model.SchoolConfig
import com.tyust.course.ui.system.SystemDialog
import com.tyust.course.ui.system.SystemPrimaryButton
import com.tyust.course.ui.system.SystemSecondaryButton
import com.tyust.course.ui.system.SystemSegmentedControl
import com.tyust.course.ui.system.glass.glassChip
import com.tyust.course.ui.system.rememberGlassAccessibilityMode
import com.tyust.course.ui.theme.MotionEasing
import com.tyust.course.ui.theme.MotionSpring

@Composable
fun EditSchoolConfigDialog(
    school: SchoolConfig,
    onDismiss: () -> Unit,
    onSave: (SchoolConfig) -> Unit
) {
    // State for all editable fields
    var name by remember { mutableStateOf(school.name) }
    var domain by remember { mutableStateOf(school.domain) }
    var protocol by remember { mutableStateOf(school.protocol) }
    var basePath by remember { mutableStateOf(school.basePath) }
    var academicSystem by remember { mutableStateOf(school.academicSystem) }
    var pageCharset by remember { mutableStateOf(school.pageCharset) }
    var allowedHosts by remember { mutableStateOf(school.allowedAcademicHosts.joinToString(", ")) }
    var courseGnmkdm by remember { mutableStateOf(school.courseGnmkdm) }
    var gradeGnmkdm by remember { mutableStateOf(school.gradeGnmkdm) }
    var scheduleGnmkdm by remember { mutableStateOf(school.scheduleGnmkdm) }

    // URL input for smart parsing
    var urlInput by remember { mutableStateOf("") }

    // Advanced paths
    var showAdvanced by remember { mutableStateOf(false) }
    var studentInfoPath by remember { mutableStateOf(school.studentInfoPath) }
    var courseIndexPath by remember { mutableStateOf(school.courseIndexPath) }
    var courseListPath by remember { mutableStateOf(school.courseListPath) }
    var selectCoursePath by remember { mutableStateOf(school.selectCoursePath) }
    var schedulePath by remember { mutableStateOf(school.schedulePath) }
    var gradesPath by remember { mutableStateOf(school.gradesPath) }
    val supportsZfModulePaths = academicSystem in setOf("auto", "legacy_zf", "zf")

    fun parseUrl(url: String) {
        val parsed = com.tyust.course.academic.AcademicAddress.parse(url) ?: return
        protocol = parsed.protocol
        domain = parsed.domain
        basePath = parsed.basePath
    }

    SystemDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "编辑学校配置",
                fontWeight = FontWeight.Bold,
                style = MaterialTheme.typography.titleLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        confirmButton = {
            SystemPrimaryButton(
                text = "保存",
                onClick = {
                    // Create updated config
                    val updatedSchool = SchoolConfig.fromJson(school.toJson()).apply {
                        this.name = name
                        this.domain = domain
                        this.protocol = protocol
                        this.basePath = basePath
                        this.courseGnmkdm = courseGnmkdm
                        this.gradeGnmkdm = gradeGnmkdm
                        this.scheduleGnmkdm = scheduleGnmkdm
                        this.studentInfoPath = studentInfoPath
                        this.courseIndexPath = courseIndexPath
                        this.courseListPath = courseListPath
                        this.selectCoursePath = selectCoursePath
                        this.schedulePath = schedulePath
                        this.gradesPath = gradesPath
                        this.academicSystem = academicSystem
                        this.detectionSource = if (academicSystem == school.academicSystem) school.detectionSource else "manual"
                        this.pageCharset = pageCharset
                        this.allowedAcademicHosts = java.util.ArrayList(allowedHosts.split(',', '，', '\n').map(String::trim).filter(String::isNotBlank))
                        this.academicConfigVersion = school.academicConfigVersion
                    }
                    onSave(updatedSchool)
                },
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = {
            SystemSecondaryButton(
                text = "取消",
                onClick = onDismiss,
                modifier = Modifier.fillMaxWidth()
            )
        }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(max = 380.dp) // 留出下方大圆角按钮的空间
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            SchoolFormPanel {
                SchoolFormPanelTitle(
                    icon = Icons.Default.AutoAwesome,
                    text = "智能识别"
                )
                SchoolFormField(
                    label = "教务系统网址",
                    value = urlInput,
                    onValueChange = { urlInput = it },
                    placeholder = "http://jwxt.example.edu.cn/jwglxt",
                    helper = "识别后会覆盖下方的域名、协议与基础路径"
                )
                SystemPrimaryButton(
                    text = "识别并填充",
                    onClick = { parseUrl(urlInput) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = urlInput.isNotBlank()
                )
            }

            SchoolFormSectionTitle("基本配置")

            val systems = listOf("auto" to "自动识别（四类教务）", "legacy_zf" to "新正方（兼容流程）", "zf" to "新正方", "zf_old" to "旧正方", "qz" to "新强智", "qz_old" to "旧强智")
            Text("教务系统类型", style = MaterialTheme.typography.labelMedium)
            com.tyust.course.ui.system.SystemPicker(
                options = systems.map { it.second },
                selectedIndex = systems.indexOfFirst { it.first == academicSystem }.takeIf { it >= 0 },
                onSelect = { academicSystem = systems[it].first },
                modifier = Modifier.fillMaxWidth()
            )

            com.tyust.course.academic.AcademicCapabilities.support(academicSystem)?.let { support ->
                Text(support.login, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(support.limits, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            SchoolFormField(
                label = "学校名称",
                value = name,
                onValueChange = { name = it }
            )

            SchoolFormField(
                label = "教务系统域名",
                value = domain,
                onValueChange = { domain = it },
                placeholder = "jwxt.example.edu.cn"
            )

            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(
                    text = "协议",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                // 原先是 ExposedDropdownMenuBox：两个选项开一个下拉太重，也不是本 App 的语言
                SystemSegmentedControl(
                    options = listOf("HTTPS", "HTTP"),
                    selectedIndex = if (protocol == "https") 0 else 1,
                    onSelect = { index -> protocol = if (index == 0) "https" else "http" },
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = if (protocol == "https") "加密连接，推荐优先尝试" else "非加密连接",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                )
            }

            SchoolFormField(
                label = "基础路径",
                value = basePath,
                onValueChange = { basePath = it },
                placeholder = "/jwglxt",
                helper = "如 /jwglxt 或 /jwxt"
            )

            if (supportsZfModulePaths) {
                SchoolFormSectionTitle("正方模块代码")

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    SchoolFormField(
                        label = "选课",
                        value = courseGnmkdm,
                        onValueChange = { courseGnmkdm = it },
                        modifier = Modifier.weight(1f)
                    )
                    SchoolFormField(
                        label = "成绩",
                        value = gradeGnmkdm,
                        onValueChange = { gradeGnmkdm = it },
                        modifier = Modifier.weight(1f)
                    )
                }
                SchoolFormField(
                    label = "课表",
                    value = scheduleGnmkdm,
                    onValueChange = { scheduleGnmkdm = it }
                )
            }

            AdvancedSectionToggle(
                expanded = showAdvanced,
                onToggle = { showAdvanced = !showAdvanced }
            )

            if (showAdvanced) {
                SchoolFormField(label = "页面字符集", value = pageCharset, onValueChange = { pageCharset = it }, helper = "通常为 UTF-8，旧站点可填写 GBK")
                SchoolFormField(label = "额外允许访问的学校域名", value = allowedHosts, onValueChange = { allowedHosts = it }, helper = "统一认证域名可填在这里，多个域名以逗号分隔")
                if (supportsZfModulePaths) {
                    SchoolFormSectionTitle("URL 路径配置")
                    SchoolFormField(
                        label = "学生信息验证",
                        value = studentInfoPath,
                        onValueChange = { studentInfoPath = it }
                    )
                    SchoolFormField(
                        label = "选课首页",
                        value = courseIndexPath,
                        onValueChange = { courseIndexPath = it }
                    )
                    SchoolFormField(
                        label = "课程列表",
                        value = courseListPath,
                        onValueChange = { courseListPath = it }
                    )
                    SchoolFormField(
                        label = "选课提交",
                        value = selectCoursePath,
                        onValueChange = { selectCoursePath = it }
                    )
                    SchoolFormField(
                        label = "课表查询",
                        value = schedulePath,
                        onValueChange = { schedulePath = it }
                    )
                    SchoolFormField(
                        label = "成绩查询",
                        value = gradesPath,
                        onValueChange = { gradesPath = it }
                    )
                }
            }
        }
    }
}

/** 高级配置的折叠开关。原先是一行带 `▲▼` 字符的 TextButton，这里换成会转的 chevron。 */
@Composable
private fun AdvancedSectionToggle(
    expanded: Boolean,
    onToggle: () -> Unit
) {
    val reduceMotion = rememberGlassAccessibilityMode().reduceMotion
    val rotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        animationSpec = if (reduceMotion) {
            androidx.compose.animation.core.snap()
        } else if (expanded) {
            MotionSpring.liquidMenu()
        } else {
            androidx.compose.animation.core.tween(140, easing = MotionEasing.Accelerate)
        },
        label = "advancedToggleArrow"
    )
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassChip(shape = RoundedCornerShape(14.dp))
            .clickable(role = Role.Button, onClick = onToggle)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Text(
            text = if (expanded) "隐藏高级配置" else "显示高级配置",
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f)
        )
        Icon(
            imageVector = Icons.Default.KeyboardArrowDown,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .size(18.dp)
                .rotate(rotation)
        )
    }
}
