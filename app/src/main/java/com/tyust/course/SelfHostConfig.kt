package com.tyust.course

/**
 * 自用改造开关（本文件不属于上游代码，由二次开发引入）。
 *
 * 上游把应用内更新、公告、用户反馈、匿名统计、学校适配申请等能力都托管在作者自己的
 * 服务上（gitee.com/znj12345 与 *.hidisiwa.xyz）。自用时不希望自编译的包与这些服务
 * 发生任何往来——尤其是更新检查：一旦上游发了更高 versionCode 的版本，本机会提示更新
 * 并下载作者的原包。
 *
 * 这里把这些通道集中关闭。想恢复上游行为，把对应开关改回 true；或整体删除本文件并
 * 同时删除各引用处的那几行判断。
 */
object SelfHostConfig {

    /** 应用内更新检查。关闭后不请求作者 Gitee 上的 version.json，也不提示任何更新。 */
    const val ENABLE_APP_UPDATE_CHECK = false

    /** 远端公告拉取。关闭后公告列表恒为空。 */
    const val ENABLE_REMOTE_ANNOUNCEMENT = false

    /** 用户反馈上报。关闭后反馈内容（含学号与最近 logcat）不会离开本机。 */
    const val ENABLE_REMOTE_FEEDBACK = false

    /** 匿名使用统计上报。关闭后不产生任何统计请求。 */
    const val ENABLE_ANONYMOUS_USAGE_STATS = false

    /** 学校适配申请与问卷中心后端。关闭后相关请求止步于本机。 */
    const val ENABLE_SCHOOL_SERVICE_BACKEND = false
}
