package com.tyust.course.network

/**
 * Shared public origin for school adaptation requests and the survey center.
 *
 * 自用改造：上游此处为 `https://school-api.hidisiwa.xyz`（作者自建后端）。自用不需要
 * 「申请适配」与问卷中心，故改指保留域名（`.invalid` 必定解析失败），让相关请求止步于
 * 本机、不触碰作者服务。想恢复上游行为，把下面的值改回原地址即可。
 *
 * 注意：这里必须保持 `const val` 字面量——SurveyApi 与 SchoolSuggestionApi 都以
 * `const val` 引用它，换成 if 表达式会同时打断那两处编译。
 */
object SchoolServiceEndpoints {
    const val BASE_URL = "https://school-api.invalid"
}
