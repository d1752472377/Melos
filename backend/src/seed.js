import { config } from './lib/config.js';
import { initDb, getSource, insertSource } from './lib/db.js';
import { encryptConfig } from './lib/crypto.js';

/**
 * 种子数据：把 .env 里配置的音源密钥加密后写入 DB（幂等，已存在则跳过）。
 * 运行：npm run seed
 */
const db = initDb(config.dbPath);

function ensureKuwo() {
  const id = 'kuwo';
  if (getSource(db, id)) { console.log(`[seed] ${id} 已存在，跳过`); return; }
  if (!config.kuwoApiKey || !config.kuwoSecretKey) {
    console.warn('[seed] 未配置 KUWO_API_KEY / KUWO_SECRET_KEY，跳过酷我源');
    return;
  }
  const enc = encryptConfig({
    apiKey: config.kuwoApiKey,
    secretKey: config.kuwoSecretKey,
    baseUrl: 'https://api.yaohud.cn/api/music/kuwo',
    defaultQuality: 'lossless',
  }, config.encryptionKey);
  // 妖狐/酷我仅作兜底（可能需 VIP），权重降到最低
  insertSource(db, { id, name: '酷我', type: 'kuwo', weight: 30, config: enc });
  console.log('[seed] 已写入酷我音源（密钥已加密存储）');
}

function ensureNetease() {
  const id = 'netease';
  if (getSource(db, id)) { console.log(`[seed] ${id} 已存在，跳过`); return; }
  const enc = encryptConfig({ cookie: '' }, config.encryptionKey);
  insertSource(db, { id, name: '网易云', type: 'netease', weight: 30, config: enc });
  console.log('[seed] 已写入网易云音源');
}

function ensureChksz() {
  const id = 'chksz';
  if (getSource(db, id)) { console.log(`[seed] ${id} 已存在，跳过`); return; }
  if (!config.chkszApiKey) {
    console.warn('[seed] 未配置 CHKSZ_API_KEY，跳过 chksz 音源');
    return;
  }
  const enc = encryptConfig({ apiKey: config.chkszApiKey }, config.encryptionKey);
  insertSource(db, { id, name: '网易云(ChKSz)', type: 'chksz', weight: 100, config: enc });
  console.log('[seed] 已写入 chksz 网易云音源（完整音质，密钥已加密存储）');
}

function ensureQQ() {
  const id = 'qq';
  if (getSource(db, id)) { console.log(`[seed] ${id} 已存在，跳过`); return; }
  if (!config.chkszApiKey) {
    console.warn('[seed] 未配置 CHKSZ_API_KEY，跳过 QQ 音源');
    return;
  }
  const enc = encryptConfig({ apiKey: config.chkszApiKey }, config.encryptionKey);
  insertSource(db, { id, name: 'QQ音乐(ChKSz)', type: 'qq', weight: 80, config: enc });
  console.log('[seed] 已写入 QQ 音源');
}

function ensureKugou() {
  const id = 'kugou';
  if (getSource(db, id)) { console.log(`[seed] ${id} 已存在，跳过`); return; }
  if (!config.chkszApiKey) {
    console.warn('[seed] 未配置 CHKSZ_API_KEY，跳过酷狗音源');
    return;
  }
  const enc = encryptConfig({ apiKey: config.chkszApiKey }, config.encryptionKey);
  insertSource(db, { id, name: '酷狗(ChKSz)', type: 'kugou', weight: 70, config: enc });
  console.log('[seed] 已写入酷狗音源');
}

ensureKuwo();
ensureNetease();
ensureChksz();
ensureQQ();
ensureKugou();
console.log('[seed] 完成');
