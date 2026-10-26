package com.tyust.course.ui.screen

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.School
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import com.tyust.course.academic.AcademicCapabilities
import com.tyust.course.academic.AcademicSystem
import com.tyust.course.academic.plugin.*
import com.tyust.course.manager.UserManager
import com.tyust.course.model.CatalogSchool
import com.tyust.course.model.SchoolCatalog
import com.tyust.course.model.SchoolConfig
import com.tyust.course.ui.system.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 一行 = 一所学校。适配信息只作为行尾的星标提示，不再单独占一行。 */
private data class SchoolRow(
    val school: SchoolConfig,
    val name: String,
    val subtitle: String,
    /**
     * 这所学校有可用的教务适配（内置协议 zf/qz 或专属插件），点选后能直接跑；
     * 没星表示目录里还没适配，只能手动填地址试。
     */
    val supported: Boolean,
    /** 已经在「我的学校」里，点一下只是切过去。 */
    val configured: Boolean
)

@Composable
fun SchoolSearchPicker(schools: List<SchoolConfig>, selected: SchoolConfig?, enabled: Boolean,
    onSelected: (SchoolConfig) -> Unit, onAdded: () -> Unit, onAddManually: (String) -> Unit,
    onManage: () -> Unit, subtitle: String? = null, onBrowseCatalog: (() -> Unit)? = null) {
    var open by remember { mutableStateOf(false) }
    InsetGroupedRow(title = selected?.name ?: "搜索学校", subtitle = subtitle ?: "搜索或切换学校",
        icon = Icons.Default.School, showDivider = false, enabled = enabled, onClick = { open = true },
        modifier = Modifier.fillMaxWidth().testTag("login-school-picker"),
        trailing = { Icon(Icons.Default.ChevronRight, "切换学校") })
    if (open) SchoolSearchDialog(schools, onDismiss = { open = false }, onSelected = {
        onSelected(it); open = false
    }, onAdded = onAdded, onAddManually = { open = false; onAddManually(it) },
        onManage = { open = false; onManage() }, onBrowseCatalog = { open = false; onBrowseCatalog?.invoke() })
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun SchoolSearchDialog(schools: List<SchoolConfig>, onDismiss: () -> Unit,
    onSelected: (SchoolConfig) -> Unit, onAdded: () -> Unit, onAddManually: (String) -> Unit,
    onManage: () -> Unit, onBrowseCatalog: (() -> Unit)? = null) {
    val context = LocalContext.current
    var query by remember { mutableStateOf("") }
    var catalog by remember { mutableStateOf<List<CatalogSchool>?>(null) }
    var problem by remember { mutableStateOf<String?>(null) }

    // 1777 校目录约 400KB，只在面板第一次打开时读一次。
    LaunchedEffect(Unit) {
        catalog = withContext(Dispatchers.IO) { SchoolCatalog.load(context) }
    }

    // 专属插件只能覆盖到极少数学校（内置的两所学校专用包不在 1777 校库里），
    // 单靠它标星等于永远不亮。这里两个来源合一：能匹配专属插件，或有内置适配（sys 非空）。
    val pluginPackages = remember {
        runCatching {
            AcademicProviderRegistry.knownPackages().filter { it.official && it.manifest.isAcademic }
        }.getOrDefault(emptyList())
    }

    val rows = remember(catalog, query, schools, pluginPackages) {
        val needle = query.trim()
        fun pluginMatched(school: SchoolConfig): Boolean = pluginPackages.any { pkg ->
            runCatching { AcademicProviderRegistry.matches(pkg, school) }.getOrDefault(false)
        }

        // 已添加的学校：空搜索词时就是默认列表，用户不必先打字才看得见自己在用的学校。
        val local = schools.distinctBy { it.id }
            .filter { needle.isEmpty() || it.name.contains(needle, ignoreCase = true) }
            .map { school ->
                val system = school.academicSystem
                SchoolRow(school, school.name, AcademicCapabilities.name(system),
                    supported = pluginMatched(school) || (system.isNotBlank() && system != AcademicSystem.AUTO.id),
                    configured = true)
            }

        // 目录：中文名 / 全拼 / 首字母都能搜，最多 60 条，避免一次塞进上千行。
        val fromCatalog = if (needle.isEmpty()) emptyList() else catalog.orEmpty()
            .asSequence()
            .filter { it.matches(needle) }
            .take(60)
            .mapNotNull { entry ->
                val school = entry.toSchoolConfig() ?: return@mapNotNull null
                if (local.any { PluginSchoolMatcher.key(it.school) == PluginSchoolMatcher.key(school) }) return@mapNotNull null
                val supported = entry.adapterReady || pluginMatched(school)
                SchoolRow(
                    school = school,
                    name = entry.name,
                    subtitle = (if (supported) entry.typeLabel else "暂未适配") + " · " + school.domain,
                    supported = supported,
                    configured = false
                )
            }
            .toList()

        (local + fromCatalog).distinctBy { PluginSchoolMatcher.key(it.school) }
    }

    fun select(school: SchoolConfig) {
        try {
            val manager = UserManager.getInstance()
            val existing = PluginSchoolSearch.existing(manager.supportedSchools, school)
            val target = existing ?: school
            if (existing == null) manager.addCustomSchool(target)
            onAdded()
            onSelected(target)
        } catch (error: Exception) {
            problem = error.message ?: "无法切换到这所学校"
        }
    }

    SystemDialog(onDismissRequest = onDismiss, title = { Text("搜索学校") },
        confirmButton = { SystemDialogButton(primary = true, onClick = onDismiss) { Text("关闭") } }) {
        Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            GlassTextField(query, { query = it }, Modifier.fillMaxWidth(),
                placeholder = "学校名 / 拼音 / 首字母",
                leadingIcon = Icons.Default.Search)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SystemActionButton("手动添加", { onAddManually(query.trim()) },
                    icon = Icons.Default.Add, enabled = query.isNotBlank())
                if (onBrowseCatalog != null) SystemActionButton("内置学校库", { onBrowseCatalog() },
                    icon = Icons.Default.School)
                SystemActionButton("管理学校", { onManage() }, icon = Icons.Default.Settings)
            }
            problem?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }

            when {
                catalog == null -> Text("正在加载学校目录…", style = MaterialTheme.typography.bodySmall)
                rows.isEmpty() && query.isBlank() -> Text("输入学校名称开始搜索，收录 ${catalog?.size ?: 0} 所高校。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                rows.isEmpty() -> Text("没有找到这所学校，可以用「手动添加」填教务地址。",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }

            LazyColumn(Modifier.fillMaxWidth().heightIn(max = 420.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                items(rows, key = { it.school.id + "|" + it.name }) { row ->
                    InsetGroupedRow(
                        title = row.name,
                        subtitle = row.subtitle,
                        icon = Icons.Default.School,
                        showDivider = false,
                        onClick = { select(row.school) },
                        trailing = {
                            when {
                                row.supported -> Icon(Icons.Default.Star, "有可用教务适配",
                                    tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                                row.configured -> Icon(Icons.Default.Check, "已添加",
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(18.dp))
                            }
                        }
                    )
                }
            }
        }
    }
}
