package store

import (
	"database/sql"
	"fmt"
	"os"
	"path/filepath"
	"strings"

	_ "modernc.org/sqlite"
)

// Store wraps the local SQLite database (mirrors backend/src/lib/db.js).
//
// Single responsibility: persist "users + sessions + sync data". The LWW
// (last-write-wins) + tombstone sync engine lives in sync.go.
type Store struct {
	db *sql.DB
}

// schema holds the DDL, one statement per element (ported verbatim from the
// Node backend so an existing data/music.db stays byte-compatible).
var schema = []string{
	`CREATE TABLE IF NOT EXISTS users (
      id            INTEGER PRIMARY KEY AUTOINCREMENT,
      username      TEXT NOT NULL UNIQUE,
      password_hash TEXT NOT NULL,
      salt          TEXT NOT NULL,
      nickname      TEXT NOT NULL DEFAULT '',
      avatar        TEXT NOT NULL DEFAULT '',
      background    TEXT NOT NULL DEFAULT '',
      role          TEXT NOT NULL DEFAULT 'user',
      email         TEXT NOT NULL DEFAULT '',
      created_at    INTEGER NOT NULL,
      updated_at    INTEGER NOT NULL,
      last_active_at INTEGER NOT NULL DEFAULT 0
    )`,
	`CREATE TABLE IF NOT EXISTS sessions (
      id            TEXT PRIMARY KEY,
      user_id       INTEGER NOT NULL,
      token_hash    TEXT NOT NULL UNIQUE,
      device_name   TEXT NOT NULL DEFAULT '',
      created_at    INTEGER NOT NULL,
      last_seen_at  INTEGER NOT NULL,
      expires_at    INTEGER NOT NULL,
      revoked_at    INTEGER
    )`,
	`CREATE INDEX IF NOT EXISTS idx_sessions_user    ON sessions(user_id)`,
	`CREATE INDEX IF NOT EXISTS idx_sessions_expires ON sessions(expires_at)`,
	`CREATE TABLE IF NOT EXISTS user_state (
      user_id  INTEGER PRIMARY KEY,
      sync_seq INTEGER NOT NULL DEFAULT 0
    )`,
	`CREATE TABLE IF NOT EXISTS favorites (
      user_id       INTEGER NOT NULL,
      platform      TEXT NOT NULL,
      song_id       TEXT NOT NULL,
      song_json     TEXT NOT NULL DEFAULT '',
      update_seq    INTEGER NOT NULL DEFAULT 0,
      updated_at_ms INTEGER NOT NULL DEFAULT 0,
      deleted       INTEGER NOT NULL DEFAULT 0,
      PRIMARY KEY (user_id, platform, song_id)
    )`,
	`CREATE INDEX IF NOT EXISTS idx_favorites_seq ON favorites(user_id, update_seq)`,
	`CREATE TABLE IF NOT EXISTS history (
      user_id       INTEGER NOT NULL,
      platform      TEXT NOT NULL,
      song_id       TEXT NOT NULL,
      song_json     TEXT NOT NULL DEFAULT '',
      played_at_ms  INTEGER NOT NULL DEFAULT 0,
      update_seq    INTEGER NOT NULL DEFAULT 0,
      updated_at_ms INTEGER NOT NULL DEFAULT 0,
      deleted       INTEGER NOT NULL DEFAULT 0,
      PRIMARY KEY (user_id, platform, song_id)
    )`,
	`CREATE INDEX IF NOT EXISTS idx_history_seq ON history(user_id, update_seq)`,
	`CREATE TABLE IF NOT EXISTS playlists (
      user_id       INTEGER NOT NULL,
      playlist_id   TEXT NOT NULL,
      kind          TEXT NOT NULL DEFAULT 'mine',
      name          TEXT NOT NULL DEFAULT '',
      cover_url     TEXT NOT NULL DEFAULT '',
      update_seq    INTEGER NOT NULL DEFAULT 0,
      updated_at_ms INTEGER NOT NULL DEFAULT 0,
      deleted       INTEGER NOT NULL DEFAULT 0,
      PRIMARY KEY (user_id, playlist_id)
    )`,
	`CREATE INDEX IF NOT EXISTS idx_playlists_seq ON playlists(user_id, update_seq)`,
	`CREATE TABLE IF NOT EXISTS playlist_songs (
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
    )`,
	`CREATE INDEX IF NOT EXISTS idx_playlist_songs_seq ON playlist_songs(user_id, update_seq)`,
	`CREATE TABLE IF NOT EXISTS user_scripts (
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
    )`,
	`CREATE INDEX IF NOT EXISTS idx_user_scripts_seq ON user_scripts(user_id, update_seq)`,
	`CREATE TABLE IF NOT EXISTS downloads (
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
    )`,
	`CREATE INDEX IF NOT EXISTS idx_downloads_seq ON downloads(user_id, update_seq)`,
	`CREATE TABLE IF NOT EXISTS play_events (
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
    )`,
	`CREATE INDEX IF NOT EXISTS idx_play_events_user ON play_events(user_id, id)`,
	`CREATE INDEX IF NOT EXISTS idx_play_events_merge ON play_events(user_id, platform, song_id, device_id, listened_at_ms)`,
	`CREATE TABLE IF NOT EXISTS email_codes (
      id          INTEGER PRIMARY KEY AUTOINCREMENT,
      email       TEXT NOT NULL,
      code        TEXT NOT NULL,
      scene       TEXT NOT NULL DEFAULT 'login',
      expires_at  INTEGER NOT NULL,
      used        INTEGER NOT NULL DEFAULT 0,
      created_at  INTEGER NOT NULL
    )`,
	`CREATE INDEX IF NOT EXISTS idx_email_codes ON email_codes(email, scene, expires_at)`,
	`CREATE TABLE IF NOT EXISTS app_versions (
      id           INTEGER PRIMARY KEY AUTOINCREMENT,
      version_code INTEGER NOT NULL UNIQUE,
      version_name TEXT NOT NULL,
      apk_url      TEXT NOT NULL,
      file_size    INTEGER NOT NULL DEFAULT 0,
      hash_sha256  TEXT DEFAULT '',
      changelog    TEXT DEFAULT '',
      force_update INTEGER DEFAULT 0,
      enabled      INTEGER DEFAULT 1,
      created_at   DATETIME DEFAULT CURRENT_TIMESTAMP
    )`,
	// 客户端日志（错误/警告/远程排障）：append-only，保留窗口由清理任务控制
	`CREATE TABLE IF NOT EXISTS client_logs (
      id           INTEGER PRIMARY KEY AUTOINCREMENT,
      user_id      INTEGER NOT NULL DEFAULT 0,
      session_id   TEXT NOT NULL DEFAULT '',
      level        TEXT NOT NULL DEFAULT 'INFO',
      tag          TEXT NOT NULL DEFAULT '',
      message      TEXT NOT NULL DEFAULT '',
      stack        TEXT NOT NULL DEFAULT '',
      extra_json   TEXT NOT NULL DEFAULT '',
      count        INTEGER NOT NULL DEFAULT 1,
      app_version  TEXT NOT NULL DEFAULT '',
      version_code INTEGER NOT NULL DEFAULT 0,
      os           TEXT NOT NULL DEFAULT '',
      device       TEXT NOT NULL DEFAULT '',
      ip           TEXT NOT NULL DEFAULT '',
      created_at   INTEGER NOT NULL DEFAULT 0
    )`,
	`CREATE INDEX IF NOT EXISTS idx_client_logs_time   ON client_logs(created_at)`,
	`CREATE INDEX IF NOT EXISTS idx_client_logs_level  ON client_logs(level, created_at)`,
	`CREATE INDEX IF NOT EXISTS idx_client_logs_user   ON client_logs(user_id, created_at)`,
	`CREATE INDEX IF NOT EXISTS idx_client_logs_fp     ON client_logs(tag, message)`,
}

// Open initialises the database, creating parent directories and applying the
// schema plus legacy-database migrations.
func Open(dbURL string) (*Store, error) {
	path, err := localPath(dbURL)
	if err != nil {
		return nil, err
	}
	if dir := filepath.Dir(path); dir != "" && dir != "." {
		if err := os.MkdirAll(dir, 0o755); err != nil {
			return nil, fmt.Errorf("create db dir: %w", err)
		}
	}

	dsn := "file:" + filepath.ToSlash(path) +
		"?_pragma=busy_timeout(10000)&_pragma=journal_mode(WAL)"
	db, err := sql.Open("sqlite", dsn)
	if err != nil {
		return nil, err
	}
	// A single connection mirrors the original libsql local client semantics and
	// avoids SQLITE_BUSY between concurrent writers.
	db.SetMaxOpenConns(1)

	s := &Store{db: db}
	if err := s.migrate(); err != nil {
		_ = db.Close()
		return nil, err
	}
	return s, nil
}

// Close releases the database handle.
func (s *Store) Close() error { return s.db.Close() }

// DB exposes the underlying handle (used by tests).
func (s *Store) DB() *sql.DB { return s.db }

func (s *Store) migrate() error {
	for _, stmt := range schema {
		if _, err := s.db.Exec(stmt); err != nil {
			return fmt.Errorf("schema: %w", err)
		}
	}

	// Smooth upgrade of legacy databases: add missing columns, tolerating the
	// "duplicate column" error (same try/catch approach as the Node backend).
	alters := []string{
		`ALTER TABLE users ADD COLUMN role TEXT NOT NULL DEFAULT 'user'`,
		`ALTER TABLE users ADD COLUMN email TEXT NOT NULL DEFAULT ''`,
		`ALTER TABLE users ADD COLUMN background TEXT NOT NULL DEFAULT ''`,
		// 最后活跃时间（ms）：由认证中间件节流刷新，用于管理后台观察账号活跃
		`ALTER TABLE users ADD COLUMN last_active_at INTEGER NOT NULL DEFAULT 0`,
		`ALTER TABLE play_events ADD COLUMN device_id TEXT NOT NULL DEFAULT ''`,
		`ALTER TABLE playlists ADD COLUMN kind TEXT NOT NULL DEFAULT 'mine'`,
	}
	for _, stmt := range alters {
		_, _ = s.db.Exec(stmt)
	}

	// Partial unique index: only non-empty emails must be unique, so multiple
	// unbound users (email = '') do not conflict.
	_, _ = s.db.Exec(`CREATE UNIQUE INDEX IF NOT EXISTS idx_users_email ON users(email) WHERE email != ''`)

	return nil
}

// localPath normalises the configured DB_URL into a filesystem path and rejects
// unsupported remote endpoints.
func localPath(dbURL string) (string, error) {
	raw := strings.TrimSpace(dbURL)
	low := strings.ToLower(raw)
	for _, prefix := range []string{"libsql://", "http://", "https://", "ws://", "wss://"} {
		if strings.HasPrefix(low, prefix) {
			return "", fmt.Errorf("DB_URL %q: remote libsql/http endpoints are not supported by the Go backend, use a local SQLite file", raw)
		}
	}
	path := raw
	if strings.HasPrefix(low, "file:") {
		path = raw[len("file:"):]
	}
	if path == "" {
		return "", fmt.Errorf("DB_URL is empty")
	}
	return path, nil
}
