// GitHub Release 资产转发 + 下载页
// private 仓库的 Release 资产需要用 GitHub API + token 下载
//
// 路由：
//   /update.json  → 转发 GitHub Release 的 update.json
//   /latest.apk   → 流式转发 GitHub Release 的 latest.apk
//   /download     → 返回下载页 HTML

const REPO = 'd1752472377/Melos';
// 发布标签：默认 v1.10.2，发新版时通过环境变量 RELEASE_TAG 覆盖，无需改代码
const RELEASE_TAG = process.env.RELEASE_TAG || 'v1.10.2';

// 资产 URL 缓存（60s）与 update.json 文本缓存（30s）：
// 旧实现每次请求都实时打 GitHub API（token 泄露面大 + 配额开销）
const assetCache = new Map(); // assetName -> { url, at }
let cachedUpdateText = null;
let cachedUpdateAt = 0;

async function getAssetUrl(assetName) {
  const token = process.env.GITHUB_TOKEN;
  if (!token) throw new Error('GITHUB_TOKEN 未配置');
  const hit = assetCache.get(assetName);
  if (hit && Date.now() - hit.at < 60_000) return hit.url;
  // GitHub API: 优先取最新正式 Release（发版自动跟随，无需维护 RELEASE_TAG）；
  // latest 不可用时回退到 RELEASE_TAG 指定的历史版本
  const headers = { Authorization: `Bearer ${token}`, Accept: 'application/vnd.github+json' };
  let relRes = await fetch(`https://api.github.com/repos/${REPO}/releases/latest`, { headers });
  if (!relRes.ok) {
    relRes = await fetch(
      `https://api.github.com/repos/${REPO}/releases/tags/${RELEASE_TAG}`,
      { headers },
    );
  }
  if (!relRes.ok) throw new Error(`获取 Release 失败: ${relRes.status}`);
  const rel = await relRes.json();
  const asset = (rel.assets || []).find((a) => a.name === assetName);
  if (!asset) throw new Error(`资产 ${assetName} 不存在`);
  // 用 API URL + Accept: octet-stream 下载（会 302 到 objects.githubusercontent.com，带 token 自动跟随）
  const url = `https://api.github.com/repos/${REPO}/releases/assets/${asset.id}`;
  assetCache.set(assetName, { url, at: Date.now() });
  return url;
}

/** 拉取 update.json 文本（带 30s 缓存，避免 /download 下载页每次请求都打 GitHub API） */
async function getUpdateJsonText(token) {
  if (cachedUpdateText && Date.now() - cachedUpdateAt < 30_000) return cachedUpdateText;
  const assetUrl = await getAssetUrl('update.json');
  const up = await fetch(assetUrl, {
    headers: { Authorization: `Bearer ${token}`, Accept: 'application/octet-stream' },
    redirect: 'follow',
  });
  if (!up.ok) throw new Error(`update.json 获取失败: ${up.status}`);
  const text = await up.text();
  cachedUpdateText = text;
  cachedUpdateAt = Date.now();
  return text;
}

export default async function handler(req, res) {
  const path = req.url || '';
  const token = process.env.GITHUB_TOKEN;

  try {
    // 下载页 HTML
    if (path.startsWith('/download')) {
      let info = {
        versionName: '1.10.2',
        publishTime: '2026-09-04',
        updateLog: '修复自定义音源沙箱 Polyfill 与设置页管理面板状态显示\n支持 QuickJS 运行时状态实时监测与异常一键重载',
      };
      try {
        const text = await getUpdateJsonText(token);
        if (text) {
          const parsed = JSON.parse(text);
          if (parsed && typeof parsed === 'object') info = parsed;
        }
      } catch (e) {
        console.warn('[proxy] 获取最新 release update.json 失败，降级使用内置信息:', e.message);
      }
      res.setHeader('Content-Type', 'text/html; charset=utf-8');
      res.setHeader('Cache-Control', 'public, max-age=60');
      res.status(200).send(renderDownloadPage(info));
      return;
    }

    // update.json 转发
    if (path.includes('update.json') || path.includes('update-info')) {
      const text = await getUpdateJsonText(token);
      res.setHeader('Content-Type', 'application/json; charset=utf-8');
      res.setHeader('Cache-Control', 'public, max-age=60');
      res.status(200).send(text);
      return;
    }

    // latest.apk 流式转发
    const assetUrl = await getAssetUrl('latest.apk');
    const up = await fetch(assetUrl, {
      headers: { Authorization: `Bearer ${token}`, Accept: 'application/octet-stream' },
      redirect: 'follow',
    });
    if (!up.ok) { res.status(up.status).json({ code: up.status, msg: `APK 获取失败: ${up.status}` }); return; }
    const contentType = 'application/vnd.android.package-archive';
    const contentLength = up.headers.get('content-length');
    res.setHeader('Content-Type', contentType);
    if (contentLength) res.setHeader('Content-Length', contentLength);
    res.setHeader('Content-Disposition', 'attachment; filename="latest.apk"');
    res.setHeader('Cache-Control', 'public, max-age=300');
    const reader = up.body.getReader();
    while (true) {
      const { done, value } = await reader.read();
      if (done) break;
      res.write(Buffer.from(value));
    }
    res.end();
  } catch (e) {
    res.status(502).json({ code: 502, msg: `转发失败: ${e.message}` });
  }
}

function esc(s) {
  return String(s)
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;');
}

function renderDownloadPage(info) {
  const v = esc(info.versionName || '1.10.0');
  const size = info.apkSize ? formatSize(info.apkSize) : '';
  const time = esc(info.publishTime || '');
  // 更新日志按行渲染；含"注意/卸载/必须"的行高亮为警示样式
  const rows = (info.updateLog || '')
    .split('\n')
    .map((s) => s.trim())
    .filter(Boolean)
    .map((s) => {
      const warn = /注意|卸载|必须/.test(s);
      return `<li${warn ? ' class="warn"' : ''}>${esc(s)}</li>`;
    })
    .join('\n');
  const md5Raw = info.md5 ? esc(String(info.md5)) : '';
  const md5Short = md5Raw ? `MD5: ${md5Raw.slice(0, 12)}…` : '';

  return `<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<meta name="theme-color" content="#121214">
<title>Melos · 音乐播放器下载</title>
<style>
  *{margin:0;padding:0;box-sizing:border-box;-webkit-tap-highlight-color:transparent}
  html{-webkit-text-size-adjust:100%}
  body{
    background:radial-gradient(120% 120% at 50% 0%,#26262b 0%,#17171a 55%,#121214 100%);
    color:#e8e4df;
    font-family:-apple-system,BlinkMacSystemFont,"Segoe UI",Roboto,"PingFang SC","Microsoft YaHei",sans-serif;
    min-height:100vh;min-height:100dvh;
    display:flex;align-items:center;justify-content:center;
    padding:max(24px,env(safe-area-inset-top)) 16px max(24px,env(safe-area-inset-bottom));
  }
  .card{
    width:100%;max-width:420px;
    background:#232327;border:1px solid #34343a;border-radius:24px;
    padding:32px 24px 26px;text-align:center;
    box-shadow:0 18px 50px rgba(0,0,0,.5);
    position:relative;overflow:hidden;
  }
  .card::before{
    content:"";position:absolute;top:0;left:10%;right:10%;height:1px;
    background:linear-gradient(90deg,transparent,rgba(245,158,11,.6),transparent);
  }
  .icon{
    width:76px;height:76px;margin:0 auto 14px;border-radius:22px;
    background:linear-gradient(135deg,#f59e0b,#fbbf24);
    display:flex;align-items:center;justify-content:center;
    font-size:36px;color:#1e1e22;font-weight:800;
    box-shadow:0 8px 24px rgba(245,158,11,.38);
    user-select:none;
  }
  .brand{
    font-size:24px;font-weight:800;letter-spacing:5px;
    background:linear-gradient(135deg,#f59e0b,#fbbf24);
    -webkit-background-clip:text;background-clip:text;-webkit-text-fill-color:transparent;
  }
  .sub{color:#9e9891;font-size:11px;margin-top:3px;letter-spacing:2.5px;font-weight:600}
  .badges{
    display:flex;justify-content:center;gap:8px;margin-top:14px;flex-wrap:wrap;
  }
  .badge{
    font-size:11.5px;padding:3px 9px;border-radius:999px;
    background:rgba(255,255,255,.05);color:#b3ada6;border:1px solid rgba(255,255,255,.08);
  }
  .version{
    display:inline-block;margin-top:14px;padding:5px 15px;border-radius:999px;
    background:rgba(245,158,11,.12);border:1px solid rgba(245,158,11,.35);
    color:#fde68a;font-size:13px;font-weight:600;
  }
  ul.log{list-style:none;text-align:left;margin:18px 0 0;display:flex;flex-direction:column;gap:8px}
  ul.log li{position:relative;padding-left:16px;font-size:13px;line-height:1.6;color:#c9c4bd}
  ul.log li::before{content:"";position:absolute;left:3px;top:.65em;width:5px;height:5px;border-radius:50%;background:#f59e0b}
  ul.log li.warn{
    color:#fecaca;background:rgba(239,68,68,.1);
    border:1px solid rgba(239,68,68,.25);border-radius:10px;
    padding:8px 10px 8px 27px;font-weight:500;
  }
  ul.log li.warn::before{background:#ef4444;left:11px;top:50%;transform:translateY(-50%)}
  .btn{
    display:flex;align-items:center;justify-content:center;gap:10px;
    margin-top:20px;height:52px;border-radius:14px;
    background:linear-gradient(135deg,#f59e0b,#fbbf24);
    color:#1e1e22;font-weight:700;font-size:16px;text-decoration:none;
    box-shadow:0 6px 20px rgba(245,158,11,.35);
    transition:transform .12s ease,filter .12s ease;
  }
  .btn svg{fill:currentColor}
  .btn small{font-weight:500;font-size:12px;opacity:.75}
  .btn:active{transform:scale(.98)}
  @media (hover:hover){.btn:hover{filter:brightness(1.08)}}
  .meta{margin-top:12px;color:#7c756e;font-size:11.5px;user-select:all}
  .tips{margin-top:12px;color:#7c756e;font-size:11.5px;line-height:1.6}
  .footer-links{
    margin-top:20px;padding-top:16px;border-top:1px solid rgba(255,255,255,.07);
    display:flex;justify-content:center;gap:18px;font-size:12px;
  }
  .footer-links a{color:#a8a29e;text-decoration:none;transition:color .15s}
  .footer-links a:hover{color:#fbbf24}
</style>
</head>
<body>
<div class="card">
  <div class="icon">&#9834;</div>
  <div class="brand">MELOS</div>
  <div class="sub">MUSIC PLAYER</div>
  <div class="badges">
    <span class="badge">⚡ 协议直连</span>
    <span class="badge">🎨 质感美学</span>
    <span class="badge">☁️ 多端同步</span>
  </div>
  <div class="version">v${v}${time ? ' &middot; ' + time : ''}</div>
  ${rows ? `<ul class="log">${rows}</ul>` : ''}
  <a class="btn" href="/latest.apk">
    <svg width="18" height="18" viewBox="0 0 24 24"><path d="M19 9h-4V3H9v6H5l7 7 7-7zM5 18v2h14v-2H5z"/></svg>
    <span>下载安装包</span>${size ? `<small>${esc(size)}</small>` : ''}
  </a>
  <div class="meta">${md5Short}</div>
  <div class="tips">Android 7.0+ · 更换签名版本需先卸载旧版<br>若点击无反应请长按选择浏览器打开</div>
  <div class="footer-links">
    <a href="/admin/">后台中枢 &rarr;</a>
    <a href="https://github.com/d1752472377/Melos" target="_blank" rel="noopener">GitHub 源码 &rarr;</a>
  </div>
</div>
</body>
</html>`;
}

function formatSize(bytes) {
  const n = Number(bytes) || 0;
  if (n < 1048576) return (n / 1024).toFixed(1) + ' KB';
  return (n / 1048576).toFixed(1) + ' MB';
}
