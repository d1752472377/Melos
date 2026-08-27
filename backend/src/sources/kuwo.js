import crypto from 'node:crypto';
import { MusicSource } from './MusicSource.js';
import { fetchWithTimeout } from '../lib/http.js';

// 统一音质 -> 酷我 size 参数
const QUALITY_MAP = {
  standard: 'Standard', // 128k mp3
  high: 'SQ', // 320k mp3
  lossless: 'lossless', // 740k flac
};

/**
 * 酷我音源（妖狐 API 代理）。
 * 接口：https://api.yaohud.cn/api/music/kuwo
 * 签名：HMAC-SHA256("key={apiKey}&timestamp={ts}", secretKey)
 * 限制：QPS 每 2 秒 5 次；播放链接有时效性。
 *
 * 已知坑（已处理）：msg 参数必填；两种取单曲模式字段名不同；action=song 忽略 size；会间歇 503。
 */
export class KuwoSource extends MusicSource {
  constructor({ id, name, apiKey, secretKey, baseUrl, minIntervalMs = 500, defaultQuality = 'lossless' }) {
    super({ id, name, type: 'kuwo' });
    this.apiKey = apiKey;
    this.secretKey = secretKey;
    this.baseUrl = baseUrl || 'https://api.yaohud.cn/api/music/kuwo';
    this.minIntervalMs = minIntervalMs;
    this.defaultQuality = defaultQuality;
    this._lastCallAt = 0;
    this._songCache = new Map(); // 缓存单曲完整数据（含歌词），避免重复请求
  }

  _sign(timestamp) {
    const signString = `key=${this.apiKey}&timestamp=${timestamp}`;
    return crypto.createHmac('sha256', this.secretKey).update(signString).digest('hex');
  }

  async _call(params = {}, retries = 3) {
    for (let attempt = 0; ; attempt++) {
      const wait = this._lastCallAt + this.minIntervalMs - Date.now();
      if (wait > 0) await new Promise((r) => setTimeout(r, wait));
      this._lastCallAt = Date.now();

      const timestamp = Math.floor(Date.now() / 1000);
      const sign = this._sign(timestamp);
      const url = new URL(this.baseUrl);
      url.searchParams.set('key', this.apiKey);
      for (const [k, v] of Object.entries(params)) {
        if (v !== undefined && v !== null && v !== '') url.searchParams.set(k, String(v));
      }

      let res;
      try {
        res = await fetchWithTimeout(url, {
          headers: {
            'X-Api-Key': this.apiKey,
            'X-Api-Timestamp': String(timestamp),
            'X-Api-Sign': sign,
            'Content-Type': 'application/json',
            'User-Agent': 'Mozilla/5.0',
          },
        }, 15_000);
      } catch (e) {
        if (attempt >= retries) throw new Error(`[kuwo] 网络错误: ${e.message}`);
        await this._backoff(attempt);
        continue;
      }

      const text = await res.text();

      // 5xx（上游酷我/代理过载，常见 503）→ 退避重试
      if (res.status >= 500) {
        if (attempt >= retries) throw new Error(`[kuwo] HTTP ${res.status}: ${text.slice(0, 120)}`);
        await this._backoff(attempt);
        continue;
      }

      let json;
      try { json = JSON.parse(text); } catch { throw new Error(`[kuwo] 非 JSON 响应 status=${res.status}: ${text.slice(0, 200)}`); }
      if (json.code !== 200) throw new Error(`[kuwo] API 错误 code=${json.code} msg=${json.msg}`);
      return json.data;
    }
  }

  async _backoff(attempt) {
    const ms = Math.max(this.minIntervalMs * 2, 500) * (attempt + 1);
    await new Promise((r) => setTimeout(r, ms));
  }

  async search(keyword, limit = 20) {
    const data = await this._call({ msg: keyword, g: Math.min(limit, 50) });
    return (data.songs || []).map((s) => ({
      id: String(s.rid),
      title: s.name,
      artist: s.singer,
      album: s.album,
      artwork: s.image || null,
      platform: this.id,
    }));
  }

  /** 取单曲完整数据（含直链 + 歌词），带 LRU 内存缓存（上限 300 条，防无限增长） */
  async _getFull(song, quality) {
    const q = QUALITY_MAP[quality] || QUALITY_MAP[this.defaultQuality] || 'lossless';
    const cacheKey = `${song.id}:${q}`;
    if (this._songCache.has(cacheKey)) {
      // LRU 触达：删除再重插，保持最近使用在前
      const v = this._songCache.get(cacheKey);
      this._songCache.delete(cacheKey);
      this._songCache.set(cacheKey, v);
      return v;
    }
    const data = await this._call({ msg: song.title, action: 'song', id: song.id, size: q });
    const full = this._normalize(data);
    this._songCache.set(cacheKey, full);
    if (this._songCache.size > 300) {
      this._songCache.delete(this._songCache.keys().next().value); // 淘汰最久未用
    }
    return full;
  }

  async getSongUrl(song, quality) {
    const full = await this._getFull(song, quality || this.defaultQuality);
    return { url: full.url, format: full.format, bitrate: full.bitrate, duration: full.duration };
  }

  async getLyric(song) {
    const full = await this._getFull(song, this.defaultQuality);
    return { lyric: full.lyric || null, translated: null };
  }

  _normalize(data) {
    const vm = data.vipmusic || {};
    return {
      id: data.rid ? String(data.rid) : null,
      title: data.name,
      artist: data.artist || data.songname || null,
      album: data.album || null,
      picture: data.picture || null,
      duration: data.duration || null,
      url: vm.url || null,
      format: vm.format || null,
      bitrate: vm.bitrate || null,
      lyric: data.lyric?.lrc || null,
      platform: this.id,
    };
  }
}
