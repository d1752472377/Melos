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
      role: session.role || 'user',
      email: session.email || '',
      created_at: session.created_at != null ? Number(session.created_at) : undefined,
      updated_at: session.updated_at != null ? Number(session.updated_at) : undefined,
    };
    req.session = session;
    req.tokenHash = tokenHash;
  };
}

/**
 * Fastify preHandler：要求管理员权限。
 */
export function requireAdmin(config, client) {
  const allowUser = requireUser(config, client);
  return async (req, reply) => {
    await allowUser(req, reply);
    if (reply.sent) return;
    if (req.user?.role !== 'admin') {
      return reply.code(403).send({ code: 403, msg: '无权限访问：仅管理员可用' });
    }
  };
}

// ==================== 登录失败锁定（防在线爆破，按 用户名+IP 隔离，带 TTL 与容量保护） ====================

const LOGIN_FAIL_LIMIT = 5;
const LOGIN_LOCK_MS = 15 * 60 * 1000;
const MAX_FAIL_MAP_SIZE = 5000;
const failMap = new Map(); // key -> { count, firstAt }
let lastFailSweepAt = 0;

function failKey(username, ip = '') {
  return ip ? `${username}\u0001${ip}` : String(username);
}

function sweepFailMap(now) {
  if (failMap.size < MAX_FAIL_MAP_SIZE && now - lastFailSweepAt < 60_000) return;
  lastFailSweepAt = now;
  for (const [k, v] of failMap) {
    if (now - v.firstAt >= LOGIN_LOCK_MS) {
      failMap.delete(k);
    }
  }
  // 若大量随机攻击仍超出容量上限，淘汰最旧记录防 OOM
  if (failMap.size > MAX_FAIL_MAP_SIZE) {
    const toDelete = failMap.size - MAX_FAIL_MAP_SIZE;
    let deleted = 0;
    for (const k of failMap.keys()) {
      failMap.delete(k);
      deleted++;
      if (deleted >= toDelete) break;
    }
  }
}

export function isLoginLocked(username, ip = '') {
  const key = failKey(username, ip);
  const e = failMap.get(key);
  if (!e) return false;
  const now = Date.now();
  if (now - e.firstAt >= LOGIN_LOCK_MS) {
    failMap.delete(key);
    return false;
  }
  return e.count >= LOGIN_FAIL_LIMIT;
}

export function recordLoginFailure(username, ip = '') {
  const key = failKey(username, ip);
  const now = Date.now();
  sweepFailMap(now);
  const e = failMap.get(key) || { count: 0, firstAt: now };
  e.count += 1;
  failMap.set(key, e);
}

export function clearLoginFailures(username, ip = '') {
  failMap.delete(failKey(username, ip));
}