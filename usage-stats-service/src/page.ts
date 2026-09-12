export function renderDashboard(nonce: string): string {
  return `<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1">
<meta name="color-scheme" content="light dark"><title>使用统计 · 教务助手</title>
<style nonce="${nonce}">
:root{color-scheme:light;--bg:#f5f5f1;--ink:#202922;--muted:#626d65;--line:#dce1d9;--card:#fff;--accent:#276044;--soft:#eaf1e9;font-family:system-ui,-apple-system,"Segoe UI","Microsoft YaHei",sans-serif}
*{box-sizing:border-box}body{margin:0;background:var(--bg);color:var(--ink)}main{max-width:1060px;margin:auto;padding:64px 28px 32px}.brand{display:flex;align-items:center;gap:10px;font-size:14px;font-weight:650}.mark{display:grid;place-items:center;width:28px;height:28px;border-radius:9px;color:white;background:var(--accent)}.private{margin-left:auto;color:var(--muted);font-size:12px;font-weight:400}.intro{margin:64px 0 32px}.eyebrow{font-size:12px;letter-spacing:.14em;color:var(--accent);font-weight:650}h1{font-size:clamp(32px,5vw,46px);letter-spacing:-.045em;margin:12px 0 16px;font-weight:650}.subtitle{margin:0;line-height:1.8;color:var(--muted);font-size:15px}.toolbar{display:flex;align-items:center;justify-content:space-between;gap:16px;margin:24px 0 16px}.clock{color:var(--muted);font-size:13px;line-height:1.8}button{background:var(--accent);border:0;border-radius:999px;color:#fff;padding:11px 20px;font:inherit;font-size:13px;cursor:pointer;white-space:nowrap}button:disabled{opacity:.55;cursor:wait}button:focus-visible{outline:3px solid #7bac83;outline-offset:3px}.cards{display:grid;grid-template-columns:repeat(3,1fr);gap:16px}.card{background:var(--card);border:1px solid var(--line);border-radius:22px;padding:28px;min-height:208px}.card:first-child{background:var(--soft);border-color:transparent}.label{font-size:14px;margin:0;font-weight:500}.number{font-size:clamp(38px,5.2vw,64px);font-weight:620;letter-spacing:-.055em;font-variant-numeric:tabular-nums;line-height:1.25;margin:24px 0 10px;overflow-wrap:anywhere}.detail{font-size:12px;color:var(--muted);margin:0;line-height:1.8}.note{margin-top:36px;border-top:1px solid var(--line);padding-top:24px;display:grid;grid-template-columns:140px 1fr;gap:24px}.note h2{font-size:14px;margin:0;font-weight:550}.note p{font-size:13px;color:var(--muted);line-height:1.9;margin:0 0 8px}#status{min-height:22px;font-size:13px;color:var(--muted);margin:12px 0}footer{margin-top:52px;color:var(--muted);font-size:12px}
@media(max-width:640px){main{padding:28px 20px}.intro{margin-top:42px}.cards{grid-template-columns:1fr;gap:12px}.card{padding:24px;min-height:160px}.number{margin:16px 0 6px;font-size:48px}.note{grid-template-columns:1fr;gap:12px}.private{font-size:11px}}
@media(prefers-color-scheme:dark){:root{color-scheme:dark;--bg:#151b17;--ink:#edf2ed;--muted:#a6b1a8;--line:#354238;--card:#1e2721;--accent:#76a98a;--soft:#24372b}button{color:#102218}.mark{color:#102218}}
</style>
</head>
<body><main>
<header class="brand"><span class="mark" aria-hidden="true">学</span>教务助手<span class="private">仅管理员可见</span></header>
<section class="intro"><span class="eyebrow">使用概况</span><h1>看看有多少设备在使用。</h1><p class="subtitle">按匿名安装实例统计，所有日期均以北京时间为准。</p></section>
<div class="toolbar"><div class="clock" id="updated">正在获取最新数据…</div><button id="refresh" type="button">刷新数据</button></div>
<section class="cards" aria-label="设备统计" aria-busy="true" id="cards">
<article class="card"><h2 class="label">累计设备数</h2><p class="number" id="total">—</p><p class="detail">接入后至少成功上报一次</p></article>
<article class="card"><h2 class="label">今日活跃</h2><p class="number" id="today">—</p><p class="detail" id="day">北京时间 00:00 起</p></article>
<article class="card"><h2 class="label">近 30 天活跃</h2><p class="number" id="month">—</p><p class="detail">包含今天，按设备去重</p></article>
</section><p id="status" role="status" aria-live="polite"></p>
<section class="note"><h2>这些数字代表什么</h2><div><p>统计从新版接入后开始，只涵盖成功上报的设备；旧版本的使用人数无法追溯。</p><p>一台设备上的一次安装计为一个实例，不代表实际人数。重装或清除应用数据可能重新计数；关闭统计、离线或尚未升级的设备不会新增使用记录。</p><p id="started">统计起始日期：—</p></div></section>
<footer>仅收集随机安装标识和应用版本，不收集学号、学校、账号或课程内容。</footer>
</main>
<script nonce="${nonce}">
const byId = id => document.getElementById(id);
const format = new Intl.NumberFormat('zh-CN');
const date = new Intl.DateTimeFormat('zh-CN', {timeZone:'Asia/Shanghai',year:'numeric',month:'2-digit',day:'2-digit',hour:'2-digit',minute:'2-digit',second:'2-digit',hour12:false});
async function refresh(){
  byId('refresh').disabled=true;byId('cards').setAttribute('aria-busy','true');byId('status').textContent='';
  try{
    const response=await fetch('/api/summary',{credentials:'same-origin',cache:'no-store',redirect:'error',headers:{Accept:'application/json'}});
    if(!response.ok) throw new Error(response.status===401?'登录已过期，请刷新页面重新登录。':'暂时无法读取统计，请稍后重试。');
    const data=await response.json();
    for(const [id,key] of [['total','totalDevices'],['today','todayActive'],['month','last30DaysActive']]){
      if(!Number.isSafeInteger(data[key])||data[key]<0)throw new Error('统计数据暂不可用，请稍后重试。');
      byId(id).textContent=format.format(data[key]);
    }
    byId('updated').textContent='更新于 '+date.format(new Date(data.updatedAt));
    byId('day').textContent=data.day+' 00:00 起';
    byId('started').textContent='统计起始时间：'+date.format(new Date(data.startedAt));
    byId('status').textContent='已更新';
  }catch(error){byId('status').textContent=error instanceof TypeError?'连接失败或登录已过期，请刷新页面重试。':error.message;}
  finally{byId('refresh').disabled=false;byId('cards').setAttribute('aria-busy','false');}
}
byId('refresh').addEventListener('click',refresh);refresh();
</script></body></html>`;
}
