// Vercel Serverless 入口：把 Fastify 实例接到 Vercel 的 Node handler 上。
// 项目根 = backend/，Vercel 会把 /api/* 路由到这里。
// 首次冷启动构建 app（含远端 DB 连接 + 音源加载），之后复用同一实例。
import { buildApp } from '../src/lib/app.js';

let appPromise;

export default async function handler(req, res) {
  if (!appPromise) appPromise = buildApp();
  const app = await appPromise;
  await app.ready();
  app.routing(req, res);
}