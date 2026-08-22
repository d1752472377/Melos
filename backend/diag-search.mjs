// 诊断脚本：复现 /api/search 聚合逻辑，打印每个音源实际返回条数
import 'dotenv/config';
import { initDb } from './src/lib/db.js';
import { Registry } from './src/lib/registry.js';
import { config } from './src/lib/config.js';

const keyword = process.argv[2] || '周杰伦';
const limit = parseInt(process.argv[3] || '20', 10);

const db = initDb(config.dbPath);
const registry = new Registry(db, config.encryptionKey);

console.log(`激活音源数: ${registry.active.length}`);
console.log(`limit=${limit} → perSource=${Math.max(5, Math.ceil(limit / Math.max(registry.active.length, 1)))}`);

for (const { source } of registry.active) {
  const t0 = Date.now();
  try {
    const list = await source.search(keyword, Math.max(5, Math.ceil(limit / registry.active.length)));
    console.log(`[${source.id}] 返回 ${list.length} 条 (${Date.now() - t0}ms)`);
    for (const s of list.slice(0, 3)) console.log(`   - ${s.title} / ${s.artist} [${s.platform}:${s.id}]`);
  } catch (e) {
    console.log(`[${source.id}] 失败 (${Date.now() - t0}ms): ${e.message}`);
  }
}

const merged = await registry.search(keyword, limit);
console.log(`\n聚合后总条数: ${merged.length}`);
merged.forEach((s, i) => console.log(`${i + 1}. ${s.title} - ${s.artist} (${s.platform})`));
process.exit(0);
