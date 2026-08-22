// 后端改动验证：歌手接口 + 修好的聚合算法
import 'dotenv/config';
import { initDb } from './src/lib/db.js';
import { Registry } from './src/lib/registry.js';
import { config } from './src/lib/config.js';

const db = initDb(config.dbPath);
const registry = new Registry(db, config.encryptionKey);

// 1) 歌手搜索
const artistSource = registry.getArtistSource('netease');
console.log('artist source:', artistSource?.id);
const artists = await artistSource.searchArtist('周杰伦', 5);
console.log('\n=== /api/artist/search →', artists.length, '条');
console.log(JSON.stringify(artists[0], null, 1));

// 2) 歌手详情
if (artists[0]) {
  const detail = await artistSource.getArtist(artists[0].id, 10);
  console.log('\n=== /api/artist →', detail.info.name, '| avatar:', !!detail.info.avatar, '| songs:', detail.songs.length);
  console.log('首首歌:', JSON.stringify(detail.songs[0]));
}

// 3) 聚合算法修复验证：强制把 netease 标记为不健康，只剩 kuwo 时也应返回满 limit
for (const { row } of registry.active) {
  if (row.id === 'netease') {
    registry.stats.set(row.id, { ok: 0, fail: 99, consecutiveFail: 99, lastFailAt: Date.now() });
  }
}
const merged = await registry.search('周杰伦', 20);
console.log('\n=== 单源故障时聚合结果:', merged.length, '条（期望接近20而不是7）');

process.exit(0);
