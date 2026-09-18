# 课表、动效与暗色主题实施记录

工作区：`D:/zfapk`，分支 `v2`。按已批准的细化计划实施，交付设备为 `127.0.0.1:5557`。

保留原有布局、玻璃局部形变、账号请求隔离和壁纸参数。不使用 Superpowers 或子代理，不提交或推送，不执行真实选课、退课或抢课。原有 `.gitignore` 改动及媒体、调试产物未纳入修改。

## 实现

| 范围 | 落地内容 | 主要入口 |
| --- | --- | --- |
| 三态主题 | System / Light / Dark 独立持久化；非法值回退；深灰语义色；主题不受壁纸采样反向改变 | `manager/AppThemeMode.kt`、`ui/theme/Theme.kt`、`ui/screen/AppThemeSettingsDialog.kt` |
| 原生及独立进程 | 原生窗口日夜资源、UiModeManager / AppCompat 配合；只读且非导出的主题 Provider；WebView 启动、恢复和通知时同步主题，关闭网页自动压暗 | `manager/AppThemeCoordinator.kt`、`AndroidManifest.xml`、`res/values-night`、`res/values-v27` |
| 玻璃与壁纸 | 内置预设有暗色呈现；自定义图片、颜色、压暗、模糊参数保持原值；局部衬底保证可读；关玻璃优先 Material；缓存加入主题失效条件 | `ui/theme/AppAppearance.kt`、`ui/system/WallpaperAppearance.kt`、`GlassCompat.kt`、`glass/GlassLens.kt` |
| 系统栏 | 根据壁纸区域与实际顶栏衬底叠加后的对比度选择图标明暗 | `ui/theme/AppSystemBars.kt`、`manager/WallpaperToneMap.kt` |
| 导航与层级 | 单一进度驱动页面和胶囊；进场 16 dp、离场 20 dp / 0.985；中断继承位置与速度；拖动松手直接交接当前位置；层级 22 dp / 前页 6 dp | `ui/theme/NavigationMotion.kt`、`ui/system/CapsuleNavigationBar.kt`、`DialogHost.kt` |
| 周切换 | Pager 偏移驱动标题、日期、网格预览；相邻页 0.97–1.0 缩放和 0.78–1.0 透明度；只提交已吸附周次；连续箭头更新目标，手势接管；共享纵向位置 | `ui/screen/ScheduleScreen.kt` |
| 课程详情 | 同窗口 Bottom Sheet，默认可用高度 52%，窄屏/大字号最多 90%；把手、内部滚动、25% / 1000 dp/s 关闭阈值、预测返回共用位移；标题语义、焦点恢复和分项进入 | `ui/screen/ScheduleCourseSheet.kt`、`ui/system/ScheduleBottomSheetState.kt` |
| 课程管理 | 稳定身份经教务桥接和缓存传递；修复旧自定义课程缺失/重复 ID；七字段编辑、非阻断冲突提示、删除和 5 秒撤销；账号参数显式绑定 | `schedule/ScheduleDomain.kt`、`ScheduleJson.kt`、`manager/ScheduleSettingsManager.kt`、`ui/screen/ScheduleCourseEditor.kt` |
| 时间和提醒 | 真实账号/学期/课程身份；默认关闭、开启后提前 15 分钟；每课仅下一次闹钟；独立 Receiver、通知渠道、PendingIntent data；权限、账号、编辑、删除、同步、开机、更新时间变化后重算 | `schedule/CourseReminder.kt`、`ScheduleReminderScheduler.kt`、`CourseReminderReceiver.kt` |
| 提醒恢复 | 冷启动先恢复账号；重算保留尚未送达且未过课时的有效闹钟；接收端校验版本、时间、课程和启用状态；旧开学日期只迁移到一次确认的当前学期 | `schedule/ScheduleCalendarStore.kt`、`ScheduleDates.kt` |
| 顶栏与筛选 | 公共尺寸和至少 48 dp 的操作触区；顶栏形变收敛；筛选按实测按钮锚点展开；角标和单行可横滑摘要；列表稳定 key 及条目动画 | `ui/system/TopBarActionRail.kt`、`AnimatedStateIcon.kt`、`ui/screen/CourseListScreen.kt` |
| 启动 | AndroidX SplashScreen 1.2.0 静态启动页衔接应用内 700 ms 帽体/闪电分离汇流；每进程一次，首帧就绪后开始，可提前结束；系统关闭动画时直接收束 | `ui/theme/StartupLogoAnimation.kt`、`res/drawable/ic_startup_*.xml` |
| iCal | 与课表/提醒共用周次解析；使用课程稳定 UID 和当前学期节次时间；修复周日偏到上一周；转义自定义课程文本 | `utils/ICalExporter.kt` |

Logo 的本地矢量预览已经检查，原桌面 PNG 保留。系统启动页与应用内动画的实际交接仍属于待执行设备验收。

## 自动验证

构建统一使用串行 Gradle、单 worker、Kotlin 进程内编译和命令级 4 GB 堆。

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:lintDebug :app:compileDebugAndroidTestKotlin --rerun-tasks --no-parallel --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' '-Dorg.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8' --console=plain
```

新增 JVM 用例覆盖主题六种组合、非法类型和持久化、语义色对比度、课程身份/桥接/缓存、自定义课程修复及账号隔离、编辑删除撤销、混合单双周、冲突边界、提醒权限/幂等/过期/重算/账号和学期隔离、冷启动待送达闹钟、学期日期迁移、日历与 iCal 一致性。

新鲜检查已通过（`--rerun-tasks`，46 项任务全部执行，耗时 5 分 35 秒）：

- JVM：52 个测试套件、295 项测试，292 项通过、0 失败、0 错误、3 项跳过。跳过的是需要真实学校会话的 `AcademicAuthenticatedSmokeTest`、`AcademicLiveSmokeTest` 和 `TyustSsoLiveTest`，未执行真实选课操作。
- Lint：0 错误、375 条警告、11 条提示；并非无警告构建。
- AndroidTest Kotlin：编译通过，设备用例尚未运行。
- 检查日志：`build/logs/schedule-theme-final-checks-3.log`；单测报告：`app/build/reports/tests/testDebugUnitTest/index.html`；Lint 报告：`app/build/reports/lint-results-debug.html`。

Debug、uiPreview、Release、benchmark 和 Debug AndroidTest 的最终 APK 构建及安装信息见交付记录。

```powershell
.\gradlew.bat :app:assembleDebug :app:assembleUiPreview :app:assembleRelease :app:assembleBenchmark :app:assembleDebugAndroidTest --rerun-tasks --no-parallel --max-workers=1 '-Pkotlin.compiler.execution.strategy=in-process' '-Dorg.gradle.jvmargs=-Xmx4096m -Dfile.encoding=UTF-8' --console=plain
```

## 待执行的设备验收

以下用例只编写、编译，尚未运行。安装成功、单测和静态对比度检查不能代表视觉、手势或性能验收通过。

`app/src/androidTest/java/com/tyust/course/ui/ScheduleMotionDeviceTest.kt` 覆盖：

- 连续导航和反向切换、释放位置连续性、保存的页面输入、账号切换移除旧弹层。
- Pager 手势和连续箭头、标题最终周次、切周后的纵向滚动位置。
- Sheet 拖动关闭一次、内容嵌套滚动、预测返回取消回位。
- 编辑草稿在切换主题和 SavedState 恢复后保留。
- 窄屏筛选摘要横向滚动。
- 导航起始、中间和结束帧输出到应用外部文件目录的 `motion-validation/`。

人工和性能验收矩阵：

| 场景 | 检查内容 |
| --- | --- |
| 冷启动、后台返回、登录/引导/首页交接 | Logo 只播放一次，系统页与应用层位置/比例一致，无二次开场；首次内容不等待网络 |
| 主题六种组合 | 系统浅/暗分别配 System / Light / Dark，尤其系统暗色强制浅色再恢复 System；WebView 宿主同步 |
| Sheet、课程编辑、权限和通知 | 各关闭方式只回调一次；预测返回取消；保存后详情更新；权限缺失状态；真实通知触发和点击 |
| 复杂壁纸、玻璃开关、低版本 | 文字和有意义的图形分别达到 4.5:1 / 3:1；静态、Backdrop、离屏及 Material 回退颜色一致 |
| 窄屏、大字号、关闭系统动画 | 不遮挡关键按钮；面板可滚动；拖动跟手；装饰动画关闭 |
| 帧检查 | 导航、周切换、层级页面、Sheet 和 Logo 均检查首帧、中间帧、结束帧，没有闪烁、错位、重影和输入穿透 |
| 性能 | 记录 60 Hz 下的 16.7 ms 帧预算、P50/P95、掉帧及低端设备降级；静止后无持续动画出帧 |

仅在设备测试限制解除后执行上述用例。此次交付只允许安装及包名、版本、更新时间、APK 哈希核对。

## 安装交付

目标：`127.0.0.1:5557`，包名 `com.tyust.course`。使用 `adb install -r -t` 保留数据安装，不启动应用或运行 instrumentation。

待构建和安装完成后补充版本、文件及设备哈希、安装时间和验证记录。
