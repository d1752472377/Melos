/**
 * 固定窗口限速（内存实现，按 桶名 + IP 隔离计数）。
 *
 * 说明：多实例（Vercel Serverless）下各实例各自计数，不是全局精确限速，
 * 但足以挡住单点滥用（暴力破解、刷接口）。生产可在网关层再做一层。
 * IP 取自 Fastify 的 req.ip（受 trustProxy 控制，不直接信任 x-forwarded-for）。
 */
const COUNTS = new Map(); // `${bucket}\u0001${ip}\u0001${slot}` -> count
let lastSweepAt = 0;

function sweep(now, windowMs) {
  // 每 30 秒或容量超过 2000 时触发清理，避免内存泄漏与无谓的高频遍历
  if (COUNTS.size < 2000 && now - lastSweepAt < 30_000) return;
  lastSweepAt = now;
  const currentSlot = Math.floor(now / windowMs);
  for (const [k] of COUNTS) {
    const s = Number(k.slice(k.lastIndexOf('\u0001') + 1));
    if (s < currentSlot - 1) {
      COUNTS.delete(k);
    }
  }
}

export function rateLimit({ name = 'default', limit = 60, windowMs = 60_000 } = {}) {
  return async (req, reply) => {
    const now = Date.now();
    const slot = Math.floor(now / windowMs);
    const key = `${name}\u0001${req.ip || 'unknown'}\u0001${slot}`;
    const count = (COUNTS.get(key) || 0) + 1;
    COUNTS.set(key, count);
    sweep(now, windowMs);
    if (count > limit) {
      return reply.code(429).send({ code: 429, msg: '请求过于频繁，请稍后再试' });
    }
  };
}