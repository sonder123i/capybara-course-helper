# 课表周数与学校服务域名验证

日期：2026-09-16。发布目标：v1.0.80，包含 v1.0.79 发布后的全部本次改动。

## 课表回归

旧版允许保存非周一的开学日期，但学期日历的教学周计算只接受周一。读取这些旧日期时会返回空周次，路由随后回到第 1 周。另外，已存在的节次时间记录和空迁移标记会阻止旧开学日期迁入学期日历。

修复在存储边界将有效日期规范为所在周的周一，补齐尚无日期的当前学期日历，并保留独立设置的节次时间、账号和其他学期。页面周数统一由学期日历控制，分页初始化和旧的滑动回传不再覆盖日历请求的目标周。

验证覆盖：

- 修复前源码快照运行新增的 3 项日历兼容测试，全部复现失败；修复后全部通过。
- 新增 6 项分页同步测试，覆盖首次显示、浏览位置恢复、连续切周回传、日期更新、延迟日历加载与学期切换。
- 完整 JVM 测试共 408 项：405 项通过、3 项条件跳过，无失败。
- Debug APK、AndroidTest APK 编译成功。AndroidTest 仅编译，未执行设备测试。
- Lint：0 个错误，385 个警告、11 个提示。与上一轮报告相比增加的是 Gradle 新版本提示，本次源码没有新增 Lint 问题。

本机验证使用独立构建目录避开 IDE 占用的输出，执行：

```powershell
.\gradlew.bat -I .survey-work\isolated-build.gradle :app:testDebugUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug --no-build-cache --rerun-tasks --no-parallel --max-workers=2 --console=plain
```

结果：`BUILD SUCCESSFUL`，84 项任务重新执行。报告位于 `.survey-work/android-build/app/reports/`，构建日志为 `.survey-work/calendar-domain-gradle.log`。

## 服务域名

使用用户确认的 `school-api.hidisiwa.xyz`。学校适配与问卷客户端共用 `SchoolServiceEndpoints.BASE_URL`，原申请记录、问卷本机状态及接口路径保持兼容。

后端仓库：`D:\school-suggestion-service`，提交 `a3c04e3`。
生产 Worker 版本：`4bbc8653-ac37-4881-8b59-9ec552224507`。

- TypeScript 检查通过，48 项后端测试通过，包括 D1 集成测试；部署前 dry-run 通过。
- 远端 D1 无待应用迁移。
- Cloudflare DNS 和阿里 DNS 均解析到新入口。
- HTTPS 证书校验及指定边缘 IP 的健康检查成功。
- 新域名的 `/health`、`/admin`、`/admin/surveys`、`/v1/surveys`、`/v1/adapted-schools` 返回 200。
- `/v1/admin/suggestions`、`/v1/admin/surveys` 未携带令牌时返回 401；携带现有管理员令牌时返回 200，投放学校管理查询也返回 200。
- 旧 workers.dev 入口执行同样检查通过，兼容未升级客户端。
- 线上检查只读取接口，未新增申请、问卷或点击统计。

管理入口：

- 学校适配：<https://school-api.hidisiwa.xyz/admin>
- 问卷中心：<https://school-api.hidisiwa.xyz/admin/surveys>

## 发布准备

`v1.0.80-announcement.json` 校验通过，公告发布脚本的 15 项测试通过。发布流水线仍由 `v1.0.80` 标签触发，执行完整编译、测试、Release 打包、Gitee 同步及公告发布。

按用户要求，本次不安装 APK，也不运行实机或模拟器验证；不同国内运营商的实际网络体验未逐一验证。
