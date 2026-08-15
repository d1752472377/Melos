import Meting from '@meting/core';
import { MusicSource } from './MusicSource.js';

// 统一音质 -> 网易云码率 bitrate
const QUALITY_MAP = {
  standard: 128000,
  high: 320000,
  lossless: 999000,
};

/**
 * 网易云音源（基于 meting 聚合库）。
 * 已实测：搜索 / 取直链 / 取歌词 全链路可用。
 * 局限：公开接口只能拿到可免费播放的歌曲（VIP/版权歌会缺失或被替换为翻唱）。
 */
export class NeteaseSource extends MusicSource {
  constructor({ id, name, cookie }) {
    super({ id, name, type: 'netease' });
    this.meting = new Meting('netease');
    this.meting.format(true);
    if (cookie) this.meting.cookie(cookie);
  }

  async search(keyword, limit = 20) {
    const raw = await this.meting.search(keyword, { page: 1, limit: Math.min(limit, 30) });
    const list = this._parse(raw);
    return list.map((s) => ({
      id: String(s.id),
      title: s.name,
      artist: Array.isArray(s.artist) ? s.artist.join('/') : s.artist,
      album: s.album,
      artwork: null, // meting 需额外 pic() 请求，骨架阶段暂不取封面
      platform: this.id,
    }));
  }

  async getSongUrl(song, quality) {
    const br = QUALITY_MAP[quality] || 320000;
    const raw = await this.meting.url(song.id, br);
    const u = this._parse(raw);
    return { url: u.url || null, format: null, bitrate: u.br || null };
  }

  async getLyric(song) {
    const raw = await this.meting.lyric(song.id);
    const l = this._parse(raw);
    return {
      lyric: l.lyric || l.lrc || null,
      translated: l.tlyric || null,
    };
  }

  /** 歌单详情（meting playlist） */
  async getPlaylist(id) {
    const raw = await this.meting.playlist(id);
    const list = this._parse(raw);
    if (!Array.isArray(list)) return [];
    return list.map((s) => ({
      id: String(s.id),
      title: s.name,
      artist: Array.isArray(s.artist) ? s.artist.join('/') : s.artist,
      album: s.album,
      artwork: null,
      platform: this.id,
    }));
  }

  /** 网易云热门歌单列表（公开接口 /personalized/playlist） */
  async getHotPlaylists(limit = 20) {
    const res = await fetch(`https://music.163.com/api/personalized/playlist?limit=${limit}`, {
      headers: { 'User-Agent': 'Mozilla/5.0', 'Referer': 'https://music.163.com' },
    });
    const j = await res.json();
    const list = j.result || [];
    return list.map((p) => ({
      id: String(p.id),
      name: p.name,
      coverUrl: p.picUrl || null,
      playCount: p.playCount ?? null,
      trackCount: p.trackCount ?? null,
      platform: this.id,
    }));
  }

  _parse(raw) {
    try {
      const v = JSON.parse(raw);
      if (Array.isArray(v)) return v;
      return v || {};
    } catch {
      return {};
    }
  }
}
