# 教务助手匿名使用统计

Worker 接收安装实例上报，D1 保存首次及最近使用时间；独立统计站点只允许管理员访问。

| 入口 | 用途 |
| --- | --- |
| `https://usage.hidisiwa.xyz/v1/usage` | 公开 POST 上报，不提供读取功能 |
| `https://stats.hidisiwa.xyz/` | Cloudflare Access 保护的统计页 |
| `https://stats.hidisiwa.xyz/api/summary` | 同样受保护的汇总接口 |

## 数据与口径

请求 JSON 只接受 `installationId`（随机 UUID v4）和 `version`。请求体上限 512 字节，不接受额外字段。服务端生成时间，按安装 ID 幂等更新，乱序请求不会使最近使用时间倒退。

D1 的 `installations` 表仅保存随机 ID、首次上报时间、最近上报时间和版本；不保存 IP、学校、学号或账号。IP 只用于 Cloudflare 的短期限流键，Worker 请求日志默认关闭。

- 累计设备：至少成功上报一次的不同安装 ID。
- 今日活跃：北京时间今天 00:00 起成功上报的不同安装 ID。
- 近 30 天活跃：今天及之前 29 个北京时间自然日内成功上报的不同安装 ID。

统计不能追溯旧版本。安装实例不等于自然人，重装或清除数据可能重新计数。关闭统计后不再更新使用记录，已有汇总记录仍保留。

Android 的标识、说明确认和开关保存在 `noBackupFilesDir` 的原子文件中，不参与云备份；覆盖更新继续使用原 ID。正式构建只有在说明已确认、开关开启、非演示模式且应用处于前台时才发送。当天成功后不重复发送，失败在后续前台使用时重试；退出前台、关闭开关或进入演示模式会取消待发送请求。

## 访问控制

Cloudflare Zero Trust 中使用 Self-hosted 应用保护整个 `stats.hidisiwa.xyz`，路径留空。Allow 策略只包含管理员的完整邮箱，登录方式使用 One-time PIN。不要添加 Everyone、Bypass 或覆盖该域名的公开路径。

`wrangler.jsonc` 的 `ACCESS_TEAM_DOMAIN`、`ACCESS_AUD` 与 `ADMIN_EMAIL` 必须匹配应用。Team domain 和 AUD 是验签配置，不是登录凭据。Worker 对统计站点的每一个路径都验证 Access JWT 的 RS256 签名、发行者、受众、有效期、主体和确切邮箱；即使 Access 配置变更也会拒绝无效身份。

`workers_dev` 与 `preview_urls` 均为 `false`。应用层还拒绝所有未配置的主机名。公开上报域名不会返回统计数据。

## 开发和部署

需要 Node.js 22.13+（测试使用内置 SQLite），以及具有 Workers、D1 和域名路由权限的 Wrangler 登录。

```powershell
npm.cmd ci
npm.cmd run typecheck
npm.cmd test
npm.cmd run migrate:local
npm.cmd run dev
```

本地服务同样验证管理身份；数据与验签测试通过独立 SQLite 和测试 RSA 密钥执行，不需要生产登录凭据。

首次部署到新账号时先创建 D1 数据库，把返回 ID 填入配置，并完成 Access 应用配置。当前生产数据库已经创建，无需再次创建。

```powershell
npx.cmd wrangler whoami
npm.cmd run migrate:remote
npx.cmd wrangler deploy --dry-run
npm.cmd run deploy
```

变更前先确认 `account_id`、两个自定义域名和 D1 ID。迁移通过编号文件维护；不要直接清空生产表。

部署后验证：未登录访问统计页和汇总接口应跳转 Access；管理员邮箱登录后显示三个数字；公开上报接口的 GET 返回 405；`workers.dev` 不可访问。

## 设备联调

常规 Debug、UiPreview 和演示模式都不参与真实计数。部署联调使用隔离 UiPreview 中的显式测试开关，随机创建专用 ID，不读取真实安装 ID：

```powershell
adb -s 127.0.0.1:16416 shell am instrument -w -r -e usageLiveCheck true -e class com.tyust.course.usage.UsageLiveServiceDeviceTest com.tyust.course.uipreview.test/androidx.test.runner.AndroidJUnitRunner
```

测试会输出 `USAGE_LIVE_INSTALLATION_ID`。用 Wrangler 查询该确切 ID，验证两次相同上报只有一条记录，并在验证结束后只删除该测试 ID 对应的行。不要用总量清零替代清理测试数据。设备上的测试文件会自动清理。

更新或回滚使用 Wrangler 的 deployments/rollback 命令；Worker 回滚不回滚 D1 迁移。更换 Access 应用后需要同步新的 AUD 并重新部署。
