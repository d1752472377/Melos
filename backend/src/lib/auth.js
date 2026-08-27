import crypto from 'node:crypto';

const TOKEN_TTL_SEC = 24 * 60 * 60; // 24 小时

/**
 * 管理端鉴权：
 * - 登录接口用 ADMIN_USER / ADMIN_PASSWORD（环境变量）校验，签发 HMAC 签名 token。
 * - 管理 API 通过 Authorization: Bearer <token> 校验。
 * - 签名密钥复用 ENCRYPTION_KEY（不额外引入依赖）。
 */

export function signToken(payload, key) {
  const body = Buffer.from(
    JSON.stringify({ ...payload, exp: Math.floor(Date.now() / 1000) + TOKEN_TTL_SEC })
  ).toString('base64url');
  const sig = crypto.createHmac('sha256', key).update(body).digest('base64url');
  return `${body}.${sig}`;
}

export function verifyToken(token, key) {
  if (!token) return null;
  const idx = token.indexOf('.');
  if (idx <= 0) return null;
  const body = token.slice(0, idx);
  const sig = token.slice(idx + 1);
  const expect = crypto.createHmac('sha256', key).update(body).digest('base64url');
  try {
    if (!crypto.timingSafeEqual(Buffer.from(sig), Buffer.from(expect))) return null;
    const payload = JSON.parse(Buffer.from(body, 'base64url').toString('utf8'));
    if (typeof payload.exp !== 'number' || payload.exp < Date.now() / 1000) return null;
    return payload;
  } catch {
    return null;
  }
}

/** 校验登录并返回 token；失败返回 null */
export function login(username, password, config) {
  if (!config.adminPassword) return { error: '服务端未配置 ADMIN_PASSWORD' };
  if (username !== config.adminUser || password !== config.adminPassword) {
    return { error: '用户名或密码错误' };
  }
  const token = signToken({ username, role: 'admin' }, config.encryptionKey);
  return { token };
}

/** Fastify preHandler：要求有效 token */
export function requireAuth(config) {
  return async (req, reply) => {
    const header = req.headers.authorization || '';
    const token = header.startsWith('Bearer ') ? header.slice(7) : header;
    const payload = verifyToken(token, config.encryptionKey);
    if (!payload) {
      return reply.code(401).send({ code: 401, msg: '未授权：请先登录获取 token' });
    }
    req.admin = payload;
  };
}

// ==================== 登录失败锁定（防在线爆破） ====================
// 连续失败 5 次锁定账号 15 分钟（内存态，服务重启即解除；配合 IP 限速使用）。

const LOGIN_FAIL_LIMIT = 5;
const LOGIN_LOCK_MS = 15 * 60 * 1000;
const failMap = new Map(); // username -> { count, firstAt }

export function isLoginLocked(username) {
  const e = failMap.get(username);
  if (!e) return false;
  if (Date.now() - e.firstAt >= LOGIN_LOCK_MS) {
    failMap.delete(username);
    return false;
  }
  return e.count >= LOGIN_FAIL_LIMIT;
}

export function recordLoginFailure(username) {
  const e = failMap.get(username) || { count: 0, firstAt: Date.now() };
  e.count += 1;
  failMap.set(username, e);
}

export function clearLoginFailures(username) {
  failMap.delete(username);
}
