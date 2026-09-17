package com.k2767.course.ui.screen

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Star
import androidx.compose.material.icons.outlined.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.k2767.course.manager.UserManager
import com.k2767.course.model.CatalogSchool
import com.k2767.course.model.SchoolCatalog
import com.k2767.course.model.SchoolConfig
import com.k2767.course.ui.system.GlassPageScaffold
import com.k2767.course.ui.system.GlassTextField
import com.k2767.course.ui.system.PagePadding
import com.k2767.course.ui.system.glassSurfaceColor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val PickerRowShape = RoundedCornerShape(16.dp)

private sealed interface PickerRow {
    data class Header(val text: String) : PickerRow
    data class Catalog(val entry: CatalogSchool) : PickerRow
    data class Custom(val school: SchoolConfig) : PickerRow
}

/**
 * 选择学校：搜索 + A–Z 索引 + 收藏（数据见 SchoolCatalog）。
 * 「暂未适配」的学校仍可选择，只是导入大概率失败——标签如实标注。
 */
@Composable
fun SchoolPickerScreen(
    favorites: Set<String>,
    onToggleFavorite: (String) -> Unit,
    onSelect: (SchoolConfig) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val listState = rememberLazyListState()

    var catalog by remember { mutableStateOf<List<CatalogSchool>?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var customSchools by remember { mutableStateOf(UserManager.getInstance().selectableSchools) }
    var showAddDialog by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        catalog = withContext(Dispatchers.IO) { SchoolCatalog.load(context) }
    }

    val entries = catalog.orEmpty()
    val rows = remember(entries, query, favorites, customSchools) {
        buildRows(entries, query, customSchools, favorites)
    }
    val letterIndex = remember(rows) {
        buildMap { rows.forEachIndexed { index, row -> if (row is PickerRow.Header) put(row.text, index) } }
    }
    val letters = remember(rows) { rows.filterIsInstance<PickerRow.Header>().map { it.text }.filter { it != FAVORITES && it != MINE } }

    GlassPageScaffold(
        title = "选择学校",
        subtitle = if (catalog == null) "正在加载学校目录…" else "共 ${entries.size} 所 · 可用 ${entries.count { it.adapterReady }} 所",
        onBack = onBack,
        modifier = modifier
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            Box(Modifier.padding(horizontal = PagePadding, vertical = 8.dp)) {
                GlassTextField(
                    value = query,
                    onValueChange = { query = it },
                    modifier = Modifier.fillMaxWidth(),
                    placeholder = "搜索学校（中文 / 拼音 / 首字母）",
                    leadingIcon = Icons.Default.Search,
                    trailing = {
                        if (query.isNotEmpty()) {
                            Icon(
                                imageVector = Icons.Outlined.Close,
                                contentDescription = "清空",
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(18.dp).clickable { query = "" }
                            )
                        }
                    }
                )
            }
            Row(Modifier.fillMaxSize()) {
                LazyColumn(
                    state = listState,
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(start = PagePadding, end = 8.dp, top = 4.dp, bottom = 40.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (catalog == null) {
                        item { HintText("正在加载…") }
                    }
                    if (query.isBlank()) {
                        item {
                            AddCustomRow(onClick = { showAddDialog = true })
                        }
                    }
                    rows.forEachIndexed { index, row ->
                        when (row) {
                            is PickerRow.Header -> stickyHeader(key = "header_$index") {
                                SectionHeader(row.text)
                            }
                            is PickerRow.Catalog -> item(key = "catalog_${row.entry.id}") {
                                CatalogSchoolRow(
                                    entry = row.entry,
                                    favorite = row.entry.id in favorites,
                                    onToggleFavorite = { onToggleFavorite(row.entry.id) },
                                    onClick = { persistAndSelect(row.entry, onSelect) }
                                )
                            }
                            is PickerRow.Custom -> item(key = "custom_${row.school.id}") {
                                SchoolRow(
                                    name = row.school.name,
                                    subtitle = row.school.domain,
                                    badge = null,
                                    onClick = { onSelect(row.school) }
                                )
                            }
                        }
                    }
                    if (rows.isEmpty() && catalog != null) {
                        item { HintText("没有匹配的学校。可以换个关键词，或手动添加。") }
                    }
                }
                AzRail(
                    letters = letters,
                    onLetter = { letter ->
                        letterIndex[letter]?.let { index -> scope.launch { listState.scrollToItem(index) } }
                    }
                )
            }
        }
    }

    if (showAddDialog) {
        AddSchoolDialog(
            onDismiss = { showAddDialog = false },
            onConfirm = { draft ->
                val school = draft.toSchoolConfig()
                UserManager.getInstance().addCustomSchool(school)
                customSchools = UserManager.getInstance().selectableSchools
                showAddDialog = false
                onSelect(school)
            }
        )
    }
}

private const val MINE = "我添加的学校"
private const val FAVORITES = "收藏"

/**
 * 目录学校「选中即入库」：写进自定义学校列表，重启后才能按 id 找回
 * （恢复登录状态时 getSchoolById 只认默认与自定义学校）。
 * 同域名的学校已存在时复用已有条目，避免出现两份配置。
 */
private fun persistAndSelect(entry: CatalogSchool, onSelect: (SchoolConfig) -> Unit) {
    val config = entry.toSchoolConfig() ?: return
    val manager = UserManager.getInstance()
    manager.addCustomSchool(config)
    val persisted = manager.selectableSchools.firstOrNull { it.domain == config.domain }
        ?: manager.supportedSchools.firstOrNull { it.domain == config.domain }
        ?: config
    onSelect(persisted)
}

private fun buildRows(
    entries: List<CatalogSchool>,
    query: String,
    customSchools: List<SchoolConfig>,
    favorites: Set<String>
): List<PickerRow> {
    val rows = ArrayList<PickerRow>()
    if (query.isNotBlank()) {
        val keyword = query.trim()
        customSchools
            .filter { it.name.contains(keyword, true) || it.domain.contains(keyword, true) }
            .forEach { rows.add(PickerRow.Custom(it)) }
        entries.filter { it.matches(keyword) }.forEach { rows.add(PickerRow.Catalog(it)) }
        return rows
    }

    if (customSchools.isNotEmpty()) {
        rows.add(PickerRow.Header(MINE))
        customSchools.forEach { rows.add(PickerRow.Custom(it)) }
    }

    val starred = entries.filter { it.id in favorites }
    if (starred.isNotEmpty()) {
        rows.add(PickerRow.Header(FAVORITES))
        starred.forEach { rows.add(PickerRow.Catalog(it)) }
    }

    var currentInitial: String? = null
    entries.forEach { entry ->
        if (entry.initial != currentInitial) {
            currentInitial = entry.initial
            rows.add(PickerRow.Header(entry.initial))
        }
        rows.add(PickerRow.Catalog(entry))
    }
    return rows
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.92f))
            .padding(vertical = 6.dp)
    )
}

@Composable
private fun HintText(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 16.dp)
    )
}

@Composable
private fun AddCustomRow(onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(PickerRowShape)
            .background(glassSurfaceColor())
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "手动添加学校",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = "找不到？自己填地址",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun CatalogSchoolRow(
    entry: CatalogSchool,
    favorite: Boolean,
    onToggleFavorite: () -> Unit,
    onClick: () -> Unit
) {
    SchoolRow(
        name = entry.name,
        subtitle = entry.url,
        badge = entry.typeLabel,
        muted = !entry.adapterReady,
        trailing = {
            Icon(
                imageVector = if (favorite) Icons.Outlined.Star else Icons.Outlined.StarBorder,
                contentDescription = if (favorite) "取消收藏" else "收藏",
                tint = if (favorite) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(20.dp).clickable(onClick = onToggleFavorite)
            )
        },
        onClick = onClick
    )
}

@Composable
private fun SchoolRow(
    name: String,
    subtitle: String,
    badge: String?,
    muted: Boolean = false,
    trailing: (@Composable () -> Unit)? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .clip(PickerRowShape)
            .background(glassSurfaceColor())
            .clickable(onClick = onClick)
            .padding(start = 14.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
        if (badge != null) {
            Text(
                text = if (muted) "暂未适配" else badge,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        trailing?.invoke()
    }
}

@Composable
private fun AzRail(letters: List<String>, onLetter: (String) -> Unit) {
    if (letters.isEmpty()) return
    Column(
        modifier = Modifier
            .width(24.dp)
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Top
    ) {
        letters.forEach { letter ->
            Text(
                text = letter,
                fontSize = 10.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .clickable { onLetter(letter) }
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            )
        }
    }
}