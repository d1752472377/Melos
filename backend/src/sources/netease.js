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
    this.cookie = cookie || '';
    this.meting = new Meting('netease');
    this.meting.format(true);
    if (cookie) this.meting.cookie(cookie);
    // 歌手接口专用实例：type=100 返回的是歌手结构，format(true) 的格式化管线只认歌曲结构会崩，
    // 因此用不格式化的原始实例，自行解析 JSON。
    this.metingRaw = new Meting('netease');
    if (cookie) this.metingRaw.cookie(cookie);
  }

  /** 具备歌手搜索/详情能力 */
  get supportsArtist() {
    return true;
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

  /** 歌手搜索（网易云 type=100），返回 [{ id, name, avatar, alias }] */
  async searchArtist(keyword, limit = 8) {
    const raw = await this.metingRaw.search(String(keyword), { type: 100, limit: Math.min(limit, 30) });
    const v = this._parse(raw);
    const artists = Array.isArray(v?.result?.artists) ? v.result.artists : [];
    return artists.slice(0, limit).map((a) => ({
      id: String(a.id),
      name: a.name || '',
      avatar: a.img1v1Url || a.picUrl || null,
      alias: Array.isArray(a.alias) ? a.alias.filter(Boolean).join('/') : '',
      platform: this.id,
    }));
  }

  /**
   * 歌手详情 + 热门歌曲。
   * 用网易云公开 API /api/artist/{id}（比 meting.artist 多返回头像/别名等歌手信息）。
   * 注意：公开接口的热门歌可能含 VIP/无版权曲目，播放失败由上层多源降级兜底。
   */
  async getArtist(artistId, limit = 50) {
    const res = await fetch(`https://music.163.com/api/artist/${encodeURIComponent(String(artistId))}`, {
      headers: {
        'User-Agent': 'Mozilla/5.0',
        'Referer': 'https://music.163.com',
        ...(this.cookie ? { Cookie: this.cookie } : {}),
      },
    });
    if (!res.ok) throw new Error(`[netease] 歌手接口 HTTP ${res.status}`);
    const j = await res.json();
    const info = j?.artist;
    if (!info?.id) throw new Error('[netease] 歌手接口返回为空');
    const hotSongs = Array.isArray(j.hotSongs) ? j.hotSongs.slice(0, limit) : [];
    return {
      info: {
        id: String(info.id),
        name: info.name || '',
        avatar: info.img1v1Url || info.picUrl || null,
        alias: Array.isArray(info.alias) ? info.alias.filter(Boolean).join('/') : '',
        albumSize: info.albumSize ?? null,
        musicSize: info.musicSize ?? null,
        platform: this.id,
      },
      songs: hotSongs
        .filter((s) => s?.id)
        .map((s) => {
          // 兼容两种字段：v1 公开接口 artists[]/album{}，v3 接口 ar[]/al{}
          const artistArr = Array.isArray(s.artists) ? s.artists : (Array.isArray(s.ar) ? s.ar : []);
          const albumObj = s.album || s.al || {};
          const pic = albumObj.picUrl || null;
          return {
            id: String(s.id),
            title: s.name || '',
            artist: artistArr.map((a) => a?.name).filter(Boolean).join('/'),
            album: albumObj.name || '',
            artwork: pic ? (String(pic).startsWith('//') ? `https:${pic}` : pic) : null,
            platform: this.id,
          };
        }),
    };
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

  // ==================== 官方搜索扩展（/api/search/get 多 type） ====================
  //
  // 网易云公开搜索接口的 type：1 单曲 / 10 专辑 / 100 歌手 / 1000 歌单 / 1006 歌词匹配。
  // 与 meting 管线解耦，直接走公开 API（同 getArtist 的直连模式），失败向上抛由路由层兜底。

  /** 统一的公开搜索入口：返回 j.result（无结果时为空对象） */
  async _publicSearch(type, keyword, limit) {
    const url =
      `https://music.163.com/api/search/get/?s=${encodeURIComponent(String(keyword))}` +
      `&type=${type}&limit=${Math.min(limit, 50)}&offset=0`;
    const res = await fetch(url, {
      headers: {
        'User-Agent': 'Mozilla/5.0',
        'Referer': 'https://music.163.com',
        ...(this.cookie ? { Cookie: this.cookie } : {}),
      },
    });
    if (!res.ok) throw new Error(`[netease] 搜索接口 HTTP ${res.status}`);
    const j = await res.json();
    if (j?.code && j.code !== 200) throw new Error(`[netease] 搜索接口 code=${j.code}`);
    return j?.result || {};
  }

  /** 兼容 v1(artists[]/album{}) 与 v3(ar[]/al{}) 两种歌曲字段结构 */
  _songFromPublic(s) {
    const artistArr = Array.isArray(s.artists) ? s.artists : (Array.isArray(s.ar) ? s.ar : []);
    const albumObj = s.album || s.al || {};
    const pic = albumObj.picUrl || null;
    return {
      id: String(s.id),
      title: s.name || '',
      artist: artistArr.map((a) => a?.name).filter(Boolean).join('/'),
      album: albumObj.name || '',
      artwork: pic ? (String(pic).startsWith('//') ? `https:${pic}` : pic) : null,
      platform: this.id,
    };
  }

  /** 歌单搜索（type=1000） */
  async searchPlaylists(keyword, limit = 20) {
    const r = await this._publicSearch(1000, keyword, limit);
    const arr = Array.isArray(r.playlists) ? r.playlists : [];
    return arr.map((p) => ({
      id: String(p.id),
      name: p.name || '',
      coverUrl: p.coverImgUrl || p.picUrl || null,
      playCount: p.playCount ?? null,
      trackCount: p.trackCount ?? null,
      platform: this.id,
    }));
  }

  /** 专辑搜索（type=10） */
  async searchAlbums(keyword, limit = 20) {
    const r = await this._publicSearch(10, keyword, limit);
    const arr = Array.isArray(r.albums) ? r.albums : [];
    return arr.map((a) => ({
      id: String(a.id),
      name: a.name || '',
      artist: Array.isArray(a.artists) ? a.artists.map((x) => x?.name).filter(Boolean).join('/') : '',
      coverUrl: a.picUrl || null,
      size: a.size ?? null,
      platform: this.id,
    }));
  }

  /** 专辑详情 + 全部曲目（公开 /api/album/{id}） */
  async getAlbum(albumId) {
    const res = await fetch(`https://music.163.com/api/album/${encodeURIComponent(String(albumId))}`, {
      headers: {
        'User-Agent': 'Mozilla/5.0',
        'Referer': 'https://music.163.com',
        ...(this.cookie ? { Cookie: this.cookie } : {}),
      },
    });
    if (!res.ok) throw new Error(`[netease] 专辑接口 HTTP ${res.status}`);
    const j = await res.json();
    const info = j?.album;
    if (!info?.id) throw new Error('[netease] 专辑接口返回为空');
    const songs = Array.isArray(j.songs) ? j.songs : [];
    return {
      info: {
        id: String(info.id),
        name: info.name || '',
        artist: Array.isArray(info.artists) ? info.artists.map((x) => x?.name).filter(Boolean).join('/') : '',
        coverUrl: info.picUrl || null,
        size: info.size ?? null,
        platform: this.id,
      },
      songs: songs.filter((s) => s?.id).map((s) => this._songFromPublic(s)),
    };
  }

  /** 歌词关键词搜歌（type=1006）：返回歌曲列表（按歌词内容命中） */
  async searchByLyric(keyword, limit = 20) {
    const r = await this._publicSearch(1006, keyword, limit);
    const arr = Array.isArray(r.songs) ? r.songs : [];
    return arr.map((s) => this._songFromPublic(s));
  }

  /** 热搜词（官方 /api/search/hot/detail） */
  async hotSearchWords(limit = 12) {
    const res = await fetch('https://music.163.com/api/search/hot/detail', {
      headers: {
        'User-Agent': 'Mozilla/5.0',
        'Referer': 'https://music.163.com',
        ...(this.cookie ? { Cookie: this.cookie } : {}),
      },
    });
    if (!res.ok) throw new Error(`[netease] 热搜接口 HTTP ${res.status}`);
    const j = await res.json();
    const arr = Array.isArray(j?.data) ? j.data : [];
    return arr
      .map((d) => d?.searchWord)
      .filter(Boolean)
      .slice(0, limit);
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
