import { requireUser } from '../lib/auth.js';
import { applySync, pullSync } from '../lib/db.js';

/**
 * 数据同步 API（多端 LWW + 墓碑）：
 *   GET  /api/sync/pull?since=<seq>&eventSince=<eventId>   增量拉取（含删除墓碑）
 *   POST /api/sync/push                                     上传增量变更
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

  app.post('/api/sync/push', { preHandler: allowAuth }, async (req) => {
    const body = req.body || {};
    const changes = {
      favorites: normalizeCollection(body.favorites),
      history: normalizeCollection(body.history),
      playlists: normalizeCollection(body.playlists),
      playlistSongs: normalizeCollection(body.playlistSongs),
      playEvents: Array.isArray(body.playEvents) ? body.playEvents : [],
    };
    const { version, acceptedEvents } = await applySync(client, req.user.id, changes, Date.now());
    return { code: 200, data: { version, acceptedEvents, serverTime: Date.now() } };
  });
}

function normalizeCollection(v) {
  if (!v || typeof v !== 'object') return { upsert: [], delete: [] };
  return {
    upsert: Array.isArray(v.upsert) ? v.upsert : [],
    delete: Array.isArray(v.delete) ? v.delete : [],
  };
}