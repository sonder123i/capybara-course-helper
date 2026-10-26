# v2.0.0 正式发版交接（写给执行发版的 agent）

> 生成于 2026-10-26 下午。上游交接：前一个 agent 完成了发行版收尾轮（8 笔 commit，见 §2）。
> 本文档是 `internal/release-v2-runbook.md` 的增补与修正——**runbook 仍有效，但有几处今天已过时，以本文为准**。
> 你的任务：把 v2.0.0 正式版发出去。全程按 §1→§6 顺序执行。

## 0. 当前状态快照（动手前先核对）

| 项 | 状态 |
| --- | --- |
| 分支 | `v2-main`（工作区干净） |
| 本地未推送 commit | `git log origin/v2-main..HEAD` 应为 8 笔（1 笔 docs + 7 笔发行版收尾，见 §2） |
| tag | `v2.0.0` **尚未打**（只有 `v2.0.0-rc1`；正式 tag 由 release.sh 创建） |
| version.properties | `VERSION_NAME=2.0.0 / VERSION_CODE=20000`（与 tag 规则一致，勿手改） |
| 版本号权威 | **tag**。CI 从 tag 反写 version.properties；已推送的 tag 永不移动（1.0.67 事故规矩） |
| remotes | `origin`=GitHub（发版主战场）、`gitee`=Gitee（双源备）、`upstream`=上游（**永远不要 push**） |
| CHANGELOG | `[2.0.0] - 2026-10-26` 段已存在 |
| release-notes | `release-notes/v2.0.0.md` 已存在，**但缺 rc1 之后的收尾内容（§1 补）** |

## 1. 发版前必须做：增补 release-notes（CI 扇出的唯一数据源）

`release-notes/v2.0.0.md` 目前只写到 rc 阶段。rc1 之后还有 8 笔 commit（发行版收尾轮），把下面这段追加到该文件的 `## notes` 列表末尾（文案已备好，直接粘贴）：

```markdown
- 正式版收尾（rc1 之后）：更新检查区分「渠道未发布」与「网络故障」，主源确认无清单时提示「已是最新版本」而非误报换网络；插件中心不再请求已切断的在线目录，空态直接说明教务适配已内置，内置 9 个协议包并入已安装列表；移除公告中心与问卷中心（上游作者单向推送/收集通道，数据源已切断，UI 只会永远报错）；问题反馈改走 GitHub Issues，自动预填版本、机型与错误码；登录页换用品牌插画图标；选校面板重做：1777 所高校实时搜索（拼音/首字母），有可用教务适配的学校标星；设置内更新历史替换为发行版专属版本史。
```

同时在 `## changelog` 列表末尾追加：

```markdown
- fix(update): 主源 404 判为渠道未发布，不再误报「请更换网络」
- feat(plugin): 插件中心适配发行版（内置包入列、死链清理）
- feat(school): 选校面板重做（1777 校直搜 + 适配星标）
- feat(brand): 登录页品牌图标
- feat(feedback): 反馈改走 GitHub Issues（含 4 项单测）
- remove(selfhost): 移除公告中心与问卷中心，清零上游作者服务入口
- build: debug 水印开关与 x86_64 release 验收属性
```

`CHANGELOG.md` 的 `[2.0.0]` 段可同步追加一行「正式版收尾：公告/问卷移除、反馈走 GitHub、选校直搜、更新检查修正（详见 release-notes/v2.0.0.md）」。

## 2. rc1 之后的 8 笔 commit（内容速览，便于你写发布说明）

```
3a1054d remove(selfhost): 移除公告中心与问卷中心，清零上游作者服务入口
87e3f7e feat(feedback): 反馈入口改走 GitHub Issues
e297ea7 feat(school): 选校面板重做——1777 校直搜 + 适配星标
181a590 feat(brand): 登录页换用品牌插画图标并放大至 88dp
e106c6a build: 本地验收构建开关——debug 水印与 x86_64 release
b4a9ef0 feat(plugin): 插件中心适配发行版
65bf0f1 fix(update): 主源 404 判为渠道未发布
1475a13 docs: 交接文档（HANDOFF.md）
```

## 3. 发版硬前置（缺一不发）

1. **GitHub Secrets**（runbook §0）：`UPDATE_SIGNING_KEY_B64`、`KEYSTORE_BASE64`、`RELEASE_STORE_PASSWORD`、`RELEASE_KEY_ALIAS`、`RELEASE_KEY_PASSWORD`、`GITEE_TOKEN`。用 `gh secret list -R sonder123i/capybara-course-helper` 核对。`UPDATE_SIGNING_KEY_B64` 若缺失，按 runbook §0 从 `C:\Users\k2767\projects\capybara-update-signing\update-signing-key.pem` 生成。
2. **§1 的 release-notes 增补已提交**。
3. **rc 验证**（runbook §1 清单）：跑过就勾掉；注意下条修正。
4. **工作区干净、在 `v2-main`**。

## 4. runbook 的过时点修正（今天收尾轮导致的差异）

跑 runbook §1（rc 验证）与 §3（发布后抽查）时，以下条目**按此替换**：

| runbook 原条目 | 替换为 |
| --- | --- |
| §1「检查更新：无清单时给出明确失败文案」 | 检查更新在无清单时应显示「**已是最新版本**」（fix(update) 改动），这是新行为，报错反而说明回退了 |
| §1「签到选点接入高德原生地图」 | 地图服务已整体移除，跳过 |
| §3「公告中心：拉到 v2.0.0 公告，重要弹窗出现」 | 公告中心已移除。CI 仍会投递公告 feed（无害），验证改为「Pages 上 announcement.json 可访问即可，App 不再消费」 |
| §3 更新链路 | 发布后 Pages 有 stable.json，1.2.2 真机检查更新应提示 v2.0.0；v2 设备走签名清单校验+断点续传 |

## 5. 发版执行

前置全绿后：

```bash
scripts/release.sh 2.0.0          # 本地：同步文件、写 CHANGELOG、提交、打 tag（无 --push 先看产物）
git show --stat HEAD              # 检查脚本生成的提交
git tag -l v2.0.0                 # 确认 tag 存在且指向正确
scripts/release.sh 2.0.0 --push   # 或手动：git push origin v2-main && git push origin refs/tags/v2.0.0
```

推 tag 即触发 `release.yml`：测试+Lint → 三包构建（arm64/armeabi/universal）→ GitHub Release → 签名清单 `docs/update/stable.json` → 同步 Gitee（附件+清单+version.json）→ Pages 落库。全流程盯 `gh run watch -R sonder123i/capybara-course-helper`。

**Gitee 推送注意**：先 `git fetch gitee` 确认远端无分叉（此前出现过本地与 Gitee 历史不一致），有分叉先停下来问用户，不要强推。

## 6. 发布后验证（修正版清单）

- [ ] GitHub Release 三包齐全、release-notes 内容正确
- [ ] `https://gitee.com/sonder123i/capybara-course-helper/releases` 附件与 `docs/update/stable.json` 就位
- [ ] Pages：`update/stable.json`、`announcement.json`、官网 index 可访问
- [ ] 1.2.2 真机：检查更新 → 提示 v2.0.0 → 下载安装成功
- [ ] v2 真机：检查更新 → 签名校验通过 → 已是最新
- [ ] 全新安装：登录→课表→成绩 冒烟一所正方学校
- [ ] 覆盖安装（1.2.2 → 2.0.0）：学校、账号、课表、组件保留

## 7. 本发行版约束（agent 必读，违反=事故）

1. **零上游作者服务依赖**：不要恢复任何 `hidisiwa.xyz` / `.invalid` 域名的调用或 UI 入口。今天已清零（外链只剩用户的 GitHub 仓库、学校教务地址、插件源码仓库）。上游依赖功能（插件目录/公告/地图/问卷/反馈/统计/油猴）的实现文件**保留在库**（死代码，R8 裁剪），**勿删勿引用**——删了会和上游 merge 冲突，引用了会复活死链。
2. **applicationId 保持 `com.k2767.course`**，namespace 保持 `com.tyust.course`（上游合并友好）。
3. **版本号唯一权威是 tag**；versionCode 语义化编码 `major*10000+minor*100+patch`；已推送 tag 不移动。
4. **公告/问卷/地图/反馈的底层代码虽死但 keys 在 `SelfHostConfig`**：`ENABLE_REMOTE_ANNOUNCEMENT=false`、`ENABLE_REMOTE_FEEDBACK=false`、`ENABLE_ANONYMOUS_USAGE_STATS=false`、`ENABLE_SCHOOL_SERVICE_BACKEND=false`、`ENABLE_PLUGIN_DIRECTORY=false`、`ENABLE_MAP_SERVICE=false`、`ENABLE_USERSCRIPT_DOWNLOAD=false`。`ENABLE_APP_UPDATE_CHECK=true` 是唯一开启项。
5. **CI 不传 `-Px86Release`**（那是本地 MuMu 验收属性）。
6. **模拟器验收包流程**（如需在 MuMu 复验）：`gradlew assembleRelease -Px86Release=true` → `apksigner sign --ks %USERPROFILE%/.android/debug.keystore --ks-pass pass:android --out emu.apk app-x86_64-release.apk` → `adb install -r`。debug 重签可覆盖安装保数据，但登录会话不恢复（换签名特有，正式用户不受影响）。
7. **MuMu adb 操作**：adb server 每条命令间会掉，所有操作放同一条命令（`start-server + connect 127.0.0.1:7555 + ...`）；`/sdcard` 路径加 `MSYS_NO_PATHCONV=1`。

## 8. 已知坑（前一个 agent 实测踩过）

- **MuMu 内 gitee.com 被 DNS 劫持**（解析到 baiduads.com）：更新检查的 Gitee 备源在模拟器里必失败，主源 GitHub Pages 正常。这不是 bug。
- **debug→release 覆盖安装后登录会话不恢复**（cookie 恢复失败，学校等数据正常）。仅换签名验收场景，正式用户 release→release 不受影响。
- **uiautomator dump 在 MuMu 上自身 SIGSEGV**（崩溃的是 uiautomator 不是 App），XML 仍能产出，可继续用。
- **单测统计**：1427 项全绿是当前基线；动 Manifest 的 Activity 列表时注意 `WindowOrientationTest`（断言全屏 Activity 方向策略）。

## 9. 回滚与延后

- 回滚、延后功能清单：直接看 runbook §4、§5，仍有效。
- 公告/问卷/地图如需接回：底层实现都在库中，git log 今天这 7 笔 commit 里有完整动机与接线说明，反向恢复即可。
