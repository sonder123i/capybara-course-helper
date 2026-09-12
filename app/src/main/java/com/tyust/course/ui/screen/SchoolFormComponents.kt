package com.tyust.course.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.tyust.course.ui.system.GlassTextField
import com.tyust.course.ui.system.glass.glassChip
import com.tyust.course.ui.theme.SemanticDanger
import com.tyust.course.academic.AcademicCapabilities
import com.tyust.course.academic.AcademicSystem
import com.tyust.course.ui.system.SystemPicker
import androidx.compose.ui.platform.testTag

@Composable
internal fun SchoolAcademicSystemField(value: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text("教务类型", style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onSurfaceVariant)
        SystemPicker(
            options = AcademicCapabilities.selectableSystems.map(AcademicCapabilities::selectionLabel),
            selectedIndex = AcademicCapabilities.selectionIndex(value),
            onSelect = { onSelect(AcademicCapabilities.selectedTypeId(value, AcademicCapabilities.selectableSystems[it])) },
            modifier = Modifier.fillMaxWidth().testTag("school-system-picker")
        )
        Text(
            if (value == AcademicSystem.AUTO.id) "登录时自动识别，也可以手动选择学校使用的教务类型。"
            else AcademicCapabilities.support(value)?.login.orEmpty(),
            style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * 学校配置表单的共用件（「添加学校」与「编辑学校配置」两个弹窗）。
 *
 * 这两个弹窗原先整套是 Material 默认件：`OutlinedTextField` 的浮动 label、
 * `ExposedDropdownMenuBox` 的下拉、`primaryContainer` 淡紫底、以及用 `▲▼` 当箭头。
 * 放在液态玻璃的壳子里非常突兀。这里把它们统一成全 App 的写法：
 * 标签在字段上方（与登录页的账号/密码字段同一形制）、输入框用 [GlassTextField]、
 * 分组用不采样的 [glassChip] 面板。
 */

/** 表单字段：上方小标签 + 玻璃输入框 + 可选的说明/错误行。 */
@Composable
internal fun SchoolFormField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String = "",
    helper: String? = null,
    error: String? = null,
    enabled: Boolean = true,
    keyboardOptions: KeyboardOptions = KeyboardOptions.Default,
    keyboardActions: KeyboardActions = KeyboardActions.Default,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: @Composable (() -> Unit)? = null,
    singleLine: Boolean = true,
    minHeight: Dp = 48.dp
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        GlassTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = placeholder,
            enabled = enabled,
            keyboardOptions = keyboardOptions,
            keyboardActions = keyboardActions,
            visualTransformation = visualTransformation,
            trailing = trailing,
            singleLine = singleLine,
            minHeight = minHeight,
            isError = error != null
        )
        val note = error ?: helper
        if (!note.isNullOrBlank()) {
            Text(
                text = note,
                style = MaterialTheme.typography.bodySmall,
                color = if (error != null) {
                    SemanticDanger
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f)
                }
            )
        }
    }
}

/** 分组标题。弹窗里段与段之间只靠间距分不开，需要一行小标题定位。 */
@Composable
internal fun SchoolFormSectionTitle(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurface
    )
}

/**
 * 表单里的玻璃小面板（「智能识别」那一块）。
 *
 * 用 [glassChip] 而不是 drawBackdrop：弹窗内容自己就在弹窗玻璃上面，
 * 再采样一次背景既没必要也有自采样风险；这里要的只是"一块微微浮起的面"。
 */
@Composable
internal fun SchoolFormPanel(
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .glassChip(shape = RoundedCornerShape(18.dp))
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
        content = content
    )
}

/** 面板标题行：小图标 + 文案，用来区分「智能识别」这类辅助区块。 */
@Composable
internal fun SchoolFormPanelTitle(
    icon: ImageVector,
    text: String,
    tint: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.primary
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Box(
            modifier = Modifier.size(16.dp),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = tint,
                modifier = Modifier.size(16.dp)
            )
        }
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            color = tint
        )
    }
}
