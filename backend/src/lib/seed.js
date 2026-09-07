import { findUserByUsername, createUser } from './db.js';
import { newSalt, hashPassword } from './auth.js';

/**
 * 确保内置管理员账号存在。
 * 服务端初始化时自动检查，若 admin 不存在则创建：
 *   用户名: admin
 *   密码: 12346
 *   角色: admin
 *   昵称: 管理员
 */
export async function ensureAdminUser(client, { username = 'admin', password = '12346' } = {}) {
  const existing = await findUserByUsername(client, username);
  if (!existing) {
    const salt = newSalt();
    const passwordHash = await hashPassword(password, salt);
    await createUser(client, {
      username,
      passwordHash,
      salt,
      nickname: '管理员',
      role: 'admin',
      nowMs: Date.now(),
    });
    return true;
  }
  // 若已存在 admin 账号但角色不是 admin，确保修复为 admin
  if (existing.role !== 'admin') {
    await client.execute({
      sql: 'UPDATE users SET role = ? WHERE id = ?',
      args: ['admin', existing.id],
    });
  }
  return false;
}
