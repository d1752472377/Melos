import { requireUser } from '../lib/auth.js';
import { applySync, pullSync } from '../lib/db.js';

/** 单个音源脚本源码上限（1MB）；客户端另有更小的自我约束（512KB），本值只是服务端兜底 */
const MAX_SCRIPT_BYTES = 1024 * 1024;

/**
 * 数据同步 API（多端 LWW + 墓碑）：
 *   GET  /api/sync/pull?since=<seq>&eventSince=<eventId>   增量拉取（含删除墓碑）
 *   POST /api/sync/push                                     上传增量变更
 *
 * 集合清单（形状统一为 { upsert: [...], delete: [...] }）：
 *   favorites / history / playlists / playlistSongs / scripts（用户上传的音源脚本）/ downloads（分设备下载记录）
 *   —— 全部走「按记录 LWW + 墓碑」；playEvents 走幂等追加。
 *
 * 体积约束：单个音源脚本源码上限 1MB（超限整批 413，附可行动的错误信息），
 * 整个请求体另有 Fastify bodyLimit 4MB。
 *
 * 冲突语义：服务端收到请求即分配单调递增版本号（version = sync_seq），
 * 谁后推送谁就是“最新”，按记录粒度覆盖；删除以墓碑形式传播，不会被晚到的旧快照复活。
 */
export function registerSyncRoutes(app, { config, client }) {
  const allowAuth = requireUser(config, client);

  app.get('/api/sync/pull', { preHandler: allowAuth }, async (req) => {
    const since = Math.max(0, Number(req.query.since) || 0);
    const eventSince = Math.max(0, Number(req.query.eventSince) || 0);
    const data = await pullSync(client, req.user.id, { since, eventSince });
    return { code: 200, data };
  });

  app.post('/api/sync/push', { preHandler: allowAuth }, async (req, reply) => {
    const body = req.body || {};
    // 单脚本体积上限（整个请求体另有 4MB 的 bodyLimit）。
    // 为什么要在这里显式挡：bodyLimit 顶爆时客户端只拿到一个笼统的 413，
    // 无法知道"是哪一个脚本/哪一类数据"出了问题；而一次 push 是**整批**的，
    // 一个超大脚本会让同批的收藏/歌单一起推不上去。这里给出能行动的错误信息。
    // 客户端已有更小的自我约束（SyncMapper.MAX_SCRIPT_BYTES = 512KB），本上限是服务端兜底。
    const tooBig = (body.scripts?.upsert || [])
      .find((s) => typeof s?.code === 'string' && Buffer.byteLength(s.code, 'utf8') > MAX_SCRIPT_BYTES);
    if (tooBig) {
      return reply.code(413).send({
        code: 413,
        msg: `单个音源脚本过大（上限 ${Math.floor(MAX_SCRIPT_BYTES / 1024)}KB）：${String(tooBig.id || '(未命名)')}`,
      });
    }
    const changes = {
      favorites: normalizeCollection(body.favorites),
      history: normalizeCollection(body.history),
      playlists: normalizeCollection(body.playlists),
      playlistSongs: normalizeCollection(body.playlistSongs),
      scripts: normalizeCollection(body.scripts),
      downloads: normalizeCollection(body.downloads),
      playEvents: Array.isArray(body.playEvents) ? body.playEvents : [],
    };
    const { version, acceptedEvents } = await applySync(client, req.user.id, changes, Date.now());
    return { code: 200, data: { version, acceptedEvents, serverTime: Date.now() } };
  });
}

/**
 * 集合形状归一化：把任意脏输入（null / 字符串 / 缺 upsert / delete 不是数组）统一成 { upsert: [], delete: [] }。
 * 这里只管「形状」，不管条目内容——缺必填键的记录由 db 层逐条丢弃（写半条脏数据比整条丢掉更糟）。
 */
function normalizeCollection(v) {
  if (!v || typeof v !== 'object') return { upsert: [], delete: [] };
  return {
    upsert: Array.isArray(v.upsert) ? v.upsert : [],
    delete: Array.isArray(v.delete) ? v.delete : [],
  };
}