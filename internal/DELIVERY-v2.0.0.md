# v2.0.0 交付文档（2026-10-26 发行版收尾轮）

> 本文档记录 2026-10-26 收尾轮的全部交付内容与最终状态。发版执行步骤见 `internal/RELEASE-HANDOFF.md`。

## 交付结论

| 项 | 状态 |
| --- | --- |
| 版本 | 2.0.0（versionCode 20000），代码全部提交 |
| 发版进度 | **发版 agent 已执行过一轮**：tag 曾推送并触发 CI（run 40290028539），build job 成功（三包 + GitHub Release），`sync-to-gitee` job 跑 15 分钟后被 `@sonder123i` 手动取消；tag v2.0.0 已删除（本地与远端） |
| 渠道包实测（本地构建） | arm64 **111.3 MB** / armeabi-v7a **107.6 MB** / universal **190.6 MB** |
| 体积对比 | 比 rc1 阶段（arm64 144.4 MB）**轻 23%**（地图 SDK 已物理移除） |
| 质量 | 1412 项单测 0 失败 · Lint 0 错误 · MuMu 全程实测通过 |
| 上游依赖 | 作者服务器依赖清零；外链仅剩用户 GitHub 仓库、学校教务地址、插件自带源码仓库 |

## 发版当前状态（重要：半完成态，交接时必须核对）

1. **GitHub Release v2.0.0**：build job 成功，大概率已带着三包与 release-notes 发布——先 `gh release view v2.0.0 -R sonder123i/capybara-course-helper` 核实内容与附件。
2. **Gitee 侧全部未同步**（附件 / `docs/update/stable.json` / `version.json`）：App 的 Gitee 备源清单因此还是旧版，重跑方式见 release.yml 的 `sync-to-gitee` job（可从被取消的 run 重跑该 job，或删掉远端 Release 重打 tag 全量重走）。
3. **tag 已删**：重新发版需重新打 `v2.0.0` tag 并推送（先确认 GitHub Release 若已存在，重推 tag 会触发全流程重跑，注意避免 Release 重复）。
4. **取消原因待确认**：cancel 由用户账号执行（可能是主动停止 Gitee 同步，也可能是发现问题）。重跑前先问用户/看 `gh run view 40290028539 --log` 的 sync-to-gitee 段是否卡在某个步骤（Gitee API 超时/凭证问题常见）。

## 交付明细（按 commit）

| commit | 内容 |
| --- | --- |
| 65bf0f1 | **更新检查修正**：主源 404 判为「渠道未发布」提示已是最新，不再误报「请更换网络」；首次发版前不再弹红色故障 |
| b4a9ef0 | **插件中心适配**：目录切断后不再请求/重试；内置 9 个协议包并入已安装列表；清空全部指向作者站点的死链入口 |
| e106c6a | **构建开关**：`-PdebugWatermark`（本地关水印）、`-Px86Release`（MuMu 验收 x86_64 包）；CI 不传，分发不变 |
| 181a590 | **品牌图标**：登录页从手绘矢量脸换成品牌插画母版，88dp 圆角 |
| e297ea7 | **选校面板重做**：1777 校实时搜索（拼音/首字母）、有适配的学校标星、移除两层预设与安装流程、LazyColumn 防卡顿 |
| 87e3f7e | **反馈改道**：从作者站点网页表单改为 GitHub Issue，自动预填版本/机型/错误码；含 4 项单测 |
| 3a1054d | **移除公告中心与问卷中心**：入口、未读角标、轮询、弹窗、WebView 全部拆除；`ENABLE_REMOTE_ANNOUNCEMENT=false`；README/官网文案同步 |
| 712f915 | **地图 SDK 物理移除**：11 个源文件 + maplibre/amap 依赖删除，arm64 包 -33MB；`device.location.pick` 能力改为明确报不支持 |
| 5507480 | **发版交接文档**：RELEASE-HANDOFF.md（runbook 增补与过时点修正） |
| 5f3bfe1* | **更新历史替换**：设置页更新历史从上游 50 条改为发行版专属 12 条（本篇同批提交） |

## 用户可见变化一览

- 设置页：「地图服务」「公告中心」「问卷中心」三个入口消失，「快捷反馈」变「问题反馈」（跳 GitHub）
- 登录页：品牌插画图标；选校支持拼音直接搜，有适配的学校带星
- 插件中心：空态说明「教务适配已内置」，内置协议包在已安装列表可见
- 更新历史：发行版专属版本史
- 体积：111MB/架构

## 明确移除的功能与恢复方式

地图服务、公告中心、问卷中心、油猴下载、匿名统计上报——均为上游作者服务依赖，数据源已在 `SelfHostConfig` 切断。**底层实现文件全部保留在库**（R8 裁剪、上游合并安全），需要接回时按 git 历史反向恢复即可。

## 保留的核心能力（未受影响）

教务全链路（登录/课表/成绩/考试/选课，QuickJS 协议引擎执行）、CAS/WebVPN 登录、滑块验证码识别（ONNX）、课表变更与提醒、自动静音、桌面组件、签名清单断点续传更新、1777 校本地学校库、本地插件导入。

## 已知事项

1. **debug→release 覆盖安装后登录会话不恢复**（学校等数据正常）——换签名验收场景特有，正式用户 release→release 不受影响
2. **MuMu 内 gitee.com 被 DNS 劫持**——更新检查的 Gitee 备源在模拟器必失败，非 bug
3. **GeckoView 浏览器内核保留**（用户拍板）：网页类插件能力完整；未来如需再省 50MB，可在 2.0.1 单独评估移除（教务零功能影响，需独立验证轮）
4. 协议包 `device.location.pick` 调用会得到明确失败提示（地图已移除），不再挂起

## 发版

上一轮由发版 agent 执行至 build 成功、Gitee 同步被取消（见「发版当前状态」）。继续收尾的核对顺序：

1. `gh release view v2.0.0` 确认 GitHub Release 现状（存在则补 Gitee 同步即可，不必重打 tag 全量重跑）
2. 查被取消 run 的 sync-to-gitee 日志定位取消原因（凭证/超时/人工）
3. 决定：重跑 sync-to-gitee job（推荐，增量）或删 Release 重打 tag 全量重走
4. 完成后按 RELEASE-HANDOFF §6 做发布后抽查（Gitee 清单、Pages、真机检查更新）

其余细节与执行命令见 `internal/RELEASE-HANDOFF.md`。
