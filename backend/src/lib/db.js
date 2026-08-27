import { createClient } from '@libsql/client';
import { mkdirSync } from 'node:fs';
import { dirname } from 'node:path';

/**
 * 存储层：基于 @libsql/client（SQLite 分支，SQL 完全兼容）。
 *
 * 支持 DB_URL 两种形态（见 .env）：
 *   - file:/path/to/music.db      本地文件（单机部署，开发默认）
 *   - http://host:port            远端 libSQL 服务（sqld），分布式部署时
 *                                 多个业务节点共享同一中央数据库
 *
 * 表结构：
 * - sources         音源注册表（config 为 AES-GCM 加密 JSON）
 * - users           用户账号（scrypt 加盐密码）
 * - favorites       云端收藏
 * - history         云端最近播放
 * - playlists       我的歌单
 * - playlist_songs  歌单歌曲
 * - play_events     听歌统计明细（服务端聚合）
 */

const SCHEMA = `
    CREATE TABLE IF NOT EXISTS sources (
      id         TEXT PRIMARY KEY,
      name       TEXT NOT NULL,
      type       TEXT NOT NULL,
      enabled    INTEGER NOT NULL DEFAULT 1,
      weight     INTEGER NOT NULL DEFAULT 100,
      config     TEXT NOT NULL DEFAULT '',
      created_at TEXT DEFAULT (datetime('now')),
      updated_at TEXT DEFAULT (datetime('now'))
    );

    CREATE TABLE IF NOT EXISTS users (
      id            INTEGER PRIMARY KEY AUTOINCREMENT,
      username      TEXT NOT NULL UNIQUE,
      password_hash TEXT NOT NULL,
      salt          TEXT NOT NULL,
      nickname      TEXT NOT NULL DEFAULT '',
      avatar        TEXT NOT NULL DEFAULT '',
      created_at    TEXT DEFAULT (datetime('now')),
      updated_at    TEXT DEFAULT (datetime('now'))
    );

    CREATE TABLE IF NOT EXISTS favorites (
      user_id    INTEGER NOT NULL,
      platform   TEXT NOT NULL,
      song_id    TEXT NOT NULL,
      song_json  TEXT NOT NULL,
      created_at TEXT DEFAULT (datetime('now')),
      PRIMARY KEY (user_id, platform, song_id)
    );

    CREATE TABLE IF NOT EXISTS history (
      user_id    INTEGER NOT NULL,
      platform   TEXT NOT NULL,
      song_id    TEXT NOT NULL,
      song_json  TEXT NOT NULL,
      played_at  TEXT NOT NULL,
      PRIMARY KEY (user_id, platform, song_id)
    );

    CREATE TABLE IF NOT EXISTS playlists (
      id         TEXT PRIMARY KEY,
      user_id    INTEGER NOT NULL,
      name       TEXT NOT NULL,
      cover_url  TEXT NOT NULL DEFAULT '',
      created_at TEXT DEFAULT (datetime('now')),
      updated_at TEXT DEFAULT (datetime('now'))
    );

    CREATE TABLE IF NOT EXISTS playlist_songs (
      playlist_id TEXT NOT NULL,
      position    INTEGER NOT NULL DEFAULT 0,
      platform    TEXT NOT NULL,
      song_id     TEXT NOT NULL,
      song_json   TEXT NOT NULL,
      added_at    TEXT DEFAULT (datetime('now')),
      PRIMARY KEY (playlist_id, platform, song_id)
    );

    CREATE TABLE IF NOT EXISTS play_events (
      id         INTEGER PRIMARY KEY AUTOINCREMENT,
      user_id    INTEGER NOT NULL,
      platform   TEXT NOT NULL,
      song_id    TEXT NOT NULL,
      title      TEXT NOT NULL DEFAULT '',
      artist     TEXT NOT NULL DEFAULT '',
      listened_ms INTEGER NOT NULL DEFAULT 0,
      duration_ms INTEGER NOT NULL DEFAULT 0,
      day        TEXT NOT NULL,
      created_at TEXT DEFAULT (datetime('now'))
    );
    CREATE INDEX IF NOT EXISTS idx_play_events_user_day ON play_events(user_id, day);
    CREATE INDEX IF NOT EXISTS idx_history_user_time ON history(user_id, played_at);
`;

/**
 * 适配器：模拟 node:sqlite 的 prepare(sql).all/get/run 形态，
 * 业务函数无需改动 SQL，只需要 await。
 * 注意：libSQL 的 INTEGER 列返回 BigInt，JSON 序列化会抛错，统一归一化为 Number。
 */
class LibsqlDb {
  constructor(client) {
    this.client = client;
  }

  _normalize(rows) {
    return rows.map((row) => {
      const out = {};
      for (const [k, v] of Object.entries(row)) {
        out[k] = typeof v === 'bigint' ? Number(v) : v;
      }
      return out;
    });
  }

  prepare(sql) {
    return {
      all: async (...args) => {
        const rs = await this.client.execute({ sql, args });
        return this._normalize(rs.rows);
      },
      get: async (...args) => {
        const rs = await this.client.execute({ sql, args });
        return this._normalize(rs.rows)[0];
      },
      run: async (...args) => {
        const rs = await this.client.execute({ sql, args });
        return {
          changes: rs.rowsAffected,
          lastInsertRowid: rs.lastInsertRowid != null ? Number(rs.lastInsertRowid) : undefined,
        };
      },
    };
  }

  /** 批量执行 SQL（可开事务）。statements: [{ sql, args }]；mode: 'deferred'|'write'|'read' */
  batch(statements, mode = 'deferred') {
    return this.client.batch(statements, mode);
  }
}

export async function initDb(dbUrl, authToken) {
  // file: 模式确保父目录存在；远端模式跳过
  if (dbUrl.startsWith('file:')) {
    const p = dbUrl.slice('file:'.length);
    mkdirSync(dirname(p), { recursive: true });
  } else if (/^[a-z]:[\\/]/i.test(dbUrl) || dbUrl.startsWith('/')) {
    // 兼容直接传本地路径的旧用法
    mkdirSync(dirname(dbUrl), { recursive: true });
    dbUrl = `file:${dbUrl}`;
  }
  const client = createClient({ url: dbUrl, authToken });

  // 建表（幂等）。schema 无字符串内嵌分号，按 ';' 切分逐条执行。
  for (const stmt of SCHEMA.split(';').map((s) => s.trim()).filter(Boolean)) {
    await client.execute(stmt);
  }

  return new LibsqlDb(client);
}

// ==================== 音源 ====================

export async function listSources(db) {
  return db.prepare('SELECT id, name, type, enabled, weight, config, created_at, updated_at FROM sources ORDER BY weight DESC, id ASC').all();
}

export async function getSource(db, id) {
  return db.prepare('SELECT id, name, type, enabled, weight, config, created_at, updated_at FROM sources WHERE id = ?').get(id);
}

export async function insertSource(db, { id, name, type, enabled = 1, weight = 100, config = '' }) {
  await db.prepare('INSERT INTO sources (id, name, type, enabled, weight, config) VALUES (?, ?, ?, ?, ?, ?)')
    .run(id, name, type, enabled ? 1 : 0, weight, config);
  return getSource(db, id);
}

export async function updateSource(db, id, fields) {
  const cur = await getSource(db, id);
  if (!cur) return null;
  const next = {
    name: fields.name ?? cur.name,
    type: fields.type ?? cur.type,
    enabled: fields.enabled ?? cur.enabled,
    weight: fields.weight ?? cur.weight,
    config: fields.config ?? cur.config,
  };
  await db.prepare('UPDATE sources SET name = ?, type = ?, enabled = ?, weight = ?, config = ?, updated_at = datetime(\'now\') WHERE id = ?')
    .run(next.name, next.type, next.enabled ? 1 : 0, next.weight, next.config, id);
  return getSource(db, id);
}

export async function deleteSource(db, id) {
  return db.prepare('DELETE FROM sources WHERE id = ?').run(id);
}

// ==================== 用户 ====================

export async function getUserByUsername(db, username) {
  return db.prepare('SELECT * FROM users WHERE username = ?').get(username);
}

export async function getUserById(db, id) {
  return db.prepare('SELECT * FROM users WHERE id = ?').get(id);
}

export async function insertUser(db, { username, passwordHash, salt, nickname }) {
  const r = await db.prepare('INSERT INTO users (username, password_hash, salt, nickname) VALUES (?, ?, ?, ?)')
    .run(username, passwordHash, salt, nickname || username);
  return getUserById(db, Number(r.lastInsertRowid));
}

export async function updateUserProfile(db, id, { nickname, avatar }) {
  const cur = await getUserById(db, id);
  if (!cur) return null;
  await db.prepare('UPDATE users SET nickname = ?, avatar = ?, updated_at = datetime(\'now\') WHERE id = ?')
    .run(
      nickname ?? cur.nickname,
      avatar ?? cur.avatar,
      id,
    );
  return getUserById(db, id);
}

export function publicUser(row) {
  if (!row) return null;
  return { id: row.id, username: row.username, nickname: row.nickname || row.username, avatar: row.avatar || '' };
}

// ==================== 收藏 / 历史（整表替换同步） ====================

/**
 * 整表替换（收藏 / 注释：历史走 mergeHistory，见下）。
 * 用单个 write 事务包裹 DELETE + 全部 INSERT，避免旧实现"先删后插、非原子"：
 * 中途失败/崩溃/并发时不再清空用户数据。
 */
async function insertSongs(db, table, userId, songs, timeCol) {
  const stmts = [{ sql: `DELETE FROM ${table} WHERE user_id = ?`, args: [userId] }];
  if (Array.isArray(songs)) {
    // 逆序插入：调用方约定"数组首元素最新"。rowid 单调递增、读取按 rowid DESC，
    // 因此最后插入的（数组首元素）rowid 最大，读回时即"新在前"，顺序不反转。
    const list = songs;
    for (let i = list.length - 1; i >= 0; i--) {
      const s = list[i];
      if (!s || !s.platform || !s.id) continue;
      stmts.push({
        sql: `INSERT OR REPLACE INTO ${table} (user_id, platform, song_id, song_json, ${timeCol}) VALUES (?, ?, ?, ?, ?)`,
        args: [userId, String(s.platform), String(s.id), JSON.stringify(s), datetimeNow()],
      });
    }
  }
  // 只有 DELETE + 至少一条 INSERT 才需要真的执行；空列表时仅清空
  if (stmts.length > 1) await db.batch(stmts, 'write');
}

/**
 * 读取歌曲列表。排序用 rowid DESC：
 * 替换写入是"整表重插"，rowid 单调递增即代表数组顺序（最新在前），
 * 比 played_at/created_at 时间戳可靠（整表重插时时间戳相同，排序会退化）。
 */
async function listSongs(db, table, userId, limit = 500) {
  const rows = await db.prepare(`SELECT song_json FROM ${table} WHERE user_id = ? ORDER BY rowid DESC LIMIT ?`).all(userId, limit);
  const out = [];
  for (const r of rows) {
    try {
      out.push(JSON.parse(r.song_json));
    } catch {
      // 单条损坏不应拖垮整组数据（旧实现会 500 且用户无法恢复）
    }
  }
  return out;
}

function datetimeNow() {
  return new Date().toISOString();
}

export function replaceFavorites(db, userId, songs) {
  return insertSongs(db, 'favorites', userId, songs, 'created_at');
}

export function listFavorites(db, userId) {
  return listSongs(db, 'favorites', userId);
}

/**
 * 历史合并同步（多设备友好）：新提交的列表置前，与云端已有记录按
 * (platform, song_id) 去重合并，再整表写回。设备 A、B 互相推送时不再互相清空。
 * 上限 300 条，超出丢弃最旧。
 */
export async function mergeHistory(db, userId, songs) {
  const existing = await listSongs(db, 'history', userId, 300);
  const seen = new Set();
  const merged = [];
  for (const s of [...(Array.isArray(songs) ? songs : []), ...existing]) {
    if (!s || !s.platform || !s.id) continue;
    const key = `${s.platform}:${s.id}`;
    if (seen.has(key)) continue;
    seen.add(key);
    merged.push(s);
  }
  await insertSongs(db, 'history', userId, merged.slice(0, 300), 'played_at');
  return listSongs(db, 'history', userId);
}

export function listHistory(db, userId) {
  return listSongs(db, 'history', userId);
}

// ==================== 我的歌单 ====================

export async function createPlaylist(db, userId, { id, name, coverUrl = '' }) {
  const pid = id || `pl_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 8)}`;
  await db.prepare('INSERT INTO playlists (id, user_id, name, cover_url) VALUES (?, ?, ?, ?)')
    .run(pid, userId, name, coverUrl || '');
  return getPlaylist(db, userId, pid);
}

export async function getPlaylist(db, userId, id) {
  const p = await db.prepare('SELECT * FROM playlists WHERE id = ? AND user_id = ?').get(id, userId);
  if (!p) return null;
  const cnt = await db.prepare('SELECT COUNT(*) AS c FROM playlist_songs WHERE playlist_id = ?').get(id);
  const cover = await db.prepare('SELECT song_json FROM playlist_songs WHERE playlist_id = ? ORDER BY position ASC LIMIT 1').get(id);
  let coverUrl = p.cover_url;
  if (!coverUrl && cover) {
    try { coverUrl = JSON.parse(cover.song_json).artwork || ''; } catch { coverUrl = ''; }
  }
  return {
    id: p.id,
    name: p.name,
    coverUrl,
    songCount: cnt?.c || 0,
    createdAt: p.created_at,
    updatedAt: p.updated_at,
  };
}

export async function listPlaylists(db, userId) {
  const rows = await db.prepare('SELECT * FROM playlists WHERE user_id = ? ORDER BY updated_at DESC').all(userId);
  const out = [];
  for (const p of rows) out.push(await getPlaylist(db, userId, p.id));
  return out;
}

export async function updatePlaylistMeta(db, userId, id, { name, coverUrl }) {
  const cur = await db.prepare('SELECT * FROM playlists WHERE id = ? AND user_id = ?').get(id, userId);
  if (!cur) return null;
  await db.prepare('UPDATE playlists SET name = ?, cover_url = ?, updated_at = datetime(\'now\') WHERE id = ?')
    .run(name ?? cur.name, coverUrl ?? cur.cover_url, id);
  return getPlaylist(db, userId, id);
}

export async function deletePlaylist(db, userId, id) {
  // 歌单与歌曲删除放同一事务，避免删一半留下孤儿歌曲
  await db.batch([
    { sql: 'DELETE FROM playlist_songs WHERE playlist_id = ?', args: [id] },
    { sql: 'DELETE FROM playlists WHERE id = ? AND user_id = ?', args: [id, userId] },
  ], 'write');
  return { changes: 1 };
}

export async function replacePlaylistSongs(db, userId, id, songs) {
  const cur = await db.prepare('SELECT id FROM playlists WHERE id = ? AND user_id = ?').get(id, userId);
  if (!cur) return null;
  const stmts = [{ sql: 'DELETE FROM playlist_songs WHERE playlist_id = ?', args: [id] }];
  let i = 0;
  for (const s of (Array.isArray(songs) ? songs : [])) {
    if (!s || !s.platform || !s.id) continue;
    stmts.push({
      sql: 'INSERT INTO playlist_songs (playlist_id, position, platform, song_id, song_json) VALUES (?, ?, ?, ?, ?)',
      args: [id, i, String(s.platform), String(s.id), JSON.stringify(s)],
    });
    i++;
  }
  if (stmts.length > 1) await db.batch(stmts, 'write');
  await db.prepare('UPDATE playlists SET updated_at = datetime(\'now\') WHERE id = ?').run(id);
  return getPlaylist(db, userId, id);
}

export async function addPlaylistSongs(db, userId, id, songs) {
  const cur = await db.prepare('SELECT id FROM playlists WHERE id = ? AND user_id = ?').get(id, userId);
  if (!cur) return null;
  const existing = await listPlaylistSongs(db, userId, id);
  return replacePlaylistSongs(db, userId, id, [...existing, ...(Array.isArray(songs) ? songs : [])]);
}

export async function removePlaylistSong(db, userId, id, platform, songId) {
  const cur = await db.prepare('SELECT id FROM playlists WHERE id = ? AND user_id = ?').get(id, userId);
  if (!cur) return null;
  await db.prepare('DELETE FROM playlist_songs WHERE playlist_id = ? AND platform = ? AND song_id = ?').run(id, platform, songId);
  await db.prepare('UPDATE playlists SET updated_at = datetime(\'now\') WHERE id = ?').run(id);
  return getPlaylist(db, userId, id);
}

export async function listPlaylistSongs(db, userId, id) {
  const cur = await db.prepare('SELECT id FROM playlists WHERE id = ? AND user_id = ?').get(id, userId);
  if (!cur) return null;
  const rows = await db.prepare('SELECT song_json FROM playlist_songs WHERE playlist_id = ? ORDER BY position ASC').all(id);
  return rows.map((r) => JSON.parse(r.song_json));
}

// ==================== 听歌统计 ====================

export async function insertPlayEvents(db, userId, events) {
  const ins = db.prepare(
    'INSERT INTO play_events (user_id, platform, song_id, title, artist, listened_ms, duration_ms, day) VALUES (?, ?, ?, ?, ?, ?, ?, ?)'
  );
  const day = new Date().toISOString().slice(0, 10);
  let n = 0;
  // 单批条数上限：防登录用户用超大 events 数组做存储 DoS
  const capped = (Array.isArray(events) ? events : []).slice(0, 200);
  for (const e of capped) {
    if (!e) continue;
    const listened = Math.max(0, Math.min(Number(e.listenedMs) || 0, 4 * 60 * 60 * 1000));
    if (listened <= 0) continue;
    await ins.run(
      userId,
      String(e.platform || ''),
      String(e.id || ''),
      String(e.title || ''),
      String(e.artist || ''),
      Math.round(listened),
      Math.max(0, Number(e.durationMs) || 0),
      day,
    );
    n++;
  }
  return n;
}

/** 清理 days 天前的听歌统计明细（防表无限膨胀）。返回删除行数。 */
export async function pruneOldPlayEvents(db, days = 90) {
  const cutoff = new Date(Date.now() - days * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
  try {
    const r = await db.prepare('DELETE FROM play_events WHERE day < ?').run(cutoff);
    return r.changes || 0;
  } catch {
    return 0;
  }
}

export async function getUserStats(db, userId) {
  const day = new Date().toISOString().slice(0, 10);
  const weekStart = new Date(Date.now() - 6 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
  const agg = await db.prepare(
    'SELECT COUNT(*) AS plays, COALESCE(SUM(listened_ms), 0) AS total FROM play_events WHERE user_id = ?'
  ).get(userId);
  const today = await db.prepare(
    'SELECT COALESCE(SUM(listened_ms), 0) AS total FROM play_events WHERE user_id = ? AND day = ?'
  ).get(userId, day);
  const week = await db.prepare(
    'SELECT COALESCE(SUM(listened_ms), 0) AS total FROM play_events WHERE user_id = ? AND day >= ?'
  ).get(userId, weekStart);
  const topSongs = await db.prepare(
    `SELECT platform, song_id AS id, MAX(title) AS title, MAX(artist) AS artist,
            SUM(listened_ms) AS listenedMs, COUNT(*) AS plays
     FROM play_events WHERE user_id = ? GROUP BY platform, song_id
     ORDER BY listenedMs DESC LIMIT 20`
  ).all(userId);
  const topArtists = await db.prepare(
    `SELECT artist, SUM(listened_ms) AS listenedMs, COUNT(*) AS plays
     FROM play_events WHERE user_id = ? AND artist != '' GROUP BY artist
     ORDER BY listenedMs DESC LIMIT 10`
  ).all(userId);
  const weekChart = await db.prepare(
    `SELECT day, COALESCE(SUM(listened_ms), 0) AS listenedMs FROM play_events
     WHERE user_id = ? AND day >= ? GROUP BY day ORDER BY day ASC`
  ).all(userId, weekStart);
  return {
    totalPlayMs: agg.total || 0,
    totalPlays: agg.plays || 0,
    todayPlayMs: today.total || 0,
    weekPlayMs: week.total || 0,
    topSongs,
    topArtists,
    weekChart,
  };
}
