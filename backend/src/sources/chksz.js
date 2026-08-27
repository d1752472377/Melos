/**
 * ChKSz 音源（api.chksz.com 聚合 API）。
 *
 * 背景：meting/网易云 的公开接口只能返回 ~45 秒试听片段（本仓库早前崩溃根因），
 * 而 ChKSz 的 /api/163_music 能返回**完整**歌曲（FLAC/MP3 直链，带版权/登录态）。
 *
 * 用法：在管理后台添加 type='chksz' 的音源，config 需含 `apiKey`。
 *   - search      /api/163_search   → 完整歌曲元数据（含封面、时长）
 *   - getSongUrl  /api/163_music    → 完整播放直链（按 level 映射音质）
 *   - getLyric    /api/163_lyric    → LRC 歌词 + 翻译
 *   - getPlaylist /api/163_playlist → 网易云歌单详情
 *   - getHotPlaylists              → 网易云热门歌单（个人推荐接口）
 */
import { MusicSource } from './MusicSource.js';
import { fetchWithTimeout } from '../lib/http.js';

// App 统一音质 -> ChKSz level（实测 163_music 合法 level）
//   standard -> 128k(standard)  high -> 320k(exhigh)  lossless -> FLAC(lossless)
const QUALITY_MAP = {
  standard: 'standard',
  high: 'exhigh',
  lossless: 'lossless',
};

const API_BASE = 'https://api.chksz.com';

export class ChkszSource extends MusicSource {
  constructor({ id, name, apiKey, baseUrl = API_BASE }) {
    super({ id, name, type: 'chksz' });
    this.apiKey = apiKey;
    this.baseUrl = (baseUrl || API_BASE).replace(/\/$/, '');
  }

  /** 带 apikey 的 GET，返回 { code, data, msg } */
  async _get(path, params = {}) {
    if (!this.apiKey) throw new Error('[chksz] 未配置 apiKey');
    const q = { type: 'json', ...params, apikey: this.apiKey };
    const url = new URL(this.baseUrl + path);
    for (const [k, v] of Object.entries(q)) {
      if (v !== undefined && v !== null && v !== '') url.searchParams.set(k, String(v));
    }
    let res, text;
    try {
      res = await fetchWithTimeout(url, { headers: { 'Accept': 'application/json', 'User-Agent': 'Mozilla/5.0' } }, 15_000);
      text = await res.text();
    } catch (e) {
      throw new Error(`[chksz] 网络错误: ${e.message}`);
    }
    let json;
    try { json = JSON.parse(text); } catch { throw new Error(`[chksz] 非 JSON 响应 status=${res.status}`); }
    // 显式判断：code=0/undefined 不再被误判为成功（旧写法 `json.code &&` 会把 code=0 放行）
    if (json.code !== undefined && json.code !== null && Number(json.code) !== 200) {
      throw new Error(`[chksz] ${json.code}: ${json.msg || '请求失败'}`);
    }
    return json.data ?? {};
  }

  _toSong(s) {
    const artists = Array.isArray(s.artists)
      ? s.artists.join('/')
      : (Array.isArray(s.ar) ? s.ar.map((a) => (typeof a === 'object' ? a.name : a)).join('/') : (s.artists || ''));
    const album = s.album || (s.al && s.al.name) || '';
    const artwork = s.picUrl || (s.al && s.al.picUrl) || null;
    return {
      id: String(s.id),
      title: s.name || '',
      artist: artists,
      album,
      artwork: artwork && artwork.startsWith('//') ? `https:${artwork}` : artwork,
      platform: this.id,
    };
  }

  /** offset：163_search 支持的分页偏移（官方文档），为后续"加载更多"预留 */
  async search(keyword, limit = 20, offset = 0) {
    const data = await this._get('/api/163_search', {
      keyword,
      limit: Math.min(Number(limit) || 20, 50),
      offset: Math.max(Number(offset) || 0, 0),
    });
    const songs = Array.isArray(data.songs) ? data.songs : [];
    return songs.map((s) => this._toSong(s));
  }

  async getSongUrl(song, quality) {
    const level = QUALITY_MAP[quality] || 'exhigh';
    const data = await this._get('/api/163_music', { id: song.id, level });
    const url = data && data.url;
    if (!url) throw new Error('[chksz] 未获取到播放地址');
    return {
      url,
      format: url.split('?')[0].split('.').pop().toLowerCase(),
      bitrate: data.br ? String(data.br) : null,
      duration: data.size ? String(data.size) : null, // size ≈ 歌曲大小标记
      platform: this.id,
    };
  }

  async getLyric(song) {
    const data = await this._get('/api/163_lyric', { id: song.id });
    const lrc = data?.lrc || null;
    const translated = data?.tlyric || null;
    return { lyric: lrc, translated };
  }

  /** 网易云歌单详情 */
  async getPlaylist(id) {
    const data = await this._get('/api/163_playlist', { id });
    const tracks = Array.isArray(data.tracks) ? data.tracks.map((t) => this._toSong(t)) : [];
    return tracks;
  }

  /**
   * 网易云热门歌单（ChKSz 未暴露独立热门歌单接口，复用网易云官方 personalized
   * 接口取列表，封面/歌单 id 可用；与 meting 源同一来源）。
   */
  async getHotPlaylists(limit = 20) {
    const res = await fetchWithTimeout(`https://music.163.com/api/personalized/playlist?limit=${Math.min(Number(limit) || 20, 50)}`, {
      headers: { 'User-Agent': 'Mozilla/5.0', 'Referer': 'https://music.163.com' },
    }, 10_000);
    const j = await res.json();
    const list = (j.result || []).map((p) => ({
      id: String(p.id),
      name: p.name || '',
      coverUrl: p.picUrl || null,
      playCount: p.playCount ?? null,
      trackCount: p.trackCount ?? null,
      platform: this.id,
    }));
    return list;
  }

  /** 连通性自检 */
  async healthCheck() {
    try {
      const r = await this.search('test', 1);
      return { ok: Array.isArray(r), detail: Array.isArray(r) ? `返回 ${r.length} 条` : '返回非数组' };
    } catch (e) {
      return { ok: false, detail: e.message };
    }
  }
}
