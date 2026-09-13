/**
 * 客户端 ↔ 后端 **字段级契约** 验证（wire conformance）。
 *
 * 为什么需要它（而不是再写一遍业务冒烟）：
 *   客户端的 wire 模型是 Gson 反射反序列化的 Kotlin data class。**字段名只要差一个字母，
 *   反序列化不会报错**——那个字段会静默变成 null / 默认值。可能的表现是：
 *     · 收藏列表同步回来空空如也（song 为 null 被跳过）；
 *     · 歌单同步后全被当成"自建歌单"（kind 缺失 → 默认 mine）；
 *     · 下载记录在别的设备上永远少一条（deviceId 缺失 → 主键拼不出来）。
 *   这些都是"不报错但数据不对"的问题，只能在两边字段名对齐这一层卡住。
 *
 * 本脚本把「Android 端 DTO 的字段名」当成契约来断言：
 *   1. 按客户端真实请求体形状推送（Kotlin 侧序列化出来的 JSON）；
 *   2. 拉取后逐字段检查**服务端返回的键**是否覆盖客户端声明的每一个键；
 *   3. 顺带验证墓碑（deleted）与账号隔离——它们决定客户端能否重建记录主键。
 *
 * 运行：npm run contract   （用 app.inject()，不占端口、不联网、不污染正式库）
 * 与客户端 DTO 的对应关系写在每个 assert 的注释里（文件名 MusicApi.kt 的类名）。
 */
import assert from 'node:assert/strict';
import { tmpdir } from 'node:os';
import { join } from 'node:path';

process.env.DB_URL = `file:${join(tmpdir(), `melos-contract-${Date.now()}-${Math.random().toString(36).slice(2)}.db`)}`;
process.env.RATE_LIMIT_DISABLED = '1';

const { buildApp } = await import('../src/app.js');

const app = await buildApp();
let passed = 0;
const failures = [];

function ok(name, cond, extra = '') {
  if (cond) {
    passed++;
    console.log(`  ✓ ${name}`);
  } else {
    failures.push(`${name}${extra ? ` (${extra})` : ''}`);
    console.log(`  ✗ ${name}${extra ? ` (${extra})` : ''}`);
  }
}

async function j(method, url, { token, payload } = {}) {
  return app.inject({
    method,
    url,
    headers: token ? { authorization: `Bearer ${token}` } : {},
    payload: payload !== undefined ? payload : undefined,
  });
}

/**
 * 断言对象包含客户端声明的每一个键（多余键不算错：Gson 会忽略未知字段）。
 * 缺键才是致命的——见文件头注释。
 */
function hasKeys(label, obj, keys) {
  const missing = keys.filter((k) => !(k in (obj || {})));
  ok(
    `${label} 覆盖客户端 DTO 的 ${keys.length} 个字段`,
    missing.length === 0,
    missing.length ? `缺少 ${missing.join(', ')}` : '',
  );
}

// ==================== 客户端 payload 构造（逐字段对应 Kotlin DTO） ====================

/** Song（data/model/Song.kt）——收藏的 upsert 直接就是整个 Song 对象 */
const song = (id, platform = 'netease') => ({
  id, title: `歌曲${id}`, artist: '歌手', album: '专辑', artwork: '', platform,
});

/** SyncHistoryUpsert */
const historyRow = (id) => ({
  id, title: `歌曲${id}`, artist: '歌手', album: '专辑', artwork: '', platform: 'netease',
});

/** SyncPlaylistUpsert（kind: mine=自建 / fav=收藏的网络歌单） */
const playlistRow = (id, name, kind, coverUrl = 'https://cdn.example.com/c.jpg') => ({ id, name, coverUrl, kind });

/** SyncPlaylistSongUpsert */
const playlistSongRow = (playlistId, position, s) => ({ playlistId, position, song: s });

/** SyncScriptUpsert */
const scriptRow = (id, name, code, enabled = true) => ({ id, name, description: '说明', code, enabled });

/** SyncDownloadUpsert */
const downloadRow = (platform, id, deviceId, deviceName) => ({
  platform, id, deviceId, deviceName,
  title: `歌曲${id}`, artist: '歌手', sizeBytes: 4096, downloadedAtMs: 1700000000000,
});

/** PlayEventDto（playEvents 为数组，不是 {upsert,delete}） */
const playEventRow = (eventId, id) => ({
  eventId, platform: 'netease', id, title: `歌曲${id}`, artist: '歌手',
  listenedMs: 30000, durationMs: 200000, listenedAtMs: 1700000000000,
});

try {
  console.log('== 准备两个账号（验证数据隔离） ==');
  // 用户名必须满足后端 USERNAME_RE（2-20 位字母/数字/下划线/中文）：带完整时间戳会超长被 400
  const suffix = Math.random().toString(36).slice(2, 8);
  const userA = await j('POST', '/api/auth/register', {
    payload: { username: `ca_${suffix}`, password: 'secret123', deviceName: 'Pixel 8' },
  });
  // 注册失败时直接把响应体打出来：契约脚本本身出问题（而不是后端出问题）时，
  // 一句 "Cannot read properties of undefined" 是排查不了的
  if (userA.statusCode !== 200) throw new Error(`账号 A 注册失败：${userA.statusCode} ${userA.body}`);
  const tokenA = userA.json().data.token;
  ok('账号 A 注册成功', Boolean(tokenA));

  const userB = await j('POST', '/api/auth/register', {
    payload: { username: `cb_${suffix}`, password: 'secret123', deviceName: 'iPad' },
  });
  if (userB.statusCode !== 200) throw new Error(`账号 B 注册失败：${userB.statusCode} ${userB.body}`);
  const tokenB = userB.json().data.token;
  ok('账号 B 注册成功', Boolean(tokenB));

  console.log('== /api/health：客户端"测试连接"用的探活接口 ==');
  const health = await j('GET', '/api/health');
  const healthBody = health.json();
  ok('GET /api/health 不需要登录即可访问', health.statusCode === 200, `status=${health.statusCode}`);
  ok('/api/health 使用 {code,msg,data} 信封', healthBody.code === 200 && typeof healthBody.data === 'object');
  hasKeys('HealthData', healthBody.data, ['ok', 'time', 'version']);

  console.log('== 推送：用客户端真实 payload 形状 ==');
  const push1 = await j('POST', '/api/sync/push', {
    token: tokenA,
    payload: {
      favorites: { upsert: [song('f1'), song('f2')] },
      history: { upsert: [historyRow('h1')] },
      playlists: {
        upsert: [playlistRow('local_1', '我的通勤', 'mine', ''), playlistRow('123456', '收藏的网易歌单', 'fav')],
      },
      playlistSongs: { upsert: [playlistSongRow('local_1', 0, song('f1')), playlistSongRow('local_1', 1, song('f2'))] },
      scripts: { upsert: [scriptRow('sc1', '自写音源', 'const a = 1;', true)] },
      downloads: { upsert: [downloadRow('netease', 'f1', 'dev-phone', 'Pixel 8')] },
      playEvents: [playEventRow('ev1', 'f1')],
    },
  });
  const pushBody = push1.json();
  ok('push 成功返回 {code,data}', push1.statusCode === 200 && pushBody.code === 200);
  hasKeys('SyncPushResult', pushBody.data, ['version', 'acceptedEvents', 'serverTime']);
  ok('push 返回单调递增的 version', Number.isFinite(pushBody.data.version) && pushBody.data.version > 0);
  ok('playEvents 事件被幂等接受', pushBody.data.acceptedEvents === 1);

  console.log('== 拉取：逐字段核对客户端 DTO ==');
  const pull1 = await j('GET', '/api/sync/pull?since=0&eventSince=0', { token: tokenA });
  const d = pull1.json().data;
  hasKeys('CloudSyncPullData', d, [
    'version', 'nextEventId', 'serverTime',
    'favorites', 'history', 'playlists', 'playlistSongs', 'playEvents', 'scripts', 'downloads',
  ]);
  hasKeys('SyncFavoriteItem', d.favorites[0], ['platform', 'id', 'song', 'updatedAtMs', 'deleted']);
  hasKeys('SyncHistoryItem', d.history[0], ['platform', 'id', 'song', 'playedAtMs', 'updatedAtMs', 'deleted']);
  hasKeys('SyncPlaylistItem', d.playlists[0], ['id', 'kind', 'name', 'coverUrl', 'updatedAtMs', 'deleted']);
  hasKeys('SyncPlaylistSongItem', d.playlistSongs[0], [
    'playlistId', 'position', 'platform', 'songId', 'song', 'updatedAtMs', 'deleted',
  ]);
  hasKeys('SyncScriptItem', d.scripts[0], ['id', 'name', 'description', 'code', 'enabled', 'updatedAtMs', 'deleted']);
  hasKeys('SyncDownloadItem', d.downloads[0], [
    'platform', 'id', 'deviceId', 'deviceName', 'title', 'artist', 'sizeBytes', 'downloadedAtMs', 'updatedAtMs', 'deleted',
  ]);
  hasKeys('SyncPlayEventItem', d.playEvents[0], [
    'eventId', 'platform', 'songId', 'title', 'artist', 'listenedMs', 'durationMs', 'listenedAtMs', 'updatedAtMs',
  ]);

  console.log('== 值也要对：字段名对了但内容被改写同样是 bug ==');
  const fav = d.favorites.find((x) => x.id === 'f1');
  ok('收藏里的 song 能解析出标题（客户端靠它渲染列表）', fav?.song?.title === '歌曲f1');
  ok('收藏列表含 2 条', d.favorites.length === 2);
  const minePl = d.playlists.find((x) => x.id === 'local_1');
  const favPl = d.playlists.find((x) => x.id === '123456');
  ok('自建歌单 kind=mine 原样返回', minePl?.kind === 'mine', `kind=${minePl?.kind}`);
  ok('收藏的歌单 kind=fav 原样返回（否则客户端会把它当自建歌单列表）', favPl?.kind === 'fav', `kind=${favPl?.kind}`);
  ok('歌单曲目顺序 position 原样返回', d.playlistSongs.map((x) => x.position).sort().join(',') === '0,1');
  ok('脚本源码原样返回（几万字符也不该被截断/转义坏）', d.scripts[0].code === 'const a = 1;');
  ok('脚本启用状态是布尔（不是 0/1）', d.scripts[0].enabled === true);
  ok('下载记录带设备维度', d.downloads[0].deviceId === 'dev-phone');
  ok('下载体积与时间未被改写', d.downloads[0].sizeBytes === 4096 && d.downloads[0].downloadedAtMs === 1700000000000);
  ok('听歌事件回读字段一致', d.playEvents[0].listenedMs === 30000 && d.playEvents[0].songId === 'f1');

  console.log('== 墓碑：客户端要靠键重建"删哪一条" ==');
  const cursor = d.version;
  const push2 = await j('POST', '/api/sync/push', {
    token: tokenA,
    payload: {
      favorites: { delete: [{ platform: 'netease', id: 'f2' }] },
      playlists: { delete: [{ id: '123456', kind: 'fav' }] },
      downloads: { delete: [{ platform: 'netease', id: 'f1', deviceId: 'dev-phone' }] },
      scripts: { delete: [{ id: 'sc1' }] },
    },
  });
  ok('墓碑推送成功', push2.statusCode === 200);
  const pull2 = await j('GET', `/api/sync/pull?since=${cursor}&eventSince=0`, { token: tokenA });
  const delta = pull2.json().data;
  const deadFav = delta.favorites.find((x) => x.id === 'f2');
  const deadPl = delta.playlists.find((x) => x.id === '123456');
  const deadDl = delta.downloads.find((x) => x.id === 'f1');
  const deadSc = delta.scripts.find((x) => x.id === 'sc1');
  ok('收藏墓碑 deleted=true', deadFav?.deleted === true);
  ok('歌单墓碑保留 kind（客户端按 kind+id 组键）', deadPl?.deleted === true && deadPl?.kind === 'fav');
  ok('下载墓碑保留 deviceId（否则会误删别的设备）', deadDl?.deleted === true && deadDl?.deviceId === 'dev-phone');
  ok('脚本墓碑 deleted=true', deadSc?.deleted === true);
  ok('增量拉取只回变更（不含未动的 f1 收藏）', delta.favorites.length === 1);
  ok('增量拉取回传新 version（客户端把它当下一次 since）', delta.version > cursor, `${delta.version} > ${cursor}`);

  const pull3 = await j('GET', `/api/sync/pull?since=${delta.version}&eventSince=0`, { token: tokenA });
  ok('游标推到最新后没有重复下发', pull3.json().data.favorites.length === 0 && pull3.json().data.playlists.length === 0);

  const full = await j('GET', '/api/sync/pull?since=0&eventSince=0', { token: tokenA });
  ok('全量拉取不含已删记录（首次安装不该看到墓碑）', !full.json().data.favorites.some((x) => x.id === 'f2'));

  console.log('== 账号隔离：A 的数据不会出现在 B 的拉取里 ==');
  const pullB = await j('GET', '/api/sync/pull?since=0&eventSince=0', { token: tokenB });
  const b = pullB.json().data;
  ok(
    'B 拉取全空',
    b.favorites.length === 0 && b.history.length === 0 && b.playlists.length === 0 &&
      b.playlistSongs.length === 0 && b.scripts.length === 0 && b.downloads.length === 0 && b.playEvents.length === 0,
  );

  console.log('== 鉴权：无 token 不得读取任何数据 ==');
  const anon = await j('GET', '/api/sync/pull?since=0&eventSince=0');
  ok('未带 token 的 pull 返回 401', anon.statusCode === 401);
} catch (e) {
  failures.push(`未捕获异常: ${e?.message || e}`);
  console.error(e);
}

console.log('');
if (failures.length === 0) {
  console.log(`契约检查全部通过：${passed} 项`);
  process.exit(0);
}
console.log(`契约检查失败 ${failures.length} 项（通过 ${passed} 项）：`);
failures.forEach((f) => console.log(`  - ${f}`));
process.exit(1);
