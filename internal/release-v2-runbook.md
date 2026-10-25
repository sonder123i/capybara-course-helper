# v2.0.0 发布手册（一键发布前唯一需要人做的部分）

> 生成于 B8（2026-10-26 深夜自动批次）。代码已发布就绪；剩余步骤按本手册执行。

## 0. 前置一次性配置

1. **更新签名私钥**（已生成，公钥已钉在 app/src/main/assets/app-update-keys.json）：
   - 私钥：`C:\Users\k2767\projects\capybara-update-signing\update-signing-key.pem` —— **先备份到安全位置**。
   - 注入 GitHub Secret `UPDATE_SIGNING_KEY_B64`：
     ```bash
     base64 -w0 C:/Users/k2767/projects/capybara-update-signing/update-signing-key.pem
     # 把输出粘贴为 repo secret
     ```
2. 确认既有 secrets 仍有效：KEYSTORE_BASE64 / RELEASE_STORE_PASSWORD / RELEASE_KEY_ALIAS / RELEASE_KEY_PASSWORD / GITEE_TOKEN。

## 1. rc 真机验证（发布硬门，不可跳过）

`git checkout v2-main` 后本地构建 release：`gradlew assembleRelease`，
取 `app/build/outputs/apk/release/app-arm64-v8a-release.apk`（或 universal）装真机：

- [ ] 与 1.2.2 共存/覆盖升级各试一次：覆盖后学校、账号、课表、抢课队列、公告已读、壁纸全部保留
- [ ] 桌面上 1.x 添加的三种组件仍渲染且随课表更新（Glance 续命）
- [ ] 任一正方学校登录→课表→成绩→选课冒烟；强智一所（如有账号）
- [ ] 插件中心本地导入一个 .zfplugin → 停用 → 回退
- [ ] 设置→课表自动化：上课自动静音开关生效一次
- [ ] 整学期课前提醒一键设置
- [ ] 飞行模式启动：无任何对 hidisiwa/znj/greasyfork 的请求（logcat 过滤）
- [ ] 检查更新：无清单时给出明确失败文案（B4 清单随正式发布才上线）

## 2. 正式发布（一条命令）

```bash
scripts/release.sh 2.0.0 --push
```

CI（release.yml，tag 触发）将自动：测试+Lint → 构建（arm64/armeabi/universal 三包）
→ GitHub Release → 生成签名清单 docs/update/stable.json → 同步 Gitee（附件+清单）
→ Pages 落库 version.json/清单/公告 feed → 公告投递校验。

tag 已打好未推送时等价于：`git push origin v2-main && git push origin refs/tags/v2.0.0`。

## 3. 发布后抽查

- 更新链路：1.2.2 真机点「检查更新」→ 应提示 v2.0.0（Pages version.json）并能下载安装
- v2 设备：检查更新 → 签名清单校验通过 → 下载/暂停/续传 → 安装
- 公告中心：拉到 v2.0.0 公告，重要弹窗出现一次，标已读后不再弹

## 4. 回滚

- 发布前任何时刻：`git tag -d v2.0.0 && git reset --hard HEAD~1`（未推送时）
- 已发布且发现阻断问题：main 分支热修 → 以 v2.0.1 发布（versionCode 单调递增保证可推）
- 1.x 维护分支：`git branch 1.x-maintenance main@{1.2.2}`（尚未创建，需要时建）

## 5. 延后功能清单（v2.0.x 跟进，源提交索引）

| 功能 | 1.x 源提交 | v2 接入点 |
| --- | --- | --- |
| 考试 .ics 导出 + 考前提醒 + 通知定位 | 34cabbb/511dabe/f3cf5a1/3d7e4ae/5886c2d | GradesScreen ExamScheduleContent |
| 开学日期第几周反推 | a020756 | SemesterAnchorNotice/ScheduleSettings |
| 课表变更检测（格子点亮 从→到） | 98205d7 | ScheduleRoute + ScheduleDiff 三件套 |
| 学校库搜索 A-Z/收藏交互增强 | 6df0730 | LoginScreen（v2 已有基础搜索，差异审计后定） |
