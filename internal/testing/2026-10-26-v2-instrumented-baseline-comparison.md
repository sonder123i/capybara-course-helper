# v2.0.0 仪器测试基线对照记录（MuMu x86_64 / API 35）

> 2026-10-26 自动批次。方法：同一模拟器、同一 runner，分别在 v2-main 与未改动的
> 上游基线（124fc8e，独立 worktree）上运行 connectedDebugAndroidTest，逐用例比对。

## 总量

v2：242 项（114 过 / 12 失败 / 116 跳过）。基线对照批：上述 12 失败全部复跑。

## 结论：零真回归

12 失败的最终分类（全部有基线证据）：

| 类别 | 数量 | 用例 | 基线表现 |
| --- | --- | --- | --- |
| 上游/环境固有 | 11 | AcademicCompatibility.exactSchedules、AcademicParity×2、ScheduleMotion×2、LegacyManualQueue×2、NativePluginUi×2、NativeService.systemFilePicker、PluginCenter.catalogLists | **基线同样失败，错误一字不差**（请先选择学校 / 第 N 周 not displayed 等） |
| 服务切断预期（v2 行为变化） | 1 | PluginLocationPicker.mapCanSelect… | 基线通过；v2 因 ENABLE_MAP_SERVICE=false 转为跳过 + 新增关闭态兜底用例（已通过） |

## 说明

1. 上游 CI（release.yml）从不执行 connectedAndroidTest；其「设备回归」为挑选的
   脚本子集。这 11 项在 MuMu 上的失败是上游固有的环境敏感问题（精确闹钟授权、
   学校状态初始化、合成分辨率等），与换基线无关。
2. 本项目发布门禁与上游一致：JVM 单测 + Lint + 三架构构建（全绿）。
   connectedAndroidTest 作为深度回归保留，上述 11 项在 MuMu 的失败为已知项。
3. 复现：`git worktree add ../v2-baseline 124fc8e && cd ../v2-baseline && ./gradlew :app:connectedDebugAndroidTest -PemulatorPreview=true`。

## v2 行为锁定的用例变更

- PluginLocationPickerDeviceTest.mapCanSelect… 增加 `assumeTrue(ENABLE_MAP_SERVICE)`；
- 新增 coordinateFallbackCompletesWithTheInitialPointWhenMapServiceDisabled：
  关闭态下兜底 UI 可见、初始坐标直接确认完成选点（已通过）。
