<p align="center">
  <img src="pic/v1.0.68/01-courses.jpg" width="170"/>
  <img src="pic/v1.0.68/03-timetable.jpg" width="170"/>
  <img src="pic/v1.0.68/05-grades.jpg" width="170"/>
  <img src="pic/v1.0.68/06-wallpaper-image.jpg" width="170"/>
</p>

<h1 align="center">正方教务助手</h1>

<p align="center">
  <strong>开源 · 免费 · 安全</strong><br/>
  Android 教务客户端，支持新正方、旧正方、新强智、旧强智<br/>
  选课、抢课、课表、成绩，UI 是一整套液态玻璃
</p>

<p align="center">
  <a href="https://github.com/znjhahaha/zhengfang-apk/releases/latest"><img src="https://img.shields.io/github/v/release/znjhahaha/zhengfang-apk?style=flat-square&color=blueviolet&label=最新版本" alt="Release"/></a>
  <a href="https://github.com/znjhahaha/zhengfang-apk/actions"><img src="https://img.shields.io/github/actions/workflow/status/znjhahaha/zhengfang-apk/release.yml?style=flat-square&label=CI/CD" alt="CI"/></a>
  <a href="LICENSE"><img src="https://img.shields.io/badge/license-GPL--3.0-blue?style=flat-square" alt="License"/></a>
  <a href="https://github.com/znjhahaha/zhengfang-apk/stargazers"><img src="https://img.shields.io/github/stars/znjhahaha/zhengfang-apk?style=flat-square" alt="Stars"/></a>
</p>

---

## 这版有什么

界面全套换成了液态玻璃。折射、色散、高光都是实时算的，按下去会形变，松手弹回来，不是贴一层半透明白糊上去。

- **背景可以自己换**：预设渐变、纯色、或者直接用相册里的图
- **图片背景能调**：模糊和蒙版两根滑条，自己拧到舒服为止
- **配色跟着背景走**：浅色底自动配深字，深色底配浅字，不用手动切
- **顶栏随滚动收起**：往下翻的时候一屏能多看一节多的内容
- **筛选改成浮层**：收起来之后列表不会跳回顶部
- **小屏不再被挡**：16:9 这类短屏幕上，弹窗按钮和列表末项以前会被导航栏压住

老设备跑不动实时模糊会自动回退到透镜采样，不会直接卡死。

---

## 功能

选课抢课：

- 按关键词搜、按类别筛，余量和教师都显示
- 即时执行 — 有空位，拼手速
- 定时任务 — 设好开抢时间，到点自动发包
- 捡漏 — 盯着满员的课，有人退立刻顶上

抢课使用前台服务，定时启动使用 AlarmManager。通知、精确闹钟权限和系统后台限制会影响运行与触发时间；学校仍决定选课窗口、名额、学分和冲突规则。

课表成绩：

- 课表周视图，课程自动配色，可导出 `.ics` 到系统日历
- 成绩按学期查，GPA 自动算，另外还有总体成绩和考试安排

杂项：

- 应用内更新，启动检测新版，下载完直接装
- 公告带时间线，反馈直接发作者邮箱
- 可添加不同学校，所有版本跨学校合计最多使用 3 个学生账号

---

## 教务系统与限制

当前源码接入以下四类教务。学校地址可自行添加；应用内「设置 → 教务支持与限制」可查看当前学校对应的规则。

| 教务 | 登录 | 执行与数据限制 |
| --- | --- | --- |
| 新正方 | 账密、图片验证码、网页登录；保留原有兼容流程 | 串行或最多 2 门并行；筛选与查询以学校返回内容为准 |
| 旧正方 | 标准 RSA 账密登录、图片验证码获取与刷新 | App 按账号串行保护表单状态；体育课与选退课使用学校当前控件 |
| 新强智 | 直接账密登录；额外的人机验证或统一认证可转网页登录 | App 按账号串行保护轮次上下文；无开放轮次时仍可查询已选、课表和成绩 |
| 旧强智 | 账密登录、图片验证码获取与刷新 | App 按账号串行保护轮次上下文；开放分类及操作权限由学校决定 |

四类教务共用单门/批量选课、退课、队列排序、精确目标、智能匹配和定时任务入口，以及课表、成绩、考试和导出功能。旧正方、新强智、旧强智的筛选使用已加载的学校数据，未公布的容量不会当作有空位。智能匹配限定同一轮次和课程，可更换教师或时段；手动填写的教师与时间仍作为约束。

验证码、选课声明、结果待确认或登录失效时需要人工处理；应用不会绕过学校限制。定制认证、无法识别的操作控件可能仍需网页登录，这属于适配范围，不等同于学校不支持该功能。可在「设置 → 统一登录适配」申请适配。

实网账密登录及查询已验证山东农业大学（新强智）、广州松田职业学院（旧强智）、黑龙江工程学院（旧正方）；原有浙江工业大学统一身份认证流程保留。验证没有向真实学校提交选课或退课。详见[账密验证记录](docs/testing/2026-09-08-password-login-validation.md)及[功能对齐记录](docs/testing/2026-09-08-four-system-parity.md)。

---

## 密码存哪了

支持账号密码登录，学校要求图片验证码时由用户填写；定制登录保留网页登录入口。

密码走 Android KeyStore 的 AES/GCM 加密，仅保存在本机，并在登录时提交到所选学校的教务或认证地址。已保存账密时，App 会在会话过期后尝试续期；学校要求验证码或二次确认时需手动完成。加密或读取失败时按「未保存凭据」处理，不会明文写盘。

不信可以自己翻 `CredentialStore.kt` 和 `SessionRenewer.kt`。

---

## 截图

<table>
  <tr>
    <td align="center"><img src="pic/v1.0.68/01-courses.jpg" width="170"/><br/><sub>课程列表</sub></td>
    <td align="center"><img src="pic/v1.0.68/02-navbar-morph.jpg" width="170"/><br/><sub>底栏切换途中</sub></td>
    <td align="center"><img src="pic/v1.0.68/03-timetable.jpg" width="170"/><br/><sub>周视图课表</sub></td>
    <td align="center"><img src="pic/v1.0.68/04-grab-scheduled.jpg" width="170"/><br/><sub>抢课工作台</sub></td>
  </tr>
  <tr>
    <td align="center"><img src="pic/v1.0.68/05-grades.jpg" width="170"/><br/><sub>成绩与考试</sub></td>
    <td align="center"><img src="pic/v1.0.68/06-wallpaper-image.jpg" width="170"/><br/><sub>图片背景</sub></td>
    <td align="center"><img src="pic/v1.0.68/07-wallpaper-color.jpg" width="170"/><br/><sub>纯色取色</sub></td>
    <td align="center"><img src="pic/v1.0.68/08-wallpaper-preset.jpg" width="170"/><br/><sub>预设背景</sub></td>
  </tr>
</table>

第二张是切 Tab 切到一半截的，图标在路上会被拉长然后并到一块，动起来比截图好看。

---

## 快速开始

### 直接下载（推荐）

去 [Releases 页面](https://github.com/znjhahaha/zhengfang-apk/releases/latest) 下载最新 APK，装上就能用。Android 7.0+，建议 12 以上，玻璃效果最全。

### 从源码构建

```bash
git clone https://github.com/znjhahaha/zhengfang-apk.git
cd zhengfang-apk
./gradlew assembleDebug
```

环境要求：Android Studio Hedgehog+、JDK 17、`compileSdk 37` / `targetSdk 34` / `minSdk 24`。

---

## 新手教程

### 第一步：登录

选择或添加学校，确认新正方、旧正方、新强智、旧强智的类型，再填写教务账号密码。需要验证码时可输入或刷新图片；定制认证按提示打开学校网页。

### 第二步：换背景（可选）

设置 → 背景。预设、颜色、图片三个来源，选图片之后能调模糊和蒙版。

<p align="center">
  <img src="pic/v1.0.68/06-wallpaper-image.jpg" width="240"/>
  <img src="pic/v1.0.68/08-wallpaper-preset.jpg" width="240"/>
</p>

### 第三步：抢课

| 模式 | 啥时候用 | 怎么操作 |
|------|----------|----------|
| 即时执行 | 现在就有空位 | 课程详情 → 立即抢课 |
| 定时任务 | 知道几点开抢 | 先把课加进队列 → 设启动时间 → 到点自动跑 |
| 捡漏 | 想抢已经满了的热门课 | 选好课 → 开捡漏 → 有人退自动顶 |

定时任务得先往队列里加课，队列空着创建不了，会提示你。

### 第四步：看课表 / 查成绩

课表在底栏第二个 Tab，周视图，右上角能导出 `.ics`。成绩在第四个，按学期切，GPA 自动算好。

---

## 技术栈

Kotlin + Jetpack Compose（Material 3）。玻璃渲染用 Kyant Backdrop 2.0，`blur + lens + vibrancy` 三层，按设备能力分档，低端机降级到透镜采样。动效走 Compose Animation，spring/tween 曲线全收在 `MotionTokens` 里。网络 OkHttp + Coroutines，HTML 用 Jsoup 解析。屏幕适配靠 `ScreenMetrics` 的两个连续紧凑度系数插值几何，没有尺寸分档。打包发布走 GitHub Actions。

---

## 二次开发

项目基于 **GPLv3** 开源，二开前先把协议看清楚。

> **不接受任何形式的私自打包和分发。** 唯一的二开渠道是向本仓库提 PR，CI/CD 会自动构建并发布——这是为了避免外面满天飞的山寨包。

提 PR 流程：

1. Fork
2. 改完跑一遍 `./gradlew assembleDebug` 确认能编
3. 提交：`git commit -m 'feat: xxx'`
4. 推上去开 PR，CI 会自己跑

UI 类改动记得附真机截图，审起来省事。

更新日志写在 `release-notes/vX.Y.Z.md`，CI 从那儿读，扇到 GitHub Release 和 App 内的更新提示，别到处各写一份。

---

## 免责声明

- 项目开源免费，仅供学习交流
- 用出任何后果自己负责
- 抢课归抢课，别挂一晚上把学校教务打挂

## 许可证

[GPL-3.0](LICENSE)
