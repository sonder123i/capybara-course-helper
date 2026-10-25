# 三类教务适配验证记录

2026-09-08，设备仅使用 `127.0.0.1:5557`（Android 15 / API 35）。范围为旧正方 `zf_old`、新版强智 `qz`、旧版强智 `qz_old` 的功能链路。

本次续接修复了抢课队列的两种提前停止情况：目标轮次尚未开放而其他轮次已开放，以及学校在提交阶段返回轮次关闭。队列现在按设定次数等待，每次重新读取目标轮次和教学班。新增的两个测试在修复前均失败，修复后通过。

实网测试增加可选参数 `academicIsolateUi=true`，在独立的临时账号存储中验证应用数据入口，完成后清空临时存储并恢复原账号状态。该参数只存在于仪器测试中；正式应用仍按所有学校合计最多 3 个账号执行限制。设备原有账号配置和绑定记录在测试前后的 SHA-256 完全一致。

## 数据与请求的来源

| 功能 | 实现与验证依据 |
| --- | --- |
| 学校地址、账号、会话 | 使用学校配置和账号独立会话；手动网页登录后的 Cookie 可恢复学号并读取数据。 |
| 轮次、分类、教学班 | 从学校返回的轮次 JSON、菜单、iframe、表格和课程记录中发现；目标使用当前课程、教学班标识定位。 |
| 强智选退课 | 解析学校页面或外部脚本提供的 URL、请求方法、参数和最新令牌；退课前重新检查当前记录及可用操作控件。 |
| 旧正方选退课 | 重新读取 WebForms 表单状态及目标控件；支持弹窗教学班和体育课列表控件、分类回传。 |
| 已选、课表、成绩、考试 | 按学校查询入口及学期选项读取；轮次关闭时仍可独立查询已选课程和学习数据。 |
| 队列、定时任务、恢复 | 按账号隔离，验证去重、定时创建与取消、会话续期、轮次等待及写入结果不明时的已选核对。 |

`AcademicFunctionalChainTest` 使用变化的课程 ID、教学班 ID、接口路径、学期和表单令牌检验实际发出的请求；`AcademicOperationDiscoveryTest`、`ZfOldSportsTest` 和 `AcademicProtocolSafetyTest` 覆盖操作权限、体育控件与异常恢复。适配器保留系统协议约定的路由和字段名；学校返回的业务标识及令牌由响应解析。无法识别的页面或脚本会明确报错，不能据此保证所有学校的自定义模板都已支持。

## 实网结果

登录范围说明：本记录中的新版强智通过直接 HTTP 账密登录建立会话；两个旧系统使用用户网页登录后导入的 Cookie。此 APK 尚未接通旧强智和旧正方的 App 内验证码获取、刷新及提交，不能将以下查询通过结果当作它们的完整账密登录验证。后续补齐登录的结果另行记录。

三类系统均完成身份校验、当前选课状态、已选课程、课表、成绩和考试查询，并通过应用使用的数据桥接层读取。新版强智和旧正方有课程返回，额外检查了教学班读取。旧版强智当前没有开放轮次，其开放轮次及选退课请求通过本地模拟学校服务验证。真实学校仅执行登录和查询，没有执行选课、退课或抢课写入。

| 系统 | 轮次/分类 | 课程条目 | 已选 | 课表记录 | 成绩记录 | 考试记录 |
| --- | ---: | ---: | ---: | ---: | ---: | ---: |
| 新版强智 | 3 | 179 | 4 | 6 | 79 | 0 |
| 旧版强智 | 0 | 0 | 11 | 13 | 28 | 0 |
| 旧正方 | 3 | 255 | 2 | 27 | 22 | 0 |

旧版强智当时没有开放选课轮次，已选课程和学习数据仍可读取。考试记录为学校当时返回的数量。上述数字是测试时快照，会随学校数据变化。

旧正方账号是设备上的第 4 个测试身份，因此其应用入口测试使用隔离账号存储；设备原有 3 个账号没有被删除或替换。

## 构建与校验

| 检查 | 结果 |
| --- | --- |
| Debug 单元测试 | 217 项：214 通过、3 跳过、0 失败、0 错误 |
| Release 单元测试 | 217 项：214 通过、3 跳过、0 失败、0 错误 |
| 5557 设备验证 | 7 项兼容测试，以及三类教务各 1 项完整查询测试通过 |
| Debug、Release、仪器测试 APK | 构建通过；Release 产物未签名 |
| lintDebug | 0 错误、352 警告、11 提示 |

构建在独立源码副本中执行，避免其他任务覆盖报告。3 项可选的外部验证单元测试未提供 JVM 凭据；实网验证通过设备私有缓存提供会话完成。账号密码、Cookie 和学校原始个人数据不在本记录或仓库测试资源中。

构建命令：

```powershell
.\gradlew.bat :app:testDebugUnitTest :app:testReleaseUnitTest :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease :app:lintDebug --offline --no-daemon --no-configuration-cache --max-workers=2 --console=plain
```

已登录会话的设备验证示例，配置文件须预先放入测试应用的私有缓存：

```powershell
adb -s 127.0.0.1:5557 shell am instrument -w -r -e class com.tyust.course.academic.AcademicAuthenticatedDeviceTest -e academicCookieSmokeFile academic-cookie-profiles.json -e academicSystem zf_old -e academicPrepareUi true -e academicIsolateUi true com.tyust.course.test/androidx.test.runner.AndroidJUnitRunner
```

## 交付文件

- 已验证的安装包：`app/build/outputs/academic-validation-20260908/app-debug.apk`。
- 未签名 Release 与仪器测试 APK、SHA-256 清单：同一产物目录。
- 单元测试报告、lint 报告、5557 测试日志、实网计数、账号状态校验及源码哈希清单：`app/build/reports/academic-validation-20260908/`。

Debug APK SHA-256：`0ED24D47674BB9304EE6A5DF9C981230C2901D0F7C501BC8D89C63A34838E252`。

教务相关源码与本轮验证副本一致。工作区的部分玻璃界面文件在验证期间继续更新，未包含在此 APK 中；此包对应上述功能测试使用的源码快照，具体差异保存在报告目录的 `source-differences.json`。
