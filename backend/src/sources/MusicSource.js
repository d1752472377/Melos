/**
 * 音源统一接口（抽象基类）。
 *
 * 标准歌曲对象：{ id, title, artist, album, artwork, platform }
 *   - id       源内唯一标识（如酷我的 rid、网易云的歌曲 id）
 *   - platform 音源实例 id（由注册表注入），App 取直链时回传
 *
 * 音质 quality 统一枚举（各源内部自行映射）：
 *   'standard' | 'high' | 'lossless'
 */
export class MusicSource {
  constructor({ id, name, type }) {
    this.id = id;
    this.name = name;
    this.type = type;
  }

  /** 搜索：返回标准歌曲数组 */
  async search(keyword, limit) {
    throw new Error(`${this.type}(${this.id}).search 未实现`);
  }

  /** 取播放直链：返回 { url, format, bitrate, headers? } */
  async getSongUrl(song, quality) {
    throw new Error(`${this.type}(${this.id}).getSongUrl 未实现`);
  }

  /** 取歌词：返回 lrc 文本，没有则返回 null */
  async getLyric(song) {
    return null;
  }

  /**
   * 歌手搜索：按关键词返回歌手数组 [{ id, name, avatar, alias }]
   * 不支持的源可不覆盖（registry 会跳过）。
   */
  async searchArtist(keyword, limit) {
    throw new Error(`${this.type}(${this.id}).searchArtist 未实现`);
  }

  /**
   * 歌手详情+热门歌曲：返回 { info: { id, name, avatar, alias }, songs: [标准歌曲对象] }
   * 不支持的源可不覆盖（registry 会跳过）。
   */
  async getArtist(artistId, limit) {
    throw new Error(`${this.type}(${this.id}).getArtist 未实现`);
  }

  /** 是否具备歌手能力（子类覆盖了对应方法即为 true） */
  get supportsArtist() {
    return false;
  }


  /** 自检（连通性探测），子类可覆盖 */
  async healthCheck() {
    try {
      const r = await this.search('test', 1);
      return { ok: Array.isArray(r), detail: Array.isArray(r) ? `返回 ${r.length} 条` : '返回非数组' };
    } catch (e) {
      return { ok: false, detail: e.message };
    }
  }
}
