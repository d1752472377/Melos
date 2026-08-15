import Fastify from 'fastify';
import cors from '@fastify/cors';
import fastifyStatic from '@fastify/static';
import { fileURLToPath } from 'node:url';
import { dirname, join } from 'node:path';
import { config } from './lib/config.js';
import { initDb } from './lib/db.js';
import { Registry } from './lib/registry.js';
import { registerMusicRoutes } from './routes/music.js';
import { registerAdminRoutes } from './routes/admin.js';
import { registerUserRoutes } from './routes/user.js';
import { registerContentRoutes } from './routes/content.js';

const __dirname = dirname(fileURLToPath(import.meta.url));

const db = initDb(config.dbPath);
const registry = new Registry(db, config.encryptionKey);

const app = Fastify({ logger: true });

// 学习项目放开跨域；生产环境应限制 origin 白名单
await app.register(cors, { origin: true });

// 管理后台静态页面：http://<host>:<port>/admin/
await app.register(fastifyStatic, {
  root: join(__dirname, '..', 'public'),
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
    '/api/search', '/api/song/url', '/api/lyric', '/api/sources',
    '/api/daily', '/api/newsongs', '/api/ranks', '/api/rank/:id',
    '/api/user/register', '/api/user/login', '/api/user/me', '/api/user/favorites',
    '/api/user/history', '/api/user/playlists', '/api/user/stats',
    '/admin/login', '/admin/sources',
  ],
}));

const addr = await app.listen({ port: config.port, host: config.host });
console.log(`[music-backend] listening on ${addr}`);
