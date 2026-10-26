# 卡皮巴拉教务助手 v2.0.0 交接文档

> 本文档已部分过期（2026-10-26）：公告中心/问卷中心已移除、notes 已改写，发版状态与执行步骤以 `internal/RELEASE-HANDOFF.md` 为准。
> 交接日期：2026-10-26 · v2-main = `bd5aa38`（本地 + Gitee 已推，GitHub 待补推）

---

## 一、项目概览

| 项 | 值 |
|---|---|
| 仓库 | https://github.com/sonder123i/capybara-course-helper |
| Gitee 镜像 | https://gitee.com/sonder123i/capybara-course-helper |
| 官网（GitHub Pages） | https://sonder123i.github.io/capybara-course-helper/ |
| 工作分支 | `v2-main`（唯一开发分支；`main` 为 1.x 维护分支，已冻结） |
| 发布目标 | v2.0.0 / versionCode 20000 |
| 上游 | https://github.com/znjhahaha/zhengfang-apk @ `124fc8e`（v1.0.128，GPL-3.0） |
| applicationId | `com.k2767.course`（不可改——老用户原位升级） |
| namespace | `com.tyust.course`（保持上游——此后同步上游不再全量改名） |
| 签名 | `app/release-key.jks`（本地已有；CI 用 Secret `KEYSTORE_BASE64`） |
| JDK | `D:\Java\jdk-17.0.16+8`（本机默认 JDK 25 不能构建） |
| Gradle | 9.3.1（腾讯镜像，wrapper 已配） |
| 模拟器 | MuMu 12（x86_64 / API 35，adb 端口 7555） |

---

## 二、架构决策（已锁定，勿改）

1. **换基线（B 路线）**：以 upstream v1.0.128 为底座，重放卡皮巴拉身份层/自建体系/自研功能。包名重命名灾难不再重演。
2. **服务切断**：`SelfHostConfig.kt`（`app/src/main/java/com/tyust/course/`）集中开关——插件目录/地图/油猴/反馈/统计/问卷全部关闭；更新+公告走自建双源（GitHub Pages 主 / Gitee raw 备）。
3. **桌面组件双体系**：1.x Glance 组件（`widget/` 模块）+ 上游 RemoteViews 组件共存。
4. **更新公钥**：自持 ECDSA P-256。私钥在 `C:\Users\k2767\projects\capybara-update-signing\update-signing-key.pem`（仓库外，**已注入 GitHub Secret `UPDATE_SIGNING_KEY_B64`，请备份本地原件**）。公钥钉在 `app/src/main/assets/app-update-keys.json`。
5. **许可**：GPL-3.0 + AGPL-3.0 组合（含滑块 ONNX 模型）。LICENSE 末尾有组合声明，NOTICE 随包。
6. **惰性会话过期**：`SelfHostConfig.LAZY_SESSION_EXPIRY = true`——不主动巡检 Cookie，只在真实请求失败时恢复。出问题改 `false` 一行回滚。

---

## 三、当前状态

### ✅ 已完成（12 笔提交，全部绿门通过）

```
bd5aa38 feat(school): 恢复 1777 校内置学校库
ab7d1c6 test(device): 仪器测试基线对照与选点用例适配
2deb601 fix(widget): 修复 release 冷启动崩溃（R8+WorkManager）
b8276f7 docs(release): v2.0.0 更新日志、公告与发布手册
284d73b docs(audit): v2 数据迁移审计
25ab26c docs(license): AGPL 组合声明与 NOTICE
86e6561 feat(session): 会话体验三项回迁 + 组件快照接线
c5effcb feat(selfhost): 更新签名清单与公告自建管线
04d25ea feat(selfhost): SelfHostConfig v2 服务切断层
c58ff1c feat(brand): 身份层重塑为卡皮巴拉发行版
bd4f6ec chore: 仓库卫生与自建体系资产回迁
b868f8e build: 换基线 upstream v1.0.128 构建拉通
```

### 测试覆盖率（全部实测）

| 测试层 | 规模 | 结果 |
|---|---|---|
| JVM 单元测试 | 1482 项 | ✅ 0 失败 |
| Lint | 全模块 | ✅ 0 错误 |
| 构建 | debug/release/arm64/armeabi/universal | ✅ 5 产物全绿 |
| 仪器测试（connectedAndroidTest，MuMu） | 242 项 | ✅ 零真回归（11 项上游固有 + 1 项服务切断预期，基线对照存档） |
| 发版脚本单测 | 19 项 | ✅ 全过 |
| 模拟器浸泡 | 2000 事件 / 87s | ✅ 零崩溃/零 ANR/零外联 |
| 1.2.2→rc 覆盖升级 | 模拟器实测 | ✅ 数据保留、零崩溃 |
| 1777 校学校库 | MuMu 截图 | ✅ 搜索/A-Z/收藏/类型标签全在 |

### ⏳ 未完成（只剩发布前最后一公里）

1. **GitHub 推送**：`git push origin v2-main`（网络波动暂不通，Gitee 已同步）
2. **真机账号冒烟**（唯一无法自动化）：教务登录→课表→成绩→选课（任一正方学校）
3. **正式发布**：`scripts/release.sh 2.0.0 --push`（CI 全自动，详见发布手册）

---

## 四、关键文件索引

| 文件 | 用途 |
|---|---|
| `SelfHostConfig.kt` | 所有服务开关与端点（改服务行为只需动这一个文件） |
| `app/src/main/assets/app-update-keys.json` | 更新签名公钥 |
| `scripts/make_update_manifest.py` | 发版时生成签名清单（ECDSA + 自验） |
| `scripts/make_announcement_feed.py` | 根公告→App feed 转换 |
| `scripts/release.sh` | 发版入口（版本/CHANGELOG/公告/tag 一条命令） |
| `.github/workflows/release.yml` | CI 发版流水线（已改：widget 测试、APK 改名、清单生成、双仓同步） |
| `internal/release-v2-runbook.md` | **发布手册**（前置配置 + rc 清单 + 一键发布 + 回滚） |
| `internal/testing/2026-10-26-v2-data-migration-audit.md` | 数据迁移审计（结论：无需垫片） |
| `internal/testing/2026-10-26-v2-instrumented-baseline-comparison.md` | 仪器测试基线对照（12 失败零回归证据） |
| `widget/` | 1.x Glance 组件模块（3 种 + 快照） |

---

## 五、发布流程（照做即可）

```bash
# 1. 确认私钥已备份 + GitHub Secret 已配（一次性，已做）
# 2. 真机装 rc 包跑通账号类冒烟
# 3. 发布
scripts/release.sh 2.0.0 --push
# CI 自动：测试→构建三包→GitHub Release→生成签名清单→Gitee 同步→Pages 落库→公告
```

回滚：未推送时 `git tag -d v2.0.0 && git reset --hard HEAD~1`；已发布后 `v2.0.1` 热修（versionCode 单调递增）。

---

## 六、已知事项

| 事项 | 状态 |
|---|---|
| release 包体积 ≈ 130MB/架构（1.x 5MB） | 预期内（GeckoView+ONNX+MapLibre），更新按 ABI 分发 |
| 仪器测试 11 项 MuMu 环境固有失败 | 上游同样失败（证据存档），发布门禁不含此层（与上游口径一致） |
| 考试 .ics 导出/第几周反推/变更检测 | 延后 v2.0.1（源提交索引在 runbook） |
| GitHub 网络不通 | Gitee 已保底；恢复后补推 |
| 惰性会话过期 | `LAZY_SESSION_EXPIRY=true`；用户报告恢复太频繁时改 `false` |

---

## 七、技术债与后续版本规划

| 版本 | 内容 |
|---|---|
| v2.0.1 | 考试 .ics 导出+考前提醒、开学日期第几周反推、课表变更检测 |
| v2.0.2+ | 成绩页缓存优先（上游只做了课表）、每周起始日微调、组件倒计时增强 |

---

## 八、维护者须知

- **同步上游**：`git fetch upstream && git merge upstream/main`（namespace 已回归，冲突面极小；注意 SelfHostConfig 可能需要新端点开关）
- **测试**：`gradlew test lint`（JVM+Lint，发布门禁）；`gradlew :app:connectedDebugAndroidTest -PemulatorPreview=true`（深度回归，含 11 项已知 MuMu 环境失败）
- **构建环境**：JDK 必须 `D:\Java\jdk-17.0.16+8`；MuMu 模拟器用 `-PemulatorPreview=true` 构建 x86_64
- **密钥**：更新签名私钥、release keystore 密码在 `local.properties`（已 gitignore）+ GitHub Secrets（CI 用）；**两者丢失则更新通道永久失效**

---

## 九、v2.0.2 增补（2026-10-26 晚）

### 版本与内容

| 版本 | versionCode | 内容 |
|---|---|---|
| v2.0.1 | 20001 | 课表卡片回迁 1.x 排版（无教师行、课名 3 行、地点 2 行 + 自适应缩字号）、`ScheduleLocation.compact` 短地址漏裁校区修复、「现在是第几周」反推开学日期 |
| v2.0.2 | 20002 | v2.0.1 全部内容 + 关掉匿名统计弹窗/设置开关、隐藏「统一登录适配」入口、「基础设置」上移到「课前提醒」之后 |

### 发布链路的两个实测坑

1. **HTTPS 推 GitHub 走本地代理 502**：`git push origin` 报 `CONNECT tunnel failed, response 502`，
   直连也超时；改用 SSH 正常：
   ```bash
   git push git@github.com:sonder123i/capybara-course-helper.git v2-main
   git push git@github.com:sonder123i/capybara-course-helper.git refs/tags/vX.Y.Z
   ```
2. **`sync-to-gitee` job 会在上传 191MB 附件后长时间挂住**（v2.0.1 实测 45 分钟仍在 in_progress）。
   而 `docs/update/stable.json` 与 `docs/version.json` 的落库写在这个 job 的**最后一步**，
   于是主源也被一起拖住。应急做法：
   ```bash
   gh run download <run-id> -n update-manifest -D ./manifest   # 已含签名清单
   # 在 main 的 worktree 里放入 docs/update/stable.json + docs/version.json 后 push
   ```
   Pages 40 秒内重新部署，`update/stable.json` 即可访问。
   **后续建议**：把 Pages 落库从 `sync-to-gitee` 拆成独立 job（`needs: build`），别让备源阻塞主源。

### 更新通道验证结论（v2.0.1 实测）

| 检查 | 结果 |
|---|---|
| `update/stable.json` | HTTP 200 · 2003 bytes |
| 清单签名 | 用 `app-update-keys.json` 公钥 `openssl dgst -verify` → Verified OK |
| 发布包完整性 | 下载 `app-release.apk`（199,927,387 B），sha256 与清单逐字节一致 |
| App 端解析 | `UpdateFeedDeviceTest`（debug 变体连线上双源）→ packageName/版本/线路全部正确，当前 20001 判定「已是最新」 |
| 更新弹窗 | **未登录时不弹**：`MainActivity:689` 的渲染条件是 `!session.expired`。演示模式也会被 `SettingsRoute.checkForUpdate()` 拦住（提示「本地演示模式不执行更新检查」）。真机验收需登录任一账号 |

### 更新包体积

CI 的 `Normalize Release APK Name` 把 universal 包改名为 `app-release.apk` 发布，清单没有
`artifacts` 字段 → 所有设备下载通用包（191MB）。`UpdateManifest.forDevice()` 已支持按 ABI
分发（arm64 111MB / armeabi 108MB），但 `make_update_manifest.py` 还没有对应参数、
Gitee 也只同步一个附件，所以暂未启用。要做的话：脚本加 `--artifact abi=path`、
CI 的 Release 上传两个 ABI 包、Gitee 同步两个附件。

### 关闭上游入口的开关

| 功能 | 开关 | 现状 |
|---|---|---|
| 匿名使用统计弹窗/设置项 | `SelfHostConfig.ENABLE_ANONYMOUS_USAGE_STATS` | `false`，UI 直接不渲染 |
| 统一登录适配入口（登录页 + 设置页） | `SelfHostConfig.ENABLE_SCHOOL_SERVICE_BACKEND` | `false`，入口隐藏；底层 `SchoolAdaptationScreen` 保留在库 |
