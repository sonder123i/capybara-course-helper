package com.k2767.course.ui.screen

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.ui.draw.drawBehind
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.PlaylistAddCheck
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.RocketLaunch
import androidx.compose.material.icons.outlined.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kyant.backdrop.Backdrop
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.k2767.course.manager.AppearanceSettingsManager
import com.k2767.course.ui.system.LocalAppBackdrop
import com.k2767.course.ui.system.GlassWindowHost
import com.k2767.course.ui.system.LocalControlBackdrop
import com.k2767.course.ui.system.PagePadding
import com.k2767.course.ui.system.SystemPrimaryButton
import com.k2767.course.ui.system.drawWallpaperPattern
import com.k2767.course.ui.system.glass.glassChip
import com.k2767.course.ui.system.glass.glassSheet
import com.k2767.course.ui.system.isBackdropSupported
import com.k2767.course.ui.system.rememberGlassAccessibilityMode
import com.k2767.course.ui.theme.MotionSpring
import com.k2767.course.ui.theme.NeuPrimary
import kotlinx.coroutines.launch
import kotlin.math.absoluteValue

data class OnboardingPage(
    val icon: ImageVector,
    val title: String,
    val description: String
)

/**
 * 首次启动引导。
 *
 * 它跑在【登录之后】（见 `MainActivity` 的渲染分支），所以文案讲的是"这个 App 能做什么"，
 * 不是"接下来去登录"。视觉上与登录页同源：同一张流体壁纸做采样底，卡片用
 * [glassSheet]（Modal 角色的整块玻璃），图标砖用不采样的 [glassChip]。
 *
 * 玻璃层是内容的【兄弟】节点，不是父节点——`layerBackdrop` 会捕获所在节点的整棵子树，
 * 挂在包含玻璃卡的父节点上就会自采样，RenderThread 直接死循环。
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun OnboardingScreen(
    onFinish: () -> Unit
) {
    val pages = remember {
        listOf(
            OnboardingPage(
                icon = Icons.Outlined.CalendarMonth,
                title = "课表与成绩",
                description = "课表、成绩与考试安排集中在一处查看；数据会缓存到本地，网络不稳时也能继续阅读。"
            ),
            OnboardingPage(
                icon = Icons.AutoMirrored.Outlined.PlaylistAddCheck,
                title = "选课与抢课",
                description = "支持单门、队列与定时任务，按你设定的规则持续尝试，成功后自动推进到下一个目标。"
            ),
            OnboardingPage(
                icon = Icons.Outlined.Shield,
                title = "账号与会话",
                description = "已保存的密码经系统密钥库加密，仅存于本机；登录失效时可尝试续期，学校要求验证码时需手动填写。所有学校合计最多 3 个学生账号。"
            ),
            OnboardingPage(
                icon = Icons.Outlined.RocketLaunch,
                title = "支持四类教务",
                description = "${com.k2767.course.academic.AcademicCapabilities.FOUR_SYSTEMS}。可自行添加学校地址；具体支持范围可在「设置 → 教务支持与限制」查看。"
            )
        )
    }
    val pagerState = rememberPagerState(pageCount = { pages.size })
    val scope = rememberCoroutineScope()
    val accessibility = rememberGlassAccessibilityMode()
    val isLastPage = pagerState.currentPage == pages.lastIndex

    GlassWindowHost(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
    ) {
        // 每处都在【绘制 lambda 内部】读 state：rememberLayerBackdrop 没有 key，
        // 捕获外面的快照会让图片壁纸异步解码完成后这一层不重绘。
        val backdrop = LocalControlBackdrop.current

        CompositionLocalProvider(
            LocalAppBackdrop provides backdrop,
            LocalControlBackdrop provides backdrop
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .systemBarsPadding()
                    .padding(horizontal = PagePadding, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp)
            ) {
                // 高度恒定：最后一页藏掉「跳过」时不能让下面整块跳一下
                Box(modifier = Modifier.fillMaxWidth().height(44.dp)) {
                    androidx.compose.foundation.Image(
                        painter = androidx.compose.ui.res.painterResource(com.k2767.course.R.mipmap.ic_launcher),
                        contentDescription = androidx.compose.ui.res.stringResource(com.k2767.course.R.string.app_name),
                        modifier = Modifier.size(40.dp).align(Alignment.CenterStart)
                    )
                    if (!isLastPage) {
                        TextButton(
                            onClick = onFinish,
                            modifier = Modifier.align(Alignment.CenterEnd)
                        ) {
                            Text(
                                text = "跳过",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }

                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier.weight(1f)
                ) { pageIndex ->
                    // 页偏移：0 = 正中，±1 = 相邻页。视差按它取值，reduceMotion 时恒为 0
                    val pageOffset = if (accessibility.reduceMotion) {
                        0f
                    } else {
                        ((pagerState.currentPage - pageIndex) +
                            pagerState.currentPageOffsetFraction).coerceIn(-1f, 1f)
                    }
                    OnboardingPageContent(
                        page = pages[pageIndex],
                        backdrop = backdrop,
                        pageOffset = pageOffset
                    )
                }

                PagerCapsuleIndicator(
                    pageCount = pages.size,
                    position = if (accessibility.reduceMotion) pagerState.currentPage.toFloat()
                        else pagerState.currentPage + pagerState.currentPageOffsetFraction
                )

                SystemPrimaryButton(
                    text = if (isLastPage) "开始使用" else "下一步",
                    onClick = {
                        if (isLastPage) {
                            onFinish()
                        } else {
                            scope.launch {
                                pagerState.animateScrollToPage(pagerState.currentPage + 1)
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }
        }
    }
}

/** Width is driven by the pager itself, including a cancelled swipe. */
@Composable
private fun PagerCapsuleIndicator(
    pageCount: Int,
    position: Float
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.Center
    ) {
        repeat(pageCount) { index ->
            val amount = (1f - (position - index).absoluteValue).coerceIn(0f, 1f)
            val width = (8f + 18f * amount).dp
            Box(
                modifier = Modifier
                    .padding(horizontal = 4.dp)
                    .width(width)
                    .height(8.dp)
                    .clip(RoundedCornerShape(999.dp))
                    .background(
                        androidx.compose.ui.graphics.lerp(
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f),
                            NeuPrimary, amount
                        )
                    )
            )
        }
    }
}

@Composable
private fun OnboardingPageContent(
    page: OnboardingPage,
    backdrop: Backdrop?,
    pageOffset: Float
) {
    val fade = 1f - (pageOffset.absoluteValue * 0.5f)
    val metrics = com.k2767.course.ui.system.rememberScreenMetrics()
    Box(
        modifier = Modifier
            .fillMaxSize()
            .padding(bottom = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        val sheetShape = RoundedCornerShape(28.dp)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                // 卡片本身跟手，内部元素走不同系数 → 视差
                .graphicsLayer { alpha = fade }
                .then(
                    if (backdrop != null) {
                        Modifier.glassSheet(backdrop = backdrop, cornerRadius = 28.dp)
                    } else {
                        Modifier
                            .clip(sheetShape)
                            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.82f))
                    }
                )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 24.dp, vertical = metrics.tall(32.dp, 20.dp)),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(metrics.tall(22.dp, 16.dp))
            ) {
                Box(
                    modifier = Modifier
                        .graphicsLayer { translationX = pageOffset * 56f }
                        .size(metrics.tall(84.dp, 64.dp))
                        .glassChip(shape = RoundedCornerShape(26.dp), elevation = 2.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = page.icon,
                        contentDescription = null,
                        tint = NeuPrimary,
                        modifier = Modifier.size(38.dp)
                    )
                }

                Column(
                    modifier = Modifier.graphicsLayer { translationX = pageOffset * 24f },
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Text(
                        text = page.title,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface,
                        textAlign = TextAlign.Center
                    )
                    Text(
                        text = page.description,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        lineHeight = 22.sp
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))
            }
        }
    }
}
