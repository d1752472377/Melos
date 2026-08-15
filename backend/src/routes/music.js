/**
 * 业务 API（App 端调用，无需鉴权）：
 *   GET /api/search?keyword=&limit=
 *   GET /api/song/url?platform=&id=&title=&quality=
 *   GET /api/lyric?platform=&id=&title=
 *   GET /api/sources        （健康状态列表，便于排查）
 */
export function registerMusicRoutes(app, registry) {
  app.get('/api/search', async (req, reply) => {
    const { keyword } = req.query;
    if (!keyword) return reply.code(400).send({ code: 400, msg: '缺少 keyword' });
    const limit = Math.min(parseInt(req.query.limit || '20', 10) || 20, 50);
    const songs = await registry.search(String(keyword), limit);
    return { code: 200, data: songs };
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
