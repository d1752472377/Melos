import Fastify from 'fastify';
import cors from '@fastify/cors';
import fastifyStatic from '@fastify/static';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { config } from './config.js';
import { initDb } from './db.js';
import { Registry } from './registry.js';
import { registerMusicRoutes } from '../routes/music.js';
import { registerAdminRoutes } from '../routes/admin.js';
import { registerUserRoutes } from '../routes/user.js';
import { registerContentRoutes } from '../routes/content.js';

const __dirname = dirname(fileURLToPath(import.meta.url));

/**
 * 构建 Fastify 应用（不监听端口）。
 * 本地/服务器用 src/start.js 调 listen()；Vercel 用 api/[...].js 调 app.routing()。
 * 数据库按 DB_URL 连接：本地 file: 或远端 libSQL（分布式/Serverless 用远端）。
 *
 * 注意：本文件放在 src/lib/ 下，避免被 Vercel 的零配置误识别为根路径函数入口
 * （Vercel 会扫描 src/app.js、src/index.js 这类文件名并要求 default export）。
 */
export async function buildApp() {
  // Serverless 环境（Vercel）文件系统只读，必须用远端库；本地默认 file: 不受影响
  if (process.env.VERCEL && config.dbUrl.startsWith('file:')) {
    throw new Error('Vercel 环境必须设置 DB_URL 指向远端 libSQL（文件系统只读，不能用 file:）');
  }

  const db = await initDb(config.dbUrl, config.dbAuthToken);
  const registry = new Registry(db, config.encryptionKey);
  await registry.init();

  const app = Fastify({ logger: true });

  // 学习项目放开跨域；生产环境应限制 origin 白名单
  await app.register(cors, { origin: true });

  // 管理后台静态页面：http://<host>:<port>/admin/
  // Vercel 上由 includeFiles 打包 public/，此插件在函数内服务 /admin/ 静态资源
  await app.register(fastifyStatic, {
    root: join(__dirname, '..', '..', 'public'),
    prefix: '/',
  });

  registerMusicRoutes(app, registry);
  registerAdminRoutes(app, { config, db, registry });
  registerUserRoutes(app, { config, db });
  registerContentRoutes(app, registry);

  app.setErrorHandler((err, req, reply) => {
    req.log.error(err);
    const status = err.statusCode || 500;
    reply.code(status).send({ code: status, msg: err.message });
  });

  app.get('/', async () => ({
    name: 'music-backend',
    version: '0.2.0',
    admin: '/admin/',
    endpoints: [
      '/api/search', '/api/song/url', '/api/lyric',
      '/api/artist/search', '/api/artist',
      '/api/sources',
      '/api/daily', '/api/newsongs', '/api/ranks', '/api/rank/:id',
      '/api/user/register', '/api/user/login', '/api/user/me', '/api/user/favorites',
      '/api/user/history', '/api/user/playlists', '/api/user/stats',
      '/admin/login', '/admin/sources',
    ],
  }));

  return app;
}