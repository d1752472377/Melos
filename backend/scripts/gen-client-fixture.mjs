/**
 * 生成客户端 JVM 测试用的「真实服务端响应」夹具。
 *
 * 用途：Android 侧的 `SyncWireContractTest` 会反序列化这个文件。
 * 夹具必须来自**真实服务端**（而不是手写 JSON）——手写的话，两端字段名一起写错也照样通过，
 * 等于没测。这里跑一次真实的 push + pull，把响应体原样落盘。
 *
 * 用法：node scripts/gen-client-fixture.mjs [输出路径]
 * 默认输出：../android/app/src/test/resources/sync_pull_golden.json
 */
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { mkdirSync, writeFileSync } from 'node:fs';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const out = resolve(process.argv[2] || join(here, '..', '..', 'android', 'app', 'src', 'test', 'resources', 'sync_pull_golden.json'));

process.env.DB_URL = `file:${join(tmpdir(), `melos-fixture-${Date.now()}.db`)}`;
process.env.RATE_LIMIT_DISABLED = '1';

const { buildApp } = await import('../src/app.js');
const app = await buildApp();
const j = (method, url, { token, payload } = {}) =>
  app.inject({ method, url, headers: token ? { authorization: `Bearer ${token}` } : {}, payload });

const reg = await j('POST', '/api/auth/register', {
  payload: { username: `fx_${Math.random().toString(36).slice(2, 8)}`, password: 'secret123', deviceName: 'Pixel 8' },
});
if (reg.statusCode !== 200) throw new Error(`注册失败：${reg.statusCode} ${reg.body}`);
const token = reg.json().data.token;

const song = (id) => ({ id, title: `歌曲${id}`, artist: '歌手', album: '专辑', artwork: 'https://cdn.example.com/a.jpg', platform: 'netease' });

await j('POST', '/api/sync/push', {
  token,
  payload: {
    favorites: { upsert: [song('f1'), song('f2')] },
    history: { upsert: [{ ...song('h1'), playedAtMs: 1700000000000 }] },
    playlists: {
      upsert: [
        { id: 'local_1', kind: 'mine', name: '我的通勤', coverUrl: '' },
        { id: '888', kind: 'fav', name: '收藏的网易歌单', coverUrl: 'https://cdn.example.com/p.jpg' },
      ],
    },
    playlistSongs: { upsert: [{ playlistId: 'local_1', position: 0, song: song('f1') }] },
    scripts: { upsert: [{ id: 'sc1', name: '自写音源', description: '说明', code: 'module.exports = { search: async () => [] };', enabled: true }] },
    downloads: {
      upsert: [{ platform: 'netease', id: 'f1', deviceId: 'dev-phone', deviceName: 'Pixel 8', title: '歌曲f1', artist: '歌手', sizeBytes: 4096, downloadedAtMs: 1700000000000 }],
    },
    playEvents: [{ eventId: 'ev1', platform: 'netease', id: 'f1', title: '歌曲f1', artist: '歌手', listenedMs: 30000, durationMs: 200000, listenedAtMs: 1700000000000 }],
  },
});

const pull = await j('GET', '/api/sync/pull?since=0&eventSince=0', { token });
if (pull.statusCode !== 200) throw new Error(`pull 失败：${pull.statusCode} ${pull.body}`);

mkdirSync(dirname(out), { recursive: true });
writeFileSync(out, `${JSON.stringify(pull.json(), null, 2)}\n`, 'utf8');
console.log(`已写入真实响应夹具：${out}`);
