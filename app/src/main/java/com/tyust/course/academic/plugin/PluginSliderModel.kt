package com.tyust.course.academic.plugin

import android.content.Context
import org.json.JSONObject

/**
 * 滑块缺口识别——**本发行版不内置模型**。
 *
 * 上游在这里挂了一个 4.7MB 的 ONNX 模型（`slider-gap.ort`）加 11.6MB 的
 * `onnxruntime` native 库，合计 16.3MB。2.x 的包因此涨到 191MB，越过 Gitee
 * 单附件 100MB 的上限，国内用户只能从 GitHub 直连下载——实测极慢。
 *
 * [PluginImageMatcher.match] 拿到 null 时会回落到像素级匹配：那条路径是纯
 * Kotlin（灰度 + 边缘 + 遮罩轮廓），不依赖任何模型，常见拼图仍能识别。
 *
 * 需要模型时：把 `plugin-models/slider-gap.ort` 与
 * `com.microsoft.onnxruntime:onnxruntime-android` 依赖加回来，并恢复本文件的
 * 推理实现（上游 v1.0.128 的 `PluginSliderModel.kt` 可作参考）。
 */
internal object PluginSliderModel {
    @Suppress("UNUSED_PARAMETER")
    fun match(
        app: Context,
        background: PluginImageMatcher.Image,
        piece: PluginImageMatcher.Image,
        expectedY: Double?
    ): JSONObject? = null
}
