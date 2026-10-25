package com.tyust.course

/**
 * 自建服务开关（本文件不属于上游代码，由二次开发引入）。
 *
 * 上游把更新、公告、反馈、统计、学校适配、插件目录、地图瓦片、油猴脚本代理等
 * 全部托管在作者自己的服务上（plugins/school-api/dl.hidisiwa.xyz、作者 Gitee、
 * GreasyFork 代理）。本发行版不与这些服务发生任何往来。
 *
 * 各通道默认指向自建端点（GitHub Pages 主 / Gitee 备），尚未部署的通道保持关闭；
 * 部署完成后把对应开关改回 true 即可。想恢复上游行为，把端点与开关一并改回。
 */
object SelfHostConfig {

    /** 应用内更新检查：清单走自建双源（见 [updateManifestSources]），签名公钥为本仓库自持密钥对。 */
    const val ENABLE_APP_UPDATE_CHECK = true

    /** 远端公告：拉取自建公告源（见 [announcementFeedUrls]）。B4 公告中心接线后开启。 */
    const val ENABLE_REMOTE_ANNOUNCEMENT = true

    /** 用户反馈上报与历史拉取：关闭后反馈内容不离开本机，历史恒为空。 */
    const val ENABLE_REMOTE_FEEDBACK = false

    /** 匿名使用统计上报：关闭后不产生任何统计请求。 */
    const val ENABLE_ANONYMOUS_USAGE_STATS = false

    /** 学校适配申请与问卷中心后端：关闭后相关请求止步于本机。 */
    const val ENABLE_SCHOOL_SERVICE_BACKEND = false

    /**
     * 插件目录（发现页联网浏览与下载）：关闭后插件中心仅保留本地导入、停用与回退；
     * 内置教务协议包（assets/bundled-academic 下的 .eduplugin）离线照常工作。
     */
    const val ENABLE_PLUGIN_DIRECTORY = false

    /** 地图服务（瓦片、个人 Key 管理、捐赠页）：关闭后位置类插件不可用地图相关能力。 */
    const val ENABLE_MAP_SERVICE = false

    /** 油猴脚本下载（经作者服务代理 GreasyFork）：关闭后脚本类插件不可下载新脚本。 */
    const val ENABLE_USERSCRIPT_DOWNLOAD = false

    /**
     * 会话惰性过期（B5-X 自有增强）：不启动 30 秒首查/周期巡检的 CookieWatchdog，
     * 只在真实业务请求失败时判定过期并自动恢复一次。教务会话普遍短命，
     * 主动巡检会让「正在恢复登录状态」频繁出现；惰性判定下不发起请求就不会发现过期，
     * 也就不必等待恢复。抢课服务与页面请求各自带有独立的恢复触发，不受影响。
     */
    const val LAZY_SESSION_EXPIRY = true

    /** 更新清单来源：GitHub Pages 主 + Gitee 仓库 raw 备。channel 为 stable/test。 */
    fun updateManifestSources(channel: String): List<String> = listOf(
        "https://sonder123i.github.io/capybara-course-helper/update/$channel.json",
        "https://gitee.com/sonder123i/capybara-course-helper/raw/main/docs/update/$channel.json",
    )

    /** 应用内公告源：GitHub Pages 主 + Gitee 仓库 raw 备。 */
    val announcementFeedUrls: List<String> = listOf(
        "https://sonder123i.github.io/capybara-course-helper/announcement.json",
        "https://gitee.com/sonder123i/capybara-course-helper/raw/main/docs/announcement.json",
    )
}
