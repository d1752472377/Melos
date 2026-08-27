/**
 * 每 IP 固定窗口限速（内存实现）。
 *
 * 注意：Vercel Serverless 下每个实例各自维护内存计数，不是全局精确限速；
 * 但可以挡住绝大多数单点滥用（暴力破解、配额耗尽式刷接口），
 * 生产多实例场景建议在网关层（如 Vercel 防火墙 / Cloudflare）再做一层限速。
 */
const COUNTS = new Map(); // `${ip}\u0001${slot}` -> count

function clientIp(req) {
  const xff = req.headers['x-forwarded-for'];
  if (xff) {
    const first = String(xff).split(',')[0].trim();
    if (first) return first;
  }
  return req.ip || 'unknown';
}

/** 概率清理过期槽位，防止长时间运行后内存膨胀 */
function maybeSweep(now, windowMs) {
  if (COUNTS.size < 5000) return;
  const currentSlot = Math.floor(now / windowMs);
  for (const [key] of COUNTS) {
    const slot = Number(key.slice(key.lastIndexOf('\u0001') + 1));
    if (slot < currentSlot - 1) COUNTS.delete(key);
  }
}

/**
 * 返回 Fastify onRequest/preHandler 用的限速函数。
 * @param {{limit?: number, windowMs?: number}} opts 窗口内最多 limit 次
 */
export function rateLimit({ limit = 60, windowMs = 60_000 } = {}) {
  return async (req, reply) => {
    const now = Date.now();
    const slot = Math.floor(now / windowMs);
    const key = `${clientIp(req)}\u0001${slot}`;
    const count = (COUNTS.get(key) || 0) + 1;
    COUNTS.set(key, count);
    maybeSweep(now, windowMs);
    if (count > limit) {
      return reply.code(429).send({ code: 429, msg: '请求过于频繁，请稍后再试' });
    }
  };
}