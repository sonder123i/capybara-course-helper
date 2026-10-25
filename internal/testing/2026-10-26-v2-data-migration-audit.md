# v2.0.0 数据迁移审计（1.2.x → 2.0.0，原位覆盖升级）

> 审计日期：2026-10-26（B7）。applicationId 未变（com.k2767.course），数据原地保留。

## 结论

**无需迁移垫片。** 1.x 全部 7 个持久化存储名是 v2 30 个的严格子集，两侧存储代码同源
（均自基线 v1.0.80 演化），向后读取兼容。

## 逐项核对

| 存储 | 1.x 写入 | v2 读取 | 判定 |
| --- | --- | --- | --- |
| user_info（学校/账号/凭据） | baseline 格式 + 1.x 附加字段 | baseline 格式（optString 容忍多余字段） | ✅ 直接可读；1.x 独有字段被忽略 |
| schedule_cache | baseline 格式 + seenRows 等附加键（98205d7） | baseline 格式键 | ✅ 附加键无害；最坏情况缓存未命中→一次重新同步 |
| grab_queue_prefs | baseline JSON | 上游 v1.0.128 格式（新增 sectionIdentityKnown，读取时默认 false） | ✅ 上游已做向后兼容 |
| announcement_prefs（已读集合） | 同名 | 同名 | ✅ 已读状态延续 |
| appearance_settings | 同名 | 同名 | ✅ 壁纸/主题延续 |
| schedule_settings | 同名（开学日期/显示偏好） | 同名 | ✅ |
| 组件快照（:widget 模块自有存储） | 1.x 组件写入 | 同模块原样并入 v2，WidgetSnapshotWriter 已在 v2 课表链路续写 | ✅ 老用户桌面组件数据续命 |

## v2 新增存储（1.x 不存在，无迁移问题）

app_update_v2（签名清单缓存）、plugin-*（插件包/权限/数据分区/凭据）、
campus-layout-*、class_quiet_*（上课静音）、grade_browse_preferences、amap-privacy。

## 残余风险与验证点（并入 rc 真机清单）

1. 真机 1.2.2 装真实数据 → 覆盖安装 rc → 学校/账号/课表/队列/已读/壁纸逐项肉眼核对。
2. 抢课队列旧条目在 v2 上可启动（sectionIdentityKnown=false 走模糊匹配路径）。
3. 桌面已添加的三种 Glance 组件在升级后仍渲染并随课表更新。
