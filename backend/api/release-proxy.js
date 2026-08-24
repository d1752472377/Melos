// GitHub Release 资产转发 + 下载页
// private 仓库的 Release 资产需要用 GitHub API + token 下载
//
// 路由：
//   /update.json  → 转发 GitHub Release 的 update.json
//   /latest.apk   → 流式转发 GitHub Release 的 latest.apk
//   /download     → 返回下载页 HTML

const REPO = 'd1752472377/Melos';
const RELEASE_TAG = 'v1.7.0';

async function getAssetUrl(assetName) {
  const token = process.env.GITHUB_TOKEN;
  if (!token) throw new Error('GITHUB_TOKEN 未配置');
  // GitHub API: 列出 Release assets
  const relRes = await fetch(
    `https://api.github.com/repos/${REPO}/releases/tags/${RELEASE_TAG}`,
    { headers: { Authorization: `Bearer ${token}`, Accept: 'application/vnd.github+json' } },
  );
  if (!relRes.ok) throw new Error(`获取 Release 失败: ${relRes.status}`);
  const rel = await relRes.json();
  const asset = (rel.assets || []).find((a) => a.name === assetName);
  if (!asset) throw new Error(`资产 ${assetName} 不存在`);
  // 用 API URL + Accept: octet-stream 下载（会 302 到 objects.githubusercontent.com，带 token 自动跟随）
  return `https://api.github.com/repos/${REPO}/releases/assets/${asset.id}`;
}

export default async function handler(req, res) {
  const path = req.url || '';
  const token = process.env.GITHUB_TOKEN;

  try {
    // 下载页 HTML
    if (path.startsWith('/download')) {
      const assetUrl = await getAssetUrl('update.json');
      const up = await fetch(assetUrl, {
        headers: { Authorization: `Bearer ${token}`, Accept: 'application/octet-stream' },
        redirect: 'follow',
      });
      const info = up.ok ? await up.json() : {};
      res.setHeader('Content-Type', 'text/html; charset=utf-8');
      res.setHeader('Cache-Control', 'public, max-age=60');
      res.status(200).send(renderDownloadPage(info));
      return;
    }

    // update.json 转发
    if (path.includes('update.json') || path.includes('update-info')) {
      const assetUrl = await getAssetUrl('update.json');
      const up = await fetch(assetUrl, {
        headers: { Authorization: `Bearer ${token}`, Accept: 'application/octet-stream' },
        redirect: 'follow',
      });
      if (!up.ok) { res.status(up.status).json({ code: up.status, msg: 'update.json 获取失败' }); return; }
      const text = await up.text();
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
  const v = esc(info.versionName || '?');
  const size = info.apkSize ? formatSize(info.apkSize) : '';
  const time = esc(info.publishTime || '');
  // 更新日志按行渲染；含"注意/卸载"的行高亮为警示样式
  const rows = (info.updateLog || '')
    .split('\n')
    .map((s) => s.trim())
    .filter(Boolean)
    .map((s) => {
      const warn = /注意|卸载|必须/.test(s);
      return `<li${warn ? ' class="warn"' : ''}>${esc(s)}</li>`;
    })
    .join('\n');
  const md5Short = info.md5 ? `MD5 ${esc(String(info.md5)).slice(0, 12)}…` : '';
  return `<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1, viewport-fit=cover">
<meta name="theme-color" content="#121214">
<title>Aesthetic Music · 下载</title>
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
    width:100%;max-width:400px;
    background:#232327;border:1px solid #34343a;border-radius:20px;
    padding:30px 22px 22px;text-align:center;
    box-shadow:0 14px 44px rgba(0,0,0,.45);
  }
  .icon{
    width:72px;height:72px;margin:0 auto 14px;border-radius:19px;
    background:linear-gradient(135deg,#f59e0b,#fbbf24);
    display:flex;align-items:center;justify-content:center;
    font-size:34px;color:#1e1e22;font-weight:800;
    box-shadow:0 6px 20px rgba(245,158,11,.35);
    user-select:none;
  }
  .brand{
    font-size:22px;font-weight:800;letter-spacing:5px;
    background:linear-gradient(135deg,#f59e0b,#fbbf24);
    -webkit-background-clip:text;background-clip:text;-webkit-text-fill-color:transparent;
  }
  .sub{color:#9e9891;font-size:11.5px;margin-top:4px;letter-spacing:2px}
  .version{
    display:inline-block;margin-top:16px;padding:5px 14px;border-radius:999px;
    background:rgba(245,158,11,.12);border:1px solid rgba(245,158,11,.35);
    color:#fde68a;font-size:13px;font-weight:600;
  }
  ul.log{list-style:none;text-align:left;margin:16px 2px 0;display:flex;flex-direction:column;gap:7px}
  ul.log li{position:relative;padding-left:15px;font-size:13px;line-height:1.65;color:#c9c4bd}
  ul.log li::before{content:"";position:absolute;left:2px;top:.6em;width:5px;height:5px;border-radius:50%;background:#f59e0b}
  ul.log li.warn{
    color:#fecaca;background:rgba(239,68,68,.09);
    border:1px solid rgba(239,68,68,.25);border-radius:10px;
    padding:8px 10px 8px 27px;font-weight:500;
  }
  ul.log li.warn::before{background:#ef4444;left:11px;top:50%;transform:translateY(-50%)}
  .meta{margin-top:13px;color:#7c756e;font-size:11.5px}
  .btn{
    display:flex;align-items:center;justify-content:center;gap:8px;
    margin-top:18px;height:54px;border-radius:14px;
    background:linear-gradient(135deg,#f59e0b,#fbbf24);
    color:#1e1e22;font-weight:700;font-size:16px;text-decoration:none;
    box-shadow:0 6px 18px rgba(245,158,11,.3);
    transition:transform .12s ease,filter .12s ease;
  }
  .btn small{font-weight:500;font-size:12px;opacity:.75}
  .btn:active{transform:scale(.97)}
  @media (hover:hover){.btn:hover{filter:brightness(1.07)}}
  .tips{margin-top:14px;color:#7c756e;font-size:11.5px;line-height:1.7}
</style>
</head>
<body>
<div class="card">
  <div class="icon">&#9834;</div>
  <div class="brand">AESTHETIC</div>
  <div class="sub">MUSIC PLAYER</div>
  <div class="version">v${v}${time ? ' &middot; ' + time : ''}</div>
  ${rows ? `<ul class="log">${rows}</ul>` : ''}
  <a class="btn" href="/latest.apk">下载安装包${size ? ` <small>${esc(size)}</small>` : ''}</a>
  <div class="meta">${md5Short}</div>
  <div class="tips">Android 7.0+ · 更换签名版本需先卸载旧版<br>若点击无反应请长按选择浏览器打开</div>
</div>
</body>
</html>`;
}

function formatSize(bytes) {
  const n = Number(bytes) || 0;
  if (n < 1048576) return (n / 1024).toFixed(1) + ' KB';
  return (n / 1048576).toFixed(1) + ' MB';
}
