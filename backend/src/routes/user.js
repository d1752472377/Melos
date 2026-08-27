import crypto from 'node:crypto';
import {
  getUserByUsername,
  insertUser,
  updateUserProfile,
  publicUser,
  replaceFavorites,
  listFavorites,
  mergeHistory,
  listHistory,
  createPlaylist,
  listPlaylists,
  getPlaylist,
  updatePlaylistMeta,
  deletePlaylist,
  replacePlaylistSongs,
  addPlaylistSongs,
  removePlaylistSong,
  listPlaylistSongs,
  insertPlayEvents,
  getUserStats,
} from '../lib/db.js';
import { hashPassword, createUserToken, validateRegisterInput, requireUser } from '../lib/userAuth.js';
import { isLoginLocked, recordLoginFailure, clearLoginFailures } from '../lib/auth.js';

/**
 * 用户体系 API：
 *   POST /api/user/register                注册
 *   POST /api/user/login                   登录
 *   GET  /api/user/me                      当前用户
 *   PUT  /api/user/profile                 改昵称/头像
 *   GET  /api/user/favorites               云端收藏
 *   PUT  /api/user/favorites               整表同步收藏
 *   GET  /api/user/history                 云端最近播放
 *   PUT  /api/user/history                 整表同步历史
 *   GET  /api/user/playlists               我的歌单
 *   POST /api/user/playlists               创建歌单
 *   PUT  /api/user/playlists/:id           重命名歌单
 *   DELETE /api/user/playlists/:id         删除歌单
 *   GET  /api/user/playlists/:id/songs     歌单歌曲
 *   PUT  /api/user/playlists/:id/songs     整表替换歌单歌曲
 *   POST /api/user/playlists/:id/songs     追加歌曲
 *   DELETE /api/user/playlists/:id/songs   移除歌曲
 *   POST /api/user/stats/report            上报听歌明细
 *   GET  /api/user/stats                   统计汇总
 */
export function registerUserRoutes(app, { config, db }) {
  app.post('/api/user/register', async (req, reply) => {
    const { username, password, nickname } = req.body || {};
    const err = validateRegisterInput(username, password, nickname);
    if (err) return reply.code(400).send({ code: 400, msg: err });
    if (await getUserByUsername(db, String(username))) {
      return reply.code(409).send({ code: 409, msg: '用户名已存在' });
    }
    const salt = crypto.randomBytes(16).toString('hex');
    const user = await insertUser(db, {
      username: String(username),
      passwordHash: await hashPassword(password, salt),
      salt,
      nickname: nickname ? String(nickname) : String(username),
    });
    const token = createUserToken(user.id, user.username, config.encryptionKey);
    return { code: 200, data: { token, user: publicUser(user) } };
  });

  app.post('/api/user/login', async (req, reply) => {
    const { username, password } = req.body || {};
    const name = String(username || '');
    // 防在线爆破：连续失败锁定
    if (isLoginLocked(name)) {
      return reply.code(429).send({ code: 429, msg: '失败次数过多，账号已临时锁定，请 15 分钟后再试' });
    }
    const user = await getUserByUsername(db, name);
    if (!user) {
      recordLoginFailure(name);
      return reply.code(401).send({ code: 401, msg: '用户名或密码错误' });
    }
    const hash = await hashPassword(String(password || ''), user.salt);
    const a = Buffer.from(hash);
    const b = Buffer.from(user.password_hash);
    if (a.length !== b.length || !crypto.timingSafeEqual(a, b)) {
      recordLoginFailure(name);
      return reply.code(401).send({ code: 401, msg: '用户名或密码错误' });
    }
    clearLoginFailures(name);
    const token = createUserToken(user.id, user.username, config.encryptionKey);
    return { code: 200, data: { token, user: publicUser(user) } };
  });

  const auth = requireUser(config, db);

  app.get('/api/user/me', { preHandler: auth }, async (req) => ({
    code: 200,
    data: publicUser(req.userRow),
  }));

  app.put('/api/user/profile', { preHandler: auth }, async (req, reply) => {
    const { nickname, avatar } = req.body || {};
    if (nickname != null && String(nickname).length > 24) {
      return reply.code(400).send({ code: 400, msg: '昵称最长 24 字' });
    }
    const user = await updateUserProfile(db, req.userRow.id, {
      nickname: nickname != null ? String(nickname) : undefined,
      avatar: avatar != null ? String(avatar) : undefined,
    });
    return { code: 200, data: publicUser(user) };
  });

  app.get('/api/user/favorites', { preHandler: auth }, async (req) => ({
    code: 200,
    data: await listFavorites(db, req.userRow.id),
  }));

  app.put('/api/user/favorites', { preHandler: auth }, async (req) => {
    await replaceFavorites(db, req.userRow.id, req.body?.songs);
    return { code: 200, data: await listFavorites(db, req.userRow.id) };
  });

  app.get('/api/user/history', { preHandler: auth }, async (req) => ({
    code: 200,
    data: await listHistory(db, req.userRow.id),
  }));

  app.put('/api/user/history', { preHandler: auth }, async (req) => {
    // 增量合并语义：新记录置前 + 与云端去重合并，多设备互相推送不再互相清空
    const data = await mergeHistory(db, req.userRow.id, req.body?.songs);
    return { code: 200, data };
  });

  app.get('/api/user/playlists', { preHandler: auth }, async (req) => ({
    code: 200,
    data: await listPlaylists(db, req.userRow.id),
  }));

  app.post('/api/user/playlists', { preHandler: auth }, async (req, reply) => {
    const name = String(req.body?.name || '').trim();
    if (!name || name.length > 30) return reply.code(400).send({ code: 400, msg: '歌单名需 1-30 字' });
    const p = await createPlaylist(db, req.userRow.id, { name, coverUrl: req.body?.coverUrl });
    return { code: 200, data: p };
  });

  app.put('/api/user/playlists/:id', { preHandler: auth }, async (req, reply) => {
    const name = req.body?.name != null ? String(req.body.name).trim() : undefined;
    if (name !== undefined && (!name || name.length > 30)) {
      return reply.code(400).send({ code: 400, msg: '歌单名需 1-30 字' });
    }
    const p = await updatePlaylistMeta(db, req.userRow.id, req.params.id, {
      name: name || undefined,
      coverUrl: req.body?.coverUrl != null ? String(req.body.coverUrl) : undefined,
    });
    if (!p) return reply.code(404).send({ code: 404, msg: '歌单不存在' });
    return { code: 200, data: p };
  });

  app.delete('/api/user/playlists/:id', { preHandler: auth }, async (req, reply) => {
    const r = await deletePlaylist(db, req.userRow.id, req.params.id);
    if (!r.changes) return reply.code(404).send({ code: 404, msg: '歌单不存在' });
    return { code: 200, msg: '已删除' };
  });

  app.get('/api/user/playlists/:id/songs', { preHandler: auth }, async (req, reply) => {
    const songs = await listPlaylistSongs(db, req.userRow.id, req.params.id);
    if (songs === null) return reply.code(404).send({ code: 404, msg: '歌单不存在' });
    return { code: 200, data: songs };
  });

  app.put('/api/user/playlists/:id/songs', { preHandler: auth }, async (req, reply) => {
    const p = await replacePlaylistSongs(db, req.userRow.id, req.params.id, req.body?.songs);
    if (!p) return reply.code(404).send({ code: 404, msg: '歌单不存在' });
    return { code: 200, data: p };
  });

  app.post('/api/user/playlists/:id/songs', { preHandler: auth }, async (req, reply) => {
    const p = await addPlaylistSongs(db, req.userRow.id, req.params.id, req.body?.songs);
    if (!p) return reply.code(404).send({ code: 404, msg: '歌单不存在' });
    return { code: 200, data: p };
  });

  app.delete('/api/user/playlists/:id/songs', { preHandler: auth }, async (req, reply) => {
    const { platform, id } = req.query;
    if (!platform || !id) return reply.code(400).send({ code: 400, msg: '需要 platform / id' });
    const p = await removePlaylistSong(db, req.userRow.id, req.params.id, String(platform), String(id));
    if (!p) return reply.code(404).send({ code: 404, msg: '歌单不存在' });
    return { code: 200, data: p };
  });

  app.post('/api/user/stats/report', { preHandler: auth }, async (req) => {
    const n = await insertPlayEvents(db, req.userRow.id, req.body?.events);
    return { code: 200, data: { accepted: n } };
  });

  app.get('/api/user/stats', { preHandler: auth }, async (req) => ({
    code: 200,
    data: await getUserStats(db, req.userRow.id),
  }));
}
