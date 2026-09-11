import {
  findUserByUsername,
  findUserById,
  createUser,
  updateUserProfile,
  updateUserPassword,
  publicUser,
  createSession,
  revokeSessionByTokenHash,
  revokeSessionById,
  revokeAllSessions,
  listSessions,
} from '../lib/db.js';
import {
  newSalt,
  hashPassword,
  verifyPassword,
  generateToken,
  hashToken,
  requireUser,
  isLoginLocked,
  recordLoginFailure,
  clearLoginFailures,
} from '../lib/auth.js';

const USERNAME_RE = /^[a-zA-Z0-9_\u4e00-\u9fa5]{2,20}$/;

function validateRegister(username, password, nickname) {
  if (!USERNAME_RE.test(String(username || ''))) return '用户名需 2-20 位（字母/数字/下划线/中文）';
  if (typeof password !== 'string' || password.length < 5 || password.length > 64) return '密码需 5-64 位';
  if (nickname != null && String(nickname).length > 24) return '昵称最长 24 字';
  return null;
}

async function issueSession(client, user, deviceName, ttlMs, maxSessions) {
  const token = generateToken();
  const { expiresAt } = await createSession(client, {
    userId: user.id,
    tokenHash: hashToken(token),
    deviceName,
    ttlMs,
    maxSessions,
    nowMs: Date.now(),
  });
  return { token, expiresAt };
}

/**
 * 用户体系 API（本后端唯一职责之一）：
 *   POST /api/auth/register           注册（自动登录）
 *   POST /api/auth/login              登录（多端各自持独立会话）
 *   POST /api/auth/logout             登出当前会话（token 立刻失效）
 *   POST /api/auth/logout-all         登出所有设备
 *   GET  /api/me                      当前用户
 *   PUT  /api/me                      改昵称/头像
 *   GET  /api/me/sessions             当前登录设备列表
 *   DELETE /api/me/sessions/:id       移除某设备会话
 */
export function registerAuthRoutes(app, { config, client }) {
  const allowAuth = requireUser(config, client);
  const SESSION_TTL_MS = config.sessionTtlDays * 24 * 60 * 60 * 1000;

  app.post('/api/auth/register', async (req, reply) => {
    const { username, password, nickname, deviceName } = req.body || {};
    const name = String(username || '').trim();
    const err = validateRegister(name, password, nickname);
    if (err) return reply.code(400).send({ code: 400, msg: err });
    if (await findUserByUsername(client, name)) {
      return reply.code(409).send({ code: 409, msg: '用户名已存在' });
    }
    const salt = newSalt();
    const user = await createUser(client, {
      username: name,
      passwordHash: await hashPassword(password, salt),
      salt,
      nickname: nickname ? String(nickname) : undefined,
      nowMs: Date.now(),
    });
    const session = await issueSession(client, user, deviceName, SESSION_TTL_MS, config.maxSessionsPerUser);
    return { code: 200, data: { token: session.token, expiresAt: session.expiresAt, user: publicUser(user) } };
  });

  app.post('/api/auth/login', async (req, reply) => {
    const { username, password, deviceName } = req.body || {};
    const name = String(username || '').trim();
    if (!name || typeof password !== 'string' || !password) {
      return reply.code(400).send({ code: 400, msg: '需要 username / password' });
    }
    if (isLoginLocked(name, req.ip)) {
      return reply.code(429).send({ code: 429, msg: '失败次数过多，请 15 分钟后再试' });
    }
    const user = await findUserByUsername(client, name);
    if (!user) {
      recordLoginFailure(name, req.ip);
      return reply.code(401).send({ code: 401, msg: '用户名或密码错误' });
    }
    const ok = await verifyPassword(password, user.salt, user.password_hash);
    if (!ok) {
      recordLoginFailure(name, req.ip);
      return reply.code(401).send({ code: 401, msg: '用户名或密码错误' });
    }
    clearLoginFailures(name, req.ip);
    const session = await issueSession(client, user, deviceName, SESSION_TTL_MS, config.maxSessionsPerUser);
    return { code: 200, data: { token: session.token, expiresAt: session.expiresAt, user: publicUser(user) } };
  });

  app.post('/api/auth/logout', { preHandler: allowAuth }, async (req) => {
    await revokeSessionByTokenHash(client, req.tokenHash);
    return { code: 200, msg: '已退出登录' };
  });

  app.post('/api/auth/logout-all', { preHandler: allowAuth }, async (req) => {
    await revokeAllSessions(client, req.user.id);
    return { code: 200, msg: '已退出所有设备' };
  });

  app.get('/api/me', { preHandler: allowAuth }, async (req) => ({
    code: 200,
    data: publicUser(req.user),
  }));

  app.put('/api/me', { preHandler: allowAuth }, async (req, reply) => {
    const { nickname, avatar } = req.body || {};
    if (nickname != null && String(nickname).length > 24) {
      return reply.code(400).send({ code: 400, msg: '昵称最长 24 字' });
    }
    const user = await updateUserProfile(client, req.user.id, {
      nickname: nickname != null ? String(nickname) : undefined,
      avatar: avatar != null ? String(avatar) : undefined,
      nowMs: Date.now(),
    });
    return { code: 200, data: publicUser(user) };
  });

  app.put('/api/me/password', { preHandler: allowAuth }, async (req, reply) => {
    const { oldPassword, newPassword } = req.body || {};
    if (!oldPassword || typeof oldPassword !== 'string') {
      return reply.code(400).send({ code: 400, msg: '请输入原密码' });
    }
    if (!newPassword || typeof newPassword !== 'string' || newPassword.length < 5 || newPassword.length > 64) {
      return reply.code(400).send({ code: 400, msg: '新密码需 5-64 位' });
    }
    const user = await findUserById(client, req.user.id);
    if (!user) {
      return reply.code(404).send({ code: 404, msg: '用户不存在' });
    }
    const ok = await verifyPassword(oldPassword, user.salt, user.password_hash);
    if (!ok) {
      return reply.code(400).send({ code: 400, msg: '原密码错误' });
    }
    const salt = newSalt();
    const passwordHash = await hashPassword(newPassword, salt);
    await updateUserPassword(client, req.user.id, {
      passwordHash,
      salt,
      nowMs: Date.now(),
    });
    // 修改密码后，注销本用户除当前设备外的其它设备会话
    await revokeAllSessions(client, req.user.id, req.session.id);
    return { code: 200, msg: '密码修改成功' };
  });

  app.get('/api/me/sessions', { preHandler: allowAuth }, async (req) => {
    const list = await listSessions(client, req.user.id);
    return {
      code: 200,
      data: list.map((s) => ({
        id: s.id,
        deviceName: s.device_name,
        createdAt: s.created_at,
        lastSeenAt: s.last_seen_at,
        expiresAt: s.expires_at,
        current: s.id === req.session.id,
      })),
    };
  });

  app.delete('/api/me/sessions/:id', { preHandler: allowAuth }, async (req) => {
    await revokeSessionById(client, req.user.id, req.params.id);
    return { code: 200, msg: '已移除设备' };
  });
}