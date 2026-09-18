# 教务助手：界面修复与私有统计验证记录

验证日期：2026-09-12。交付目标为 GitHub 的 `v2` 分支。

## 实施范围

- 抢课入口为 64dp 玻璃圆钮，长按向左上展开扇形玻璃底板。六个 48dp 操作按钮位于约 188dp 半径的同一条圆弧上，页面随展开进度模糊。支持拖动预选、松手执行、原地松手后点选、取消手势和 SVG 图标动效。
- 恢复下拉选择器的玻璃高光、折射和投影，去除重复临时轮廓；“添加学校”等待下拉回收后执行，重复点击去重，离开页面取消。
- 修复自定义图片下课表、成绩顶栏边缘的白雾和接缝。普通模式不再叠加整块浅色遮罩，系统状态栏使用相同的实际颜色判断；显式高对比度模式保留可读性衬底。
- 添加、编辑学校共用自动识别和四类教务选项，解析网址保留手动类型，编辑保留兼容路径；当前应用名称统一为“教务助手”。
- 匿名统计在首次说明确认后启用，设置中可关闭；安装 ID 不参与备份，仅在正式构建的前台使用中每日上报 ID 和版本。
- 纳入工作区已有且与当前界面共用的课表、主题、提醒、启动和导航实现及测试，详见 `internal/design/2026-09-10-schedule-motion-theme-implementation.md`。此前“不提交或推送”的限制已由用户本次明确的提交、推送要求取代。

## Android 构建与单元测试

| 检查 | 最终结果 |
| --- | --- |
| JVM 单元测试 | 57 个套件、314 项：311 通过、3 跳过、0 失败、0 错误 |
| Debug / Release | 构建通过；Debug 使用调试签名，Release 为 unsigned |
| Lint Debug | 0 错误、376 条警告、11 条提示 |
| Debug AndroidTest Kotlin | 编译通过 |
| UiPreview / UiPreview AndroidTest | 构建通过，并安装到隔离预览应用 |

单元测试在本次收尾中使用任务级 `--rerun` 强制执行，记录见 `artifacts/feedback-delivery-checks.log`。该次检查随后被设备测试代码中的 Lint 项阻断，因此不能将整份日志当作全部检查成功的证据。

收尾将 `UiRedesignDeviceTest` 的课表弹层状态改为由测试用例持有，维持重组期间的同一个实例，并消除 `RememberReturnType` 检查错误。修正后的完整复核命令：

~~~powershell
.\gradlew.bat :app:compileDebugAndroidTestKotlin :app:lintDebug :app:testDebugUnitTest :app:assembleDebug :app:assembleRelease --no-parallel --max-workers=1 --console=plain
~~~

结果为 `BUILD SUCCESSFUL in 49s`，116 项任务中 7 项执行、109 项为最新；日志为 `artifacts/feedback-delivery-final-checks.log`。单层圆弧版本此前的 Debug / Release 构建也已通过，见 `artifacts/feedback-single-arc-release-build.log`。

~~~powershell
.\gradlew.bat :app:assembleUiPreview :app:assembleUiPreviewAndroidTest -PuiTestBuildType=uiPreview --no-parallel --max-workers=1 --console=plain
~~~

UiPreview 构建耗时 15 秒，70 项任务中 4 项执行、66 项为最新；日志为 `artifacts/feedback-delivery-preview-build.log`。

三个跳过的 JVM 用例需要实际学校环境或授权会话，本次未执行真实选课操作：

- `AcademicAuthenticatedSmokeTest.verifyImportedSessionReadQueries`
- `AcademicLiveSmokeTest.verifyReadOnlySchoolChains`
- `TyustSsoLiveTest.authorizedAccountCanReachAuthenticatedTeachingPage`

正式签名由仓库现有 GitHub 发布流程处理，本次无需本地发布证书密码。

## 设备回归

设备为 MuMu Android 15，ADB 地址 `127.0.0.1:16416`。测试使用独立包 `com.tyust.course.uipreview`。

| 检查 | 结果 | 本地日志 |
| --- | --- | --- |
| 最终单层菜单与界面回归 | 13/13 通过，187.68 秒 | `artifacts/feedback-single-arc-device-tests.log` |
| 冷启动及六个反馈区域的完整页面流程 | 1/1 通过，57.402 秒 | `artifacts/feedback-single-arc-flow.log` |
| 修正后的课程详情、固定底部操作及主题切换用例 | 1/1 通过，3.449 秒 | `artifacts/feedback-delivery-detail-test.log` |
| 关闭系统动画后的原地长按、点选、取消、单层几何及拖动选择 | 2/2 通过，19.933 秒 | `artifacts/feedback-delivery-reduced-motion.log` |

最后两项菜单检查属于额外的减少动态效果复测，与前述 13 项有重叠，不计为新的独立用例。测试结束后，`animator_duration_scale` 已恢复为原值 `1`。

13 项回归覆盖 320/360/412dp 宽度和 1.0/1.6 倍字体下的单层半径、48dp 触区及互不重叠，长按后原地松手保留菜单、点选和拖动执行、取消手势、下拉玻璃、学校表单、统计说明，以及队列和定时操作。

图片顶栏用例直接使用预览应用中已有的自定义图片，校验浅色与深色的课表、成绩顶栏边缘像素，RGB 最大通道差值不超过 6；同时生成展开、中间、收起状态截图。窄屏测试同时提供一致的窗口尺寸和 Configuration。

单层菜单、顶栏和下拉截图保存在 `artifacts/feedback-single-arc/feedback-validation/`，窄屏大字体截图位于 `artifacts/feedback-single-arc/compact-validation/`；手势录屏为 `artifacts/feedback-single-arc/quarter-fan-gesture.mp4`。截图和录屏为本地验收材料，不纳入 Git。

## 统计服务

本次再次执行 `npm run typecheck` 和 `npm test`，类型检查及 12/12 项测试通过。日志为 `artifacts/feedback-delivery-service-checks.log`，覆盖请求校验、幂等更新、北京时间边界、JWT 验签和访问限制。

既有生产部署与联调记录：

- Worker：`academic-assistant-usage`，部署版本 `1d6ad0dc-8ba3-4bba-8f8e-84b714f71261`。
- D1：`academic-assistant-usage`（APAC）。
- 公开上报接口的 GET 返回 405，公开域的统计读取路径返回 404。
- 统计页及查询 API 在未登录时跳转 Cloudflare Access；Access JWKS 返回 200。
- workers.dev 与预览域已禁用，默认域返回 404。
- 用户已通过自己的邮箱验证码登录，并确认可查看累计设备、今日活跃和近 30 天活跃。
- 专用测试 ID 的两次真实上报只产生一条记录，验证后已精确删除；`artifacts/usage-live-cleanup.json` 记录 `remainingTestRows = 0`。本次收尾未重复生产写入。

## GitHub 交付范围

提交源代码、资源、测试、统计服务配置及文档。APK、截图、录屏、构建缓存、依赖目录、本地凭据和签名文件不纳入提交；根 `.gitignore` 的原有本地修改保留在工作区。

按用户要求推送 `v2`。仓库 `.github/workflows/release.yml` 只在推送版本标签或手动 `workflow_dispatch` 时运行；普通分支推送不会触发 APK 发布，本次不创建版本标签。
