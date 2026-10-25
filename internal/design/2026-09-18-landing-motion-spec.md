# 官网动效规格（landing motion spec）

日期：2026-09-18。对象：`docs/index.html`（单文件零构建落地页）。
目标：在不引入构建链、不牺牲性能的前提下，把整站动效从「随手写的过渡」升级成「有体系的编排」。

---

## 一、先定规矩：为什么需要动效令牌

当前页面的问题是**参数散落**——按钮 `0.18s`、卡片进场 `0.7s`、导航 `0.3s`，缓动还混用了 `ease` 和自造贝塞尔。
单看每个都还行，凑在一起就是「说不上哪里怪」。**这就是业余和专业的差别所在**，不是效果不够炫。

参考两个开源设计系统的做法：

- **IBM Carbon** 给了 6 档静态时长 + 三组缓动（standard / entrance / exit），每组还分 productive（快）和 expressive（有表现力）两种风格
- **Material Design** 给了「时长按面积缩放、退场比进场快」的配对规则

下面这套令牌就是从这两家抄的口径，直接可用。

### 1.1 时长令牌（Carbon 口径）

```css
:root{
  --dur-fast-01: 70ms;    /* 按钮、开关这类微交互 */
  --dur-fast-02: 110ms;   /* 淡入淡出 */
  --dur-moderate-01: 150ms; /* 小展开、短距离位移 */
  --dur-moderate-02: 240ms; /* 展开、提示条 */
  --dur-slow-01: 400ms;   /* 大区块展开、重要通知 */
  --dur-slow-02: 700ms;   /* 背景变暗、首屏编排 */
}
```

⚠️ **Carbon 的硬标准：微交互必须落在 90–120ms**。你的按钮 hover 现在是 180ms，**偏慢**——按住按钮的那一刻要有"立刻响应"的感觉，180ms 会显得迟钝。改成 `--dur-fast-02`（110ms）。

### 1.2 缓动令牌

```css
:root{
  /* 标准：起止都在屏幕内（元素位置变化、状态切换） */
  --ease-standard: cubic-bezier(0.2, 0, 0.38, 0.9);
  /* 进场：从屏幕外进入，起点就是峰值速度，末端轻柔落定 */
  --ease-entrance: cubic-bezier(0, 0, 0.38, 0.9);
  /* 退场：离开屏幕，末端仍是峰值速度（暗示"捞不回来了"） */
  --ease-exit:     cubic-bezier(0.2, 0, 1, 0.9);
  /* 回弹：用于按压反馈，轻微过冲 */
  --ease-spring:   cubic-bezier(0.34, 1.56, 0.64, 1);
}
```

**三条使用规则**（Material 的口径）：

1. 进退场要用**不同**缓动。进场用 decelerate、退场用 accelerate——**退场永远比进场快**（退场不需要用户花注意力）
2. 时长跟着**面积**走。小图标 110ms，整块卡片要 400ms。这不是讲究，是视觉速度一致性的来源
3. 用户**输入**（点击、按压）一律用 `ease-out`，不要 `ease-in`。`ease-in` 会让人感觉"按了没反应"

---

## 二、分级实施方案

### P0 · 零成本纯 CSS（建议先做，当天可上）

#### P0-1 收口动效令牌
把上面两组变量加进 `:root`，然后把现有的 `0.18s` / `0.7s` / `0.3s` 全部替换成令牌引用。
**这一步不加任何新效果，但整站观感会立刻"齐"**。所有后续工作都以此为地基。

#### P0-2 首屏分级编排（hero choreography）

现状：hero 里的图标、标题、副标题、按钮、信任点**同时**淡入，看起来是一坨。
改成**逐级延迟**，间隔 80ms：

```css
.hero .stagger > *{ opacity:0; transform:translateY(14px); }
.hero .stagger > *{ animation: rise var(--dur-slow-02) var(--ease-entrance) both; }
.hero .stagger > *:nth-child(1){ animation-delay: 0ms; }
.hero .stagger > *:nth-child(2){ animation-delay: 80ms; }
.hero .stagger > *:nth-child(3){ animation-delay: 160ms; }
.hero .stagger > *:nth-child(4){ animation-delay: 240ms; }
.hero .stagger > *:nth-child(5){ animation-delay: 320ms; }

@keyframes rise{ to{ opacity:1; transform:none; } }
```

注意用 `both` 而不是 `forwards`——**首帧也要被应用**，否则元素会先闪一下原样再跳回去。

#### P0-3 用原生 CSS 滚动动效替换手写 IntersectionObserver

现在卡片进场是 JS 写的（`IntersectionObserver` + 加 class）。原生 CSS 版本更短、跑在合成线程上、主线程零负担：

```css
@supports (animation-timeline: scroll()) {
  .rv{
    animation: reveal linear both;
    animation-timeline: view();
    animation-range: entry 0% entry 60%;
  }
  @keyframes reveal{
    from{ opacity:0; transform:translateY(24px); }
    to  { opacity:1; transform:none; }
  }
}
```

**三个必须遵守的坑**（这几条踩了会很难查）：

1. **`@supports` 的检测必须写 `animation-timeline: scroll()`**，不能写 `scroll-timeline: root`——后者是草案期拼法，**任何浏览器都没实现过**，检测恒为 false
2. **基线状态必须是「可见」**。绝不能把 `opacity:0` 写在 `@supports` 外面——那样 Firefox 用户（还没默认开启）会看到**整页空白**，没有任何 JS 能救。正确做法是默认可见，只在支持时叠加动画
3. 如果你保留了 JS 兜底，**两条路径要互斥**。JS 加 class 写终态、CSS timeline 每帧重算，同一元素上两条同时跑会在阈值处闪一下。用同一个 `@supports` 检测把 JS 那条也 gate 掉

顺带一个性能事实：有实测显示 **JS 滚动监听在 2019 年的安卓机上、20 个元素就掉到 30fps；换成 CSS 版本后 50 个元素仍稳 60fps**。你页面上现在有 20+ 个进场元素，换过来是实打实的收益。

#### P0-4 顶部阅读进度条

零 JS，一屏代码，但"精致度"提升明显：

```css
.progress{
  position:fixed; inset:0 0 auto 0; height:2px; z-index:60;
  background:linear-gradient(90deg, var(--brand), var(--violet));
  transform-origin:0 50%; transform:scaleX(0);
  animation: grow linear both;
  animation-timeline: scroll(root);
}
@keyframes grow{ to{ transform:scaleX(1); } }
```

配合 P0-3 一起放进 `@supports` 里。

### P1 · 约 4KB，值得投入

#### P1-1 Lenis 平滑滚动（3KB）

工业标准，且**明确不会破坏 `position: sticky`，也不干扰 IntersectionObserver**——跟你现有的 sticky 导航零冲突。

⚠️ **不要用 CDN**。你的站现在是**零外部请求**（这点很值钱，别破坏）。把 `lenis.min.js` 下载到 `docs/assets/` 自托管：

```html
<script src="assets/lenis.min.js"></script>
<script>
(function(){
  if (window.matchMedia('(prefers-reduced-motion: reduce)').matches) return;
  if (!window.Lenis) return;
  var lenis = new Lenis({ duration: 1.1 });
  (function raf(t){ lenis.raf(t); requestAnimationFrame(raf); })(0);
})();
</script>
```

两个注意点：v1 需要配套的 `lenis.css`（自托管时一并下载）；`prefers-reduced-motion` 时必须**直接不初始化**——平滑滚动对前庭功能敏感的人是实打实的不适来源。

#### P1-2 鼠标跟随的玻璃高光（~20 行，无依赖）

真玻璃和假玻璃的分界线之一是**镜面高光跟着光源/视角走**。现在卡片的渐变高光是固定的，所以看着"贴上去的"。

做法：用 CSS 自定义属性存指针位置，`pointermove` 时更新，渐变跟着走。

```css
.glass{ --mx:50%; --my:0%; }
.glass::after{
  background:radial-gradient(420px circle at var(--mx) var(--my),
    rgba(255,255,255,.13), transparent 46%);
}
```
```js
document.querySelectorAll('.glass').forEach(function(el){
  el.addEventListener('pointermove', function(e){
    var r = el.getBoundingClientRect();
    el.style.setProperty('--mx', ((e.clientX - r.left) / r.width * 100) + '%');
    el.style.setProperty('--my', ((e.clientY - r.top) / r.height * 100) + '%');
  });
});
```

**不改布局、不引库**，但卡片立刻有"玻璃在反光"的活感。这是全案里性价比最高的一条。

### P2 · 看需求再上

#### P2-1 GSAP ScrollTrigger 做「抢课三模式」滚动叙事（+18KB）

**这是唯一真正需要 JS 库的地方**，原因是：原生 `view()` 时间线**不能 pin**（固定住元素）、**不能触发回调**、**不能做吸附**。要"滚到某处停住，三个模式依次点亮"，只能上 ScrollTrigger。

要不要花这 18KB，取决于你多想把"抢课三模式"讲透。如果只是想要入场动画，P0-3 已经够了。

#### P2-2 View Transitions API

单页站收益有限。除非以后加子页面（比如单独的文档页、更新日志页），再考虑。

### 明确不要做的

| 别做 | 原因 |
|---|---|
| **WebGL / Three.js 背景** | 对静态站太重；弱网加载不可接受；极容易做出「看起来很努力但很土」的效果 |
| **满屏视差（parallax）** | 最容易过度使用的手法。一两个装饰元素可以，铺满全站就是灾难 |
| **无限循环的装饰动画** | 持续消耗电量与注意力，Carbon 的检查清单里明确反对 |
| **`ease-in` 用在交互反馈上** | 会让"按下没反应"的感觉成真 |

---

## 三、验收清单

每条都可实测，不达标就不算完成：

| 项 | 标准 | 怎么验 |
|---|---|---|
| 性能 | Lighthouse Performance ≥ 95（移动端 4× 降速） | Chrome DevTools |
| 布局稳定 | CLS = 0 | Lighthouse |
| 绘制开销 | 滚动时无重绘 | DevTools → Rendering → Paint Flashing |
| 动画属性 | 只允许 `transform` / `opacity`，禁止 `top/left/width/height/color` | 代码审查 |
| 降级完整 | **禁用 JS 后页面全部内容可见可读** | DevTools 关 JS 刷新 |
| 无障碍 | `prefers-reduced-motion: reduce` 下动画停用、内容完整 | DevTools → Rendering → Emulate |
| 体积增量 | ≤ 4KB（Lenis 3KB + 手写 ~1KB，Lenis 算了自托管） | 对比文件总大小 |
| 微交互时长 | 点击/按压类落在 90–120ms | 代码审查 |
| 跨浏览器 | Firefox / Safari 下内容不缺失（只是少了部分动效） | 实机或 BrowserStack |

`prefers-reduced-motion` 的完整写法要包含时间线重置，否则滚动动画还会走：

```css
@media (prefers-reduced-motion: reduce){
  *, *::before, *::after{
    animation-duration:.01ms !important;
    animation-iteration-count:1 !important;
    transition-duration:.01ms !important;
    scroll-behavior:auto !important;
    animation-timeline:auto !important;   /* 关键：不加这条滚动动画仍然会走 */
  }
}
```

**这不是可选项**：前庭功能障碍研究显示约 **35% 的成年人**对屏幕动效敏感，忽略这个查询是实打实的可用性缺陷，不是"以后再补"的优化项。

---

## 四、实施顺序与预期

| 顺序 | 内容 | 成本 | 收益 |
|---|---|---|---|
| 1 | 动效令牌收口 | 半小时 | 整站观感立刻变"齐" |
| 2 | 首屏分级编排 | 半小时 | 第一屏从"一坨"变"有节奏" |
| 3 | 原生滚动动效 + 进度条 | 1 小时 | 性能提升 + 精致度 |
| 4 | 鼠标跟随玻璃高光 | 半小时 | 玻璃从"贴图"变"活物" |
| 5 | Lenis 平滑滚动 | 半小时 | 全站手感升一档 |
| 6 | GSAP 滚动叙事（可选） | 半天 | 讲透核心卖点 |

**前 5 项加起来半天内可完成，体积增量约 4KB。**

---

## 五、参考来源

- **IBM Carbon Motion** — 时长令牌（70/110/150/240/400/700ms）、三组缓动、90–120ms 微交互标准、动效评估清单
- **Material Design · Easing and Duration** — 时长按面积缩放、进退场配对规则、退场更快的原则
- **CSS Scroll-Driven Animations** — `animation-timeline: view()/scroll()`、`animation-range`；Chrome 115+ / Safari 17.4+ 原生支持，Firefox 仍需 flag，全球覆盖约 82.6%（2026 春）
- **Codrops（tympanus.net/codrops）** — 纯 CSS/原生 JS 动效的实现拆解，找 vanilla 实现的首选
- **Web Animation in 2026（CSS vs GSAP）** — 体积对照与选型边界：CSS 0KB / anime.js 5KB / GSAP 核心 9KB / GSAP+ScrollTrigger+Flip 18KB
