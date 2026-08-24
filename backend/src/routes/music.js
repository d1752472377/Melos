/**
 * 业务 API（App 端调用，无需鉴权）：
 *   GET /api/search?keyword=&limit=&platform=      （聚合搜索；platform 可选：只搜指定源）
 *   GET /api/search/hot                             （热搜词，netease 官方）
 *   GET /api/search/playlist?keyword=&limit=        （歌单搜索，netease）
 *   GET /api/search/album?keyword=&limit=           （专辑搜索，netease）
 *   GET /api/search/lyric?keyword=&limit=           （歌词关键词搜歌，netease）
 *   GET /api/album?id=                              （专辑详情+曲目，netease）
 *   GET /api/song/url?platform=&id=&title=&quality=
 *   GET /api/lyric?platform=&id=&title=
 *   GET /api/artist/search?keyword=&limit=     （歌手搜索）
 *   GET /api/artist?platform=&id=&limit=       （歌手详情+热门歌曲）
 *   GET /api/sources        （健康状态列表，便于排查）
 */
export function registerMusicRoutes(app, registry) {
  app.get('/api/search', async (req, reply) => {
    const { keyword } = req.query;
    if (!keyword) return reply.code(400).send({ code: 400, msg: '缺少 keyword' });
    const limit = Math.min(parseInt(req.query.limit || '20', 10) || 20, 50);
    const platform = req.query.platform ? String(req.query.platform) : null;
    const songs = await registry.search(String(keyword), limit, platform);
    return { code: 200, data: songs };
  });

  // 热搜词（App 搜索页"热门搜索"）；无 netease 源时返回兜底空列表而非报错
  app.get('/api/search/hot', async () => {
    const source = registry.getSourceByType('netease');
    if (!source || !source.hotSearchWords) return { code: 200, data: [] };
    try {
      const words = await source.hotSearchWords(12);
      return { code: 200, data: words };
    } catch {
      return { code: 200, data: [] };
    }
  });

  // 歌单搜索（netease type=1000）
  app.get('/api/search/playlist', async (req, reply) => {
    const { keyword } = req.query;
    if (!keyword) return reply.code(400).send({ code: 400, msg: '缺少 keyword' });
    const source = registry.getSourceByType('netease');
    if (!source || !source.searchPlaylists) {
      return reply.code(503).send({ code: 503, msg: '没有支持歌单搜索的可用音源（需启用 netease）' });
    }
    const limit = Math.min(parseInt(req.query.limit || '20', 10) || 20, 50);
    try {
      const list = await source.searchPlaylists(String(keyword), limit);
      return { code: 200, data: list };
    } catch (e) {
      return reply.code(502).send({ code: 502, msg: `歌单搜索失败: ${e.message}` });
    }
  });

  // 专辑搜索（netease type=10）
  app.get('/api/search/album', async (req, reply) => {
    const { keyword } = req.query;
    if (!keyword) return reply.code(400).send({ code: 400, msg: '缺少 keyword' });
    const source = registry.getSourceByType('netease');
    if (!source || !source.searchAlbums) {
      return reply.code(503).send({ code: 503, msg: '没有支持专辑搜索的可用音源（需启用 netease）' });
    }
    const limit = Math.min(parseInt(req.query.limit || '20', 10) || 20, 50);
    try {
      const list = await source.searchAlbums(String(keyword), limit);
      return { code: 200, data: list };
    } catch (e) {
      return reply.code(502).send({ code: 502, msg: `专辑搜索失败: ${e.message}` });
    }
  });

  // 歌词关键词搜歌（netease type=1006）
  app.get('/api/search/lyric', async (req, reply) => {
    const { keyword } = req.query;
    if (!keyword) return reply.code(400).send({ code: 400, msg: '缺少 keyword' });
    const source = registry.getSourceByType('netease');
    if (!source || !source.searchByLyric) {
      return reply.code(503).send({ code: 503, msg: '没有支持歌词搜索的可用音源（需启用 netease）' });
    }
    const limit = Math.min(parseInt(req.query.limit || '20', 10) || 20, 50);
    try {
      const songs = await source.searchByLyric(String(keyword), limit);
      return { code: 200, data: songs };
    } catch (e) {
      return reply.code(502).send({ code: 502, msg: `歌词搜索失败: ${e.message}` });
    }
  });

  // 专辑详情 + 曲目列表（点击专辑搜索结果时拉取）
  app.get('/api/album', async (req, reply) => {
    const { id } = req.query;
    if (!id) return reply.code(400).send({ code: 400, msg: '缺少 id' });
    const source = registry.getSourceByType('netease');
    if (!source || !source.getAlbum) {
      return reply.code(503).send({ code: 503, msg: '没有支持专辑详情的可用音源（需启用 netease）' });
    }
    try {
      const detail = await source.getAlbum(String(id));
      return { code: 200, data: detail };
    } catch (e) {
      return reply.code(502).send({ code: 502, msg: `获取专辑失败: ${e.message}` });
    }
  });

  app.get('/api/song/url', async (req, reply) => {
    const { platform, id, title } = req.query;
    if (!platform || !id || !title) {
      return reply.code(400).send({ code: 400, msg: '需要 platform / id / title 参数' });
    }
    const song = {
      platform: String(platform),
      id: String(id),
      title: String(title),
      artist: req.query.artist ? String(req.query.artist) : undefined,
      album: req.query.album ? String(req.query.album) : undefined,
    };
    const result = await registry.getSongUrl(song, String(req.query.quality || 'lossless'));
    if (!result.url) {
      return reply.code(502).send({ code: 502, msg: '取直链失败', errors: result.errors });
    }
    return { code: 200, data: result };
  });

  app.get('/api/lyric', async (req, reply) => {
    const { platform, id, title } = req.query;
    if (!platform || !id || !title) {
      return reply.code(400).send({ code: 400, msg: '需要 platform / id / title 参数' });
    }
    const song = { platform: String(platform), id: String(id), title: String(title) };
    const result = await registry.getLyric(song);
    return { code: 200, data: result };
  });

  app.get('/api/sources', async () => ({ code: 200, data: registry.list() }));

  // 歌手搜索（当前仅 netease 源支持）
  app.get('/api/artist/search', async (req, reply) => {
    const { keyword } = req.query;
    if (!keyword) return reply.code(400).send({ code: 400, msg: '缺少 keyword' });
    const source = registry.getArtistSource(req.query.platform ? String(req.query.platform) : undefined);
    if (!source) return reply.code(503).send({ code: 503, msg: '没有支持歌手搜索的可用音源（需启用 netease）' });
    const limit = Math.min(parseInt(req.query.limit || '8', 10) || 8, 30);
    try {
      const artists = await source.searchArtist(String(keyword), limit);
      return { code: 200, data: artists };
    } catch (e) {
      return reply.code(502).send({ code: 502, msg: `歌手搜索失败: ${e.message}` });
    }
  });

  // 歌手详情 + 热门歌曲
  app.get('/api/artist', async (req, reply) => {
    const { id } = req.query;
    if (!id) return reply.code(400).send({ code: 400, msg: '缺少 id' });
    // 歌手 ID 是平台内标识，必须用对应平台的源；未指定 platform 时回退到任一具备能力的源
    const source = req.query.platform
      ? registry.getArtistSource(String(req.query.platform))
      : registry.getArtistSource();
    if (!source) return reply.code(503).send({ code: 503, msg: '没有支持歌手详情的可用音源（需启用 netease）' });
    const limit = Math.min(parseInt(req.query.limit || '50', 10) || 50, 100);
    try {
      const detail = await source.getArtist(String(id), limit);
      return { code: 200, data: detail };
    } catch (e) {
      return reply.code(502).send({ code: 502, msg: `获取歌手失败: ${e.message}` });
    }
  });

  // 歌单详情（当前支持网易云歌单 ID）
  app.get('/api/playlist', async (req, reply) => {
    const { id } = req.query;
    if (!id) return reply.code(400).send({ code: 400, msg: '缺少 id' });
    // 优先 chksz（能返回完整歌曲），其次 netease
    const source = registry.getSourceByType('chksz') || registry.getSourceByType('netease');
    if (!source) return reply.code(503).send({ code: 503, msg: '网易云音源不可用' });
    try {
      const songs = await source.getPlaylist(String(id));
      return { code: 200, data: songs };
    } catch (e) {
      return reply.code(502).send({ code: 502, msg: `获取歌单失败: ${e.message}` });
    }
  });

  // 热门歌单列表（网易云）
  app.get('/api/playlist/hot', async (req, reply) => {
    const source = registry.getSourceByType('chksz') || registry.getSourceByType('netease');
    if (!source) return reply.code(503).send({ code: 503, msg: '网易云音源不可用' });
    try {
      const limit = Math.min(parseInt(req.query.limit || '20', 10) || 20, 50);
      const list = await source.getHotPlaylists(limit);
      return { code: 200, data: list };
    } catch (e) {
      return reply.code(502).send({ code: 502, msg: `获取热门歌单失败: ${e.message}` });
    }
  });
}
