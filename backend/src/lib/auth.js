import crypto from 'node:crypto';
import { findSessionByTokenHash, touchSession, revokeSessionByTokenHash } from './db.js';

/**
 * 鉴权原语：
 * - 密码：异步 scrypt（加盐），不阻塞事件循环。
 * - 登录态：不透明随机 token（crypto.randomBytes），服务端只存 SHA-256 哈希。
 *   登出即删行，token 立刻失效；不存在“签发出去就收不回”的无状态 JWT 堆积问题。
 */

export function newSalt() {
  return crypto.randomBytes(16).toString('hex');
}

export function hashPassword(password, salt) {
  return new Promise((resolve, reject) => {
    crypto.scrypt(String(password), salt, 64, (err, derivedKey) => {
      if (err) reject(err);
      else resolve(derivedKey.toString('hex'));
    });
  });
}

export async function verifyPassword(password, salt, expectedHex) {
  const derived = await hashPassword(password, salt);
  const a = Buffer.from(derived, 'hex');
  const b = Buffer.from(expectedHex, 'hex');
  return a.length === b.length && crypto.timingSafeEqual(a, b);
}

export function generateToken() {
  return crypto.randomBytes(32).toString('base64url');
}

export function hashToken(token) {
  return crypto.createHash('sha256').update(token).digest('hex');
}

const LAST_SEEN_REFRESH_MS = 5 * 60 * 1000; // 节流：最多每 5 分钟刷新一次 last_seen_at

/**
 * Fastify preHandler：要求有效登录态。校验通过后注入 req.user / req.session / req.tokenHash。
 */
export function requireUser(config, client) {
  return async (req, reply) => {
    const header = req.headers.authorization || '';
    const token = header.startsWith('Bearer ') ? header.slice(7).trim() : header.trim();
    if (!token) {
      return reply.code(401).send({ code: 401, msg: '未登录' });
    }
    const tokenHash = hashToken(token);
    const session = await findSessionByTokenHash(client, tokenHash);
    if (!session || session.revoked_at != null) {
      return reply.code(401).send({ code: 401, msg: '登录已失效，请重新登录' });
    }
    const now = Date.now();
    if (session.expires_at <= now) {
      await revokeSessionByTokenHash(client, tokenHash); // 顺手清掉过期行，防堆积
      return reply.code(401).send({ code: 401, msg: '登录已过期，请重新登录' });
    }
    if (now - session.last_seen_at >= LAST_SEEN_REFRESH_MS) {
      await touchSession(client, session.id, now);
    }
    req.user = {
      id: session.user_id,
      username: session.username,
      nickname: session.nickname,
      avatar: session.avatar,
    };
    req.session = session;
    req.tokenHash = tokenHash;
  };
}

// ==================== 登录失败锁定（防在线爆破，内存态） ====================

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