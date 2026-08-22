import crypto from 'node:crypto';
import { getUserByUsername, getUserById } from './db.js';

const TOKEN_TTL_SEC = 30 * 24 * 60 * 60; // 30 天
const USERNAME_RE = /^[a-zA-Z0-9_\u4e00-\u9fa5]{2,20}$/;

export function hashPassword(password, salt) {
  return crypto.scryptSync(String(password), salt, 64).toString('hex');
}

export function createUserToken(userId, username, key) {
  const body = Buffer.from(JSON.stringify({
    uid: userId,
    username,
    role: 'user',
    exp: Math.floor(Date.now() / 1000) + TOKEN_TTL_SEC,
  })).toString('base64url');
  const sig = crypto.createHmac('sha256', key).update(body).digest('base64url');
  return `${body}.${sig}`;
}

export function verifyUserToken(token, key) {
  if (!token) return null;
  const idx = token.indexOf('.');
  if (idx <= 0) return null;
  const body = token.slice(0, idx);
  const sig = token.slice(idx + 1);
  const expect = crypto.createHmac('sha256', key).update(body).digest('base64url');
  try {
    if (!crypto.timingSafeEqual(Buffer.from(sig), Buffer.from(expect))) return null;
    const payload = JSON.parse(Buffer.from(body, 'base64url').toString('utf8'));
    if (payload.role !== 'user' || typeof payload.exp !== 'number' || payload.exp < Date.now() / 1000) return null;
    if (!payload.uid) return null;
    return payload;
  } catch {
    return null;
  }
}

export function validateRegisterInput(username, password, nickname) {
  if (!USERNAME_RE.test(String(username || ''))) return '用户名需 2-20 位（字母/数字/下划线/中文）';
  if (typeof password !== 'string' || password.length < 6 || password.length > 64) return '密码需 6-64 位';
  if (nickname != null && String(nickname).length > 24) return '昵称最长 24 字';
  return null;
}

/** Fastify preHandler：要求有效用户 token，并注入 req.user / req.userRow */
export function requireUser(config, db) {
  return async (req, reply) => {
    const header = req.headers.authorization || '';
    const token = header.startsWith('Bearer ') ? header.slice(7) : header;
    const payload = verifyUserToken(token, config.encryptionKey);
    if (!payload) {
      return reply.code(401).send({ code: 401, msg: '未登录或登录已过期' });
    }
    const user = await getUserById(db, payload.uid);
    if (!user) {
      return reply.code(401).send({ code: 401, msg: '账号不存在' });
    }
    req.user = payload;
    req.userRow = user;
  };
}
