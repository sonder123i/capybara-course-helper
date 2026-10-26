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

## 发版当前状态（重要：对外产物为零，需完整重走发版）

1. **GitHub Release 已删除**：曾于 08:28Z 创建（3 个 APK 齐全），回滚时已连同资产删除（`gh release list` 最新为 v1.2.2）——不是「创建在被取消的 job 里」，创建发生在取消之前
2. **Gitee 有残留，重发前必须人工删除（硬前置）**：sync-to-gitee 被取消前已创建 Release v2.0.0 对象、附件 curl 上传中途被杀；releases 页至今可见 v2.0.0。CI 对已存在的 Release 是「跳过复用」，不删会把旧/残缺附件挂在新包上。删除入口：https://gitee.com/sonder123i/capybara-course-helper/releases → v2.0.0 → 删除（tags 页已无 v2.0.0）
3. **tag v2.0.0 已删除**（本地与远端），CI run 40290028539 结论 cancelled（build ✓ 22m，sync-to-gitee 附件上传段被取消）
4. **可回收的产物**：被取消 run 的 artifacts 里有已构建的三包，但对外发布需重走流程
5. **取消原因已确认**：用户在本地做地图移除调整（→ 712f915），主动叫停发版。sync-to-gitee 日志显示附件（curl）上传进行中被杀、无 API/凭证报错——重发时 Gitee 上传段预计仍需 15 分钟以上，属正常耗时，不要误判为卡死

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
| 3a1054d（同笔并入） | **更新历史替换**：设置页更新历史从上游 50 条改为发行版专属 12 条 |

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

上一轮发版 agent 的尝试没有产出对外可见结果（Release/Gitee/Pages 均未落库），**发版需完整重走**：

1. ~~查上次卡点~~ 已完成：附件上传中被用户叫停，无 API/凭证错误（见「发版当前状态」第 5 条）
2. 硬前置已核：Secrets 6/6（2026-10-26 实测 `gh secret list`）；release-notes / CHANGELOG / 公告已含全部收尾内容，体积（130→110MB）与测试数（1412）已按实测修正
3. **前置人工步骤**：在 Gitee 网页删除残留的 Release v2.0.0（agent 无 GITEE_TOKEN，见「发版当前状态」第 2 条）
4. 推送分支并打**附注 tag**。注意：**不要重跑 `release.sh 2.0.0`**——发版提交 `ca2f1a7` 已包含全部版本文件，重跑会死在「nothing to commit」；**不要建轻量 tag**——仓库规矩重打 tag 须 `-a`：

   ```bash
   git push git@github.com:sonder123i/capybara-course-helper.git v2-main
   git tag -a v2.0.0 -m "Release v2.0.0"
   git push git@github.com:sonder123i/capybara-course-helper.git refs/tags/v2.0.0
   ```

5. 完成后按 RELEASE-HANDOFF §6 做发布后抽查（GitHub Release 三包、Gitee 清单、Pages、真机检查更新）

执行命令细节见 `internal/RELEASE-HANDOFF.md`。
