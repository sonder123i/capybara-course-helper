package com.tyust.course.academic.plugin

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.tyust.course.ui.system.*
import com.tyust.course.ui.theme.CourseSelectorTheme
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject

class PluginMapSettingsActivity : ComponentActivity() {
    private var generation by mutableIntStateOf(0)
    override fun onResume() { super.onResume(); generation++ }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        setContent { CourseSelectorTheme { GlassWindowHost { Settings() } } }
    }

    @Composable private fun Settings() {
        val store = remember { PluginPersonalMapStore(this) }
        val scope = rememberCoroutineScope()
        var input by remember { mutableStateOf("") }
        var personal by remember { mutableStateOf(false) }
        var busy by remember { mutableStateOf(false) }
        var status by remember { mutableStateOf<JSONObject?>(null) }
        var message by remember { mutableStateOf("") }
        fun operate(block: suspend () -> Unit) {
            if (busy) return
            scope.launch {
                busy = true; message = ""
                try { block() }
                catch (error: CancellationException) { throw error }
                catch (error: Exception) { message = error.message?.take(160) ?: "操作未完成，请稍后重试" }
                finally { busy = false }
            }
        }
        LaunchedEffect(generation) {
            if (busy) return@LaunchedEffect
            busy = true
            try {
                val token = withContext(Dispatchers.IO) { store.read() }
                personal = token != null
                if (token != null) status = PluginPersonalMapClient.status(token)
            } catch (error: CancellationException) { throw error }
            catch (error: Exception) { personal = true; message = error.message ?: "个人地图连接暂时不可用" }
            finally { busy = false }
        }
        GlassPageScaffold(title = "地图服务", onBack = { finish() }) { padding ->
            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp,
                top = padding.calculateTopPadding() + 8.dp, bottom = padding.calculateBottomPadding() + 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)) {
                InsetGroupedSection(header = "当前搜索来源", footer = "获取当前位置、地图选点和输入坐标不消耗搜索额度。个人模式不会使用公共搜索池。") {
                    InsetGroupedRow(title = if (personal) "个人地图服务" else "公共地图服务",
                        subtitle = if (personal) "使用你接入的高德 Web 服务 Key" else "使用公共免费搜索额度", showDivider = false)
                }
                if (com.tyust.course.SelfHostConfig.ENABLE_MAP_SERVICE) {
                    InsetGroupedSection(header = "接入自己的 Key", footer = "在插件站登录并加密提交自己的高德 Web 服务 Key，核实免费额度后启用。Key 原值只保存在云端；App 使用个人连接码。") {
                        InsetGroupedRow(title = "管理个人 Key", subtitle = "提交 Key、查看审核和额度、生成连接码",
                            onClick = { startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(PluginPersonalMapStore.MANAGE_URL))) }, showDivider = false)
                    }
                }
                OutlinedTextField(input, { input = it.take(100) }, label = { Text("个人地图连接码") },
                    placeholder = { Text("粘贴插件站生成的连接码") }, visualTransformation = PasswordVisualTransformation(),
                    singleLine = true, enabled = !busy, modifier = Modifier.fillMaxWidth().testTag("personal-map-code"))
                SystemDialogButton(primary = true, enabled = !busy && PluginPersonalMapStore.valid(input.trim()),
                    modifier = Modifier.fillMaxWidth().testTag("personal-map-connect"), onClick = { operate {
                        val token = input.trim()
                        val verified = PluginPersonalMapClient.status(token)
                        withContext(Dispatchers.IO) { store.save(token) }
                        input = ""; personal = true; status = verified
                        message = if (verified.optInt("activeKeyCount") > 0) "已连接个人地图服务" else "已保存连接，Key 审核通过并启用后可用于搜索"
                    } }) { Text(if (busy) "处理中…" else "验证并连接") }
                if (personal) {
                    SystemDialogButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = { operate {
                        val token = withContext(Dispatchers.IO) { store.read() } ?: error("请重新填写连接码")
                        status = PluginPersonalMapClient.status(token)
                    } }) { Text("刷新个人额度") }
                    SystemDialogButton(enabled = !busy, modifier = Modifier.fillMaxWidth(), onClick = { operate {
                        withContext(Dispatchers.IO) { store.clear() }
                        personal = false; status = null; input = ""; message = "已切换公共地图服务"
                    } }) { Text("切换公共服务并移除本机连接") }
                }
                if (message.isNotBlank()) Text(message, modifier = Modifier.testTag("personal-map-message"))
                status?.let { value ->
                    val items = PluginJson.objects(value.optJSONArray("items") ?: org.json.JSONArray())
                    if (items.isEmpty()) Text("尚未提交个人 Key，请先进入“管理个人 Key”。")
                    items.forEach { item ->
                        val label = when (item.optString("status")) { "pending" -> "待审核"; "approved" -> if (item.optBoolean("available")) "可用" else "已暂停、到期或额度用完"; "withdrawn" -> "已撤回"; else -> "已退回" }
                        SystemCard(Modifier.fillMaxWidth()) { Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            Text(item.optString("label") + " · " + label)
                            Text("本 Key 已用 ${item.optInt("keyUsed")} / ${item.optInt("monthlyLimit")}；账号已用 ${item.optInt("accountUsed")} / ${item.optInt("accountLimit")}")
                            Text("当前可用 ${item.optInt("remaining")} 次；同一高德账号共用免费额度")
                            if (item.optString("reviewNote").isNotBlank()) Text(item.optString("reviewNote"))
                        } }
                    }
                }
                Text("连接码仅授权个人地点搜索，有效期 90 天。可在插件站重新生成或撤销；重新生成后，其他设备需要重新连接。")
            }
        }
    }
}
