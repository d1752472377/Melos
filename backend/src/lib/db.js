import { createClient } from '@libsql/client';
import { mkdirSync } from 'node:fs';
import { dirname, isAbsolute } from 'node:path';
import crypto from 'node:crypto';

/**
 * 存储层：基于 @libsql/client（SQLite 兼容，支持本地 file: 与远端 libsql:// / http://）。
 *
 * 设计要点：
 * - 单一职责：只存「用户 + 会话 + 同步数据」，不存任何服务端音源配置。
 *   （user_scripts 存的是「用户自己上传的脚本源码」，属于用户数据：服务端只保存与同步，
 *     不解析、不执行、不校验其语义 —— 执行与安全检查都在客户端。）
 * - 多端同步采用「按记录 last-write-wins + 删除墓碑」：
 *     * 每条记录带 update_seq（服务端分配的单调递增序号）与 updated_at_ms（服务端收包时间）。
 *     * 谁后推送谁就是最新版本（update_seq 大者胜）；删除记录以 deleted=1 的墓碑形式保留，
 *       保证设备 A 删除后，晚一步推送旧快照的设备 B 不会把已删项“复活”。
 *     * 客户端用 version（即服务端 sync_seq）做增量拉取游标。
 *     * 参与该机制的集合：favorites / history / playlists / playlist_songs / user_scripts / downloads。
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
      background    TEXT NOT NULL DEFAULT '',
      role          TEXT NOT NULL DEFAULT 'user',
      -- 邮箱仅「登记/绑定」，未做邮件验证（本后端无 SMTP 配置）；空串 = 未绑定
      email         TEXT NOT NULL DEFAULT '',
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

    -- 歌单：kind 区分「自建歌单(mine)」与「收藏的网络歌单(fav)」，两者共用同一张表。
    -- 为什么不拆两张表：字段完全一致（id/名称/封面/时间戳/墓碑），拆开只会让 schema、路由与
    -- 客户端 payload 各多一份，而没有任何查询需要它们分开。
    -- kind 不进主键（主键仍是 user_id + playlist_id）：客户端两个来源的 id 本身已分域
    -- （自建是本地生成的 local_xxx，收藏是平台的数字 id），服务端只需把 kind 原样存回，
    -- 客户端拉取后用「kind + id」组成本地基线键。
    CREATE TABLE IF NOT EXISTS playlists (
      user_id       INTEGER NOT NULL,
      playlist_id   TEXT NOT NULL,
      kind          TEXT NOT NULL DEFAULT 'mine',
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

    -- 用户上传的音源脚本：后端只「保存 + 同步」源码文本，绝不解析/执行（执行发生在客户端）。
    -- 与收藏/歌单同款的 LWW + 墓碑三件套；主键 (user_id, script_id)：
    -- script_id 由客户端生成并保持稳定（重命名/改代码都走同一个 id），所以更新是原地覆盖而非新增行。
    -- code 可能几万字符，只作为「整条记录的载荷」读写，不建任何索引、也不参与任何 WHERE 条件：
    -- 按内容检索在客户端做（本地 Room 已有全量副本），服务端建索引只会放大写成本与库体积。
    CREATE TABLE IF NOT EXISTS user_scripts (
      user_id       INTEGER NOT NULL,
      script_id     TEXT NOT NULL,
      name          TEXT NOT NULL DEFAULT '',
      description   TEXT NOT NULL DEFAULT '',
      code          TEXT NOT NULL DEFAULT '',
      enabled       INTEGER NOT NULL DEFAULT 1,
      update_seq    INTEGER NOT NULL DEFAULT 0,
      updated_at_ms INTEGER NOT NULL DEFAULT 0,
      deleted       INTEGER NOT NULL DEFAULT 0,
      PRIMARY KEY (user_id, script_id)
    );
    CREATE INDEX IF NOT EXISTS idx_user_scripts_seq ON user_scripts(user_id, update_seq);

    -- 下载记录：一行 = 「某台设备磁盘上的一个文件」，因此 device_id 必须进主键。
    -- 若只用 (user_id, platform, song_id)：手机删掉本地文件打一条墓碑，会把平板上其实还在的文件
    -- 的下载记录一起删掉（多端互相误删）。把设备维度放进联合主键后，各设备只管自己那一条，
    -- 同一次同步里两设备的记录互不覆盖（LWW 是「按记录」生效的，记录边界就是这把联合主键）。
    CREATE TABLE IF NOT EXISTS downloads (
      user_id          INTEGER NOT NULL,
      platform         TEXT NOT NULL,
      song_id          TEXT NOT NULL,
      device_id        TEXT NOT NULL,
      title            TEXT NOT NULL DEFAULT '',
      artist           TEXT NOT NULL DEFAULT '',
      album            TEXT NOT NULL DEFAULT '',
      artwork          TEXT NOT NULL DEFAULT '',
      quality          TEXT NOT NULL DEFAULT '',
      size_bytes       INTEGER NOT NULL DEFAULT 0,
      downloaded_at_ms INTEGER NOT NULL DEFAULT 0,
      device_name      TEXT NOT NULL DEFAULT '',
      update_seq       INTEGER NOT NULL DEFAULT 0,
      updated_at_ms    INTEGER NOT NULL DEFAULT 0,
      deleted          INTEGER NOT NULL DEFAULT 0,
      PRIMARY KEY (user_id, platform, song_id, device_id)
    );
    CREATE INDEX IF NOT EXISTS idx_downloads_seq ON downloads(user_id, update_seq);

    CREATE TABLE IF NOT EXISTS play_events (
      id             INTEGER PRIMARY KEY AUTOINCREMENT,
      user_id        INTEGER NOT NULL,
      event_id       TEXT NOT NULL,
      device_id      TEXT NOT NULL DEFAULT '',
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

    CREATE TABLE IF NOT EXISTS email_codes (
      id          INTEGER PRIMARY KEY AUTOINCREMENT,
      email       TEXT NOT NULL,
      code        TEXT NOT NULL,
      scene       TEXT NOT NULL DEFAULT 'login',
      expires_at  INTEGER NOT NULL,
      used        INTEGER NOT NULL DEFAULT 0,
      created_at  INTEGER NOT NULL
    );
    CREATE INDEX IF NOT EXISTS idx_email_codes ON email_codes(email, scene, expires_at);
`;

function num(v) {
  return typeof v === 'bigint' ? Number(v) : v;
}

/**
 * 0/1 布尔列（如 user_scripts.enabled）的归一化：SQLite 无布尔类型，统一落 0/1。
 * 字段缺省（undefined/null/空串）时取 dflt —— 新建脚本默认「启用」，
 * 比「用户传上来的脚本被静默当成停用」更符合直觉；显式 false/0/'0' 才算停用。
 */
function flag(v, dflt = 0) {
  if (v === undefined || v === null || v === '') return dflt;
  return (v === false || v === 0 || v === '0') ? 0 : 1;
}

/**
 * 歌单类型归一化：只认 'mine'（自建）与 'fav'（收藏的网络歌单），其余一律按 'mine'。
 *
 * 为什么白名单而不是原样存：kind 是**客户端用来分域本地记录键**的字段，
 * 服务端原样透传一个拼错的/未来版本的值，会让同一首歌单在两台设备上被算成两条记录
 * （一端 key=kind:id、另一端 key=mine:id），表现为"歌单调不动、列表里出现幽灵条目"。
 */
function playlistKind(v) {
  return v === 'fav' ? 'fav' : 'mine';
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
  // 平滑升级老数据库：确保 users 表有 role / email 字段
  try {
    await client.execute("ALTER TABLE users ADD COLUMN role TEXT NOT NULL DEFAULT 'user'");
  } catch {
    // 字段已存在时忽略错误
  }
  try {
    await client.execute("ALTER TABLE users ADD COLUMN email TEXT NOT NULL DEFAULT ''");
  } catch {
    // 字段已存在时忽略错误
  }
  try {
    await client.execute("ALTER TABLE users ADD COLUMN background TEXT NOT NULL DEFAULT ''");
  } catch {
    // 字段已存在时忽略错误
  }
  try {
    await client.execute("ALTER TABLE play_events ADD COLUMN device_id TEXT NOT NULL DEFAULT ''");
  } catch {
    // 字段已存在时忽略错误
  }
  // playlists.kind（自建 / 收藏）在老库里不存在。老数据全是自建歌单，
  // 因此默认值 'mine' 对老库天然正确，无需数据回填。
  try {
    await client.execute("ALTER TABLE playlists ADD COLUMN kind TEXT NOT NULL DEFAULT 'mine'");
  } catch {
    // 字段已存在时忽略错误
  }
  // 邮箱唯一性：用「部分索引」只约束非空邮箱，让多个未绑定用户（email=''）互不冲突。
  // 若底层引擎不支持部分索引（极老版本），退化为「应用层查重 + 无唯一索引」：
  // 常规并发下应用层查重已足够，只是失去数据库级的兜底（见 README「邮箱绑定」一节）。
  try {
    await client.execute("CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email ON users(email) WHERE email != ''");
  } catch {
    // 不支持部分索引：依赖路由层 findUserByEmail 查重
  }
  // 验证码表：老库可能未在创建时执行该部分，确保升级
  try {
    await client.execute(`CREATE TABLE IF NOT EXISTS email_codes (
      id          INTEGER PRIMARY KEY AUTOINCREMENT,
      email       TEXT NOT NULL,
      code        TEXT NOT NULL,
      scene       TEXT NOT NULL DEFAULT 'login',
      expires_at  INTEGER NOT NULL,
      used        INTEGER NOT NULL DEFAULT 0,
      created_at  INTEGER NOT NULL
    )`);
    await client.execute("CREATE INDEX IF NOT EXISTS idx_email_codes ON email_codes(email, scene, expires_at)");
  } catch {
    // 忽略
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

// ==================== 邮箱验证码 ====================

export async function saveEmailCode(client, { email, code, scene = 'login', expiresAt, nowMs = Date.now() }) {
  const r = await run(client,
    'INSERT INTO email_codes (email, code, scene, expires_at, used, created_at) VALUES (?, ?, ?, ?, 0, ?)',
    [email, code, scene, expiresAt, nowMs],
  );
  return r.lastId;
}

export async function findLatestValidEmailCode(client, email, scene, nowMs) {
  return first(client,
    'SELECT * FROM email_codes WHERE email = ? AND scene = ? AND used = 0 AND expires_at > ? ORDER BY id DESC LIMIT 1',
    [email, scene, nowMs],
  );
}

export async function markEmailCodeUsed(client, id) {
  return run(client, 'UPDATE email_codes SET used = 1 WHERE id = ?', [id]);
}

export async function getLatestCodeSentTime(client, email, scene) {
  const row = await first(client,
    'SELECT created_at FROM email_codes WHERE email = ? AND scene = ? ORDER BY id DESC LIMIT 1',
    [email, scene],
  );
  return row ? num(row.created_at) : 0;
}

// ==================== 用户 ====================

export async function findUserByUsername(client, username) {
  return first(client, 'SELECT * FROM users WHERE username = ?', [username]);
}

export async function findUserById(client, id) {
  return first(client, 'SELECT * FROM users WHERE id = ?', [id]);
}

/** 按邮箱查用户（用于绑定前查重）；调用方保证 email 已归一化（trim+小写）且非空。 */
export async function findUserByEmail(client, email) {
  return first(client, "SELECT * FROM users WHERE email = ? AND email != ''", [email]);
}

export async function createUser(client, { username, passwordHash, salt, nickname, role = 'user', email = '', nowMs }) {
  const r = await run(client,
    'INSERT INTO users (username, password_hash, salt, nickname, role, email, created_at, updated_at) VALUES (?, ?, ?, ?, ?, ?, ?, ?)',
    [username, passwordHash, salt, nickname || username, role || 'user', email || '', nowMs, nowMs],
  );
  return findUserById(client, r.lastId);
}

export async function updateUserProfile(client, id, { nickname, avatar, background, email, nowMs }) {
  const cur = await findUserById(client, id);
  if (!cur) return null;
  // email 与 nickname/avatar/background 同款语义：undefined = 不改动；'' = 显式解绑/清除
  await run(client, 'UPDATE users SET nickname = ?, avatar = ?, background = ?, email = ?, updated_at = ? WHERE id = ?',
    [nickname ?? cur.nickname, avatar ?? cur.avatar, background ?? cur.background ?? '', email ?? cur.email ?? '', nowMs, id]);
  return findUserById(client, id);
}

export async function updateUserPassword(client, id, { passwordHash, salt, nowMs }) {
  return run(client, 'UPDATE users SET password_hash = ?, salt = ?, updated_at = ? WHERE id = ?',
    [passwordHash, salt, nowMs, id]);
}

export async function listUsers(client, { offset = 0, limit = 10, search = '', role = '' } = {}) {
  let sql = 'SELECT id, username, nickname, avatar, role, email, created_at, updated_at FROM users WHERE 1=1';
  const args = [];
  if (search) {
    sql += ' AND (username LIKE ? OR nickname LIKE ? OR email LIKE ?)';
    args.push(`%${search}%`, `%${search}%`, `%${search}%`);
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
    sql += ' AND (username LIKE ? OR nickname LIKE ? OR email LIKE ?)';
    args.push(`%${search}%`, `%${search}%`, `%${search}%`);
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
    { sql: 'DELETE FROM downloads WHERE user_id = ?', args: [id] },
    { sql: 'DELETE FROM user_scripts WHERE user_id = ?', args: [id] },
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
    background: row.background || '',
    role: row.role || 'user',
    email: row.email || '',
    created_at: row.created_at != null ? Number(row.created_at) : undefined,
    updated_at: row.updated_at != null ? Number(row.updated_at) : undefined,
    // createdAt 驼峰别名：Android 客户端按 camelCase 反序列化注册时间；
    // created_at 保留给 music-admin（snake_case），两键同值避免破坏既有对接。
    createdAt: row.created_at != null ? Number(row.created_at) : undefined,
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
    `SELECT s.*, u.username, u.nickname, u.avatar, u.role, u.email, u.created_at, u.updated_at
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
 * 应用一次客户端推送。
 * 走 LWW 的集合：favorites / history / playlists / playlistSongs / scripts / downloads
 * （全部共用 lwwStatements 生成的 INSERT ... ON CONFLICT DO UPDATE，语义完全一致：
 *   同一联合主键后到者胜，删除落成 deleted=1 的墓碑）；
 * 走幂等追加的集合：playEvents。
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
      data: [
        ['kind', playlistKind(p.kind)],
        ['name', String(p.name || '')],
        ['cover_url', String(p.coverUrl || '')],
      ],
      deleted: 0,
    });
  }
  // 删除歌单时在该事务内同步墓碑化其下歌曲（纯 SQL UPDATE，避免“孤儿歌曲”复活）
  const deletedPlaylistIds = (changes.playlists?.delete || []).map((p) => String(p.id)).filter(Boolean);
  const playlistSongTombstoneStmts = [];
  for (const pid of deletedPlaylistIds) {
    // 墓碑同样带上 kind（客户端按「kind + id」组键，缺了它会把收藏歌单的墓碑当成自建歌单的）。
    // 客户端没传 kind 时不写这一列：已存在的行保留原 kind，比按默认值覆盖更安全。
    const tomb = { table: 'playlists', keys: [['playlist_id', pid]], data: [['name', ''], ['cover_url', '']], deleted: 1 };
    const deletedRow = (changes.playlists?.delete || []).find((p) => String(p?.id) === pid);
    if (deletedRow && typeof deletedRow.kind === 'string' && deletedRow.kind) {
      tomb.data.unshift(['kind', playlistKind(deletedRow.kind)]);
    }
    lww.push(tomb);
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

  // ---------- 用户上传的音源脚本：LWW + 墓碑 ----------
  // 必填键只有 script_id（wire 上叫 id）：id 缺失就跳过整条，
  // 否则会插入一条 id 为空串的“半条脏数据”——它永远无法被后续更新/删除命中，只能长留库里。
  for (const s of changes.scripts?.upsert || []) {
    if (!s?.id) continue;
    lww.push({
      table: 'user_scripts', keys: [['script_id', String(s.id)]],
      data: [
        ['name', String(s.name || '')],
        ['description', String(s.description || '')],
        ['code', String(s.code || '')],
        ['enabled', flag(s.enabled, 1)],
      ],
      deleted: 0,
    });
  }
  for (const d of changes.scripts?.delete || []) {
    if (!d?.id) continue;
    // 墓碑指令把载荷槽位写成空值：新行直接落空（已删脚本不再占库体积）；
    // 命中已有行时 lwwStatements 只翻 deleted 标记（沿用既有集合的一致做法），
    // 残留的旧 code 由 pullSync 的 live() 在下发时屏蔽，不会回流给客户端。
    lww.push({
      table: 'user_scripts', keys: [['script_id', String(d.id)]],
      data: [['name', ''], ['description', ''], ['code', ''], ['enabled', 0]], deleted: 1,
    });
  }

  // ---------- 下载记录：LWW + 墓碑（设备维度进主键，见 SCHEMA 注释） ----------
  // platform / id / deviceId 三者共同定位「哪台设备上的哪个文件」，缺任意一段都整条跳过：
  // 拼出来的主键不完整 → 记录定位不到 → 更新覆盖不了、删除删不掉，属于必须挡在门外的脏数据。
  const downloadKey = (r) => [
    ['platform', String(r.platform)], ['song_id', String(r.id)], ['device_id', String(r.deviceId)],
  ];
  for (const s of changes.downloads?.upsert || []) {
    if (!s?.platform || s?.id == null || !s?.deviceId) continue;
    lww.push({
      table: 'downloads', keys: downloadKey(s),
      data: [
        ['title', String(s.title || '')],
        ['artist', String(s.artist || '')],
        ['album', String(s.album || '')],
        ['artwork', String(s.artwork || '')],
        ['quality', String(s.quality || '')],
        ['size_bytes', Math.max(0, Number(s.sizeBytes) || 0)],
        ['downloaded_at_ms', Math.max(0, Number(s.downloadedAtMs) || 0)],
        ['device_name', String(s.deviceName || '')],
      ],
      deleted: 0,
    });
  }
  for (const d of changes.downloads?.delete || []) {
    if (!d?.platform || d?.id == null || !d?.deviceId) continue;
    // 只墓碑化「这一台设备」那一条，同歌曲其他设备的记录不受影响。
    // 空载荷只为「新建墓碑行」时不落脏值；命中已有行时 lwwStatements 只翻 deleted 标记，
    // 旧标题/音质等残留由 pullSync 的 live() 屏蔽，客户端只需按主键三段（platform/id/deviceId）删本地那行。
    lww.push({
      table: 'downloads', keys: downloadKey(d),
      data: [
        ['title', ''], ['artist', ''], ['album', ''], ['artwork', ''], ['quality', ''],
        ['size_bytes', 0], ['downloaded_at_ms', 0], ['device_name', ''],
      ],
      deleted: 1,
    });
  }

  const eventStmts = (changes.playEvents || []).map((e) => {
    if (!e) return null;
    const listened = Math.max(0, Math.min(Number(e.listenedMs) || 0, 24 * 60 * 60 * 1000));
    return {
      sql: `INSERT INTO play_events (user_id, event_id, device_id, platform, song_id, title, artist, listened_ms, duration_ms, listened_at_ms, updated_at_ms)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            ON CONFLICT (user_id, event_id) DO NOTHING`,
      args: [
        userId,
        String(e.eventId || `${e.platform || ''}:${e.id || ''}:${e.listenedAtMs || nowMs}`),
        String(e.deviceId || ''),
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
 * 墓碑记录只下发「主键 + updatedAtMs + deleted」，业务载荷一律用空值占位：
 * 既省带宽（脚本 code 可能几万字符），也避免已删内容被重新灌回客户端；
 * 键仍然齐全（空串 / 0 / false），客户端反序列化不会遇到字段缺失。
 */
function live(v, deleted, empty) {
  return deleted ? empty : v;
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

  const [favorites, history, playlists, playlistSongs, scripts, downloads, events] = await Promise.all([
    all(client, `SELECT platform, song_id, song_json, updated_at_ms, deleted FROM favorites WHERE user_id = ? AND update_seq > ? AND update_seq <= ?${deletedFilter} ORDER BY update_seq`, [userId, since, head]),
    all(client, `SELECT platform, song_id, song_json, played_at_ms, updated_at_ms, deleted FROM history WHERE user_id = ? AND update_seq > ? AND update_seq <= ?${deletedFilter} ORDER BY update_seq`, [userId, since, head]),
    all(client, `SELECT playlist_id, kind, name, cover_url, updated_at_ms, deleted FROM playlists WHERE user_id = ? AND update_seq > ? AND update_seq <= ?${deletedFilter} ORDER BY update_seq`, [userId, since, head]),
    all(client, `SELECT playlist_id, position, platform, song_id, song_json, updated_at_ms, deleted FROM playlist_songs WHERE user_id = ? AND update_seq > ? AND update_seq <= ?${deletedFilter} ORDER BY update_seq`, [userId, since, head]),
    // 脚本：按 update_seq 走，与其余 LWW 集合完全同款；这里不拆开分页，
    // 因为「首次全量同步就要拿到可直接执行的完整副本」是客户端的硬需求。
    // 代价是 since=0 时会把所有存活脚本源码一次性下发（受 app.js bodyLimit / 响应体积约束，
    // 真膨胀到扛不住时再加「按需单独取 code」的接口，而不是改动同步语义）。
    all(client, `SELECT script_id, name, description, code, enabled, updated_at_ms, deleted FROM user_scripts WHERE user_id = ? AND update_seq > ? AND update_seq <= ?${deletedFilter} ORDER BY update_seq`, [userId, since, head]),
    // 下载记录：同样按 (user_id, update_seq) 索引扫描，一次拉取覆盖所有设备的行，
    // 由客户端按 deviceId 决定「本机该建哪几行、该删哪几行」。
    all(client, `SELECT platform, song_id, device_id, title, artist, album, artwork, quality, size_bytes, downloaded_at_ms, device_name, updated_at_ms, deleted FROM downloads WHERE user_id = ? AND update_seq > ? AND update_seq <= ?${deletedFilter} ORDER BY update_seq`, [userId, since, head]),
    all(client, 'SELECT id, event_id, device_id, platform, song_id, title, artist, listened_ms, duration_ms, listened_at_ms, updated_at_ms FROM play_events WHERE user_id = ? AND id > ? ORDER BY id LIMIT 500', [userId, eventSince]),
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
      id: r.playlist_id, kind: playlistKind(r.kind), name: r.name, coverUrl: r.cover_url,
      updatedAtMs: r.updated_at_ms, deleted: !!r.deleted,
    })),
    playlistSongs: playlistSongs.map((r) => ({
      playlistId: r.playlist_id, position: r.position, platform: r.platform, songId: r.song_id,
      song: r.deleted ? null : safeParse(r.song_json),
      updatedAtMs: r.updated_at_ms, deleted: !!r.deleted,
    })),
    scripts: scripts.map((r) => ({
      id: r.script_id,
      name: live(r.name, r.deleted, ''),
      description: live(r.description, r.deleted, ''),
      code: live(r.code, r.deleted, ''),
      enabled: live(!!r.enabled, r.deleted, false),
      updatedAtMs: r.updated_at_ms, deleted: !!r.deleted,
    })),
    downloads: downloads.map((r) => ({
      platform: r.platform, id: r.song_id, deviceId: r.device_id,
      deviceName: live(r.device_name, r.deleted, ''),
      title: live(r.title, r.deleted, ''),
      artist: live(r.artist, r.deleted, ''),
      album: live(r.album, r.deleted, ''),
      artwork: live(r.artwork, r.deleted, ''),
      quality: live(r.quality, r.deleted, ''),
      sizeBytes: live(r.size_bytes, r.deleted, 0),
      downloadedAtMs: live(r.downloaded_at_ms, r.deleted, 0),
      updatedAtMs: r.updated_at_ms, deleted: !!r.deleted,
    })),
    playEvents: events.map((r) => ({
      eventId: r.event_id, deviceId: r.device_id || '', platform: r.platform, songId: r.song_id,
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