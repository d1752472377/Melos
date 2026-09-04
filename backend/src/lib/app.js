import Fastify from 'fastify';
import cors from '@fastify/cors';
import fastifyStatic from '@fastify/static';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { config } from './config.js';
import { initDb } from './db.js';
import { Registry } from './registry.js';
import { rateLimit } from './rateLimit.js';
import { registerMusicRoutes } from '../routes/music.js';
import { registerAdminRoutes } from '../routes/admin.js';
import { registerUserRoutes } from '../routes/user.js';
import { registerContentRoutes } from '../routes/content.js';
import { registerScriptsRoutes } from '../routes/scripts.js';
import { registerSyncRoutes } from '../routes/sync.js';

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

  // 跨域：默认全开（学习项目）；生产可通过 CORS_ORIGIN 配置为逗号分隔白名单
  const corsOrigin = config.corsOrigin === '*' ? true : config.corsOrigin.split(',').map((s) => s.trim());
  await app.register(cors, { origin: corsOrigin });

  // 每 IP 限速（内存窗口），防止暴力破解与配额滥用：
  //   admin 登录 5 次/分、用户登录/注册 10 次/分、其余 API 60 次/分
  if (!config.rateLimitDisabled) {
    app.addHook('onRequest', async (req, reply) => {
      const p = (req.url || '').split('?')[0];
      if (p === '/admin/login') return rateLimit({ limit: 5, windowMs: 60_000 })(req, reply);
      if (p === '/api/user/login' || p === '/api/user/register') {
        return rateLimit({ limit: 10, windowMs: 60_000 })(req, reply);
      }
      if (p.startsWith('/api/') || p.startsWith('/admin/')) {
        return rateLimit({ limit: 60, windowMs: 60_000 })(req, reply);
      }
    });
  }

  // 管理后台静态页面：http://<host>:<port>/admin/
  // Vercel 上由 includeFiles 打包 public/，此插件在函数内服务 /admin/ 静态资源
  await app.register(fastifyStatic, {
    root: join(__dirname, '..', '..', 'public'),
    prefix: '/',
  });

  // 访问 /admin（无斜杠）自动 302 重定向至 /admin/，保证静态资源相对路径与路由一致
  app.get('/admin', (req, reply) => {
    reply.redirect(302, '/admin/');
  });

  registerMusicRoutes(app, registry);
  registerAdminRoutes(app, { config, db, registry });
  registerUserRoutes(app, { config, db });
  registerContentRoutes(app, registry);
  registerScriptsRoutes(app, { config, db });
  registerSyncRoutes(app, { config, db });

  app.setErrorHandler((err, req, reply) => {
    req.log.error(err);
    const status = err.statusCode || 500;
    // 4xx 业务错误（路由内主动 throw 的）可以透出 message；
    // 5xx 内部错误在生产环境只给泛化提示，避免泄露内部路径 / 上游接口细节
    const msg = status >= 500 && process.env.NODE_ENV === 'production'
      ? '服务器内部错误'
      : err.message;
    reply.code(status).send({ code: status, msg });
  });

  app.get('/', async () => ({
    name: 'music-backend',
    version: '0.2.0',
    admin: '/admin/',
    endpoints: [
      '/api/search', '/api/search/hot', '/api/search/playlist', '/api/search/album',
      '/api/search/lyric', '/api/album', '/api/song/url', '/api/lyric',
      '/api/artist/search', '/api/artist', '/api/sources',
      '/api/daily', '/api/newsongs', '/api/ranks', '/api/rank/:id', '/api/categories',
      '/api/user/register', '/api/user/login', '/api/user/me', '/api/user/favorites',
      '/api/user/history', '/api/user/playlists', '/api/user/stats',
      '/admin/login', '/admin/sources',
    ],
  }));

  return app;
}