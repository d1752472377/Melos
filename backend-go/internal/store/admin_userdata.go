package store

import (
	"context"
	"encoding/json"
	"fmt"
)

// Admin per-user data queries (read-only views over the sync tables).

// AdminUserOverview is the summary card shown at the top of the user detail page.
// PlaySessions counts merged play sessions (consecutive same-song heartbeats
// within 10s count as ONE play); PlayEvents stays the raw heartbeat count.
type AdminUserOverview struct {
	Favorites     int64 `json:"favorites"`
	History       int64 `json:"history"`
	Playlists     int64 `json:"playlists"`
	PlaylistSongs int64 `json:"playlistSongs"`
	Downloads     int64 `json:"downloads"`
	Scripts       int64 `json:"scripts"`
	PlaySessions  int64 `json:"playSessions"`
	PlayEvents    int64 `json:"playEvents"`
	ListenedMs    int64 `json:"listenedMs"`
	Devices       int64 `json:"devices"`
	FirstListenAt int64 `json:"firstListenAt"`
	LastListenAt  int64 `json:"lastListenAt"`
}

// playSessionGapMs（见 sync.go）是"一次连续播放"的唯一定义：同一
// (平台, 歌曲, 设备) 上，上一段结束（开始+时长）后 15 秒内开始的段算同一次
// 播放。ingest 合并、概览的"播放次数"、活跃统计的每日播放数、播放记录 Tab
// 全部从这里取数，避免同一份数据算出三个数字。

// playSessionsCTE 把某个用户的 play_events 归并成播放会话（CTE 名 play_sessions）。
// 调用方在后面接自己的 SELECT。
var playSessionsCTE = fmt.Sprintf(`
WITH ordered AS (
  SELECT id, platform, song_id, device_id, title, artist, listened_at_ms, listened_ms, duration_ms,
         CASE WHEN LAG(listened_at_ms) OVER (PARTITION BY platform, song_id, device_id ORDER BY listened_at_ms, id) IS NULL
                OR listened_at_ms > LAG(listened_at_ms) OVER (PARTITION BY platform, song_id, device_id ORDER BY listened_at_ms, id)
                                   + LAG(listened_ms) OVER (PARTITION BY platform, song_id, device_id ORDER BY listened_at_ms, id)
                                   + %d
              THEN 1 ELSE 0 END AS is_new
  FROM play_events
  WHERE user_id = ?
),
numbered AS (
  SELECT *, SUM(is_new) OVER (PARTITION BY platform, song_id, device_id ORDER BY listened_at_ms, id) AS sess
  FROM ordered
),
play_sessions AS (
  SELECT platform, song_id, device_id, sess,
         MIN(listened_at_ms) AS started_at,
         MAX(listened_at_ms) AS last_at,
         SUM(listened_ms)    AS listened_ms,
         MAX(duration_ms)    AS duration_ms,
         COUNT(*)            AS segments,
         MIN(title)          AS title,
         MIN(artist)         AS artist
  FROM numbered
  GROUP BY platform, song_id, device_id, sess
)`, playSessionGapMs)

// deviceNameSQL resolves a client device id to its human-readable name.
//
// play_events.device_id 是客户端自己生成的设备 id，sessions.id 是服务端会话
// UUID，两者永远不会相等——旧 SQL 按 sessions.id = device_id 关联，所以
// 「设备」列一直是空的。真正同时存了客户端设备 id 与名称的只有 downloads。
const deviceNameSQL = `COALESCE((SELECT d.device_name FROM downloads d
     WHERE d.user_id = %s AND d.device_id = %s AND d.device_name <> ''
     ORDER BY d.updated_at_ms DESC LIMIT 1), '')`

func deviceNameExpr(userExpr, deviceExpr string) string {
	return fmt.Sprintf(deviceNameSQL, userExpr, deviceExpr)
}

// AdminUserActiveDay is one day of listening totals for the activity chart.
type AdminUserActiveDay struct {
	Day        string `json:"day"`
	Events     int64  `json:"events"`
	ListenedMs int64  `json:"listenedMs"`
}

func (s *Store) AdminUserOverview(ctx context.Context, userID int64) (*AdminUserOverview, error) {
	o := &AdminUserOverview{}
	// 计数查询必须把错误带出去：以前忽略错误，DB 异常时接口回 200 + 全 0。
	count := func(sqlStr string, args ...any) (int64, error) {
		var n int64
		if err := s.db.QueryRowContext(ctx, sqlStr, args...).Scan(&n); err != nil {
			return 0, err
		}
		return n, nil
	}
	targets := []struct {
		dst *int64
		sql string
	}{
		{&o.Favorites, `SELECT COUNT(*) FROM favorites WHERE user_id = ? AND deleted = 0`},
		{&o.History, `SELECT COUNT(*) FROM history WHERE user_id = ? AND deleted = 0`},
		{&o.Playlists, `SELECT COUNT(*) FROM playlists WHERE user_id = ? AND deleted = 0`},
		{&o.PlaylistSongs, `SELECT COUNT(*) FROM playlist_songs WHERE user_id = ? AND deleted = 0`},
		{&o.Downloads, `SELECT COUNT(*) FROM downloads WHERE user_id = ? AND deleted = 0`},
		{&o.Scripts, `SELECT COUNT(*) FROM user_scripts WHERE user_id = ? AND deleted = 0`},
	}
	for _, t := range targets {
		n, err := count(t.sql, userID)
		if err != nil {
			return nil, err
		}
		*t.dst = n
	}
	if err := s.db.QueryRowContext(ctx, `
		SELECT COUNT(*), COALESCE(SUM(listened_ms),0), COALESCE(MIN(listened_at_ms),0),
		       COALESCE(MAX(listened_at_ms),0), COUNT(DISTINCT device_id)
		FROM play_events WHERE user_id = ?`, userID).Scan(
		&o.PlayEvents, &o.ListenedMs, &o.FirstListenAt, &o.LastListenAt, &o.Devices); err != nil {
		return nil, err
	}
	if err := s.db.QueryRowContext(ctx, playSessionsCTE+` SELECT COUNT(*) FROM play_sessions`, userID).Scan(&o.PlaySessions); err != nil {
		return nil, err
	}
	return o, nil
}

// AdminUserActiveDays returns per-day listening totals over the last 30 days.
//
// 播放数按会话计（与概览、播放记录 Tab 同一口径），一次会话记在它开始的
// 那一天；听歌时长按会话累计，两者都来自 playSessionsCTE，不再单独写一套窗口函数。
func (s *Store) AdminUserActiveDays(ctx context.Context, userID int64) ([]AdminUserActiveDay, error) {
	windowStart := nowMs() - 30*24*60*60*1000
	rows, err := s.db.QueryContext(ctx, playSessionsCTE+`
		SELECT date(started_at / 1000, 'unixepoch', 'localtime') AS d,
		       COUNT(*)        AS plays,
		       SUM(listened_ms) AS listened
		FROM play_sessions
		WHERE started_at >= ?
		GROUP BY d
		ORDER BY d`, userID, windowStart)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	out := []AdminUserActiveDay{}
	for rows.Next() {
		var r AdminUserActiveDay
		if err := rows.Scan(&r.Day, &r.Events, &r.ListenedMs); err != nil {
			return nil, err
		}
		out = append(out, r)
	}
	return out, rows.Err()
}

// AdminFavoriteRow is one favourite song of a user.
type AdminFavoriteRow struct {
	Platform    string          `json:"platform"`
	SongID      string          `json:"songId"`
	Song        json.RawMessage `json:"song"`
	UpdatedAtMs int64           `json:"updatedAtMs"`
}

// AdminUserFavorites returns the user's favourite songs, newest first.
func (s *Store) AdminUserFavorites(ctx context.Context, userID int64, offset, limit int) (int64, []AdminFavoriteRow, error) {
	var total int64
	if err := s.db.QueryRowContext(ctx, `SELECT COUNT(*) FROM favorites WHERE user_id = ? AND deleted = 0`, userID).Scan(&total); err != nil {
		return 0, nil, err
	}
	rows, err := s.db.QueryContext(ctx,
		`SELECT platform, song_id, song_json, updated_at_ms FROM favorites
		 WHERE user_id = ? AND deleted = 0 ORDER BY updated_at_ms DESC LIMIT ? OFFSET ?`, userID, limit, offset)
	if err != nil {
		return 0, nil, err
	}
	defer rows.Close()
	items := []AdminFavoriteRow{}
	for rows.Next() {
		var it AdminFavoriteRow
		var songJSON string
		if err := rows.Scan(&it.Platform, &it.SongID, &songJSON, &it.UpdatedAtMs); err != nil {
			return 0, nil, err
		}
		it.Song = jsonOrRaw(songJSON)
		items = append(items, it)
	}
	return total, items, rows.Err()
}

// jsonOrRaw converts a TEXT json column into a raw JSON message.
func jsonOrRaw(s string) json.RawMessage {
	if json.Valid([]byte(s)) {
		return json.RawMessage(s)
	}
	return json.RawMessage("null")
}

// AdminPlaylistRow is one playlist with its live song count.
type AdminPlaylistRow struct {
	PlaylistID  string `json:"playlistId"`
	Kind        string `json:"kind"`
	Name        string `json:"name"`
	CoverURL    string `json:"coverUrl"`
	SongCount   int64  `json:"songCount"`
	UpdatedAtMs int64  `json:"updatedAtMs"`
}

// AdminUserPlaylists returns the user's playlists with live song counts.
func (s *Store) AdminUserPlaylists(ctx context.Context, userID int64) ([]AdminPlaylistRow, error) {
	rows, err := s.db.QueryContext(ctx, `
		SELECT p.playlist_id, p.kind, p.name, p.cover_url, p.updated_at_ms,
		       (SELECT COUNT(*) FROM playlist_songs ps
		         WHERE ps.user_id = p.user_id AND ps.playlist_id = p.playlist_id AND ps.deleted = 0) AS song_count
		FROM playlists p
		WHERE p.user_id = ? AND p.deleted = 0
		ORDER BY p.updated_at_ms DESC`, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	items := []AdminPlaylistRow{}
	for rows.Next() {
		var it AdminPlaylistRow
		var kind string
		if err := rows.Scan(&it.PlaylistID, &kind, &it.Name, &it.CoverURL, &it.UpdatedAtMs, &it.SongCount); err != nil {
			return nil, err
		}
		it.Kind = playlistKind(kind)
		items = append(items, it)
	}
	return items, rows.Err()
}

// AdminPlaylistSongRow is one song inside a playlist.
type AdminPlaylistSongRow struct {
	Position    int64           `json:"position"`
	Platform    string          `json:"platform"`
	SongID      string          `json:"songId"`
	Song        json.RawMessage `json:"song"`
	UpdatedAtMs int64           `json:"updatedAtMs"`
}

// AdminPlaylistExists reports whether the user still has a live playlist with this id.
//
// 管理端往歌单加歌前要先问一句：直接 INSERT playlist_songs 会留下一条
// 挂在不存在歌单上的孤儿行（管理端「查看歌曲」和客户端都看不到它）。
func (s *Store) AdminPlaylistExists(ctx context.Context, userID int64, playlistID string) (bool, error) {
	var n int64
	err := s.db.QueryRowContext(ctx,
		`SELECT COUNT(*) FROM playlists WHERE user_id = ? AND playlist_id = ? AND deleted = 0`,
		userID, playlistID).Scan(&n)
	if err != nil {
		return false, err
	}
	return n > 0, nil
}

// AdminUserPlaylistSongs returns the live songs of one playlist in order.
func (s *Store) AdminUserPlaylistSongs(ctx context.Context, userID int64, playlistID string, offset, limit int) (int64, []AdminPlaylistSongRow, error) {
	var total int64
	if err := s.db.QueryRowContext(ctx,
		`SELECT COUNT(*) FROM playlist_songs WHERE user_id = ? AND playlist_id = ? AND deleted = 0`, userID, playlistID).Scan(&total); err != nil {
		return 0, nil, err
	}
	rows, err := s.db.QueryContext(ctx,
		`SELECT position, platform, song_id, song_json, updated_at_ms FROM playlist_songs
		 WHERE user_id = ? AND playlist_id = ? AND deleted = 0 ORDER BY position ASC LIMIT ? OFFSET ?`,
		userID, playlistID, limit, offset)
	if err != nil {
		return 0, nil, err
	}
	defer rows.Close()
	items := []AdminPlaylistSongRow{}
	for rows.Next() {
		var it AdminPlaylistSongRow
		var songJSON string
		if err := rows.Scan(&it.Position, &it.Platform, &it.SongID, &songJSON, &it.UpdatedAtMs); err != nil {
			return 0, nil, err
		}
		it.Song = jsonOrRaw(songJSON)
		items = append(items, it)
	}
	return total, items, rows.Err()
}

// AdminHistoryRow is one play-history entry.
type AdminHistoryRow struct {
	Platform    string          `json:"platform"`
	SongID      string          `json:"songId"`
	Song        json.RawMessage `json:"song"`
	PlayedAtMs  int64           `json:"playedAtMs"`
	UpdatedAtMs int64           `json:"updatedAtMs"`
}

// AdminUserHistory returns the user's play history, most recent first.
func (s *Store) AdminUserHistory(ctx context.Context, userID int64, offset, limit int) (int64, []AdminHistoryRow, error) {
	var total int64
	if err := s.db.QueryRowContext(ctx, `SELECT COUNT(*) FROM history WHERE user_id = ? AND deleted = 0`, userID).Scan(&total); err != nil {
		return 0, nil, err
	}
	rows, err := s.db.QueryContext(ctx,
		`SELECT platform, song_id, song_json, played_at_ms, updated_at_ms FROM history
		 WHERE user_id = ? AND deleted = 0 ORDER BY played_at_ms DESC LIMIT ? OFFSET ?`, userID, limit, offset)
	if err != nil {
		return 0, nil, err
	}
	defer rows.Close()
	items := []AdminHistoryRow{}
	for rows.Next() {
		var it AdminHistoryRow
		var songJSON string
		if err := rows.Scan(&it.Platform, &it.SongID, &songJSON, &it.PlayedAtMs, &it.UpdatedAtMs); err != nil {
			return 0, nil, err
		}
		it.Song = jsonOrRaw(songJSON)
		items = append(items, it)
	}
	return total, items, rows.Err()
}

// AdminScriptRow is one user script (code included for review).
type AdminScriptRow struct {
	ScriptID    string `json:"scriptId"`
	Name        string `json:"name"`
	Description string `json:"description"`
	Code        string `json:"code"`
	Enabled     bool   `json:"enabled"`
	UpdatedAtMs int64  `json:"updatedAtMs"`
}

// AdminUserScripts returns the user's uploaded source scripts.
func (s *Store) AdminUserScripts(ctx context.Context, userID int64) ([]AdminScriptRow, error) {
	rows, err := s.db.QueryContext(ctx,
		`SELECT script_id, name, description, code, enabled, updated_at_ms FROM user_scripts
		 WHERE user_id = ? AND deleted = 0 ORDER BY updated_at_ms DESC`, userID)
	if err != nil {
		return nil, err
	}
	defer rows.Close()
	items := []AdminScriptRow{}
	for rows.Next() {
		var it AdminScriptRow
		var enabled int
		if err := rows.Scan(&it.ScriptID, &it.Name, &it.Description, &it.Code, &enabled, &it.UpdatedAtMs); err != nil {
			return nil, err
		}
		it.Enabled = enabled != 0
		items = append(items, it)
	}
	return items, rows.Err()
}

// AdminDownloadRow is one per-device download record.
type AdminDownloadRow struct {
	Platform       string `json:"platform"`
	SongID         string `json:"songId"`
	DeviceID       string `json:"deviceId"`
	DeviceName     string `json:"deviceName"`
	Title          string `json:"title"`
	Artist         string `json:"artist"`
	Album          string `json:"album"`
	Artwork        string `json:"artwork"`
	Quality        string `json:"quality"`
	SizeBytes      int64  `json:"sizeBytes"`
	DownloadedAtMs int64  `json:"downloadedAtMs"`
	UpdatedAtMs    int64  `json:"updatedAtMs"`
}

// AdminUserDownloads returns the user's download records, newest first.
func (s *Store) AdminUserDownloads(ctx context.Context, userID int64, offset, limit int) (int64, []AdminDownloadRow, error) {
	var total int64
	if err := s.db.QueryRowContext(ctx, `SELECT COUNT(*) FROM downloads WHERE user_id = ? AND deleted = 0`, userID).Scan(&total); err != nil {
		return 0, nil, err
	}
	rows, err := s.db.QueryContext(ctx,
		`SELECT platform, song_id, device_id, device_name, title, artist, album, artwork, quality, size_bytes, downloaded_at_ms, updated_at_ms
		 FROM downloads WHERE user_id = ? AND deleted = 0 ORDER BY downloaded_at_ms DESC LIMIT ? OFFSET ?`, userID, limit, offset)
	if err != nil {
		return 0, nil, err
	}
	defer rows.Close()
	items := []AdminDownloadRow{}
	for rows.Next() {
		var it AdminDownloadRow
		if err := rows.Scan(&it.Platform, &it.SongID, &it.DeviceID, &it.DeviceName, &it.Title, &it.Artist, &it.Album,
			&it.Artwork, &it.Quality, &it.SizeBytes, &it.DownloadedAtMs, &it.UpdatedAtMs); err != nil {
			return 0, nil, err
		}
		items = append(items, it)
	}
	return total, items, rows.Err()
}

// AdminPlaySession is one continuous listening session: consecutive segments of
// the same song on the same device (gap ≤ 15s) collapsed into a single play.
//
// Why the admin shows sessions instead of raw rows: clients report either a
// 1-2s heartbeat (older builds) or a 20s segment (current), so raw rows make a
// 5-minute listen look like "several 20-second plays". The raw table stays
// intact for auditing; the view merges by playback continuity.
type AdminPlaySession struct {
	Platform   string `json:"platform"`
	SongID     string `json:"songId"`
	DeviceID   string `json:"deviceId"`
	DeviceName string `json:"deviceName"`
	Title      string `json:"title"`
	Artist     string `json:"artist"`
	StartedAt  int64  `json:"startedAt"`
	LastAt     int64  `json:"lastAt"`
	ListenedMs int64  `json:"listenedMs"`
	DurationMs int64  `json:"durationMs"`
	Segments   int64  `json:"segments"`
}

// AdminUserPlaySessions returns merged playback sessions, newest first.
func (s *Store) AdminUserPlaySessions(ctx context.Context, userID int64, offset, limit int) (int64, []AdminPlaySession, error) {
	var total int64
	if err := s.db.QueryRowContext(ctx, playSessionsCTE+` SELECT COUNT(*) FROM play_sessions`, userID).Scan(&total); err != nil {
		return 0, nil, err
	}
	rows, err := s.db.QueryContext(ctx, playSessionsCTE+`
		SELECT platform, song_id, device_id,
		       `+deviceNameExpr("?", "play_sessions.device_id")+`,
		       title, artist, started_at, last_at, listened_ms, duration_ms, segments
		FROM play_sessions
		ORDER BY started_at DESC LIMIT ? OFFSET ?`, userID, userID, limit, offset)
	if err != nil {
		return 0, nil, err
	}
	defer rows.Close()

	items := []AdminPlaySession{}
	for rows.Next() {
		var it AdminPlaySession
		if err := rows.Scan(&it.Platform, &it.SongID, &it.DeviceID, &it.DeviceName, &it.Title, &it.Artist,
			&it.StartedAt, &it.LastAt, &it.ListenedMs, &it.DurationMs, &it.Segments); err != nil {
			return 0, nil, err
		}
		items = append(items, it)
	}
	return total, items, rows.Err()
}

// AdminPlayEventRow is one raw (unmerged) listening event row.
type AdminPlayEventRow struct {
	EventID      string `json:"eventId"`
	DeviceID     string `json:"deviceId"`
	DeviceName   string `json:"deviceName"`
	Platform     string `json:"platform"`
	SongID       string `json:"songId"`
	Title        string `json:"title"`
	Artist       string `json:"artist"`
	ListenedMs   int64  `json:"listenedMs"`
	DurationMs   int64  `json:"durationMs"`
	ListenedAtMs int64  `json:"listenedAtMs"`
}

// AdminUserPlayEvents returns the raw listening events (paginated).
//
// 管理端「播放事件」Tab 用的是按会话合并后的 [AdminUserPlaySessions]；
// 这个原始接口保留给需要逐条核对心跳的场景（排障/审计）。
func (s *Store) AdminUserPlayEvents(ctx context.Context, userID int64, offset, limit int) (int64, []AdminPlayEventRow, error) {
	var total int64
	if err := s.db.QueryRowContext(ctx, `SELECT COUNT(*) FROM play_events WHERE user_id = ?`, userID).Scan(&total); err != nil {
		return 0, nil, err
	}
	rows, err := s.db.QueryContext(ctx,
		`SELECT e.event_id, e.device_id, `+deviceNameExpr("e.user_id", "e.device_id")+`, e.platform, e.song_id, e.title, e.artist, e.listened_ms, e.duration_ms, e.listened_at_ms
		 FROM play_events e WHERE e.user_id = ? ORDER BY e.listened_at_ms DESC LIMIT ? OFFSET ?`, userID, limit, offset)
	if err != nil {
		return 0, nil, err
	}
	defer rows.Close()
	items := []AdminPlayEventRow{}
	for rows.Next() {
		var it AdminPlayEventRow
		if err := rows.Scan(&it.EventID, &it.DeviceID, &it.DeviceName, &it.Platform, &it.SongID, &it.Title, &it.Artist,
			&it.ListenedMs, &it.DurationMs, &it.ListenedAtMs); err != nil {
			return 0, nil, err
		}
		items = append(items, it)
	}
	return total, items, rows.Err()
}
