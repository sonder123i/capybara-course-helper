# UI 回退与会话稳定性实施记录

本次将 A「清透」改造的视觉层回退到改造前基线 `5c719f9` 的液体玻璃界面，保留会话稳定性、请求过滤、巡检取消、页面数据隔离和加载性能改动。已按用户要求更新安装到 `127.0.0.1:5557`，本次变更提交目标为 `v2` 分支；设备功能测试和性能采样按要求跳过。

## 已完成

- 课程、已选、成绩、课表、抢课、设置、登录和辅助页面恢复原有排版、卡片、主题与液体玻璃控件。
- 恢复原有流光背景与页面内容层级；A 版紧凑顶栏、雾面内容卡片和新表单壳不再作为页面视觉入口。
- 保留 `SessionToken` 版本校验、`SessionRequestGate` 操作编号、旧适配器会话退休、Cookie 更新后的提示清理、续期单飞和 `CookieWatchdog` 取消逻辑。
- 保留成绩派生数据缓存、课程列表稳定 key、账号页面数据隔离、页面内容缓存提示和玻璃末帧/空闲调度修复。
- 登录页保留返回调用方的闭环参数与稳定定位语义，不改变原有布局。

## 本次续接补齐

- 将回退备份中尚未重新应用的两套课程路由会话校验补回正式源码，保留原有顶栏、卡片和过渡；旧版本结果与旧批量任务不能更新当前页面状态。
- 修正 `CourseApiClient` 忽略显式会话版本的问题；异步回调内的后续请求沿用原请求版本，Cookie 更新后旧回调不能借用新版本继续请求。过期事件仍只按当前有效会话判定。
- 课程分页和重试绑定原请求编号及会话，页面退出或更新请求后停止旧链路。已选课程顶栏的刷新操作对应已选列表。
- 为课程分组和成绩行补齐稳定标识，区分重修与重复记录；缓存成绩分项与备注解析，保留所有排版、材质和动画参数。
- 新增 5 项本地回归，覆盖旧版本请求、回调内后续请求，以及成绩行插入、重排、成绩更新和重复记录。

## 当前验证证据

- 最终源码的 Debug 单元测试：45 个测试类，269 项通过、3 项跳过、0 失败、0 错误。测试报告在 `app/build/test-results/testDebugUnitTest`。
- Debug、uiPreview、Release、benchmark 和 Debug AndroidTest 构建全部通过；Release 产物为未签名 APK。AndroidTest 仅构建测试包，未在设备执行。
- `lintDebug` 通过：0 个错误，357 个警告。完整报告：`app/build/reports/lint-results-debug.html`。
- 最终构建 `BUILD SUCCESSFUL`，225 项任务实际执行，用时 18 分 27 秒。日志：`output/resume-20260910/final-build-serial-2.log`。
- 首轮并行编译遇到 Kotlin 堆内存不足，已改为单 worker、进程内编译和 4 GB 构建堆内存；只对本次命令生效，未更改项目构建内存配置。
- 此前在回退中间版本上完成的会话与玻璃交互设备样本仅作过程证据；最终回退版本不再执行实机测试，不能据此宣称完整设备验收。

复现本次本地检查的 PowerShell 命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:assembleDebug :app:assembleUiPreview :app:assembleRelease :app:assembleBenchmark :app:assembleDebugAndroidTest :app:lintDebug --rerun-tasks --no-build-cache --no-daemon --no-parallel --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' '-Dorg.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8' --console=plain
```

## 安装结果

- 目标：`127.0.0.1:5557`；应用：`com.tyust.course`，普通 Debug 变体，版本 `1.0.73`（versionCode `73`）。
- 使用 `adb install -r -t` 覆盖安装，保留应用数据；安装返回 `Success`，设备更新时间为 `2026-09-10 18:03:26`（Asia/Shanghai）。
- 本地 APK：`app/build/outputs/apk/debug/app-debug.apk`，36,551,300 字节。
- 已比对设备 `base.apk` 与本地产物 SHA-256，完全一致：`22500d6423712b4284c1ffaf5df0648483b7b7dd8759e0df88ab2d80ff87bac7`。
- 安装日志与校验记录：`output/resume-20260910/install-5557.log`、`output/resume-20260910/installation-verification.json`。
- 安装后仅核对包信息和文件哈希，未启动设备测试或操作真实教务业务。

## 设备验证范围

以下设备检查按用户要求跳过，不引用历史样本作为当前版本通过证据：

- 真实时钟性能采样：页面切换、成绩列表、课表滑动、静止十秒，以及 P50/P95/P99 和空闲重组对照。
- 过期弹窗与登录返回的完整设备流程、账号切换及页面状态恢复。
- 浅深色、复杂壁纸、窄屏、横屏、大字号、减少动画、TalkBack，以及 API 31/32 设备适配。

本地会话替身与 MockWebServer 回归已经执行，不访问真实教务业务；不执行真实选课、退课或抢课。
