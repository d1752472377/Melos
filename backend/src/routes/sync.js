import { verifyToken } from '../lib/auth.js';
import {
  getActiveScript,
  listScriptsFull,
  listFavorites,
  replaceFavorites,
  listHistory,
  mergeHistory,
  listPlaylists,
  listPlaylistSongs,
  createPlaylist,
  replacePlaylistSongs,
  deletePlaylist,
} from '../lib/db.js';

// 单租户固定 Owner ID
const SINGLE_TENANT_USER_ID = 1;

export function registerSyncRoutes(app, { config, db }) {
  function checkSyncAuth(req, reply) {
    const authHeader = req.headers['authorization'] || '';
    const syncTokenHeader = req.headers['x-sync-token'] || '';

    let token = '';
    if (authHeader.startsWith('Bearer ')) {
      token = authHeader.slice(7).trim();
    } else if (syncTokenHeader) {
      token = syncTokenHeader.trim();
    }

    if (token && (token === config.syncToken || token === config.adminPassword)) {
      return true;
    }

    if (token && verifyToken(token, config.encryptionKey)) {
      return true;
    }

    reply.status(401).send({ code: 401, msg: '未授权：同步密钥无效' });
    return false;
  }

  // 1. 连通性测试
  app.get('/api/sync/ping', async (req, reply) => {
    if (!checkSyncAuth(req, reply)) return;
    return {
      code: 200,
      msg: 'pong',
      data: {
        serverTime: Date.now(),
        syncEnabled: true,
      },
    };
  });

  // 2. 拉取全量云端数据（音源脚本 + 歌单 + 收藏 + 历史）
  app.get('/api/sync/pull', async (req, reply) => {
    if (!checkSyncAuth(req, reply)) return;

    // 音源脚本
    const activeScript = await getActiveScript(db);
    // 全部脚本（含 content）：客户端下载后可在本地「设置音源」里直接切换，无需回服务端改激活
    const allScripts = await listScriptsFull(db);

    // 收藏
    const favorites = await listFavorites(db, SINGLE_TENANT_USER_ID);

    // 历史
    const history = await listHistory(db, SINGLE_TENANT_USER_ID);

    // 歌单及曲目
    const playlists = await listPlaylists(db, SINGLE_TENANT_USER_ID);
    const fullPlaylists = await Promise.race([
      Promise.all(
        playlists.map(async (pl) => ({
          ...pl,
          songs: await listPlaylistSongs(db, pl.id),
        }))
      ),
      new Promise((r) => setTimeout(() => r([]), 5000)),
    ]);

    return {
      code: 200,
      msg: '拉取成功',
      data: {
        script: activeScript
          ? {
              id: activeScript.id,
              name: activeScript.name,
              version: activeScript.version,
              content: activeScript.content,
              status: activeScript.status,
              statusMessage: activeScript.status_message,
              lastCheckedAt: activeScript.last_checked_at,
              updatedAt: activeScript.updated_at,
            }
          : null,
        scripts: (allScripts || []).map((s) => ({
          id: s.id,
          name: s.name,
          version: s.version,
          description: s.description || '',
          content: s.content || '',
          isActive: s.is_active === 1,
          status: s.status,
          statusMessage: s.status_message,
          updatedAt: s.updated_at,
        })),
        favorites: favorites || [],
        history: history || [],
        playlists: fullPlaylists || [],
        syncedAt: new Date().toISOString(),
      },
    };
  });

  // 3. 上传本地数据合并至云端
  app.post('/api/sync/push', async (req, reply) => {
    if (!checkSyncAuth(req, reply)) return;

    const { favorites, history, playlists } = req.body || {};

    // 同步收藏
    if (Array.isArray(favorites)) {
      await replaceFavorites(db, SINGLE_TENANT_USER_ID, favorites);
    }

    // 同步最近播放（合并）
    if (Array.isArray(history)) {
      await mergeHistory(db, SINGLE_TENANT_USER_ID, history);
    }

    // 同步歌单
    if (Array.isArray(playlists)) {
      // 简单策略：遍历传入歌单，更新或创建
      const existing = await listPlaylists(db, SINGLE_TENANT_USER_ID);
      const existingIds = new Set(existing.map((p) => p.id));
      const incomingIds = new Set(playlists.map((p) => p.id));

      for (const pl of playlists) {
        if (!pl.id || !pl.name) continue;
        if (!existingIds.has(pl.id)) {
          await createPlaylist(db, {
            id: pl.id,
            userId: SINGLE_TENANT_USER_ID,
            name: pl.name,
            coverUrl: pl.coverUrl || '',
          });
        }
        if (Array.isArray(pl.songs)) {
          await replacePlaylistSongs(db, pl.id, pl.songs);
        }
      }

      // 如果客户端明确提交完整歌单列表，可清理已被客户端删除的歌单
      for (const old of existing) {
        if (!incomingIds.has(old.id)) {
          await deletePlaylist(db, old.id, SINGLE_TENANT_USER_ID);
        }
      }
    }

    return {
      code: 200,
      msg: '同步数据保存成功',
      data: {
        syncedAt: new Date().toISOString(),
      },
    };
  });
}
