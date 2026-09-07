import {
  findUserById,
  findUserByUsername,
  createUser,
  adminUpdateUser,
  adminDeleteUser,
  listUsers,
  countUsers,
  publicUser,
  revokeAllSessions,
} from '../lib/db.js';
import {
  newSalt,
  hashPassword,
  requireAdmin,
} from '../lib/auth.js';

const USERNAME_RE = /^[a-zA-Z0-9_\u4e00-\u9fa5]{2,20}$/;

/**
 * 管理员专用接口（必须持 admin 角色）：
 *   GET    /api/admin/users         用户列表（分页/搜索/角色筛选）
 *   GET    /api/admin/users/:id     用户详情
 *   POST   /api/admin/users         创建用户（支持指定角色）
 *   PUT    /api/admin/users/:id     更新用户（昵称/头像/角色/重置密码）
 *   DELETE /api/admin/users/:id     删除用户（级联清理数据）
 */
export function registerAdminRoutes(app, { config, client }) {
  const allowAdmin = requireAdmin(config, client);

  // 用户列表
  app.get('/api/admin/users', { preHandler: allowAdmin }, async (req, reply) => {
    const query = req.query || {};
    let limit = Math.max(1, Math.min(100, parseInt(query.limit || query.pageSize, 10) || 10));
    let page = Math.max(1, parseInt(query.page || query.current, 10) || 1);

    // 兼容 simple-rest 的 _start / _end 模式
    if (query._start !== undefined && query._end !== undefined) {
      const start = Math.max(0, parseInt(query._start, 10) || 0);
      const end = Math.max(start, parseInt(query._end, 10) || 10);
      limit = Math.max(1, end - start);
      page = Math.floor(start / limit) + 1;
    }

    const offset = (page - 1) * limit;
    const search = String(query.search || query.q || '').trim();
    const role = String(query.role || '').trim();

    const [total, items] = await Promise.all([
      countUsers(client, { search, role }),
      listUsers(client, { offset, limit, search, role }),
    ]);

    reply.header('x-total-count', String(total));
    return {
      code: 200,
      data: {
        items: items.map(publicUser),
        total,
        page,
        limit,
      },
    };
  });

  // 单用户详情
  app.get('/api/admin/users/:id', { preHandler: allowAdmin }, async (req, reply) => {
    const id = parseInt(req.params.id, 10);
    const user = await findUserById(client, id);
    if (!user) {
      return reply.code(404).send({ code: 404, msg: '用户不存在' });
    }
    return { code: 200, data: publicUser(user) };
  });

  // 管理员创建用户
  app.post('/api/admin/users', { preHandler: allowAdmin }, async (req, reply) => {
    const { username, password, nickname, role = 'user' } = req.body || {};
    const name = String(username || '').trim();

    if (!USERNAME_RE.test(name)) {
      return reply.code(400).send({ code: 400, msg: '用户名需 2-20 位（字母/数字/下划线/中文）' });
    }
    if (typeof password !== 'string' || password.length < 5 || password.length > 64) {
      return reply.code(400).send({ code: 400, msg: '密码需 5-64 位' });
    }
    if (nickname != null && String(nickname).length > 24) {
      return reply.code(400).send({ code: 400, msg: '昵称最长 24 字' });
    }
    if (role !== 'admin' && role !== 'user') {
      return reply.code(400).send({ code: 400, msg: '角色必须是 admin 或 user' });
    }
    if (await findUserByUsername(client, name)) {
      return reply.code(409).send({ code: 409, msg: '用户名已存在' });
    }

    const salt = newSalt();
    const user = await createUser(client, {
      username: name,
      passwordHash: await hashPassword(password, salt),
      salt,
      nickname: nickname ? String(nickname) : undefined,
      role,
      nowMs: Date.now(),
    });

    return { code: 200, data: publicUser(user) };
  });

  // 管理员更新用户
  app.put('/api/admin/users/:id', { preHandler: allowAdmin }, async (req, reply) => {
    const id = parseInt(req.params.id, 10);
    const user = await findUserById(client, id);
    if (!user) {
      return reply.code(404).send({ code: 404, msg: '用户不存在' });
    }

    const { nickname, avatar, role, password } = req.body || {};
    const updateData = { nowMs: Date.now() };

    if (nickname !== undefined) {
      if (nickname !== null && String(nickname).length > 24) {
        return reply.code(400).send({ code: 400, msg: '昵称最长 24 字' });
      }
      updateData.nickname = nickname != null ? String(nickname) : '';
    }

    if (avatar !== undefined) {
      updateData.avatar = avatar != null ? String(avatar) : '';
    }

    if (role !== undefined) {
      if (role !== 'admin' && role !== 'user') {
        return reply.code(400).send({ code: 400, msg: '角色必须是 admin 或 user' });
      }
      // 防御：如果是修改当前账号且要把自己从 admin 降为 user，检查是否还有其他管理员
      if (id === req.user.id && role !== 'admin') {
        const adminCount = await countUsers(client, { role: 'admin' });
        if (adminCount <= 1) {
          return reply.code(400).send({ code: 400, msg: '系统至少需要保留一个管理员账号' });
        }
      }
      updateData.role = role;
    }

    if (password !== undefined && password !== '') {
      if (typeof password !== 'string' || password.length < 5 || password.length > 64) {
        return reply.code(400).send({ code: 400, msg: '密码需 5-64 位' });
      }
      const salt = newSalt();
      updateData.salt = salt;
      updateData.passwordHash = await hashPassword(password, salt);
      // 重置他人密码后注销其全部会话
      if (id !== req.user.id) {
        await revokeAllSessions(client, id);
      }
    }

    const updated = await adminUpdateUser(client, id, updateData);
    return { code: 200, data: publicUser(updated) };
  });

  // 管理员删除用户
  app.delete('/api/admin/users/:id', { preHandler: allowAdmin }, async (req, reply) => {
    const id = parseInt(req.params.id, 10);
    if (id === req.user.id) {
      return reply.code(400).send({ code: 400, msg: '不能删除当前登录的账号' });
    }

    const user = await findUserById(client, id);
    if (!user) {
      return reply.code(404).send({ code: 404, msg: '用户不存在' });
    }

    // 若删除管理员账号，检查是否是最后一个
    if (user.role === 'admin') {
      const adminCount = await countUsers(client, { role: 'admin' });
      if (adminCount <= 1) {
        return reply.code(400).send({ code: 400, msg: '系统至少需要保留一个管理员账号' });
      }
    }

    await adminDeleteUser(client, id);
    return { code: 200, msg: '用户已删除' };
  });
}
