/**
 * 老库平滑升级检查：拿一份**已有数据库**（默认 `data/music.db` 的副本）跑一遍 `initDb`，
 * 确认升级过程不会因为"字段/表已存在"或"缺字段"而失败，并打印升级后的实际结构。
 *
 * 为什么需要它：`initDb` 里的 `ALTER TABLE ... ADD COLUMN` 是靠 `try/catch` 吞掉
 * "duplicate column" 错误来实现幂等的——**这条路径在全新的临时库上永远测不到**（新库建表时就带上了列）。
 * 而线上真正会发生的是"老库缺列"，写错一个列名/表名就是启动即崩。
 *
 * 用法：
 *   node scripts/migrate-check.mjs                  # 自动复制 data/music.db 到临时文件再升级（不碰原库）
 *   node scripts/migrate-check.mjs <某个.db 路径>    # 检查指定的库（同样会复制一份，不改原文件）
 *   npm run migrate-check
 */
import { copyFileSync, existsSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const source = resolve(process.argv[2] || join(here, '..', 'data', 'music.db'));
if (!existsSync(source)) {
  console.error(`找不到待检查的数据库：${source}`);
  process.exit(1);
}

// 一律在副本上操作：升级检查绝不能改动用户的真实数据
const copy = join(tmpdir(), `melos-migrate-${Date.now()}.db`);
copyFileSync(source, copy);
process.env.DB_URL = `file:${copy}`;
process.env.RATE_LIMIT_DISABLED = '1';

const { initDb, pullSync, findUserByUsername } = await import('../src/lib/db.js');
const { buildApp } = await import('../src/app.js');

const client = await initDb(`file:${copy}`);

/** 打印某张表的列，用来肉眼确认新增列是否真的落地 */
async function columnsOf(table) {
  const rs = await client.execute(`PRAGMA table_info(${table})`);
  return rs.rows.map((r) => r.name);
}

let failed = false;
function check(name, cond, extra = '') {
  console.log(`  ${cond ? '✓' : '✗'} ${name}${extra ? ` (${extra})` : ''}`);
  if (!cond) failed = true;
}

console.log(`== 升级老库：${source} ==`);
const playlists = await columnsOf('playlists');
check('playlists 有 kind 列（老库经 ALTER TABLE 补上）', playlists.includes('kind'), playlists.join(','));
check('user_scripts 表已建好（含 code 原文列）', (await columnsOf('user_scripts')).includes('code'));
check('downloads 表已建好（device_id 进主键）', (await columnsOf('downloads')).includes('device_id'));

const app = await buildApp();
const health = await app.inject({ method: 'GET', url: '/api/health' });
check('升级后的库上 /api/health 正常', health.statusCode === 200 && health.json().data.ok === true);

// 直接走库层做一次真实拉取：老库里已有多少数据都能拉，不能因为多了列/表而报错
const admin = await findUserByUsername(client, 'admin');
const pulled = await pullSync(client, admin.id, { since: 0, eventSince: 0 });
check(
  '老用户全量 pull 不报错（新增数据集返回空数组）',
  Array.isArray(pulled.playlists) && Array.isArray(pulled.scripts) && Array.isArray(pulled.downloads),
  `playlists=${pulled.playlists.length} scripts=${pulled.scripts.length} downloads=${pulled.downloads.length}`,
);
check('老库里的歌单拿到 kind（不是 undefined）', pulled.playlists.every((p) => p.kind === 'mine' || p.kind === 'fav'));

console.log(failed ? '\n升级检查失败' : '\n升级检查通过（原库未被修改）');
process.exit(failed ? 1 : 0);
