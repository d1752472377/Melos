import { listSources } from './db.js';
import { decryptConfig } from './crypto.js';
import { createSource } from '../sources/index.js';

const HEALTH = {
  maxConsecutiveFail: 5, // 连续失败多少次后判定为不健康
  cooldownMs: 60_000, // 不健康后的冷却时间（此期间跳过该源）
};

/**
 * 音源注册表：运行时状态 + 健康度 + 降级。
 *
 * - reload()：从 DB 重新加载（管理后台增删改后调用）。
 * - search()：聚合所有健康源结果。
 * - getSongUrl()：优先用歌曲所属源，失败按权重降级到其它源。
 */
export class Registry {
  constructor(db, encryptionKey) {
    this.db = db;
    this.encryptionKey = encryptionKey;
    this.active = []; // [{ row, source, weight }] 按 weight 降序
    this.stats = new Map(); // id -> { ok, fail, consecutiveFail, lastFailAt }
  }

  /**
   * 从 DB 加载音源（异步，DB 层已改为远程 libSQL）。
   * 启动时在 index.js 里 await registry.init()；管理后台增删改后调用 reload()。
   */
  async init() {
    return this.reload();
  }

  async reload() {
    const rows = await listSources(this.db);
    const next = [];
    for (const row of rows) {
      if (!row.enabled) continue;
      try {
        const config = decryptConfig(row.config, this.encryptionKey);
        const source = createSource(row, config);
        next.push({ row, source, weight: row.weight ?? 100 });
      } catch (e) {
        console.warn(`[registry] 加载音源 ${row.id} 失败: ${e.message}`);
      }
    }
    next.sort((a, b) => b.weight - a.weight);
    this.active = next;
  }

  list() {
    return this.active.map(({ row, source }) => ({
      id: row.id,
      name: row.name,
      type: row.type,
      weight: row.weight,
      enabled: !!row.enabled,
      healthy: this._isHealthy(row.id),
      stats: this.stats.get(row.id) || { ok: 0, fail: 0, consecutiveFail: 0 },
    }));
  }

  /** 按类型找第一个健康源（如 'netease'），供歌单等扩展能力使用 */
  getSourceByType(type) {
    const entry = this.active.find(({ row }) => row.type === type && this._isHealthy(row.id));
    return entry?.source || null;
  }

  /**
   * 找一个支持歌手能力的健康源：
   * 优先指定 platform id，其次任意具备能力的源。没有则返回 null。
   */
  getArtistSource(platformId) {
    const capable = this.active.filter(
      ({ row, source }) => source.supportsArtist && this._isHealthy(row.id)
    );
    const preferred = capable.find(({ row }) => row.id === platformId);
    return preferred?.source || capable[0]?.source || null;
  }

  _record(id, ok) {
    const s = this.stats.get(id) || { ok: 0, fail: 0, consecutiveFail: 0, lastFailAt: 0 };
    if (ok) {
      s.ok += 1;
      s.consecutiveFail = 0;
    } else {
      s.fail += 1;
      s.consecutiveFail += 1;
      s.lastFailAt = Date.now();
    }
    this.stats.set(id, s);
  }

  _isHealthy(id) {
    const s = this.stats.get(id);
    if (!s) return true;
    if (s.consecutiveFail >= HEALTH.maxConsecutiveFail) {
      // 冷却期内视为不健康
      return Date.now() - s.lastFailAt > HEALTH.cooldownMs;
    }
    return true;
  }

  _orderedSources(preferPlatform) {
    const healthy = this.active.filter(({ row }) => this._isHealthy(row.id));
    const preferred = healthy.filter(({ row }) => row.id === preferPlatform);
    const rest = healthy
      .filter(({ row }) => row.id !== preferPlatform)
      .sort((a, b) => b.weight - a.weight);
    return [...preferred, ...rest].map((e) => e.source);
  }

  /** 聚合搜索：合并所有健康源结果，按权重排序，去重。
   *  platformId 可选：只聚合指定源（App 端搜索页的平台切换）。 */
  async search(keyword, limit = 20, platformId = null) {
    // 只统计"健康"的源来分摊配额：不健康源不参与请求，也不该拉低每源配额。
    let healthy = this.active.filter(({ row }) => this._isHealthy(row.id));
    if (platformId) {
      healthy = healthy.filter(({ row }) => row.id === platformId);
      if (healthy.length === 0) return [];
    }
    // 超额请求（×2，至少 limit）：补偿部分源返回不足/去重损耗；合并后统一裁剪到 limit。
    const perSource = Math.min(50, Math.max(limit, Math.ceil((limit * 2) / Math.max(healthy.length, 1))));
    const results = await Promise.allSettled(
      healthy.map(async ({ source }) => {
        try {
          const list = await source.search(keyword, perSource);
          this._record(source.id, true);
          return list;
        } catch (e) {
          this._record(source.id, false);
          return [];
        }
      })
    );

    // 轮询交错合并：避免高权重源直接把结果占满，保证 QQ/酷狗等平台也有露出
    const lists = results
      .map((r) => (r.status === 'fulfilled' && Array.isArray(r.value) ? r.value : []));
    const merged = [];
    const seen = new Set();
    const idx = new Array(lists.length).fill(0);
    let advanced = true;
    while (merged.length < limit && advanced) {
      advanced = false;
      for (let i = 0; i < lists.length && merged.length < limit; i++) {
        if (idx[i] >= lists[i].length) continue;
        const item = lists[i][idx[i]++];
        advanced = true;
        const dedupKey = `${item.platform}:${item.id}`;
        if (seen.has(dedupKey)) continue;
        seen.add(dedupKey);
        merged.push(item);
      }
    }
    return merged;
  }

  /** 取直链：优先歌曲所属源，失败按权重降级 */
  async getSongUrl(song, quality) {
    const errors = [];
    for (const source of this._orderedSources(song.platform)) {
      try {
        const r = await source.getSongUrl(song, quality);
        if (r && r.url) {
          this._record(source.id, true);
          return { ...r, platform: source.id, sourceName: source.name };
        }
        this._record(source.id, false);
        errors.push(`${source.id}: 返回空直链`);
      } catch (e) {
        this._record(source.id, false);
        errors.push(`${source.id}: ${e.message}`);
      }
    }
    return { url: null, errors };
  }

  /** 取歌词：优先歌曲所属源，失败降级。返回 { lyric, translated } */
  async getLyric(song) {
    for (const source of this._orderedSources(song.platform)) {
      try {
        const r = await source.getLyric(song);
        const lyric = typeof r === 'string' ? r : r?.lyric;
        if (lyric) {
          this._record(source.id, true);
          return {
            lyric,
            translated: typeof r === 'object' ? (r.translated || null) : null,
            platform: source.id,
            sourceName: source.name,
          };
        }
      } catch (e) {
        this._record(source.id, false);
      }
    }
    return { lyric: null, translated: null };
  }
}
