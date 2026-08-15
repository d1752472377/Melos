import { DatabaseSync } from 'node:sqlite';
import { mkdirSync } from 'node:fs';
import { dirname } from 'node:path';

/**
 * SQLite 存储层（node:sqlite，零原生依赖）。
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
export function initDb(dbPath) {
  mkdirSync(dirname(dbPath), { recursive: true });
  const db = new DatabaseSync(dbPath);

  db.exec(`
    PRAGMA journal_mode = WAL;
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
  `);

  return db;
}

// ==================== 音源 ====================

export function listSources(db) {
  return db.prepare('SELECT id, name, type, enabled, weight, config, created_at, updated_at FROM sources ORDER BY weight DESC, id ASC').all();
}

export function getSource(db, id) {
  return db.prepare('SELECT id, name, type, enabled, weight, config, created_at, updated_at FROM sources WHERE id = ?').get(id);
}

export function insertSource(db, { id, name, type, enabled = 1, weight = 100, config = '' }) {
  db.prepare('INSERT INTO sources (id, name, type, enabled, weight, config) VALUES (?, ?, ?, ?, ?, ?)')
    .run(id, name, type, enabled ? 1 : 0, weight, config);
  return getSource(db, id);
}

export function updateSource(db, id, fields) {
  const cur = getSource(db, id);
  if (!cur) return null;
  const next = {
    name: fields.name ?? cur.name,
    type: fields.type ?? cur.type,
    enabled: fields.enabled ?? cur.enabled,
    weight: fields.weight ?? cur.weight,
    config: fields.config ?? cur.config,
  };
  db.prepare('UPDATE sources SET name = ?, type = ?, enabled = ?, weight = ?, config = ?, updated_at = datetime(\'now\') WHERE id = ?')
    .run(next.name, next.type, next.enabled ? 1 : 0, next.weight, next.config, id);
  return getSource(db, id);
}

export function deleteSource(db, id) {
  return db.prepare('DELETE FROM sources WHERE id = ?').run(id);
}

// ==================== 用户 ====================

export function getUserByUsername(db, username) {
  return db.prepare('SELECT * FROM users WHERE username = ?').get(username);
}

export function getUserById(db, id) {
  return db.prepare('SELECT * FROM users WHERE id = ?').get(id);
}

export function insertUser(db, { username, passwordHash, salt, nickname }) {
  const r = db.prepare('INSERT INTO users (username, password_hash, salt, nickname) VALUES (?, ?, ?, ?)')
    .run(username, passwordHash, salt, nickname || username);
  return getUserById(db, Number(r.lastInsertRowid));
}

export function updateUserProfile(db, id, { nickname, avatar }) {
  const cur = getUserById(db, id);
  if (!cur) return null;
  db.prepare('UPDATE users SET nickname = ?, avatar = ?, updated_at = datetime(\'now\') WHERE id = ?')
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

function insertSongs(db, table, userId, songs, timeCol) {
  const del = db.prepare(`DELETE FROM ${table} WHERE user_id = ?`);
  del.run(userId);
  if (!Array.isArray(songs) || songs.length === 0) return;
  const stmt = db.prepare(
    `INSERT OR REPLACE INTO ${table} (user_id, platform, song_id, song_json, ${timeCol}) VALUES (?, ?, ?, ?, ?)`
  );
  songs.forEach((s) => {
    if (!s || !s.platform || !s.id) return;
    const json = JSON.stringify(s);
    stmt.run(userId, String(s.platform), String(s.id), json, datetimeNow());
  });
}

function listSongs(db, table, userId, limit = 500) {
  const col = table === 'favorites' ? 'created_at' : 'played_at';
  const rows = db.prepare(`SELECT song_json FROM ${table} WHERE user_id = ? ORDER BY ${col} DESC LIMIT ?`).all(userId, limit);
  return rows.map((r) => JSON.parse(r.song_json));
}

function datetimeNow() {
  return new Date().toISOString();
}

export function replaceFavorites(db, userId, songs) {
  insertSongs(db, 'favorites', userId, songs, 'created_at');
}

export function listFavorites(db, userId) {
  return listSongs(db, 'favorites', userId);
}

export function replaceHistory(db, userId, songs) {
  insertSongs(db, 'history', userId, songs, 'played_at');
}

export function listHistory(db, userId) {
  return listSongs(db, 'history', userId);
}

// ==================== 我的歌单 ====================

export function createPlaylist(db, userId, { id, name, coverUrl = '' }) {
  const pid = id || `pl_${Date.now().toString(36)}_${Math.random().toString(36).slice(2, 8)}`;
  db.prepare('INSERT INTO playlists (id, user_id, name, cover_url) VALUES (?, ?, ?, ?)')
    .run(pid, userId, name, coverUrl || '');
  return getPlaylist(db, userId, pid);
}

export function getPlaylist(db, userId, id) {
  const p = db.prepare('SELECT * FROM playlists WHERE id = ? AND user_id = ?').get(id, userId);
  if (!p) return null;
  const count = db.prepare('SELECT COUNT(*) AS c FROM playlist_songs WHERE playlist_id = ?').get(id).c;
  const cover = db.prepare('SELECT song_json FROM playlist_songs WHERE playlist_id = ? ORDER BY position ASC LIMIT 1').get(id);
  let coverUrl = p.cover_url;
  if (!coverUrl && cover) {
    try { coverUrl = JSON.parse(cover.song_json).artwork || ''; } catch { coverUrl = ''; }
  }
  return {
    id: p.id,
    name: p.name,
    coverUrl,
    songCount: count,
    createdAt: p.created_at,
    updatedAt: p.updated_at,
  };
}

export function listPlaylists(db, userId) {
  const rows = db.prepare('SELECT * FROM playlists WHERE user_id = ? ORDER BY updated_at DESC').all(userId);
  return rows.map((p) => getPlaylist(db, userId, p.id));
}

export function updatePlaylistMeta(db, userId, id, { name, coverUrl }) {
  const cur = db.prepare('SELECT * FROM playlists WHERE id = ? AND user_id = ?').get(id, userId);
  if (!cur) return null;
  db.prepare('UPDATE playlists SET name = ?, cover_url = ?, updated_at = datetime(\'now\') WHERE id = ?')
    .run(name ?? cur.name, coverUrl ?? cur.cover_url, id);
  return getPlaylist(db, userId, id);
}

export function deletePlaylist(db, userId, id) {
  db.prepare('DELETE FROM playlist_songs WHERE playlist_id = ?').run(id);
  return db.prepare('DELETE FROM playlists WHERE id = ? AND user_id = ?').run(id, userId);
}

export function replacePlaylistSongs(db, userId, id, songs) {
  const cur = db.prepare('SELECT id FROM playlists WHERE id = ? AND user_id = ?').get(id, userId);
  if (!cur) return null;
  db.prepare('DELETE FROM playlist_songs WHERE playlist_id = ?').run(id);
  const ins = db.prepare('INSERT INTO playlist_songs (playlist_id, position, platform, song_id, song_json) VALUES (?, ?, ?, ?, ?)');
  (Array.isArray(songs) ? songs : []).forEach((s, i) => {
    if (!s || !s.platform || !s.id) return;
    ins.run(id, i, String(s.platform), String(s.id), JSON.stringify(s));
  });
  db.prepare('UPDATE playlists SET updated_at = datetime(\'now\') WHERE id = ?').run(id);
  return getPlaylist(db, userId, id);
}

export function addPlaylistSongs(db, userId, id, songs) {
  const cur = db.prepare('SELECT id FROM playlists WHERE id = ? AND user_id = ?').get(id, userId);
  if (!cur) return null;
  const existing = listPlaylistSongs(db, userId, id);
  return replacePlaylistSongs(db, userId, id, [...existing, ...(Array.isArray(songs) ? songs : [])]);
}

export function removePlaylistSong(db, userId, id, platform, songId) {
  const cur = db.prepare('SELECT id FROM playlists WHERE id = ? AND user_id = ?').get(id, userId);
  if (!cur) return null;
  db.prepare('DELETE FROM playlist_songs WHERE playlist_id = ? AND platform = ? AND song_id = ?').run(id, platform, songId);
  db.prepare('UPDATE playlists SET updated_at = datetime(\'now\') WHERE id = ?').run(id);
  return getPlaylist(db, userId, id);
}

export function listPlaylistSongs(db, userId, id) {
  const cur = db.prepare('SELECT id FROM playlists WHERE id = ? AND user_id = ?').get(id, userId);
  if (!cur) return null;
  const rows = db.prepare('SELECT song_json FROM playlist_songs WHERE playlist_id = ? ORDER BY position ASC').all(id);
  return rows.map((r) => JSON.parse(r.song_json));
}

// ==================== 听歌统计 ====================

export function insertPlayEvents(db, userId, events) {
  const ins = db.prepare(
    'INSERT INTO play_events (user_id, platform, song_id, title, artist, listened_ms, duration_ms, day) VALUES (?, ?, ?, ?, ?, ?, ?, ?)'
  );
  const day = new Date().toISOString().slice(0, 10);
  let n = 0;
  for (const e of Array.isArray(events) ? events : []) {
    if (!e) continue;
    const listened = Math.max(0, Math.min(Number(e.listenedMs) || 0, 4 * 60 * 60 * 1000));
    if (listened <= 0) continue;
    ins.run(
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

export function getUserStats(db, userId) {
  const day = new Date().toISOString().slice(0, 10);
  const weekStart = new Date(Date.now() - 6 * 24 * 60 * 60 * 1000).toISOString().slice(0, 10);
  const agg = db.prepare(
    'SELECT COUNT(*) AS plays, COALESCE(SUM(listened_ms), 0) AS total FROM play_events WHERE user_id = ?'
  ).get(userId);
  const today = db.prepare(
    'SELECT COALESCE(SUM(listened_ms), 0) AS total FROM play_events WHERE user_id = ? AND day = ?'
  ).get(userId, day);
  const week = db.prepare(
    'SELECT COALESCE(SUM(listened_ms), 0) AS total FROM play_events WHERE user_id = ? AND day >= ?'
  ).get(userId, weekStart);
  const topSongs = db.prepare(
    `SELECT platform, song_id AS id, MAX(title) AS title, MAX(artist) AS artist,
            SUM(listened_ms) AS listenedMs, COUNT(*) AS plays
     FROM play_events WHERE user_id = ? GROUP BY platform, song_id
     ORDER BY listenedMs DESC LIMIT 20`
  ).all(userId);
  const topArtists = db.prepare(
    `SELECT artist, SUM(listened_ms) AS listenedMs, COUNT(*) AS plays
     FROM play_events WHERE user_id = ? AND artist != '' GROUP BY artist
     ORDER BY listenedMs DESC LIMIT 10`
  ).all(userId);
  const weekChart = db.prepare(
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
