/**
 * 每 IP 固定窗口限速（内存实现）。
 *
 * 说明：多实例（Vercel Serverless）下各实例各自计数，不是全局精确限速，
 * 但足以挡住单点滥用（暴力破解、刷接口）。生产可在网关层再做一层。
 * IP 取自 Fastify 的 req.ip（受 trustProxy 控制，不直接信任 x-forwarded-for）。
 */
const COUNTS = new Map(); // `${ip}\u0001${slot}` -> count

function sweep(now, windowMs) {
  if (COUNTS.size < 10000) return;
  const slot = Math.floor(now / windowMs);
  for (const k of COUNTS.keys()) {
    const s = Number(k.slice(k.lastIndexOf('\u0001') + 1));
    if (s < slot - 1) COUNTS.delete(k);
  }
}

export function rateLimit({ limit = 60, windowMs = 60_000 } = {}) {
  return async (req, reply) => {
    const now = Date.now();
    const slot = Math.floor(now / windowMs);
    const key = `${req.ip || 'unknown'}\u0001${slot}`;
    const count = (COUNTS.get(key) || 0) + 1;
    COUNTS.set(key, count);
    sweep(now, windowMs);
    if (count > limit) {
      return reply.code(429).send({ code: 429, msg: '请求过于频繁，请稍后再试' });
    }
  };
}