import Fastify from 'fastify';
import { config } from './lib/config.js';
import { initDb } from './lib/db.js';
import { rateLimit } from './lib/rateLimit.js';
import { registerAuthRoutes } from './routes/auth.js';
import { registerSyncRoutes } from './routes/sync.js';

/**
 * 构建 Fastify 应用（不监听端口）。
 * 本地/自托管用 src/start.js 调 listen()；Vercel 用 api/[...].js 调 app.routing()。
 */
export async function buildApp() {
  // Serverless（Vercel）文件系统只读，必须用远端库
  if (process.env.VERCEL && config.dbUrl.startsWith('file:')) {
    throw new Error('Vercel 环境必须设置 DB_URL 指向远端 libSQL（文件系统只读，不能用 file:）');
  }

  const client = await initDb(config.dbUrl, config.dbAuthToken);

  const app = Fastify({
    logger: true,
    bodyLimit: 4 * 1024 * 1024, // 同步一次可能携带较大 payload
    trustProxy: config.trustProxy,
  });

  // 每 IP 限速：登录/注册 10 次/分，其余 API 120 次/分
  if (!config.rateLimitDisabled) {
    app.addHook('onRequest', async (req, reply) => {
      const path = (req.url || '').split('?')[0];
      if (path === '/api/auth/login' || path === '/api/auth/register') {
        return rateLimit({ limit: 10, windowMs: 60_000 })(req, reply);
      }
      if (path.startsWith('/api/')) {
        return rateLimit({ limit: 120, windowMs: 60_000 })(req, reply);
      }
    });
  }

  registerAuthRoutes(app, { config, client });
  registerSyncRoutes(app, { config, client });

  app.setErrorHandler((err, req, reply) => {
    req.log.error(err);
    const status = err.statusCode || 500;
    const msg = status >= 500 && process.env.NODE_ENV === 'production'
      ? '服务器内部错误'
      : err.message;
    reply.code(status).send({ code: status, msg });
  });

  app.get('/', async () => ({
    name: 'melos-backend',
    version: '2.0.0',
    endpoints: [
      '/api/auth/register', '/api/auth/login', '/api/auth/logout', '/api/auth/logout-all',
      '/api/me', '/api/me/sessions',
      '/api/sync/pull', '/api/sync/push',
    ],
  }));

  return app;
}