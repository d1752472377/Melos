import { buildApp } from './app.js';
import { config } from './lib/config.js';

// 本地 / 自托管入口：构建应用并监听端口。
// Vercel 部署不经过这里（见 api/[...].js）。
const app = await buildApp();
const addr = await app.listen({ port: config.port, host: config.host });
console.log(`[music-backend] listening on ${addr}`);