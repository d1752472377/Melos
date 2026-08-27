import { ChkszSource } from './chksz.js';
import { fetchWithTimeout } from '../lib/http.js';

/**
 * ChKSz 聚合 API 的 QQ 音乐 / 酷狗音乐音源（同一把 apikey，无需 VIP）。
 *
 * QQ:
 *   搜索 GET /api/qq_music?msg={关键词}&num={数量}&apikey=
 *   解析 GET /api/qq_music?mid={mid}&size={音质}&apikey=  （也支持 n= 序号）
 * 酷狗:
 *   搜索 GET /api/kugou_music?msg={关键词}&num={数量}&apikey=
 *   解析 GET /api/kugou_music?id={歌曲ID}&size={音质}&apikey=
 */
const QQ_SIZE = { standard: '128k', high: '320k', lossless: 'flac' };
const KUGOU_SIZE = { standard: '128k', high: '320k', lossless: 'flac' };

class ChkszPointSource extends ChkszSource {
  constructor({ id, name, apiKey, kind }) {
    super({ id, name, apiKey });
    this.kind = kind;
    this.sizeMap = kind === 'qq' ? QQ_SIZE : KUGOU_SIZE;
  }

  async _raw(path, params = {}, _retried = false) {
    if (!this.apiKey) throw new Error(`[${this.kind}] 未配置 apiKey`);
    const q = { type: 'json', ...params, apikey: this.apiKey };
    const url = new URL(this.baseUrl + path);
    for (const [k, v] of Object.entries(q)) {
      if (v !== undefined && v !== null && v !== '') url.searchParams.set(k, String(v));
    }
    let res;
    let text;
    try {
      res = await fetchWithTimeout(url, { headers: { 'Accept': 'application/json', 'User-Agent': 'Mozilla/5.0' } }, 15_000);
      text = await res.text();
    } catch (e) {
      throw new Error(`[${this.kind}] 网络错误: ${e.message}`);
    }
    let json;
    try {
      json = JSON.parse(text);
    } catch {
      throw new Error(`[${this.kind}] 非 JSON 响应 status=${res.status}`);
    }
    // 官方文档错误语义（https://api.chksz.com 智能体接入提示词）：
    //   402 免费和付费额度均耗尽 —— 不重试，等次日额度或兑换 LDC
    //   429 超过速率限制(20次/分钟) —— 按 Retry-After 等待后最多重试一次
    //   401 Key 无效 / 403 被封禁 —— 不重试
    if (res.status === 402) {
      throw new Error(`[${this.kind}] ChKSz 额度已用尽(402)，请次日再试或兑换 LDC`);
    }
    if (res.status === 429) {
      if (_retried) throw new Error(`[${this.kind}] ChKSz 限流(429)，重试后仍失败`);
      const waitSec = Math.min(parseInt(res.headers?.get?.('retry-after') || '5', 10) || 5, 30);
      await new Promise((r) => setTimeout(r, waitSec * 1000));
      return this._raw(path, params, true);
    }
    if (res.status === 401 || res.status === 403) {
      throw new Error(`[${this.kind}] HTTP ${res.status}: ${json.msg || text.slice(0, 160)}`);
    }
    if (json.code && json.code !== 200) {
      throw new Error(`[${this.kind}] ${json.code}: ${json.msg || '请求失败'}`);
    }
    return json;
  }

  async search(keyword, limit = 20) {
    const path = this.kind === 'qq' ? '/api/qq_music' : '/api/kugou_music';
    const json = await this._raw(path, { msg: keyword, num: Math.min(Number(limit) || 20, 50) });
    const list = Array.isArray(json.list) ? json.list : [];
    return list.map((s) => {
      if (this.kind === 'qq') {
        return {
          id: String(s.mid || s.n),
          title: s.name || '',
          artist: s.singer || '',
          album: s.album || '',
          artwork: s.cover || null,
          platform: this.id,
        };
      }
      return {
        id: String(s.id),
        title: s.name || '',
        artist: s.singer || '',
        album: s.album || '',
        artwork: s.cover || null,
        platform: this.id,
      };
    });
  }

  async getSongUrl(song, quality) {
    const size = this.sizeMap[quality] || this.sizeMap.lossless;
    const path = this.kind === 'qq' ? '/api/qq_music' : '/api/kugou_music';
    const params = { size };
    if (this.kind === 'qq') params.mid = song.id;
    else params.id = song.id;
    const json = await this._raw(path, params);
    const url = json.url || json.data?.url || null;
    if (!url) throw new Error(`[${this.kind}] 未获取到播放地址`);
    return {
      url,
      format: json.format || url.split('?')[0].split('.').pop() || null,
      bitrate: json.bitrate ? String(json.bitrate) : null,
      duration: json.interval ? String(json.interval) : null,
      platform: this.id,
    };
  }

  async getLyric(song) {
    const path = this.kind === 'qq' ? '/api/qq_music' : '/api/kugou_music';
    const params = this.kind === 'qq' ? { mid: song.id } : { id: song.id };
    try {
      const json = await this._raw(path, params);
      return { lyric: json.lrc || null, translated: null };
    } catch {
      return { lyric: null, translated: null };
    }
  }

  async getPlaylist() {
    return [];
  }

  async getHotPlaylists() {
    return [];
  }

  async healthCheck() {
    try {
      const r = await this.search('test', 1);
      return { ok: Array.isArray(r), detail: Array.isArray(r) ? `返回 ${r.length} 条` : '返回非数组' };
    } catch (e) {
      return { ok: false, detail: e.message };
    }
  }
}

export class ChkszQQSource extends ChkszPointSource {
  constructor(opts) {
    super({ ...opts, kind: 'qq' });
  }
}

export class ChkszKugouSource extends ChkszPointSource {
  constructor(opts) {
    super({ ...opts, kind: 'kugou' });
  }
}
