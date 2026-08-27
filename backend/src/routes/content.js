import { fetchWithTimeout } from '../lib/http.js';

/**
 * 内容体系 API（App 端调用，无需鉴权）：
 *   GET /api/daily        每日推荐（网易云个性化歌单，带本地内存缓存）
 *   GET /api/newsongs     新歌速递（网易云公开接口）
 *   GET /api/ranks        排行榜列表
 *   GET /api/rank/:id     排行榜歌曲（经 chksz/netease 歌单接口拉取）
 *   GET /api/categories   首页分类歌单（流行/电音/民谣/摇滚）
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

/**
 * 内存缓存：容量上限 + TTL 淘汰 + 并发合并（singleflight）。
 * 旧实现是无上限 Map：TTL 到期条目只跳过命中、从不删除（内存泄漏），
 * 且 rank:<id> 可被任意 id 撑爆内存；并发 miss 还会重复打上游。
 */
const CACHE = new Map();
const TTL_MS = 10 * 60 * 1000;
const MAX_CACHE_ENTRIES = 50;
const inflight = new Map(); // key -> Promise（进行中的加载，后续请求复用）

function trimCache() {
  if (CACHE.size <= MAX_CACHE_ENTRIES) return;
  const oldestFirst = [...CACHE.entries()].sort((a, b) => a[1].at - b[1].at);
  for (let i = 0; i < oldestFirst.length - MAX_CACHE_ENTRIES; i++) {
    CACHE.delete(oldestFirst[i][0]);
  }
}

function cached(key, loader) {
  const hit = CACHE.get(key);
  if (hit && Date.now() - hit.at < TTL_MS) return Promise.resolve(hit.value);
  if (inflight.has(key)) return inflight.get(key);
  const p = loader()
    .then((value) => {
      CACHE.set(key, { at: Date.now(), value });
      trimCache();
      return value;
    })
    .finally(() => inflight.delete(key));
  inflight.set(key, p);
  return p;
}

async function fetchJson(url, headers = {}) {
  const res = await fetchWithTimeout(url, {
    headers: {
      'User-Agent': 'Mozilla/5.0',
      'Referer': 'https://music.163.com',
      ...headers,
    },
  }, 10_000);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}

/** 网易云官方榜名称 → 稳定 kind，供 App 按语义取榜而不是 ranks[0] */
function classifyRankKind(name) {
  const n = String(name || '');
  if (n.includes('热歌')) return 'hot';
  if (n.includes('新歌')) return 'new';
  if (n.includes('飙升')) return 'rising';
  if (n.includes('原创')) return 'original';
  return 'other';
}

function rankKindOrder(kind) {
  switch (kind) {
    case 'hot': return 0;
    case 'new': return 1;
    case 'rising': return 2;
    case 'original': return 3;
    default: return 9;
  }
}

/** 首页分类歌单：标签走网易云公开 playlist/highquality，预览曲目走歌单详情前 3 首 */
const HOME_CATEGORIES = [
  { id: 'pop', title: '流行', cat: '流行', quote: '人生走在路上，愿你心花路放' },
  { id: 'edm', title: '电音', cat: '电子', quote: '跟随强劲节拍，释放自由灵魂' },
  { id: 'folk', title: '民谣', cat: '民谣', quote: '一把木吉他，诉说岁月温柔' },
  { id: 'rock', title: '摇滚', cat: '摇滚', quote: '热血不灭，唱响青春回响' },
];

function toPlaylistItem(p) {
  return {
    id: String(p.id),
    name: p.name || '',
    coverUrl: p.coverImgUrl || p.picUrl || null,
    playCount: p.playCount ?? null,
    trackCount: p.trackCount ?? null,
    platform: 'chksz',
  };
}

function previewLine(song) {
  const title = song?.title || '';
  const artist = song?.artist || '';
  if (title && artist) return `${title} - ${artist}`;
  return title || artist;
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
  // kind 给 App 首页「新歌榜 / 热歌榜」Tab 精确取数；旧客户端忽略该字段即可。
  app.get('/api/ranks', async (req, reply) => {
    try {
      const data = await cached('ranks:v2', async () => {
        const j = await fetchJson('https://music.163.com/api/toplist/detail');
        const list = (j.list || []).slice(0, 20).map((p) => {
          const name = p.name || '';
          return {
            id: String(p.id),
            name,
            coverUrl: p.coverImgUrl || p.picUrl || null,
            playCount: p.playCount ?? null,
            trackCount: p.trackCount ?? null,
            platform: 'chksz',
            kind: classifyRankKind(name),
          };
        });
        list.sort((a, b) => rankKindOrder(a.kind) - rankKindOrder(b.kind));
        return { title: '排行榜', ranks: list };
      });
      return { code: 200, data };
    } catch (e) {
      return reply.code(502).send({ code: 502, msg: `获取排行榜失败: ${e.message}` });
    }
  });

  // 首页分类歌单：每个分类取一张高质量歌单 + 前 3 首预览
  app.get('/api/categories', async (req, reply) => {
    try {
      const data = await cached('categories:v1', async () => {
        const categories = [];
        for (const def of HOME_CATEGORIES) {
          const j = await fetchJson(
            `https://music.163.com/api/playlist/highquality/list?cat=${encodeURIComponent(def.cat)}&limit=1`,
          );
          const raw = Array.isArray(j.playlists) ? j.playlists[0] : null;
          const playlist = raw ? toPlaylistItem(raw) : null;
          let previewSongs = [];
          if (playlist?.id) {
            try {
              const songs = await getPlaylistSongs(registry, playlist.id);
              previewSongs = songs.slice(0, 3).map(previewLine).filter(Boolean);
            } catch {
              previewSongs = [];
            }
          }
          categories.push({
            id: def.id,
            title: def.title,
            quote: def.quote,
            coverUrl: playlist?.coverUrl || null,
            previewSongs,
            playlist,
          });
        }
        return { title: '分类歌单', categories };
      });
      return { code: 200, data };
    } catch (e) {
      return reply.code(502).send({ code: 502, msg: `获取分类歌单失败: ${e.message}` });
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
