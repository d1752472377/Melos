/**
 * 端到端冒烟测试：用 Fastify app.inject()（不占端口、不联网）验证核心链路。
 * 运行：npm run smoke
 */
import assert from 'node:assert/strict';
import { tmpdir } from 'node:os';
import { join } from 'node:path';
import { rmSync } from 'node:fs';

// 必须在动态 import 前设好环境变量，配置模块才会读到
process.env.DB_URL = `file:${join(tmpdir(), `melos-smoke-${Date.now()}-${Math.random().toString(36).slice(2)}.db`)}`;
process.env.RATE_LIMIT_DISABLED = '1';
process.env.SESSION_TTL_DAYS = '90';

const { buildApp } = await import('../src/app.js');

const app = await buildApp();
const bearer = (t) => ({ authorization: `Bearer ${t}` });
let passed = 0;

function ok(name, cond, extra = '') {
  assert.ok(cond, `${name}${extra ? ` (${extra})` : ''}`);
  passed++;
  console.log(`  ✓ ${name}`);
}

async function j(method, url, { token, payload } = {}) {
  const res = await app.inject({
    method,
    url,
    headers: token ? bearer(token) : {},
    payload: payload !== undefined ? payload : undefined,
  });
  return res;
}

const song = (id) => ({ id, title: `歌${id}`, artist: '测试歌手', album: '测试专辑', artwork: '', platform: 'netease' });

try {
  console.log('== 健康检查 ==');
  const root = await j('GET', '/');
  ok('GET / 返回服务信息', root.statusCode === 200 && root.json().name === 'melos-backend');

  console.log('== 注册 / 登录 / 我 ==');
  const reg = await j('POST', '/api/auth/register', { payload: { username: 'alice', password: 'secret123', deviceName: 'phone-a' } });
  ok('注册成功并返回 token', reg.statusCode === 200 && reg.json().data.token);
  const tokenA = reg.json().data.token;

  const dup = await j('POST', '/api/auth/register', { payload: { username: 'alice', password: 'secret123' } });
  ok('重复注册返回 409', dup.statusCode === 409);

  const bad = await j('POST', '/api/auth/login', { payload: { username: 'alice', password: 'wrongpass' } });
  ok('错误密码返回 401', bad.statusCode === 401);

  const me = await j('GET', '/api/me', { token: tokenA });
  ok('带 token 取 /me', me.statusCode === 200 && me.json().data.username === 'alice');

  console.log('== 同步：push 与增量 pull ==');
  let push = await j('POST', '/api/sync/push', {
    token: tokenA,
    payload: { favorites: { upsert: [song('1'), song('2')] } },
  });
  ok('push 收藏返回 version=1', push.statusCode === 200 && push.json().data.version === 1);
  let version = push.json().data.version;

  let pull = await j('GET', `/api/sync/pull?since=0&eventSince=0`, { token: tokenA });
  const full = pull.json().data;
  ok('全量 pull 返回 2 首收藏', full.favorites.length === 2 && full.version === version);

  push = await j('POST', '/api/sync/push', {
    token: tokenA,
    payload: {
      favorites: { delete: [{ platform: 'netease', id: '2' }] },
      playEvents: [{ eventId: 'evt-1', platform: 'netease', id: '2', title: '歌2', artist: '测试歌手', listenedMs: 120000, durationMs: 269000, listenedAtMs: Date.now() }],
    },
  });
  ok('push 删除(墓碑) + 上报听歌事件', push.statusCode === 200 && push.json().data.version === 2 && push.json().data.acceptedEvents === 1);
  version = push.json().data.version;

  pull = await j('GET', `/api/sync/pull?since=1&eventSince=0`, { token: tokenA });
  const inc = pull.json().data;
  ok('增量 pull 返回墓碑(删除)', inc.favorites.length === 1 && inc.favorites[0].id === '2' && inc.favorites[0].deleted === true && inc.favorites[0].song === null);
  ok('增量 pull 返回听歌事件', inc.playEvents.length === 1 && inc.playEvents[0].eventId === 'evt-1');

  let eventSince = inc.nextEventId;
  const dedupe = await j('POST', '/api/sync/push', {
    token: tokenA,
    payload: { playEvents: [{ eventId: 'evt-1', platform: 'netease', id: '2', title: '歌2', artist: '', listenedMs: 5000, listenedAtMs: Date.now() }] },
  });
  ok('听歌事件按 eventId 幂等去重', dedupe.json().data.acceptedEvents === 0);

  console.log('== 多端 LWW：后到者覆盖 ==');
  // 设备 B 复活 歌2 -> 设备 A 晚一步又删 -> 最终以最后到达的删除为准
  await j('POST', '/api/sync/push', { token: tokenA, payload: { favorites: { upsert: [song('2')] } } });   // 复活
  push = await j('POST', '/api/sync/push', { token: tokenA, payload: { favorites: { delete: [{ platform: 'netease', id: '2' }] } } }); // 晚到的删除
  version = push.json().data.version;
  pull = await j('GET', `/api/sync/pull?since=${version - 1}`, { token: tokenA });
  ok('后到的删除获胜', pull.json().data.favorites.some((f) => f.id === '2' && f.deleted === true));

  console.log('== 歌单与删除级联墓碑 ==');
  push = await j('POST', '/api/sync/push', {
    token: tokenA,
    payload: {
      playlists: { upsert: [{ id: 'pl_1', name: '我的歌单' }] },
      playlistSongs: { upsert: [{ playlistId: 'pl_1', position: 0, song: song('9') }] },
    },
  });
  version = push.json().data.version;
  push = await j('POST', '/api/sync/push', { token: tokenA, payload: { playlists: { delete: [{ id: 'pl_1' }] } } });
  version = push.json().data.version;
  pull = await j('GET', `/api/sync/pull?since=${version - 1}`, { token: tokenA });
  const pl = pull.json().data;
  ok('歌单删除传播为墓碑', pl.playlists.some((p) => p.id === 'pl_1' && p.deleted === true));
  ok('歌单下歌曲同步墓碑化', pl.playlistSongs.every((s) => s.deleted === true));

  const newDevicePull = await j('GET', `/api/sync/pull?since=0&eventSince=0`, { token: tokenA });
  const ndData = newDevicePull.json().data;
  ok('新设备全量拉取(since=0)不包含已删除的墓碑记录', !ndData.playlists.some((p) => p.deleted) && !ndData.playlistSongs.some((s) => s.deleted));


  console.log('== 登出失效 / 多端独立会话 ==');
  const out = await j('POST', '/api/auth/logout', { token: tokenA });
  ok('登出返回 200', out.statusCode === 200);

  const meAfter = await j('GET', '/api/me', { token: tokenA });
  ok('登出后旧 token 立即失效(401)', meAfter.statusCode === 401);

  const login = await j('POST', '/api/auth/login', { payload: { username: 'alice', password: 'secret123', deviceName: 'tablet-b' } });
  ok('再次登录签发新 token', login.statusCode === 200);
  const tokenB = login.json().data.token;

  const meB = await j('GET', '/api/me', { token: tokenB });
  ok('新 token 可用', meB.statusCode === 200);
  ok('旧 token 仍失效', (await j('GET', '/api/me', { token: tokenA })).statusCode === 401);

  const sess = await j('GET', '/api/me/sessions', { token: tokenB });
  ok('设备列表只剩当前会话', sess.statusCode === 200 && sess.json().data.length === 1 && sess.json().data[0].current === true);

  const unauth = await j('GET', '/api/sync/pull?since=0');
  ok('无 token 拉取被拒(401)', unauth.statusCode === 401);

  console.log('== 内置管理员与权限控制 ==');
  const adminLogin = await j('POST', '/api/auth/login', { payload: { username: 'admin', password: '12346' } });
  ok('默认管理员账号 admin/12346 登录成功', adminLogin.statusCode === 200 && adminLogin.json().data.token);
  const adminToken = adminLogin.json().data.token;
  const adminMe = await j('GET', '/api/me', { token: adminToken });
  ok('管理员角色为 admin', adminMe.statusCode === 200 && adminMe.json().data.role === 'admin');

  // 普通用户权限拦截
  const normalUserForbidden = await j('GET', '/api/admin/users', { token: tokenB });
  ok('普通用户请求管理员接口被拦截 (403)', normalUserForbidden.statusCode === 403);

  // 管理员查看用户列表
  const adminList = await j('GET', '/api/admin/users', { token: adminToken });
  ok('管理员获取用户列表成功 (200)', adminList.statusCode === 200 && adminList.json().data.total >= 2);

  // 管理员创建用户
  const createdBob = await j('POST', '/api/admin/users', {
    token: adminToken,
    payload: { username: 'bob_test', password: 'bobpassword', nickname: '鲍勃', role: 'user' },
  });
  ok('管理员创建用户成功', createdBob.statusCode === 200 && createdBob.json().data.username === 'bob_test');
  const bobId = createdBob.json().data.id;

  // 管理员更新用户
  const updatedBob = await j('PUT', `/api/admin/users/${bobId}`, {
    token: adminToken,
    payload: { nickname: '鲍勃改名', role: 'admin' },
  });
  ok('管理员更新用户角色与昵称成功', updatedBob.statusCode === 200 && updatedBob.json().data.nickname === '鲍勃改名' && updatedBob.json().data.role === 'admin');

  // 管理员删除用户
  const deleteBob = await j('DELETE', `/api/admin/users/${bobId}`, { token: adminToken });
  ok('管理员删除用户成功', deleteBob.statusCode === 200);

  // 管理员无法删除自己
  const deleteSelf = await j('DELETE', `/api/admin/users/${adminMe.json().data.id}`, { token: adminToken });
  ok('管理员删除自己被阻止 (400)', deleteSelf.statusCode === 400);

  console.log('== 修改账号密码 ==');
  // 用错误旧密码修改
  const wrongOldPass = await j('PUT', '/api/me/password', {
    token: adminToken,
    payload: { oldPassword: 'wrong', newPassword: 'newpassword999' },
  });
  ok('旧密码错误拒绝修改 (400)', wrongOldPass.statusCode === 400);

  // 用正确旧密码修改
  const changePass = await j('PUT', '/api/me/password', {
    token: adminToken,
    payload: { oldPassword: '12346', newPassword: 'newpassword999' },
  });
  ok('密码修改成功 (200)', changePass.statusCode === 200);

  // 旧密码登录失败
  const oldLoginFail = await j('POST', '/api/auth/login', { payload: { username: 'admin', password: '12346' } });
  ok('修改后旧密码登录失败 (401)', oldLoginFail.statusCode === 401);

  // 新密码登录成功
  const newLoginSuccess = await j('POST', '/api/auth/login', { payload: { username: 'admin', password: 'newpassword999' } });
  ok('修改后新密码登录成功 (200)', newLoginSuccess.statusCode === 200 && newLoginSuccess.json().data.token);

  console.log(`\n全部通过，共 ${passed} 项断言。`);
} finally {
  await app.close();
  try { rmSync(process.env.DB_URL.replace(/^file:/, '').replace(/[\\/]/g, '/'), { force: true }); } catch {}
  try {
    // Windows 路径兼容：删除 file: 前缀后按原生路径删除
    const p = process.env.DB_URL.startsWith('file:') ? process.env.DB_URL.slice(5) : process.env.DB_URL;
    rmSync(p, { force: true });
    rmSync(`${p}-wal`, { force: true });
    rmSync(`${p}-shm`, { force: true });
  } catch {}
}