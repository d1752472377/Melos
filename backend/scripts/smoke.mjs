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