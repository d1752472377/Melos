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

function renderDownloadPage(info) {
  const v = info.versionName || '?';
  const log = (info.updateLog || '').replace(/\n/g, '<br>');
  const size = info.apkSize ? formatSize(info.apkSize) : '?';
  const time = info.publishTime || '';
  return `<!doctype html>
<html lang="zh-CN">
<head>
<meta charset="utf-8">
<meta name="viewport" content="width=device-width, initial-scale=1">
<title>Aesthetic Music 下载</title>
<style>
  *{margin:0;padding:0;box-sizing:border-box}
  body{background:#1e1e22;color:#e8e4df;font-family:-apple-system,"Segoe UI",Roboto,sans-serif;min-height:100vh;display:flex;align-items:center;justify-content:center}
  .card{width:90%;max-width:420px;background:#28282c;border-radius:16px;padding:36px 28px;text-align:center;box-shadow:0 8px 32px rgba(0,0,0,.3)}
  .brand{font-size:28px;font-weight:700;letter-spacing:6px;background:linear-gradient(135deg,#f59e0b,#fbbf24);-webkit-background-clip:text;background-clip:text;-webkit-text-fill-color:transparent}
  .sub{color:#9e9891;font-size:13px;margin-top:6px}
  .version{margin-top:20px;font-size:15px;color:#fde68a}
  .log{color:#b8b2aa;font-size:13px;line-height:1.7;margin-top:10px;text-align:left;padding:0 8px}
  .size{color:#7c756e;font-size:12px;margin-top:8px}
  .btn{display:block;margin-top:24px;padding:14px;background:#f59e0b;color:#1e1e22;font-weight:700;font-size:16px;border-radius:10px;text-decoration:none}
  .btn:active{opacity:.85}
  .tips{margin-top:14px;color:#7c756e;font-size:12px;text-align:center;line-height:1.7}
</style>
</head>
<body>
<div class="card">
  <div class="brand">AESTHETIC</div>
  <div class="sub">Music Player</div>
  <div class="version">当前版本 v${v}</div>
  ${log ? `<div class="log">${log}</div>` : ''}
  <div class="size">大小 ${size}${time ? ' · ' + time : ''}</div>
  <a class="btn" href="/latest.apk">下载 APK</a>
  <div class="tips">Android 7.0+<br>仅限已安装用户更新或手动安装</div>
</div>
</body>
</html>`;
}

function formatSize(bytes) {
  const n = Number(bytes) || 0;
  if (n < 1048576) return (n / 1024).toFixed(1) + ' KB';
  return (n / 1048576).toFixed(1) + ' MB';
}
