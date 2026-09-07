import { buildApp } from './app.js';
import { config } from './lib/config.js';
import { initDb, pruneExpiredSessions, pruneOldPlayEvents } from './lib/db.js';

// 本地 / 自托管入口。Vercel 部署不经过这里（见 api/[...].js）。
const app = await buildApp();
const addr = await app.listen({ port: config.port, host: config.host });
console.log(`[melos-backend] listening on ${addr}`);

// 启动时清理：过期会话 + 180 天前的听歌明细（防表无限膨胀）
try {
  const client = await initDb(config.dbUrl, config.dbAuthToken);
  const sessions = await pruneExpiredSessions(client);
  const events = await pruneOldPlayEvents(client, 180);
  if (sessions || events) {
    console.log(`[melos-backend] 启动清理：过期会话 ${sessions} 条，旧听歌明细 ${events} 条`);
  }
} catch (e) {
  console.warn(`[melos-backend] 启动清理失败: ${e.message}`);
}