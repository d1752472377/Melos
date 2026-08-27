import { buildApp } from './lib/app.js';
import { config } from './lib/config.js';
import { initDb, pruneOldPlayEvents } from './lib/db.js';

// 本地 / 自托管入口：构建应用并监听端口。
// Vercel 部署不经过这里（见 api/[...].js）。
const app = await buildApp();
const addr = await app.listen({ port: config.port, host: config.host });
console.log(`[music-backend] listening on ${addr}`);

// 听歌统计保留策略：启动时清一次 90 天前的明细（防表无限膨胀）。
// Serverless（Vercel）环境不走本入口，如需清理请用定时任务调用同一函数或自行建索引。
try {
  const db = await initDb(config.dbUrl, config.dbAuthToken);
  const removed = await pruneOldPlayEvents(db, 90);
  if (removed > 0) console.log(`[music-backend] 已清理 ${removed} 条 90 天前的听歌统计`);
} catch (e) {
  console.warn(`[music-backend] 统计清理失败: ${e.message}`);
}