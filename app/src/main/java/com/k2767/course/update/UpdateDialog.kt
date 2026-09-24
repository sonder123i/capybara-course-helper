package com.k2767.course.update

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.k2767.course.ui.system.SystemDialog
import com.k2767.course.ui.system.SystemPrimaryButton
import com.k2767.course.ui.system.SystemSecondaryButton
import com.k2767.course.ui.theme.MotionDuration
import com.k2767.course.ui.theme.NeuPrimary

/**
 * 更新对话框（玻璃 SystemDialog 版）
 */
@Composable
fun UpdateDialog(
    updateInfo: UpdateManager.UpdateInfo,
    currentVersion: String,
    onDismiss: () -> Unit,
    onUpdate: () -> Unit,
    downloadProgress: Int = -1,  // -1 表示未开始下载
    isDownloading: Boolean = false,
    /** 上一次下载失败的原因。非空时按钮换成「重试 / 用浏览器下载」。 */
    downloadError: String = "",
    /** 用浏览器打开下载地址：应用内下载都不通时的保底出口。 */
    onBrowserDownload: () -> Unit = {}
) {
    val failed = downloadError.isNotBlank() && !isDownloading
    val canDismiss = !isDownloading && !updateInfo.forceUpdate

    // 图标动画：呼吸效果
    val infiniteTransition = rememberInfiniteTransition(label = "iconScale")
    val iconScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.15f,
        animationSpec = infiniteRepeatable(
            animation = tween(MotionDuration.EmphasisPulse, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    SystemDialog(
        onDismissRequest = { if (canDismiss) onDismiss() },
        icon = {
            Box(
                modifier = Modifier
                    .graphicsLayer {
                        scaleX = iconScale
                        scaleY = iconScale
                    }
                    .size(72.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(NeuPrimary.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.SystemUpdate,
                    contentDescription = null,
                    modifier = Modifier.size(36.dp),
                    tint = NeuPrimary
                )
            }
        },
        title = {
            Text(
                text = "发现新版本",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        },
        dismissButton = when {
            isDownloading -> null
            // 失败之后不再给「稍后」：要换成一条确定走得通的路
            failed -> {
                {
                    SystemSecondaryButton(
                        text = "用浏览器下载",
                        onClick = onBrowserDownload,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            !updateInfo.forceUpdate -> {
                {
                    SystemSecondaryButton(
                        text = "稍后",
                        onClick = onDismiss,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
            else -> null
        },
        confirmButton = if (!isDownloading) {
            {
                SystemPrimaryButton(
                    text = if (failed) "重试" else "立即更新",
                    onClick = onUpdate,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        } else null
    ) {
        Column(
            modifier = Modifier.fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // 版本信息
            Row(
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "v$currentVersion",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = " → ",
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "v${updateInfo.versionName}",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold,
                    color = NeuPrimary
                )
            }

            // 更新说明
            if (updateInfo.releaseNotes.isNotEmpty()) {
                Spacer(modifier = Modifier.height(16.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(12.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f))
                        .heightIn(max = 200.dp)
                        .padding(12.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = "更新内容",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = updateInfo.releaseNotes,
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurface,
                        lineHeight = 20.sp
                    )
                }
            }

            // 下载进度
            AnimatedVisibility(visible = isDownloading) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    val indeterminate = downloadProgress < 0
                    val animatedProgress by animateFloatAsState(
                        targetValue = (downloadProgress / 100f).coerceIn(0f, 1f),
                        label = "progress"
                    )

                    if (indeterminate) {
                        // 进度未知（CDN 未给总大小）：显示滚动条，别停在 0%
                        LinearProgressIndicator(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = NeuPrimary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    } else {
                        LinearProgressIndicator(
                            progress = { animatedProgress },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp)),
                            color = NeuPrimary,
                            trackColor = MaterialTheme.colorScheme.surfaceVariant
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    Text(
                        text = when {
                            downloadProgress >= 100 -> "下载完成，正在安装..."
                            indeterminate -> "正在下载…"
                            else -> "正在下载 $downloadProgress%"
                        },
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 下载由应用自己完成（不再交给系统下载器），进程活着才算数。
                    // 如实说清代价，别让用户以为可以退出去干别的。
                    if (downloadProgress < 100) {
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = "下载中请留在本页，退出应用会中断下载",
                            fontSize = 12.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.8f),
                            textAlign = TextAlign.Center
                        )
                    }
                }
            }

            // 失败原因留在弹窗里：只弹一条 toast，用户回头看到的还是一个正常的
            // 「立即更新」，根本不知道刚才为什么没下成。
            AnimatedVisibility(visible = failed) {
                Text(
                    text = downloadError,
                    modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.error,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

/**
 * 更新检查状态管理
 */
@Composable
fun rememberUpdateState(
    context: android.content.Context = LocalContext.current
): UpdateState {
    val updateManager = remember { UpdateManager.getInstance(context) }
    
    var updateInfo by remember { mutableStateOf<UpdateManager.UpdateInfo?>(null) }
    var showDialog by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableIntStateOf(0) }
    var downloadError by remember { mutableStateOf("") }
    
    return remember(updateManager) {
        UpdateState(
            updateManager = updateManager,
            updateInfo = { updateInfo },
            setUpdateInfo = { updateInfo = it },
            showDialog = { showDialog },
            setShowDialog = { showDialog = it },
            isDownloading = { isDownloading },
            setIsDownloading = { isDownloading = it },
            downloadProgress = { downloadProgress },
            setDownloadProgress = { downloadProgress = it },
            downloadError = { downloadError },
            setDownloadError = { downloadError = it }
        )
    }
}

class UpdateState(
    private val updateManager: UpdateManager,
    val updateInfo: () -> UpdateManager.UpdateInfo?,
    val setUpdateInfo: (UpdateManager.UpdateInfo?) -> Unit,
    val showDialog: () -> Boolean,
    val setShowDialog: (Boolean) -> Unit,
    val isDownloading: () -> Boolean,
    val setIsDownloading: (Boolean) -> Unit,
    val downloadProgress: () -> Int,
    val setDownloadProgress: (Int) -> Unit,
    val downloadError: () -> String,
    val setDownloadError: (String) -> Unit
) {
    fun checkForUpdate() {
        updateManager.checkForUpdate { info ->
            if (info != null) {
                setUpdateInfo(info)
                setShowDialog(true)
            }
        }
    }
    
    fun startDownload() {
        val info = updateInfo() ?: return
        setIsDownloading(true)
        setDownloadProgress(0)
        setDownloadError("")
        
        updateManager.downloadApk(
            downloadUrl = info.downloadUrl,
            mirrorUrl = info.mirrorUrl,
            onProgress = { progress ->
                setDownloadProgress(progress)
            },
            onComplete = { file ->
                setIsDownloading(false)
                if (file != null && file.exists()) {
                    updateManager.installApk(file)
                    setShowDialog(false)
                }
            },
            onFailure = { message ->
                // 同时留在弹窗里（按钮变成重试/浏览器），toast 只是即时提醒
                setDownloadError(message)
                com.k2767.course.ui.system.GlassToaster.show(message)
            }
        )
    }
    
    /** 应用内下载都不通时的保底：交给系统浏览器。 */
    fun browserDownload() {
        val info = updateInfo() ?: return
        setDownloadError("")
        updateManager.openDownloadInBrowser(info.downloadUrl.ifBlank { info.mirrorUrl })
    }
    
    fun dismiss() {
        setShowDialog(false)
        setUpdateInfo(null)
    }
    
    fun getCurrentVersion(): String {
        return updateManager.getCurrentVersionName()
    }
}
