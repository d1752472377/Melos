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
  const meData = me.json().data;
  ok('/me 返回注册日期 createdAt(毫秒) 且与 created_at 同值',
    Number.isFinite(meData.createdAt) && meData.createdAt > 0 && meData.createdAt === meData.created_at);
  ok('/me 初始未绑定邮箱(email 为空串)', meData.email === '');

  console.log('== 邮箱绑定（仅登记，无邮件验证） ==');
  const bind = await j('PUT', '/api/me', { token: tokenA, payload: { email: '  Alice@Example.COM  ' } });
  ok('绑定邮箱成功且归一化(trim+小写)', bind.statusCode === 200 && bind.json().data.email === 'alice@example.com');

  const badEmail = await j('PUT', '/api/me', { token: tokenA, payload: { email: 'not-an-email' } });
  ok('非法邮箱格式返回 400', badEmail.statusCode === 400);
  const longEmail = await j('PUT', '/api/me', { token: tokenA, payload: { email: 'a'.repeat(118) + '@x.io' } });
  ok('超长邮箱(>120)返回 400', longEmail.statusCode === 400);

  const emailKept = await j('PUT', '/api/me', { token: tokenA, payload: { nickname: '爱丽丝' } });
  ok('不传 email 时邮箱不受影响', emailKept.statusCode === 200 && emailKept.json().data.email === 'alice@example.com');

  const regDupEmail = await j('POST', '/api/auth/register', { payload: { username: 'mallory', password: 'secret999', email: 'alice@example.com' } });
  ok('注册时占用他人邮箱返回 409', regDupEmail.statusCode === 409);

  const regCarol = await j('POST', '/api/auth/register', { payload: { username: 'carol', password: 'secret999' } });
  const tokenC = regCarol.json().data.token;
  const carolId = regCarol.json().data.user.id;
  const steal = await j('PUT', '/api/me', { token: tokenC, payload: { email: 'alice@example.com' } });
  ok('绑定他人已占用的邮箱返回 409', steal.statusCode === 409 && String(steal.json().msg).includes('其它账号'));

  const regWithEmail = await j('POST', '/api/auth/register', { payload: { username: 'dave', password: 'secret999', email: 'Dave@Example.com' } });
  ok('注册时直接绑定邮箱成功(归一化)', regWithEmail.statusCode === 200 && regWithEmail.json().data.user.email === 'dave@example.com');

  const unbind = await j('PUT', '/api/me', { token: tokenA, payload: { email: '' } });
  ok('传空串解绑邮箱', unbind.statusCode === 200 && unbind.json().data.email === '');

  const carolBind = await j('PUT', '/api/me', { token: tokenC, payload: { email: 'alice@example.com' } });
  ok('解绑后该邮箱可被他人绑定', carolBind.statusCode === 200 && carolBind.json().data.email === 'alice@example.com');

  const rebind = await j('PUT', '/api/me', { token: tokenC, payload: { email: 'alice@example.com' } });
  ok('重复提交自己的邮箱不算冲突', rebind.statusCode === 200);

  const meC = await j('GET', '/api/me', { token: tokenC });
  const meCData = meC.json().data;
  ok('GET /api/me 返回 email/username/createdAt 三字段',
    meCData.email === 'alice@example.com' && meCData.username === 'carol' && Number.isFinite(meCData.createdAt));

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

  console.log('== 歌单类型 kind：自建(mine) 与 收藏的网络歌单(fav) ==');
  // 客户端把两个来源的歌单存在同一张表、用 kind 区分，并**用「kind + id」组本地记录键**：
  // 因此服务端必须原样存回 kind，且墓碑也要带——丢了 kind，收藏歌单的删除会落到自建歌单上。
  push = await j('POST', '/api/sync/push', {
    token: tokenA,
    payload: {
      playlists: {
        upsert: [
          { id: 'local_mine_1', name: '通勤', kind: 'mine' },
          { id: '777', name: '收藏的网易歌单', kind: 'fav' },
          { id: 'old_client', name: '老客户端没传 kind' },
          { id: 'weird', name: '拼错的 kind', kind: 'FAVOURITE' },
        ],
      },
    },
  });
  version = push.json().data.version;
  pull = await j('GET', '/api/sync/pull?since=0&eventSince=0', { token: tokenA });
  const plById = Object.fromEntries(pull.json().data.playlists.map((p) => [p.id, p]));
  ok('kind=mine 原样返回', plById.local_mine_1?.kind === 'mine');
  ok('kind=fav 原样返回（客户端靠它区分收藏的歌单）', plById['777']?.kind === 'fav');
  ok('缺省 kind 按 mine 处理（兼容老客户端）', plById.old_client?.kind === 'mine');
  ok('非法 kind 归一化为 mine（避免两端键不一致）', plById.weird?.kind === 'mine');

  push = await j('POST', '/api/sync/push', { token: tokenA, payload: { playlists: { delete: [{ id: '777', kind: 'fav' }] } } });
  version = push.json().data.version;
  pull = await j('GET', `/api/sync/pull?since=${version - 1}&eventSince=0`, { token: tokenA });
  const deadFavPl = pull.json().data.playlists.find((p) => p.id === '777');
  ok('歌单墓碑保留 kind（客户端按 kind+id 组键）', deadFavPl?.deleted === true && deadFavPl?.kind === 'fav');
  pull = await j('GET', `/api/sync/pull?since=${version}&eventSince=0`, { token: tokenA });
  ok('拉取不回传 kind=FAVOURITE 之类脏值', pull.json().data.playlists.every((p) => p.kind === 'mine' || p.kind === 'fav'));

  console.log('== 探活接口 /api/health（客户端「测试连接」用，无需鉴权） ==');
  const healthRes = await j('GET', '/api/health');
  const healthJson = healthRes.json();
  ok('未登录也能访问 /api/health', healthRes.statusCode === 200);
  ok('/api/health 返回 {code,msg,data} 信封（客户端 ApiResponse 只认这个形状）',
    healthJson.code === 200 && typeof healthJson.data === 'object' && healthJson.data !== null);
  ok('/api/health data 含 ok/time/version', healthJson.data.ok === true && Number.isFinite(healthJson.data.time) && Boolean(healthJson.data.version));

  // ==================== 新增数据集：音源脚本 + 下载记录 ====================
  // 与上面 favorites/playlists 同款语义：LWW 覆盖 + 墓碑传播 + version(sync_seq) 游标。
  console.log('== 音源脚本 scripts：push / pull / code 原文 / enabled 布尔 ==');
  // code 刻意做得长（两万+ 字符）且含换行，验证「脚本源码原样回传」不被截断/不被转义破坏
  const scriptCode = `${'// 音源脚本骨架\n'.repeat(2500)}module.exports = { search: async (kw) => [] };`;
  push = await j('POST', '/api/sync/push', {
    token: tokenA,
    payload: {
      scripts: {
        upsert: [
          { id: 'sc_src', name: '我的音源', description: '测试脚本', code: scriptCode, enabled: true },
          { id: 'sc_off', name: '停用脚本', code: 'x', enabled: false },
          { id: 'sc_default', name: '缺省启用', code: 'y' }, // 不传 enabled：默认按启用入库
        ],
      },
    },
  });
  ok('脚本 push 成功', push.statusCode === 200);
  version = push.json().data.version;

  pull = await j('GET', '/api/sync/pull?since=0&eventSince=0', { token: tokenA });
  const byId = Object.fromEntries(pull.json().data.scripts.map((s) => [s.id, s]));
  ok('pull 拿到 code 原文（长文本不截断）', byId.sc_src?.code === scriptCode);
  ok('脚本 name/description 原样回传', byId.sc_src?.name === '我的音源' && byId.sc_src?.description === '测试脚本');
  ok('enabled=true 出参为布尔 true', byId.sc_src?.enabled === true);
  ok('enabled=false 落库 0 并出参布尔 false', byId.sc_off?.enabled === false);
  ok('未传 enabled 时默认启用(true)', byId.sc_default?.enabled === true);
  ok('存活脚本 deleted=false 且带 updatedAtMs', byId.sc_src?.deleted === false && Number.isFinite(byId.sc_src?.updatedAtMs));

  console.log('== 音源脚本：单脚本体积上限（超限整批 413 + 可行动的错误信息） ==');
  // 整个请求体另有 4MB bodyLimit，但顶爆它时客户端只拿到笼统的 413，
  // 不知道该改什么；这里保证超限脚本被点名、且不影响其它集合的语义判断。
  const oversized = await j('POST', '/api/sync/push', {
    token: tokenA,
    payload: { scripts: { upsert: [{ id: 'sc_huge', name: '巨型脚本', code: 'x'.repeat(1024 * 1024 + 1) }] } },
  });
  ok('超过 1MB 的脚本返回 413', oversized.statusCode === 413, `status=${oversized.statusCode}`);
  ok('413 的错误信息点明是哪个脚本', /音源脚本过大/.test(oversized.json().msg) && oversized.json().msg.includes('sc_huge'));
  const justRight = await j('POST', '/api/sync/push', {
    token: tokenA,
    payload: { scripts: { upsert: [{ id: 'sc_edge', name: '边界脚本', code: 'x'.repeat(1024 * 1024) }] } },
  });
  ok('恰好 1MB 的脚本仍可入库', justRight.statusCode === 200);
  // 立刻删掉这条 1MB 的边界记录，并把 version 归位到当前 head：
  // 后面几节都靠 `version` 变量推算"下一次 push 是否恰好 +1"，本节点了版本号却不更新它，
  // 会让后续断言莫名失败（这正是本节第一次写错时踩到的坑）。
  const edgeCleanup = await j('POST', '/api/sync/push', { token: tokenA, payload: { scripts: { delete: [{ id: 'sc_edge' }] } } });
  version = edgeCleanup.json().data.version;

  console.log('== 音源脚本：同 id 覆盖 + version 游标只回一次 ==');
  const scPrev = version;
  push = await j('POST', '/api/sync/push', {
    token: tokenA,
    payload: { scripts: { upsert: [{ id: 'sc_src', name: '改名了', description: '', code: 'const q = 1;', enabled: false }] } },
  });
  const scV2 = push.json().data.version;
  ok('同 id 二次 push 使 version 递增', scV2 === scPrev + 1);
  pull = await j('GET', `/api/sync/pull?since=${scPrev}&eventSince=0`, { token: tokenA });
  const scDelta = pull.json().data.scripts;
  ok('增量拉取只回一条被覆盖的脚本(update_seq 递增)', scDelta.length === 1 && scDelta[0].id === 'sc_src');
  ok('覆盖后旧值不再存在', scDelta[0].name === '改名了' && scDelta[0].code === 'const q = 1;' && scDelta[0].enabled === false);
  pull = await j('GET', `/api/sync/pull?since=${scV2}&eventSince=0`, { token: tokenA });
  ok('游标推进后同一变更不再重复下发', pull.json().data.scripts.length === 0);
  pull = await j('GET', '/api/sync/pull?since=0&eventSince=0', { token: tokenA });
  const scRows = pull.json().data.scripts.filter((s) => s.id === 'sc_src');
  ok('同 id 覆盖是原地更新(不留第二行)', scRows.length === 1 && scRows[0].name === '改名了');

  console.log('== 音源脚本：删除墓碑传播（增量可见 / 全量不可见）==');
  push = await j('POST', '/api/sync/push', { token: tokenA, payload: { scripts: { delete: [{ id: 'sc_src' }] } } });
  const scV3 = push.json().data.version;
  pull = await j('GET', `/api/sync/pull?since=${scV3 - 1}&eventSince=0`, { token: tokenA });
  const scTomb = pull.json().data.scripts.find((s) => s.id === 'sc_src');
  ok('增量拉取可见脚本墓碑 deleted=true', !!scTomb && scTomb.deleted === true);
  ok('墓碑不下发 code 载荷(省带宽，键仍齐全)', scTomb.code === '' && scTomb.enabled === false && scTomb.name === '');
  pull = await j('GET', '/api/sync/pull?since=0&eventSince=0', { token: tokenA });
  ok('全量拉取不含已删脚本', !pull.json().data.scripts.some((s) => s.id === 'sc_src'));
  version = scV3;

  console.log('== 下载记录 downloads：设备维度进主键，多设备互不覆盖 ==');
  const dl = (deviceId, deviceName, extra = {}) => ({
    platform: 'netease', id: 'd_song', deviceId, deviceName,
    title: '下载的歌', artist: '测试歌手', album: '测试专辑', artwork: 'https://art/1.jpg',
    quality: '320k', sizeBytes: 8123456, downloadedAtMs: 1700000000000, ...extra,
  });
  push = await j('POST', '/api/sync/push', {
    token: tokenA,
    payload: { downloads: { upsert: [dl('dev-phone', 'Pixel 8'), dl('dev-tablet', '小米平板')] } },
  });
  ok('同一首歌在两台设备各存一条(push 200)', push.statusCode === 200);
  const dlV1 = push.json().data.version;
  pull = await j('GET', '/api/sync/pull?since=0&eventSince=0', { token: tokenA });
  const dlRows = pull.json().data.downloads.filter((d) => d.id === 'd_song');
  ok('pull 返回两台设备的下载记录', dlRows.length === 2);
  const phoneRow = dlRows.find((d) => d.deviceId === 'dev-phone');
  ok('字段按驼峰回传(platform/id/deviceId/deviceName/title/...)',
    phoneRow.title === '下载的歌' && phoneRow.artist === '测试歌手' && phoneRow.album === '测试专辑'
    && phoneRow.artwork === 'https://art/1.jpg' && phoneRow.quality === '320k'
    && phoneRow.sizeBytes === 8123456 && phoneRow.downloadedAtMs === 1700000000000
    && phoneRow.deviceName === 'Pixel 8' && phoneRow.deleted === false);
  const tabletRow = dlRows.find((d) => d.deviceId === 'dev-tablet');
  ok('两台设备的 deviceId 互不覆盖', !!tabletRow && tabletRow.deviceName === '小米平板');

  push = await j('POST', '/api/sync/push', { token: tokenA, payload: { downloads: { upsert: [dl('dev-phone', 'Pixel 8', { quality: 'flac' })] } } });
  const dlV2 = push.json().data.version;
  ok('设备内更新使 version 递增', dlV2 === dlV1 + 1);
  pull = await j('GET', `/api/sync/pull?since=${dlV1}&eventSince=0`, { token: tokenA });
  const dlDelta = pull.json().data.downloads;
  ok('增量只回被改的那台设备', dlDelta.length === 1 && dlDelta[0].deviceId === 'dev-phone' && dlDelta[0].quality === 'flac');

  console.log('== 下载记录：删除只影响对应 deviceId ==');
  push = await j('POST', '/api/sync/push', {
    token: tokenA,
    payload: { downloads: { delete: [{ platform: 'netease', id: 'd_song', deviceId: 'dev-phone' }] } },
  });
  const dlV3 = push.json().data.version;
  pull = await j('GET', '/api/sync/pull?since=0&eventSince=0', { token: tokenA });
  const dlAlive = pull.json().data.downloads.filter((d) => d.id === 'd_song');
  ok('手机删文件不会连带删掉平板的记录', dlAlive.length === 1 && dlAlive[0].deviceId === 'dev-tablet' && dlAlive[0].deleted === false);
  pull = await j('GET', `/api/sync/pull?since=${dlV3 - 1}&eventSince=0`, { token: tokenA });
  const dlTomb = pull.json().data.downloads.find((d) => d.deviceId === 'dev-phone');
  ok('增量拉取可见该设备维度的墓碑(主键三段齐全)',
    !!dlTomb && dlTomb.deleted === true && dlTomb.platform === 'netease' && dlTomb.id === 'd_song');
  version = dlV3;

  console.log('== 跨用户隔离：B(carol) 拉不到 A(alice) 的脚本/下载记录 ==');
  const pullOther = await j('GET', '/api/sync/pull?since=0&eventSince=0', { token: tokenC });
  const otherData = pullOther.json().data;
  ok('另一用户脚本列表为空(含 scripts 键)', Array.isArray(otherData.scripts) && otherData.scripts.length === 0);
  ok('另一用户下载记录为空(含 downloads 键)', Array.isArray(otherData.downloads) && otherData.downloads.length === 0);
  ok('另一用户也拉不到 A 的收藏/歌单', otherData.favorites.length === 0 && otherData.playlists.length === 0);

  const pushC2 = await j('POST', '/api/sync/push', {
    token: tokenC,
    payload: { scripts: { upsert: [{ id: 'sc_default', name: 'Carol的脚本', code: 'c' }] } },
  });
  ok('不同用户可复用同一 script_id(主键含 user_id)', pushC2.statusCode === 200);
  pull = await j('GET', '/api/sync/pull?since=0&eventSince=0', { token: tokenA });
  ok('B 推同名脚本不会覆盖 A 的那条', pull.json().data.scripts.find((s) => s.id === 'sc_default')?.name === '缺省启用');

  console.log('== 缺必填键的脏数据整条跳过（不写半条、不报 500）==');
  const dirtyBefore = version;
  const dirty = await j('POST', '/api/sync/push', {
    token: tokenA,
    payload: {
      scripts: {
        upsert: [
          { name: '没有 id 的脚本', code: 'bad' },
          null,
          { id: 'sc_good', name: '合法脚本', code: 'good' },
        ],
        delete: [{}], // 缺 id：不能产生 id='' 的墓碑
      },
      downloads: {
        upsert: [
          { platform: 'netease', id: 'no_device', title: '缺 deviceId' },
          { platform: 'netease', deviceId: 'dev-x', title: '缺歌曲 id' },
          dl('dev-phone2', '第二台手机'),
        ],
        // 缺 deviceId：绝不允许退化成「删掉这首歌在所有设备上的记录」
        delete: [{ platform: 'netease', id: 'd_song' }],
      },
    },
  });
  ok('脏数据 push 返回 200（不是 500）', dirty.statusCode === 200);
  const dirtyAfter = dirty.json().data.version;
  pull = await j('GET', `/api/sync/pull?since=${dirtyBefore}&eventSince=0`, { token: tokenA });
  const dirtyScripts = pull.json().data.scripts;
  const dirtyDownloads = pull.json().data.downloads;
  ok('缺 id / null 的脚本被跳过，同批合法脚本照常写入',
    dirtyScripts.length === 1 && dirtyScripts[0].id === 'sc_good' && dirtyScripts[0].code === 'good');
  ok('缺 deviceId / 缺歌曲 id 的下载被跳过，同批合法记录照常写入',
    dirtyDownloads.length === 1 && dirtyDownloads[0].deviceId === 'dev-phone2');
  ok('缺键的 delete 指令被忽略(不产生任何墓碑)',
    dirtyScripts.every((s) => !s.deleted) && dirtyDownloads.every((d) => !d.deleted));
  pull = await j('GET', '/api/sync/pull?since=0&eventSince=0', { token: tokenA });
  const aliveScriptIds = pull.json().data.scripts.map((s) => s.id).sort();
  ok('全量脚本列表只剩未删记录', JSON.stringify(aliveScriptIds) === JSON.stringify(['sc_default', 'sc_good', 'sc_off']));
  const aliveDlDevices = pull.json().data.downloads.map((d) => d.deviceId).sort();
  ok('全量下载列表只剩存活的设备行', JSON.stringify(aliveDlDevices) === JSON.stringify(['dev-phone2', 'dev-tablet']));

  console.log('== 数字/布尔字段归一化（脏值不落库成 NaN / 负数）==');
  push = await j('POST', '/api/sync/push', {
    token: tokenA,
    payload: {
      scripts: { upsert: [{ id: 'sc_norm', name: '归一化', code: '', enabled: '0' }] },
      downloads: { upsert: [{ platform: 'netease', id: 'd_norm', deviceId: 'dev-n', sizeBytes: -42, downloadedAtMs: 'abc', quality: null }] },
    },
  });
  ok('脏值 push 不报错', push.statusCode === 200);
  pull = await j('GET', `/api/sync/pull?since=${dirtyAfter}&eventSince=0`, { token: tokenA });
  const normScript = pull.json().data.scripts.find((s) => s.id === 'sc_norm');
  const normDl = pull.json().data.downloads.find((d) => d.id === 'd_norm');
  ok("enabled:'0'（字符串零）判为停用 false", normScript?.enabled === false);
  ok('负数/非数字/null 归一化为 0 与空串',
    normDl?.sizeBytes === 0 && normDl?.downloadedAtMs === 0 && normDl?.quality === '');

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
  ok('管理员用户列表每项均含 email 字段', adminList.json().data.items.every((u) => typeof u.email === 'string'));
  const adminCarol = await j('GET', `/api/admin/users/${carolId}`, { token: adminToken });
  ok('管理员用户详情可见绑定邮箱', adminCarol.statusCode === 200 && adminCarol.json().data.email === 'alice@example.com');

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

  // 管理员删除用户要连带清理新数据集（否则脚本源码/下载记录会永久留在库里）
  const bobLogin = await j('POST', '/api/auth/login', { payload: { username: 'bob_test', password: 'bobpassword' } });
  ok('管理员创建的账号可登录', bobLogin.statusCode === 200);
  const bobToken = bobLogin.json().data.token;
  const bobPush = await j('POST', '/api/sync/push', {
    token: bobToken,
    payload: {
      scripts: { upsert: [{ id: 'bob_sc', name: 'B的脚本', code: 'z' }] },
      downloads: { upsert: [{ platform: 'netease', id: 'bob_dl', deviceId: 'bob-dev', title: 'B的下载' }] },
    },
  });
  ok('该账号可推送脚本与下载记录', bobPush.statusCode === 200);
  const bobPull = await j('GET', '/api/sync/pull?since=0&eventSince=0', { token: bobToken });
  ok('该账号只拉到自己的一条脚本 + 一条下载',
    bobPull.json().data.scripts.length === 1 && bobPull.json().data.scripts[0].id === 'bob_sc'
    && bobPull.json().data.downloads.length === 1 && bobPull.json().data.downloads[0].deviceId === 'bob-dev');

  // 管理员删除用户
  const deleteBob = await j('DELETE', `/api/admin/users/${bobId}`, { token: adminToken });
  ok('管理员删除用户成功', deleteBob.statusCode === 200);

  // 走 HTTP 已无法再验证（账号与会话都没了），直接查库确认两张新表按 user_id 清干净
  const leftover = await app.client.execute({
    sql: 'SELECT (SELECT COUNT(*) FROM user_scripts WHERE user_id = ?) AS s, (SELECT COUNT(*) FROM downloads WHERE user_id = ?) AS d',
    args: [bobId, bobId],
  });
  ok('删除用户后 user_scripts / downloads 无残留(含墓碑)',
    Number(leftover.rows[0].s) === 0 && Number(leftover.rows[0].d) === 0);

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

  console.log('== 邮箱验证码登录与自动开户 ==');
  const badSend1 = await j('POST', '/api/auth/send-code', { payload: {} });
  ok('发送验证码不传邮箱返回 400', badSend1.statusCode === 400);

  const badSend2 = await j('POST', '/api/auth/send-code', { payload: { email: 'not-an-email' } });
  ok('发送验证码非法邮箱返回 400', badSend2.statusCode === 400);

  const { saveEmailCode } = await import('../src/lib/db.js');
  // 模拟发送了验证码
  await saveEmailCode(app.client, { email: 'eva@example.com', code: '123456', expiresAt: Date.now() + 300000 });

  const badCodeLogin = await j('POST', '/api/auth/email-login', {
    payload: { email: 'eva@example.com', code: '000000' }
  });
  ok('错误验证码登录返回 400', badCodeLogin.statusCode === 400);

  const codeLoginSuccess = await j('POST', '/api/auth/email-login', {
    payload: { email: 'eva@example.com', code: '123456', deviceName: 'phone-eva' }
  });
  ok('正确验证码登录并自动开户成功 (200)', codeLoginSuccess.statusCode === 200 && codeLoginSuccess.json().data.token);
  const evaToken = codeLoginSuccess.json().data.token;
  const evaUser = codeLoginSuccess.json().data.user;
  ok('自动开户邮箱字段正确', evaUser.email === 'eva@example.com');

  const replayCode = await j('POST', '/api/auth/email-login', {
    payload: { email: 'eva@example.com', code: '123456' }
  });
  ok('已消费的验证码再次使用返回 400', replayCode.statusCode === 400);

  // 再次发送验证码给老用户登录
  await saveEmailCode(app.client, { email: 'eva@example.com', code: '654321', expiresAt: Date.now() + 300000 });
  const reLogin = await j('POST', '/api/auth/email-login', {
    payload: { email: 'eva@example.com', code: '654321' }
  });
  ok('老用户再次验证码登录成功 (200)', reLogin.statusCode === 200);
  ok('老用户登录保持相同用户ID', reLogin.json().data.user.id === evaUser.id);

  const evaMe = await j('GET', '/api/me', { token: evaToken });
  ok('新登录用户可正常请求 /api/me', evaMe.statusCode === 200 && evaMe.json().data.email === 'eva@example.com');

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