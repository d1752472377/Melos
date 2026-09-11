import { createClient } from '@libsql/client';
import { mkdirSync } from 'node:fs';
import { dirname, isAbsolute } from 'node:path';
import crypto from 'node:crypto';

/**
 * 存储层：基于 @libsql/client（SQLite 兼容，支持本地 file: 与远端 libsql:// / http://）。
 *
 * 设计要点：
 * - 单一职责：只存「用户 + 会话 + 同步数据」，不存任何音源配置。
 * - 多端同步采用「按记录 last-write-wins + 删除墓碑」：
 *     * 每条记录带 update_seq（服务端分配的单调递增序号）与 updated_at_ms（服务端收包时间）。
 *     * 谁后推送谁就是最新版本（update_seq 大者胜）；删除记录以 deleted=1 的墓碑形式保留，
 *       保证设备 A 删除后，晚一步推送旧快照的设备 B 不会把已删项“复活”。
 *     * 客户端用 version（即服务端 sync_seq）做增量拉取游标。
 * - play_events 是 append-only 事件（不参与 LWW），用自增 id 做游标、event_id 做幂等去重。
 */

const SCHEMA = `
    CREATE TABLE IF NOT EXISTS users (
      id            INTEGER PRIMARY KEY AUTOINCREMENT,
      username      TEXT NOT NULL UNIQUE,
      password_hash TEXT NOT NULL,
      salt          TEXT NOT NULL,
      nickname      TEXT NOT NULL DEFAULT '',
      avatar        TEXT NOT NULL DEFAULT '',
      role          TEXT NOT NULL DEFAULT 'user',
      created_at    INTEGER NOT NULL,
      updated_at    INTEGER NOT NULL
    );

    CREATE TABLE IF NOT EXISTS sessions (
      id            TEXT PRIMARY KEY,
      user_id       INTEGER NOT NULL,
      token_hash    TEXT NOT NULL UNIQUE,
      device_name   TEXT NOT NULL DEFAULT '',
      created_at    INTEGER NOT NULL,
      last_seen_at  INTEGER NOT NULL,
      expires_at    INTEGER NOT NULL,
      revoked_at    INTEGER
    );
    CREATE INDEX IF NOT EXISTS idx_sessions_user    ON sessions(user_id);
    CREATE INDEX IF NOT EXISTS idx_sessions_expires ON sessions(expires_at);

    -- 每用户一个单调递增的同步序号（sync_seq），作为 LWW 版本游标
    CREATE TABLE IF NOT EXISTS user_state (
      user_id  INTEGER PRIMARY KEY,
      sync_seq INTEGER NOT NULL DEFAULT 0
    );

    CREATE TABLE IF NOT EXISTS favorites (
      user_id       INTEGER NOT NULL,
      platform      TEXT NOT NULL,
      song_id       TEXT NOT NULL,
      song_json     TEXT NOT NULL DEFAULT '',
      update_seq    INTEGER NOT NULL DEFAULT 0,
      updated_at_ms INTEGER NOT NULL DEFAULT 0,
      deleted       INTEGER NOT NULL DEFAULT 0,
      PRIMARY KEY (user_id, platform, song_id)
    );
    CREATE INDEX IF NOT EXISTS idx_favorites_seq ON favorites(user_id, update_seq);

    CREATE TABLE IF NOT EXISTS history (
      user_id       INTEGER NOT NULL,
      platform      TEXT NOT NULL,
      song_id       TEXT NOT NULL,
      song_json     TEXT NOT NULL DEFAULT '',
      played_at_ms  INTEGER NOT NULL DEFAULT 0,
      update_seq    INTEGER NOT NULL DEFAULT 0,
      updated_at_ms INTEGER NOT NULL DEFAULT 0,
      deleted       INTEGER NOT NULL DEFAULT 0,
      PRIMARY KEY (user_id, platform, song_id)
    );
    CREATE INDEX IF NOT EXISTS idx_history_seq ON history(user_id, update_seq);

    CREATE TABLE IF NOT EXISTS playlists (
      user_id       INTEGER NOT NULL,
      playlist_id   TEXT NOT NULL,
      name          TEXT NOT NULL DEFAULT '',
      cover_url     TEXT NOT NULL DEFAULT '',
      update_seq    INTEGER NOT NULL DEFAULT 0,
      updated_at_ms INTEGER NOT NULL DEFAULT 0,
      deleted       INTEGER NOT NULL DEFAULT 0,
      PRIMARY KEY (user_id, playlist_id)
    );
    CREATE INDEX IF NOT EXISTS idx_playlists_seq ON playlists(user_id, update_seq);

    CREATE TABLE IF NOT EXISTS playlist_songs (
      user_id       INTEGER NOT NULL,
      playlist_id   TEXT NOT NULL,
      platform      TEXT NOT NULL,
      song_id       TEXT NOT NULL,
      position      INTEGER NOT NULL DEFAULT 0,
      song_json     TEXT NOT NULL DEFAULT '',
      update_seq    INTEGER NOT NULL DEFAULT 0,
      updated_at_ms INTEGER NOT NULL DEFAULT 0,
      deleted       INTEGER NOT NULL DEFAULT 0,
      PRIMARY KEY (user_id, playlist_id, platform, song_id)
    );
    CREATE INDEX IF NOT EXISTS idx_playlist_songs_seq ON playlist_songs(user_id, update_seq);

    CREATE TABLE IF NOT EXISTS play_events (
      id             INTEGER PRIMARY KEY AUTOINCREMENT,
      user_id        INTEGER NOT NULL,
      event_id       TEXT NOT NULL,
      platform       TEXT NOT NULL DEFAULT '',
      song_id        TEXT NOT NULL DEFAULT '',
      title          TEXT NOT NULL DEFAULT '',
      artist         TEXT NOT NULL DEFAULT '',
      listened_ms    INTEGER NOT NULL DEFAULT 0,
      duration_ms    INTEGER NOT NULL DEFAULT 0,
      listened_at_ms INTEGER NOT NULL DEFAULT 0,
      updated_at_ms  INTEGER NOT NULL DEFAULT 0,
      UNIQUE (user_id, event_id)
    );
    CREATE INDEX IF NOT EXISTS idx_play_events_user ON play_events(user_id, id);
`;

function num(v) {
  return typeof v === 'bigint' ? Number(v) : v;
}

function mapRow(r) {
  const out = {};
  for (const [k, v] of Object.entries(r)) out[k] = num(v);
  return out;
}

function rowsOf(rs) {
  return rs.rows.map(mapRow);
}

export async function initDb(dbUrl, authToken) {
  let url = dbUrl;
  if (url.startsWith('file:')) {
    const p = url.slice('file:'.length);
    try { mkdirSync(dirname(p), { recursive: true }); } catch { /* 远端无需处理 */ }
  } else if (/^[a-zA-Z]:[\\/]/.test(url) || isAbsolute(url)) {
    try { mkdirSync(dirname(url), { recursive: true }); } catch { /* ignore */ }
    url = `file:${url}`;
  }
  const client = createClient({ url, authToken, intMode: 'number' });
  if (typeof client.executeMultiple === 'function') {
    await client.executeMultiple(SCHEMA);
  } else {
    for (const stmt of SCHEMA.split(';').map((s) => s.trim()).filter(Boolean)) {
      await client.execute(stmt);
    }
  }
  // 平滑升级老数据库：确保 users 表有 role 字段
  try {
    await client.execute("ALTER TABLE users ADD COLUMN role TEXT NOT NULL DEFAULT 'user'");
  } catch {
    // 字段已存在时忽略错误
  }
  return client;
}

async function all(client, sql, args = []) {
  return rowsOf(await client.execute({ sql, args }));
}

async function first(client, sql, args = []) {
  return rowsOf(await client.execute({ sql, args }))[0];
}

async function run(client, sql, args = []) {
  const rs = await client.execute({ sql, args });
  return { changes: rs.rowsAffected, lastId: rs.lastInsertRowid != null ? Number(rs.lastInsertRowid) : undefined };
}

// ==================== 用户 ====================

export async function findUserByUsername(client, username) {
  return first(client, 'SELECT * FROM users WHERE username = ?', [username]);
}

export async function findUserById(client, id) {
  return first(client, 'SELECT * FROM users WHERE id = ?', [id]);
}

export async function createUser(client, { username, passwordHash, salt, nickname, role = 'user', nowMs }) {
  const r = await run(client,
    'INSERT INTO users (username, password_hash, salt, nickname, role, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?)',
    [username, passwordHash, salt, nickname || username, role || 'user', nowMs, nowMs],
  );
  return findUserById(client, r.lastId);
}

export async function updateUserProfile(client, id, { nickname, avatar, nowMs }) {
  const cur = await findUserById(client, id);
  if (!cur) return null;
  await run(client, 'UPDATE users SET nickname = ?, avatar = ?, updated_at = ? WHERE id = ?',
    [nickname ?? cur.nickname, avatar ?? cur.avatar, nowMs, id]);
  return findUserById(client, id);
}

export async function updateUserPassword(client, id, { passwordHash, salt, nowMs }) {
  return run(client, 'UPDATE users SET password_hash = ?, salt = ?, updated_at = ? WHERE id = ?',
    [passwordHash, salt, nowMs, id]);
}

export async function listUsers(client, { offset = 0, limit = 10, search = '', role = '' } = {}) {
  let sql = 'SELECT id, username, nickname, avatar, role, created_at, updated_at FROM users WHERE 1=1';
  const args = [];
  if (search) {
    sql += ' AND (username LIKE ? OR nickname LIKE ?)';
    args.push(`%${search}%`, `%${search}%`);
  }
  if (role) {
    sql += ' AND role = ?';
    args.push(role);
  }
  sql += ' ORDER BY id DESC LIMIT ? OFFSET ?';
  args.push(limit, offset);
  return all(client, sql, args);
}

export async function countUsers(client, { search = '', role = '' } = {}) {
  let sql = 'SELECT COUNT(*) AS total FROM users WHERE 1=1';
  const args = [];
  if (search) {
    sql += ' AND (username LIKE ? OR nickname LIKE ?)';
    args.push(`%${search}%`, `%${search}%`);
  }
  if (role) {
    sql += ' AND role = ?';
    args.push(role);
  }
  const row = await first(client, sql, args);
  return row ? num(row.total) : 0;
}

export async function adminUpdateUser(client, id, { nickname, avatar, role, passwordHash, salt, nowMs }) {
  const cur = await findUserById(client, id);
  if (!cur) return null;
  const updates = ['updated_at = ?'];
  const args = [nowMs];
  if (nickname !== undefined) {
    updates.push('nickname = ?');
    args.push(nickname);
  }
  if (avatar !== undefined) {
    updates.push('avatar = ?');
    args.push(avatar);
  }
  if (role !== undefined) {
    updates.push('role = ?');
    args.push(role);
  }
  if (passwordHash && salt) {
    updates.push('password_hash = ?', 'salt = ?');
    args.push(passwordHash, salt);
  }
  args.push(id);
  await run(client, `UPDATE users SET ${updates.join(', ')} WHERE id = ?`, args);
  return findUserById(client, id);
}

export async function adminDeleteUser(client, id) {
  const stmts = [
    { sql: 'DELETE FROM play_events WHERE user_id = ?', args: [id] },
    { sql: 'DELETE FROM playlist_songs WHERE user_id = ?', args: [id] },
    { sql: 'DELETE FROM playlists WHERE user_id = ?', args: [id] },
    { sql: 'DELETE FROM history WHERE user_id = ?', args: [id] },
    { sql: 'DELETE FROM favorites WHERE user_id = ?', args: [id] },
    { sql: 'DELETE FROM user_state WHERE user_id = ?', args: [id] },
    { sql: 'DELETE FROM sessions WHERE user_id = ?', args: [id] },
    { sql: 'DELETE FROM users WHERE id = ?', args: [id] },
  ];
  await client.batch(stmts, 'write');
  return true;
}

export function publicUser(row) {
  if (!row) return null;
  return {
    id: row.id,
    username: row.username,
    nickname: row.nickname || row.username,
    avatar: row.avatar || '',
    role: row.role || 'user',
    created_at: row.created_at != null ? Number(row.created_at) : undefined,
    updated_at: row.updated_at != null ? Number(row.updated_at) : undefined,
  };
}

// ==================== 会话 ====================

export async function createSession(client, { userId, tokenHash, deviceName, ttlMs, maxSessions, nowMs }) {
  // 清理已过期 & 保持并发会话数上限（淘汰最久未活动）
  await client.execute('DELETE FROM sessions WHERE expires_at < ?', [nowMs]);
  const count = rowsOf(await client.execute({ sql: 'SELECT COUNT(*) AS c FROM sessions WHERE user_id = ? AND revoked_at IS NULL', args: [userId] }))[0]?.c ?? 0;
  if (count >= maxSessions) {
    await client.execute(
      'DELETE FROM sessions WHERE id IN (SELECT id FROM sessions WHERE user_id = ? AND revoked_at IS NULL ORDER BY last_seen_at ASC LIMIT ?)',
      [userId, count - maxSessions + 1],
    );
  }
  const id = crypto.randomUUID();
  await run(client,
    'INSERT INTO sessions (id, user_id, token_hash, device_name, created_at, last_seen_at, expires_at) VALUES (?, ?, ?, ?, ?, ?, ?)',
    [id, userId, tokenHash, deviceName || '', nowMs, nowMs, nowMs + ttlMs],
  );
  return { id, expiresAt: nowMs + ttlMs };
}

export async function findSessionByTokenHash(client, tokenHash) {
  return first(client,
    `SELECT s.*, u.username, u.nickname, u.avatar, u.role, u.created_at, u.updated_at
     FROM sessions s JOIN users u ON u.id = s.user_id
     WHERE s.token_hash = ?`, [tokenHash]);
}

export async function touchSession(client, id, nowMs) {
  return run(client, 'UPDATE sessions SET last_seen_at = ? WHERE id = ?', [nowMs, id]);
}

export async function revokeSessionByTokenHash(client, tokenHash) {
  return run(client, 'DELETE FROM sessions WHERE token_hash = ?', [tokenHash]);
}

export async function revokeSessionById(client, userId, id) {
  return run(client, 'DELETE FROM sessions WHERE id = ? AND user_id = ?', [id, userId]);
}

export async function revokeAllSessions(client, userId, exceptId = null) {
  if (exceptId) {
    return run(client, 'DELETE FROM sessions WHERE user_id = ? AND id != ?', [userId, exceptId]);
  }
  return run(client, 'DELETE FROM sessions WHERE user_id = ?', [userId]);
}

export async function listSessions(client, userId) {
  return all(client,
    'SELECT id, device_name, created_at, last_seen_at, expires_at FROM sessions WHERE user_id = ? AND revoked_at IS NULL ORDER BY last_seen_at DESC',
    [userId]);
}

// ==================== 同步：LWW + 墓碑 ====================

const SEQ_SQL = '(SELECT sync_seq FROM user_state WHERE user_id = ?)';

/**
 * 生成一批 LWW 的 upsert/delete 语句。
 * @param rows [{ table, keys:[[col,val]...], data:[[col,val]...], deleted:0|1 }]
 * table 只允许调用方硬编码的固定值（无注入面）。
 */
function lwwStatements(userId, nowMs, rows) {
  return rows.map((r) => {
    const keyCols = r.keys.map(([c]) => c);
    const keyVals = r.keys.map(([, v]) => v);
    const dataCols = r.data.map(([c]) => c);
    const dataVals = r.data.map(([, v]) => (typeof v === 'object' && v !== null ? JSON.stringify(v) : v));

    const insertCols = ['user_id', ...keyCols, ...dataCols, 'update_seq', 'updated_at_ms', 'deleted'];
    const placeholders = [
      '?',
      ...keyCols.map(() => '?'),
      ...dataCols.map(() => '?'),
      SEQ_SQL,
      '?',
      '?',
    ];
    const args = [userId, ...keyVals, ...dataVals, userId, nowMs, r.deleted ? 1 : 0];
    const conflict = ['user_id', ...keyCols].join(', ');

    const setClause = r.deleted
      ? 'update_seq = excluded.update_seq, updated_at_ms = excluded.updated_at_ms, deleted = 1'
      : `${dataCols.map((c) => `${c} = excluded.${c}`).join(', ')}, update_seq = excluded.update_seq, updated_at_ms = excluded.updated_at_ms, deleted = 0`;

    return {
      sql: `INSERT INTO ${r.table} (${insertCols.join(', ')}) VALUES (${placeholders.join(', ')}) ON CONFLICT (${conflict}) DO UPDATE SET ${setClause}`,
      args,
    };
  });
}

function songKey(song) {
  return [['platform', String(song.platform || '')], ['song_id', String(song.id || '')]];
}

/**
 * 应用一次客户端推送（favorites/history/playlists/playlistSongs 走 LWW，playEvents 走幂等追加）。
 * 整个写操作在单个事务（batch 'write'）内完成，seq 与数据在同一事务分配，保证游标不丢行。
 */
export async function applySync(client, userId, changes, nowMs) {
  const lww = [];

  for (const s of changes.favorites?.upsert || []) {
    if (!s?.platform || s?.id == null) continue;
    lww.push({ table: 'favorites', keys: songKey(s), data: [['song_json', s]], deleted: 0 });
  }
  for (const d of changes.favorites?.delete || []) {
    lww.push({ table: 'favorites', keys: songKey(d), data: [['song_json', '']], deleted: 1 });
  }

  for (const s of changes.history?.upsert || []) {
    if (!s?.platform || s?.id == null) continue;
    lww.push({
      table: 'history', keys: songKey(s),
      data: [['song_json', s], ['played_at_ms', Math.max(0, Number(s.playedAtMs) || 0)]], deleted: 0,
    });
  }
  for (const d of changes.history?.delete || []) {
    lww.push({ table: 'history', keys: songKey(d), data: [['song_json', ''], ['played_at_ms', 0]], deleted: 1 });
  }

  for (const p of changes.playlists?.upsert || []) {
    if (!p?.id) continue;
    lww.push({
      table: 'playlists', keys: [['playlist_id', String(p.id)]],
      data: [['name', String(p.name || '')], ['cover_url', String(p.coverUrl || '')]], deleted: 0,
    });
  }
  // 删除歌单时在该事务内同步墓碑化其下歌曲（纯 SQL UPDATE，避免“孤儿歌曲”复活）
  const deletedPlaylistIds = (changes.playlists?.delete || []).map((p) => String(p.id)).filter(Boolean);
  const playlistSongTombstoneStmts = [];
  for (const pid of deletedPlaylistIds) {
    lww.push({ table: 'playlists', keys: [['playlist_id', pid]], data: [['name', ''], ['cover_url', '']], deleted: 1 });
    playlistSongTombstoneStmts.push({
      sql: `UPDATE playlist_songs SET update_seq = ${SEQ_SQL}, updated_at_ms = ?, deleted = 1 WHERE user_id = ? AND playlist_id = ?`,
      args: [userId, nowMs, userId, pid],
    });
  }

  for (const s of changes.playlistSongs?.upsert || []) {
    if (!s?.playlistId || !s?.song?.platform || s?.song?.id == null) continue;
    lww.push({
      table: 'playlist_songs',
      keys: [['playlist_id', String(s.playlistId)], ['platform', String(s.song.platform)], ['song_id', String(s.song.id)]],
      data: [['position', Math.max(0, Number(s.position) || 0)], ['song_json', s.song]], deleted: 0,
    });
  }
  for (const d of changes.playlistSongs?.delete || []) {
    if (!d?.playlistId || !d?.platform || d?.songId == null) continue;
    lww.push({
      table: 'playlist_songs',
      keys: [['playlist_id', String(d.playlistId)], ['platform', String(d.platform)], ['song_id', String(d.songId)]],
      data: [['position', -1], ['song_json', '']], deleted: 1,
    });
  }

  const eventStmts = (changes.playEvents || []).map((e) => {
    if (!e) return null;
    const listened = Math.max(0, Math.min(Number(e.listenedMs) || 0, 24 * 60 * 60 * 1000));
    return {
      sql: `INSERT INTO play_events (user_id, event_id, platform, song_id, title, artist, listened_ms, duration_ms, listened_at_ms, updated_at_ms)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (user_id, event_id) DO NOTHING`,
      args: [
        userId,
        String(e.eventId || `${e.platform || ''}:${e.id || ''}:${e.listenedAtMs || nowMs}`),
        String(e.platform || ''),
        String(e.id || ''),
        String(e.title || ''),
        String(e.artist || ''),
        Math.round(listened),
        Math.max(0, Number(e.durationMs) || 0),
        Math.max(0, Number(e.listenedAtMs) || nowMs),
        nowMs,
      ],
    };
  }).filter(Boolean);

  const lwwStmts = lwwStatements(userId, nowMs, lww);
  const stmts = [
    { sql: 'INSERT INTO user_state (user_id, sync_seq) VALUES (?, 0) ON CONFLICT(user_id) DO NOTHING', args: [userId] },
    { sql: 'UPDATE user_state SET sync_seq = sync_seq + 1 WHERE user_id = ?', args: [userId] },
    ...lwwStmts,
    ...playlistSongTombstoneStmts,
    ...eventStmts,
    { sql: 'SELECT sync_seq FROM user_state WHERE user_id = ?', args: [userId] },
  ];

  const results = await client.batch(stmts, 'write');
  const last = results[results.length - 1];
  const version = num(last.rows?.[0]?.sync_seq ?? 0);

  // 统计本次成功插入的事件数（rowsAffected 与 eventStmts 顺序对齐）
  const evtStart = 2 + lwwStmts.length + playlistSongTombstoneStmts.length;
  let acceptedEvents = 0;
  for (let i = 0; i < eventStmts.length; i++) {
    const rs = results[evtStart + i];
    if (rs?.rowsAffected > 0) acceptedEvents += rs.rowsAffected;
  }

  return { version, acceptedEvents };
}

function safeParse(s) {
  try { return JSON.parse(s); } catch { return null; }
}

/**
 * 拉取自 since（sync_seq 游标）/ eventSince（play_events id 游标）之后的变化，
 * 含墓碑（deleted=true），客户端据此本地增删。返回的 version 作为下一次 since。
 */
export async function pullSync(client, userId, { since = 0, eventSince = 0 } = {}) {
  const headRow = await first(client, 'SELECT sync_seq FROM user_state WHERE user_id = ?', [userId]);
  const head = headRow ? headRow.sync_seq : 0;
  const isInitial = since === 0;

  // 全量首次拉取(since=0)无需下发已被删除的历史墓碑，增量拉取才下发墓碑通知客户端本地删除
  const deletedFilter = isInitial ? ' AND deleted = 0' : '';

  const [favorites, history, playlists, playlistSongs, events] = await Promise.all([
    all(client, `SELECT platform, song_id, song_json, updated_at_ms, deleted FROM favorites WHERE user_id = ? AND update_seq > ? AND update_seq <= ?${deletedFilter} ORDER BY update_seq`, [userId, since, head]),
    all(client, `SELECT platform, song_id, song_json, played_at_ms, updated_at_ms, deleted FROM history WHERE user_id = ? AND update_seq > ? AND update_seq <= ?${deletedFilter} ORDER BY update_seq`, [userId, since, head]),
    all(client, `SELECT playlist_id, name, cover_url, updated_at_ms, deleted FROM playlists WHERE user_id = ? AND update_seq > ? AND update_seq <= ?${deletedFilter} ORDER BY update_seq`, [userId, since, head]),
    all(client, `SELECT playlist_id, position, platform, song_id, song_json, updated_at_ms, deleted FROM playlist_songs WHERE user_id = ? AND update_seq > ? AND update_seq <= ?${deletedFilter} ORDER BY update_seq`, [userId, since, head]),
    all(client, 'SELECT id, event_id, platform, song_id, title, artist, listened_ms, duration_ms, listened_at_ms, updated_at_ms FROM play_events WHERE user_id = ? AND id > ? ORDER BY id LIMIT 500', [userId, eventSince]),
  ]);

  const nextEventId = events.length ? events[events.length - 1].id : eventSince;

  return {
    version: head,
    nextEventId,
    serverTime: Date.now(),
    favorites: favorites.map((r) => ({
      platform: r.platform, id: r.song_id,
      song: r.deleted ? null : safeParse(r.song_json),
      updatedAtMs: r.updated_at_ms, deleted: !!r.deleted,
    })),
    history: history.map((r) => ({
      platform: r.platform, id: r.song_id,
      song: r.deleted ? null : safeParse(r.song_json),
      playedAtMs: r.played_at_ms, updatedAtMs: r.updated_at_ms, deleted: !!r.deleted,
    })),
    playlists: playlists.map((r) => ({
      id: r.playlist_id, name: r.name, coverUrl: r.cover_url,
      updatedAtMs: r.updated_at_ms, deleted: !!r.deleted,
    })),
    playlistSongs: playlistSongs.map((r) => ({
      playlistId: r.playlist_id, position: r.position, platform: r.platform, songId: r.song_id,
      song: r.deleted ? null : safeParse(r.song_json),
      updatedAtMs: r.updated_at_ms, deleted: !!r.deleted,
    })),
    playEvents: events.map((r) => ({
      eventId: r.event_id, platform: r.platform, songId: r.song_id,
      title: r.title, artist: r.artist, listenedMs: r.listened_ms, durationMs: r.duration_ms,
      listenedAtMs: r.listened_at_ms, updatedAtMs: r.updated_at_ms,
    })),
  };
}

/** 清理 days 天前的听歌统计明细（防表无限膨胀）。Serviceless 可由定时任务调用。 */
export async function pruneOldPlayEvents(client, days = 180) {
  const cutoff = Date.now() - days * 24 * 60 * 60 * 1000;
  const r = await run(client, 'DELETE FROM play_events WHERE listened_at_ms < ? AND listened_at_ms > 0', [cutoff]);
  return r.changes || 0;
}

/** 顺手清理过期会话（登录/启动时调用）。 */
export async function pruneExpiredSessions(client, nowMs = Date.now()) {
  const r = await run(client, 'DELETE FROM sessions WHERE expires_at < ?', [nowMs]);
  return r.changes || 0;
}