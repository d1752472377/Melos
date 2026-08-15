/**
 * 内容体系 API（App 端调用，无需鉴权）：
 *   GET /api/daily        每日推荐（网易云个性化歌单，带本地内存缓存）
 *   GET /api/newsongs     新歌速递（网易云公开接口）
 *   GET /api/ranks        排行榜列表
 *   GET /api/rank/:id     排行榜歌曲（经 chksz/netease 歌单接口拉取）
 *
 * 说明：每日推荐强依赖登录 cookie，公开部署下退化为“个性化歌单精选”，
 * 接口形状与推荐位一致，后续有网易云会员 cookie 时可直接替换数据源。
 */
async function getPlaylistSongs(registry, id) {
  const source = registry.getSourceByType('chksz') || registry.getSourceByType('netease');
  if (!source) throw new Error('可用音源不足');
  const songs = await source.getPlaylist(id);
  if (!Array.isArray(songs)) throw new Error('返回数据格式错误');
  return songs.slice(0, 50);
}

const CACHE = new Map();
const TTL_MS = 10 * 60 * 1000;

function cached(key, loader) {
  const hit = CACHE.get(key);
  if (hit && Date.now() - hit.at < TTL_MS) return Promise.resolve(hit.value);
  return loader().then((value) => {
    CACHE.set(key, { at: Date.now(), value });
    return value;
  });
}

async function fetchJson(url, headers = {}) {
  const res = await fetch(url, {
    headers: {
      'User-Agent': 'Mozilla/5.0',
      'Referer': 'https://music.163.com',
      ...headers,
    },
  });
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

function neteaseSongToStandard(s, platform = 'chksz') {
  const artists = Array.isArray(s.artists)
    ? s.artists.map((a) => (typeof a === 'object' ? a.name : String(a))).join('/')
    : Array.isArray(s.ar)
      ? s.ar.map((a) => (typeof a === 'object' ? a.name : String(a))).join('/')
      : (s.artist || '');
  const album = s.album && typeof s.album === 'object' ? s.album.name : (s.al && s.al.name ? s.al.name : (s.album || ''));
  const artwork = s.picUrl || (s.album && s.album.picUrl) || (s.al && s.al.picUrl) || null;
  return {
    id: String(s.id),
    title: s.name || '',
    artist: artists,
    album,
    artwork: artwork && artwork.startsWith('//') ? `https:${artwork}` : artwork,
    platform,
  };
}

export function registerContentRoutes(app, registry) {
  // 每日推荐：个性化歌单精选（网易云公开接口，无 cookie 可用）
  app.get('/api/daily', async (req, reply) => {
    try {
      const data = await cached('daily', async () => {
        const limit = Math.min(parseInt(req.query.limit || '8', 10) || 8, 20);
        const j = await fetchJson(`https://music.163.com/api/personalized/playlist?limit=${limit}`);
        const list = (j.result || []).map((p) => ({
          id: String(p.id),
          name: p.name || '',
          coverUrl: p.picUrl || null,
          playCount: p.playCount ?? null,
          trackCount: p.trackCount ?? null,
          platform: 'chksz',
        }));
        return { title: '每日推荐', playlists: list };
      });
      return { code: 200, data };
    } catch (e) {
      return reply.code(502).send({ code: 502, msg: `获取每日推荐失败: ${e.message}` });
    }
  });

  // 新歌速递
  app.get('/api/newsongs', async (req, reply) => {
    try {
      const data = await cached('newsongs', async () => {
        const limit = Math.min(parseInt(req.query.limit || '20', 10) || 20, 40);
        const j = await fetchJson(`https://music.163.com/api/personalized/newsong?limit=${limit}`);
        const songs = (j.result || []).map((s) => neteaseSongToStandard(s, 'chksz'));
        return { title: '新歌速递', songs };
      });
      return { code: 200, data };
    } catch (e) {
      return reply.code(502).send({ code: 502, msg: `获取新歌速递失败: ${e.message}` });
    }
  });

  // 排行榜列表
  app.get('/api/ranks', async (req, reply) => {
    try {
      const data = await cached('ranks', async () => {
        const j = await fetchJson('https://music.163.com/api/toplist/detail');
        const list = (j.list || []).slice(0, 20).map((p) => ({
          id: String(p.id),
          name: p.name || '',
          coverUrl: p.coverImgUrl || p.picUrl || null,
          playCount: p.playCount ?? null,
          trackCount: p.trackCount ?? null,
          platform: 'chksz',
        }));
        return { title: '排行榜', ranks: list };
      });
      return { code: 200, data };
    } catch (e) {
      return reply.code(502).send({ code: 502, msg: `获取排行榜失败: ${e.message}` });
    }
  });

  // 排行榜/歌单歌曲：优先 chksz 完整音源
  app.get('/api/rank/:id', async (req, reply) => {
    const { id } = req.params;
    if (!id) return reply.code(400).send({ code: 400, msg: '缺少 id' });
    try {
      const songs = await cached(`rank:${id}`, () => getPlaylistSongs(registry, String(id)));
      return { code: 200, data: songs };
    } catch (e) {
      return reply.code(502).send({ code: 502, msg: `获取榜单歌曲失败: ${e.message}` });
    }
  });
}
