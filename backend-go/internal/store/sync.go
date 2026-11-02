package store

import (
	"context"
	"database/sql"
	"encoding/json"
	"fmt"
	"math"
	"strconv"
	"strings"
)

// Multi-device sync engine: per-record last-write-wins + tombstones.
//
// Every participating row carries a server-assigned monotonic update_seq and
// the server receive time updated_at_ms. Deletes are kept as deleted=1
// tombstones so a stale snapshot from another device cannot resurrect data.
// Clients use version (= user_state.sync_seq) as their pull cursor.
//
// Participating sets: favorites / history / playlists / playlist_songs /
// user_scripts / downloads. play_events is append-only (idempotent by event_id).

const seqSQL = "(SELECT sync_seq FROM user_state WHERE user_id = ?)"

type lwwOp struct {
	table    string
	keyCols  []string
	keyVals  []any
	dataCols []string
	dataVals []any
	deleted  bool
}

func boolInt(b bool) int {
	if b {
		return 1
	}
	return 0
}

func buildLWW(op lwwOp, userID, nowMs int64) (string, []any) {
	insertCols := make([]string, 0, 1+len(op.keyCols)+len(op.dataCols)+3)
	insertCols = append(insertCols, "user_id")
	insertCols = append(insertCols, op.keyCols...)
	insertCols = append(insertCols, op.dataCols...)
	insertCols = append(insertCols, "update_seq", "updated_at_ms", "deleted")

	placeholders := make([]string, 0, len(insertCols))
	placeholders = append(placeholders, "?")
	for range op.keyCols {
		placeholders = append(placeholders, "?")
	}
	for range op.dataCols {
		placeholders = append(placeholders, "?")
	}
	placeholders = append(placeholders, seqSQL, "?", "?")

	args := make([]any, 0, len(insertCols)+1)
	args = append(args, userID)
	args = append(args, op.keyVals...)
	args = append(args, op.dataVals...)
	args = append(args, userID, nowMs, boolInt(op.deleted))

	conflict := append([]string{"user_id"}, op.keyCols...)

	var setClause string
	if op.deleted {
		setClause = "update_seq = excluded.update_seq, updated_at_ms = excluded.updated_at_ms, deleted = 1"
	} else {
		parts := make([]string, 0, len(op.dataCols)+3)
		for _, c := range op.dataCols {
			parts = append(parts, c+" = excluded."+c)
		}
		parts = append(parts, "update_seq = excluded.update_seq", "updated_at_ms = excluded.updated_at_ms", "deleted = 0")
		setClause = strings.Join(parts, ", ")
	}

	sqlStr := fmt.Sprintf("INSERT INTO %s (%s) VALUES (%s) ON CONFLICT (%s) DO UPDATE SET %s",
		op.table,
		strings.Join(insertCols, ", "),
		strings.Join(placeholders, ", "),
		strings.Join(conflict, ", "),
		setClause)
	return sqlStr, args
}

// ==================== value coercion (mirrors JS semantics) ====================

func truthy(v any) bool {
	switch t := v.(type) {
	case nil:
		return false
	case bool:
		return t
	case float64:
		return t != 0 && !math.IsNaN(t)
	case string:
		return t != ""
	default:
		return true
	}
}

func jsString(v any) string {
	switch t := v.(type) {
	case nil:
		return ""
	case string:
		return t
	case bool:
		if t {
			return "true"
		}
		return "false"
	case float64:
		if math.IsNaN(t) || math.IsInf(t, 0) {
			return "NaN"
		}
		return strconv.FormatFloat(t, 'f', -1, 64)
	default:
		return fmt.Sprintf("%v", t)
	}
}

// jsNumber mirrors Number(v) || 0.
func jsNumber(v any) float64 {
	switch t := v.(type) {
	case nil:
		return 0
	case float64:
		if math.IsNaN(t) {
			return 0
		}
		return t
	case string:
		f, err := strconv.ParseFloat(strings.TrimSpace(t), 64)
		if err != nil || math.IsNaN(f) {
			return 0
		}
		return f
	case bool:
		if t {
			return 1
		}
		return 0
	default:
		return 0
	}
}

// nonNeg mirrors Math.max(0, Number(v) || 0) truncated to an int64.
func nonNeg(v any) int64 {
	f := jsNumber(v)
	if math.IsNaN(f) || f < 0 {
		return 0
	}
	return int64(f)
}

// flag mirrors lib/db.js `flag`: missing/null/” -> dflt; false/0/'0' -> 0; else 1.
func flag(v any, dflt int) int {
	if v == nil {
		return dflt
	}
	if s, ok := v.(string); ok {
		if s == "" {
			return dflt
		}
		if s == "0" {
			return 0
		}
		return 1
	}
	if b, ok := v.(bool); ok {
		if b {
			return 1
		}
		return 0
	}
	if f, ok := v.(float64); ok {
		if f == 0 {
			return 0
		}
		return 1
	}
	return 1
}

// playlistKind mirrors lib/db.js: only 'fav' is preserved, everything else -> 'mine'.
func playlistKind(v any) string {
	if s, ok := v.(string); ok && s == "fav" {
		return "fav"
	}
	return "mine"
}

func jsonBytes(v any) string {
	b, err := json.Marshal(v)
	if err != nil {
		return ""
	}
	return string(b)
}

// ==================== push ====================

func getCollection(body map[string]any, key string) (upserts, deletes []any) {
	v, ok := body[key]
	if !ok || v == nil {
		return nil, nil
	}
	m, ok := v.(map[string]any)
	if !ok {
		return nil, nil
	}
	if a, ok := m["upsert"].([]any); ok {
		upserts = a
	}
	if a, ok := m["delete"].([]any); ok {
		deletes = a
	}
	return upserts, deletes
}

// ApplySync applies one client push inside a single transaction and returns the
// new version plus the number of newly accepted play events.
func (s *Store) ApplySync(ctx context.Context, userID int64, body map[string]any, nowMs int64) (int64, int64, error) {
	var lww []lwwOp
	var playlistSongTombstones []string
	var playlistSongTombstoneArgs [][]any

	favUp, favDel := getCollection(body, "favorites")
	for _, item := range favUp {
		m, ok := item.(map[string]any)
		if !ok || !truthy(m["platform"]) || m["id"] == nil {
			continue
		}
		lww = append(lww, lwwOp{
			table:    "favorites",
			keyCols:  []string{"platform", "song_id"},
			keyVals:  []any{jsString(m["platform"]), jsString(m["id"])},
			dataCols: []string{"song_json"},
			dataVals: []any{jsonBytes(m)},
		})
	}
	for _, item := range favDel {
		m, ok := item.(map[string]any)
		if !ok {
			continue
		}
		lww = append(lww, lwwOp{
			table:    "favorites",
			keyCols:  []string{"platform", "song_id"},
			keyVals:  []any{jsString(m["platform"]), jsString(m["id"])},
			dataCols: []string{"song_json"},
			dataVals: []any{""},
			deleted:  true,
		})
	}

	histUp, histDel := getCollection(body, "history")
	for _, item := range histUp {
		m, ok := item.(map[string]any)
		if !ok || !truthy(m["platform"]) || m["id"] == nil {
			continue
		}
		lww = append(lww, lwwOp{
			table:    "history",
			keyCols:  []string{"platform", "song_id"},
			keyVals:  []any{jsString(m["platform"]), jsString(m["id"])},
			dataCols: []string{"song_json", "played_at_ms"},
			dataVals: []any{jsonBytes(m), nonNeg(m["playedAtMs"])},
		})
	}
	for _, item := range histDel {
		m, ok := item.(map[string]any)
		if !ok {
			continue
		}
		lww = append(lww, lwwOp{
			table:    "history",
			keyCols:  []string{"platform", "song_id"},
			keyVals:  []any{jsString(m["platform"]), jsString(m["id"])},
			dataCols: []string{"song_json", "played_at_ms"},
			dataVals: []any{"", int64(0)},
			deleted:  true,
		})
	}

	plUp, plDel := getCollection(body, "playlists")
	for _, item := range plUp {
		m, ok := item.(map[string]any)
		if !ok || !truthy(m["id"]) {
			continue
		}
		lww = append(lww, lwwOp{
			table:    "playlists",
			keyCols:  []string{"playlist_id"},
			keyVals:  []any{jsString(m["id"])},
			dataCols: []string{"kind", "name", "cover_url"},
			dataVals: []any{playlistKind(m["kind"]), jsString(m["name"]), jsString(m["coverUrl"])},
		})
	}
	for _, item := range plDel {
		m, ok := item.(map[string]any)
		if !ok {
			continue
		}
		id, hasID := m["id"]
		if !hasID {
			continue
		}
		pid := jsString(id)
		if pid == "" {
			continue
		}
		op := lwwOp{
			table:    "playlists",
			keyCols:  []string{"playlist_id"},
			keyVals:  []any{pid},
			dataCols: []string{"name", "cover_url"},
			dataVals: []any{"", ""},
			deleted:  true,
		}
		if kind, hasKind := m["kind"]; hasKind && truthy(kind) {
			op.dataCols = append([]string{"kind"}, op.dataCols...)
			op.dataVals = append([]any{playlistKind(kind)}, op.dataVals...)
		}
		lww = append(lww, op)

		playlistSongTombstones = append(playlistSongTombstones,
			"UPDATE playlist_songs SET update_seq = "+seqSQL+", updated_at_ms = ?, deleted = 1 WHERE user_id = ? AND playlist_id = ?")
		playlistSongTombstoneArgs = append(playlistSongTombstoneArgs, []any{userID, nowMs, userID, pid})
	}

	psUp, psDel := getCollection(body, "playlistSongs")
	for _, item := range psUp {
		m, ok := item.(map[string]any)
		if !ok {
			continue
		}
		song, ok := m["song"].(map[string]any)
		if !ok || !truthy(m["playlistId"]) || !truthy(song["platform"]) || song["id"] == nil {
			continue
		}
		lww = append(lww, lwwOp{
			table:    "playlist_songs",
			keyCols:  []string{"playlist_id", "platform", "song_id"},
			keyVals:  []any{jsString(m["playlistId"]), jsString(song["platform"]), jsString(song["id"])},
			dataCols: []string{"position", "song_json"},
			dataVals: []any{nonNeg(m["position"]), jsonBytes(song)},
		})
	}
	for _, item := range psDel {
		m, ok := item.(map[string]any)
		if !ok || !truthy(m["playlistId"]) || !truthy(m["platform"]) || m["songId"] == nil {
			continue
		}
		lww = append(lww, lwwOp{
			table:    "playlist_songs",
			keyCols:  []string{"playlist_id", "platform", "song_id"},
			keyVals:  []any{jsString(m["playlistId"]), jsString(m["platform"]), jsString(m["songId"])},
			dataCols: []string{"position", "song_json"},
			dataVals: []any{int64(-1), ""},
			deleted:  true,
		})
	}

	scUp, scDel := getCollection(body, "scripts")
	for _, item := range scUp {
		m, ok := item.(map[string]any)
		if !ok || !truthy(m["id"]) {
			continue
		}
		lww = append(lww, lwwOp{
			table:    "user_scripts",
			keyCols:  []string{"script_id"},
			keyVals:  []any{jsString(m["id"])},
			dataCols: []string{"name", "description", "code", "enabled"},
			dataVals: []any{jsString(m["name"]), jsString(m["description"]), jsString(m["code"]), flag(m["enabled"], 1)},
		})
	}
	for _, item := range scDel {
		m, ok := item.(map[string]any)
		if !ok || !truthy(m["id"]) {
			continue
		}
		lww = append(lww, lwwOp{
			table:    "user_scripts",
			keyCols:  []string{"script_id"},
			keyVals:  []any{jsString(m["id"])},
			dataCols: []string{"name", "description", "code", "enabled"},
			dataVals: []any{"", "", "", 0},
			deleted:  true,
		})
	}

	dlUp, dlDel := getCollection(body, "downloads")
	for _, item := range dlUp {
		m, ok := item.(map[string]any)
		if !ok || !truthy(m["platform"]) || m["id"] == nil || !truthy(m["deviceId"]) {
			continue
		}
		lww = append(lww, lwwOp{
			table:    "downloads",
			keyCols:  []string{"platform", "song_id", "device_id"},
			keyVals:  []any{jsString(m["platform"]), jsString(m["id"]), jsString(m["deviceId"])},
			dataCols: []string{"title", "artist", "album", "artwork", "quality", "size_bytes", "downloaded_at_ms", "device_name"},
			dataVals: []any{
				jsString(m["title"]), jsString(m["artist"]), jsString(m["album"]), jsString(m["artwork"]),
				jsString(m["quality"]), nonNeg(m["sizeBytes"]), nonNeg(m["downloadedAtMs"]), jsString(m["deviceName"]),
			},
		})
	}
	for _, item := range dlDel {
		m, ok := item.(map[string]any)
		if !ok || !truthy(m["platform"]) || m["id"] == nil || !truthy(m["deviceId"]) {
			continue
		}
		lww = append(lww, lwwOp{
			table:    "downloads",
			keyCols:  []string{"platform", "song_id", "device_id"},
			keyVals:  []any{jsString(m["platform"]), jsString(m["id"]), jsString(m["deviceId"])},
			dataCols: []string{"title", "artist", "album", "artwork", "quality", "size_bytes", "downloaded_at_ms", "device_name"},
			dataVals: []any{"", "", "", "", "", int64(0), int64(0), ""},
			deleted:  true,
		})
	}

	type eventInsert struct {
		sql  string
		args []any
	}
	var events []eventInsert
	if raw, ok := body["playEvents"].([]any); ok {
		for _, item := range raw {
			e, ok := item.(map[string]any)
			if !ok {
				continue
			}
			listened := math.Max(0, math.Min(jsNumber(e["listenedMs"]), 24*60*60*1000))
			deviceID := jsString(e["deviceId"])
			platform := jsString(e["platform"])
			songID := jsString(e["id"])
			title := jsString(e["title"])
			artist := jsString(e["artist"])
			durationMs := nonNeg(e["durationMs"])
			// 事件时间：客户端显式时间优先，否则用服务器时间。
			listenedAt := nowMs
			if truthy(e["listenedAtMs"]) {
				listenedAt = nonNeg(e["listenedAtMs"])
			}
			clientEventID := jsString(e["eventId"])
			fallbackEventID := platform + ":" + songID + ":" + strconv.FormatInt(listenedAt, 10)
			// 合并行的新幂等键：优先用客户端 eventId（同一片段重试时此键相同，天然幂等）；
			// 没有就用 内容+时间 派生键。旧键（被合并行）随行更新，不再参与去重。
			newEventID := clientEventID
			if newEventID == "" {
				newEventID = fallbackEventID
			}

			// ===== 心跳合并（兼容旧客户端的 1 秒心跳上报）=====
			// 旧客户端把每秒心跳当独立事件上报。这里把"同用户+同歌+同设备、
			// 且上一条事件距今 ≤ 10 秒"的心跳并进上一条（累加 listened_ms、
			// 幂等键换新、时间取最新），避免 5 分钟的歌被拆成 200+ 条记录。
			// 新客户端（分段上报，片段间隔 10~30 秒）落在 ≤10s 窗口内时同样会被
			// 合并成完整播放次数；超过 10 秒的间隔视为一次新播放。
			var (
				dupID       string
				dupListened int64
			)
			row := s.db.QueryRowContext(ctx, `
				SELECT event_id, listened_ms FROM play_events
				WHERE user_id = ? AND platform = ? AND song_id = ? AND device_id = ?
				  AND listened_at_ms > ?
				ORDER BY listened_at_ms DESC LIMIT 1`,
				userID, platform, songID, deviceID, listenedAt-10_000)
			switch err := row.Scan(&dupID, &dupListened); {
			case err == nil:
				if dupID == newEventID {
					// 客户端重试（同一片段 eventId 原样重发）：幂等拒绝，不再累加
					continue
				}
				events = append(events, eventInsert{
					sql: `UPDATE play_events
					      SET listened_ms = ?, event_id = ?, duration_ms = ?, listened_at_ms = ?, updated_at_ms = ?
					      WHERE user_id = ? AND event_id = ?`,
					args: []any{
						dupListened + int64(math.Round(listened)), newEventID, durationMs, listenedAt, nowMs,
						userID, dupID,
					},
				})
				continue
			case err != sql.ErrNoRows:
				return 0, 0, err
			}

			// 无相邻心跳：正常插入一条新事件
			events = append(events, eventInsert{
				sql: `INSERT INTO play_events (user_id, event_id, device_id, platform, song_id, title, artist, listened_ms, duration_ms, listened_at_ms, updated_at_ms)
                      VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                      ON CONFLICT (user_id, event_id) DO NOTHING`,
				args: []any{
					userID, newEventID, deviceID, platform, songID,
					title, artist, int64(math.Round(listened)), durationMs, listenedAt, nowMs,
				},
			})
		}
	}

	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return 0, 0, err
	}
	defer func() { _ = tx.Rollback() }()

	if _, err := tx.ExecContext(ctx,
		`INSERT INTO user_state (user_id, sync_seq) VALUES (?, 0) ON CONFLICT(user_id) DO NOTHING`, userID); err != nil {
		return 0, 0, err
	}
	if _, err := tx.ExecContext(ctx,
		`UPDATE user_state SET sync_seq = sync_seq + 1 WHERE user_id = ?`, userID); err != nil {
		return 0, 0, err
	}
	for _, op := range lww {
		sqlStr, args := buildLWW(op, userID, nowMs)
		if _, err := tx.ExecContext(ctx, sqlStr, args...); err != nil {
			return 0, 0, err
		}
	}
	for i, stmt := range playlistSongTombstones {
		if _, err := tx.ExecContext(ctx, stmt, playlistSongTombstoneArgs[i]...); err != nil {
			return 0, 0, err
		}
	}
	var acceptedEvents int64
	for _, ev := range events {
		res, err := tx.ExecContext(ctx, ev.sql, ev.args...)
		if err != nil {
			return 0, 0, err
		}
		if n, err := res.RowsAffected(); err == nil {
			acceptedEvents += n
		}
	}
	var version int64
	if err := tx.QueryRowContext(ctx, `SELECT sync_seq FROM user_state WHERE user_id = ?`, userID).Scan(&version); err != nil {
		return 0, 0, err
	}
	if err := tx.Commit(); err != nil {
		return 0, 0, err
	}
	return version, acceptedEvents, nil
}

// ==================== pull ====================

// PullResult is the /api/sync/pull payload (camelCase keys).
type PullResult struct {
	Version       int64              `json:"version"`
	NextEventID   int64              `json:"nextEventId"`
	ServerTime    int64              `json:"serverTime"`
	Favorites     []FavoriteItem     `json:"favorites"`
	History       []HistoryItem      `json:"history"`
	Playlists     []PlaylistItem     `json:"playlists"`
	PlaylistSongs []PlaylistSongItem `json:"playlistSongs"`
	Scripts       []ScriptItem       `json:"scripts"`
	Downloads     []DownloadItem     `json:"downloads"`
	PlayEvents    []PlayEventItem    `json:"playEvents"`
}

// FavoriteItem is one favourites entry (or tombstone).
type FavoriteItem struct {
	Platform    string          `json:"platform"`
	ID          string          `json:"id"`
	Song        json.RawMessage `json:"song"`
	UpdatedAtMs int64           `json:"updatedAtMs"`
	Deleted     bool            `json:"deleted"`
}

// HistoryItem is one play-history entry (or tombstone).
type HistoryItem struct {
	Platform    string          `json:"platform"`
	ID          string          `json:"id"`
	Song        json.RawMessage `json:"song"`
	PlayedAtMs  int64           `json:"playedAtMs"`
	UpdatedAtMs int64           `json:"updatedAtMs"`
	Deleted     bool            `json:"deleted"`
}

// PlaylistItem is one playlist (mine/fav) or tombstone.
type PlaylistItem struct {
	ID          string `json:"id"`
	Kind        string `json:"kind"`
	Name        string `json:"name"`
	CoverURL    string `json:"coverUrl"`
	UpdatedAtMs int64  `json:"updatedAtMs"`
	Deleted     bool   `json:"deleted"`
}

// PlaylistSongItem is one playlist track or tombstone.
type PlaylistSongItem struct {
	PlaylistID  string          `json:"playlistId"`
	Position    int64           `json:"position"`
	Platform    string          `json:"platform"`
	SongID      string          `json:"songId"`
	Song        json.RawMessage `json:"song"`
	UpdatedAtMs int64           `json:"updatedAtMs"`
	Deleted     bool            `json:"deleted"`
}

// ScriptItem is a user-uploaded source script or tombstone.
type ScriptItem struct {
	ID          string `json:"id"`
	Name        string `json:"name"`
	Description string `json:"description"`
	Code        string `json:"code"`
	Enabled     bool   `json:"enabled"`
	UpdatedAtMs int64  `json:"updatedAtMs"`
	Deleted     bool   `json:"deleted"`
}

// DownloadItem is a per-device download record or tombstone.
type DownloadItem struct {
	Platform       string `json:"platform"`
	ID             string `json:"id"`
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
	Deleted        bool   `json:"deleted"`
}

// PlayEventItem is an append-only listening event.
type PlayEventItem struct {
	EventID      string `json:"eventId"`
	DeviceID     string `json:"deviceId"`
	Platform     string `json:"platform"`
	SongID       string `json:"songId"`
	Title        string `json:"title"`
	Artist       string `json:"artist"`
	ListenedMs   int64  `json:"listenedMs"`
	DurationMs   int64  `json:"durationMs"`
	ListenedAtMs int64  `json:"listenedAtMs"`
	UpdatedAtMs  int64  `json:"updatedAtMs"`
}

func rawJSON(s string) json.RawMessage {
	if json.Valid([]byte(s)) {
		return json.RawMessage(s)
	}
	return json.RawMessage("null")
}

// PullSync returns every change after the given cursors.
func (s *Store) PullSync(ctx context.Context, userID, since, eventSince int64) (*PullResult, error) {
	var head int64
	err := s.db.QueryRowContext(ctx, `SELECT sync_seq FROM user_state WHERE user_id = ?`, userID).Scan(&head)
	if err != nil && err != sql.ErrNoRows {
		return nil, err
	}
	initial := since == 0
	deletedFilter := ""
	if initial {
		deletedFilter = " AND deleted = 0"
	}

	res := &PullResult{
		Version:       head,
		ServerTime:    nowMs(),
		Favorites:     []FavoriteItem{},
		History:       []HistoryItem{},
		Playlists:     []PlaylistItem{},
		PlaylistSongs: []PlaylistSongItem{},
		Scripts:       []ScriptItem{},
		Downloads:     []DownloadItem{},
		PlayEvents:    []PlayEventItem{},
	}

	if err := s.pullFavorites(ctx, userID, since, head, deletedFilter, res); err != nil {
		return nil, err
	}
	if err := s.pullHistory(ctx, userID, since, head, deletedFilter, res); err != nil {
		return nil, err
	}
	if err := s.pullPlaylists(ctx, userID, since, head, deletedFilter, res); err != nil {
		return nil, err
	}
	if err := s.pullPlaylistSongs(ctx, userID, since, head, deletedFilter, res); err != nil {
		return nil, err
	}
	if err := s.pullScripts(ctx, userID, since, head, deletedFilter, res); err != nil {
		return nil, err
	}
	if err := s.pullDownloads(ctx, userID, since, head, deletedFilter, res); err != nil {
		return nil, err
	}
	if err := s.pullEvents(ctx, userID, eventSince, res); err != nil {
		return nil, err
	}
	return res, nil
}

func (s *Store) pullFavorites(ctx context.Context, userID, since, head int64, filter string, res *PullResult) error {
	rows, err := s.db.QueryContext(ctx,
		`SELECT platform, song_id, song_json, updated_at_ms, deleted FROM favorites WHERE user_id = ? AND update_seq > ? AND update_seq <= ?`+filter+` ORDER BY update_seq`,
		userID, since, head)
	if err != nil {
		return err
	}
	defer rows.Close()
	for rows.Next() {
		var it FavoriteItem
		var songJSON string
		var deleted int
		if err := rows.Scan(&it.Platform, &it.ID, &songJSON, &it.UpdatedAtMs, &deleted); err != nil {
			return err
		}
		it.Deleted = deleted != 0
		if it.Deleted {
			it.Song = json.RawMessage("null")
		} else {
			it.Song = rawJSON(songJSON)
		}
		res.Favorites = append(res.Favorites, it)
	}
	return rows.Err()
}

func (s *Store) pullHistory(ctx context.Context, userID, since, head int64, filter string, res *PullResult) error {
	rows, err := s.db.QueryContext(ctx,
		`SELECT platform, song_id, song_json, played_at_ms, updated_at_ms, deleted FROM history WHERE user_id = ? AND update_seq > ? AND update_seq <= ?`+filter+` ORDER BY update_seq`,
		userID, since, head)
	if err != nil {
		return err
	}
	defer rows.Close()
	for rows.Next() {
		var it HistoryItem
		var songJSON string
		var deleted int
		if err := rows.Scan(&it.Platform, &it.ID, &songJSON, &it.PlayedAtMs, &it.UpdatedAtMs, &deleted); err != nil {
			return err
		}
		it.Deleted = deleted != 0
		if it.Deleted {
			it.Song = json.RawMessage("null")
		} else {
			it.Song = rawJSON(songJSON)
		}
		res.History = append(res.History, it)
	}
	return rows.Err()
}

func (s *Store) pullPlaylists(ctx context.Context, userID, since, head int64, filter string, res *PullResult) error {
	rows, err := s.db.QueryContext(ctx,
		`SELECT playlist_id, kind, name, cover_url, updated_at_ms, deleted FROM playlists WHERE user_id = ? AND update_seq > ? AND update_seq <= ?`+filter+` ORDER BY update_seq`,
		userID, since, head)
	if err != nil {
		return err
	}
	defer rows.Close()
	for rows.Next() {
		var it PlaylistItem
		var kind string
		var deleted int
		if err := rows.Scan(&it.ID, &kind, &it.Name, &it.CoverURL, &it.UpdatedAtMs, &deleted); err != nil {
			return err
		}
		it.Kind = playlistKind(kind)
		it.Deleted = deleted != 0
		res.Playlists = append(res.Playlists, it)
	}
	return rows.Err()
}

func (s *Store) pullPlaylistSongs(ctx context.Context, userID, since, head int64, filter string, res *PullResult) error {
	rows, err := s.db.QueryContext(ctx,
		`SELECT playlist_id, position, platform, song_id, song_json, updated_at_ms, deleted FROM playlist_songs WHERE user_id = ? AND update_seq > ? AND update_seq <= ?`+filter+` ORDER BY update_seq`,
		userID, since, head)
	if err != nil {
		return err
	}
	defer rows.Close()
	for rows.Next() {
		var it PlaylistSongItem
		var songJSON string
		var deleted int
		if err := rows.Scan(&it.PlaylistID, &it.Position, &it.Platform, &it.SongID, &songJSON, &it.UpdatedAtMs, &deleted); err != nil {
			return err
		}
		it.Deleted = deleted != 0
		if it.Deleted {
			it.Song = json.RawMessage("null")
		} else {
			it.Song = rawJSON(songJSON)
		}
		res.PlaylistSongs = append(res.PlaylistSongs, it)
	}
	return rows.Err()
}

func (s *Store) pullScripts(ctx context.Context, userID, since, head int64, filter string, res *PullResult) error {
	rows, err := s.db.QueryContext(ctx,
		`SELECT script_id, name, description, code, enabled, updated_at_ms, deleted FROM user_scripts WHERE user_id = ? AND update_seq > ? AND update_seq <= ?`+filter+` ORDER BY update_seq`,
		userID, since, head)
	if err != nil {
		return err
	}
	defer rows.Close()
	for rows.Next() {
		var it ScriptItem
		var enabled, deleted int
		if err := rows.Scan(&it.ID, &it.Name, &it.Description, &it.Code, &enabled, &it.UpdatedAtMs, &deleted); err != nil {
			return err
		}
		it.Deleted = deleted != 0
		if it.Deleted {
			it.Name, it.Description, it.Code, it.Enabled = "", "", "", false
		} else {
			it.Enabled = enabled != 0
		}
		res.Scripts = append(res.Scripts, it)
	}
	return rows.Err()
}

func (s *Store) pullDownloads(ctx context.Context, userID, since, head int64, filter string, res *PullResult) error {
	rows, err := s.db.QueryContext(ctx,
		`SELECT platform, song_id, device_id, title, artist, album, artwork, quality, size_bytes, downloaded_at_ms, device_name, updated_at_ms, deleted
		 FROM downloads WHERE user_id = ? AND update_seq > ? AND update_seq <= ?`+filter+` ORDER BY update_seq`,
		userID, since, head)
	if err != nil {
		return err
	}
	defer rows.Close()
	for rows.Next() {
		var it DownloadItem
		var deleted int
		if err := rows.Scan(&it.Platform, &it.ID, &it.DeviceID, &it.Title, &it.Artist, &it.Album, &it.Artwork,
			&it.Quality, &it.SizeBytes, &it.DownloadedAtMs, &it.DeviceName, &it.UpdatedAtMs, &deleted); err != nil {
			return err
		}
		it.Deleted = deleted != 0
		if it.Deleted {
			it.DeviceName, it.Title, it.Artist, it.Album, it.Artwork, it.Quality = "", "", "", "", "", ""
			it.SizeBytes, it.DownloadedAtMs = 0, 0
		}
		res.Downloads = append(res.Downloads, it)
	}
	return rows.Err()
}

func (s *Store) pullEvents(ctx context.Context, userID, eventSince int64, res *PullResult) error {
	rows, err := s.db.QueryContext(ctx,
		`SELECT id, event_id, device_id, platform, song_id, title, artist, listened_ms, duration_ms, listened_at_ms, updated_at_ms
		 FROM play_events WHERE user_id = ? AND id > ? ORDER BY id LIMIT 500`,
		userID, eventSince)
	if err != nil {
		return err
	}
	defer rows.Close()
	nextEventID := eventSince
	for rows.Next() {
		var id int64
		var it PlayEventItem
		if err := rows.Scan(&id, &it.EventID, &it.DeviceID, &it.Platform, &it.SongID, &it.Title, &it.Artist,
			&it.ListenedMs, &it.DurationMs, &it.ListenedAtMs, &it.UpdatedAtMs); err != nil {
			return err
		}
		nextEventID = id
		res.PlayEvents = append(res.PlayEvents, it)
	}
	if err := rows.Err(); err != nil {
		return err
	}
	res.NextEventID = nextEventID
	return nil
}

// GlobalSyncStats contains aggregate sync health metrics across all users.
type GlobalSyncStats struct {
	TotalUsers         int64 `json:"totalUsers"`
	SyncUsers          int64 `json:"syncUsers"`
	TotalFavorites     int64 `json:"totalFavorites"`
	TotalPlaylists     int64 `json:"totalPlaylists"`
	TotalPlaylistSongs int64 `json:"totalPlaylistSongs"`
	TotalHistory       int64 `json:"totalHistory"`
	TotalDownloads     int64 `json:"totalDownloads"`
	TotalScripts       int64 `json:"totalScripts"`
	TotalPlayEvents    int64 `json:"totalPlayEvents"`
	RecentPlayEvents   int64 `json:"recentPlayEvents"`
}

// GlobalSyncEvent is an audit record of a user's recent listening activity.
type GlobalSyncEvent struct {
	ID           int64  `json:"id"`
	UserID       int64  `json:"userId"`
	Username     string `json:"username"`
	EventID      string `json:"eventId"`
	DeviceID     string `json:"deviceId"`
	Platform     string `json:"platform"`
	SongID       string `json:"songId"`
	Title        string `json:"title"`
	Artist       string `json:"artist"`
	ListenedMs   int64  `json:"listenedMs"`
	ListenedAtMs int64  `json:"listenedAtMs"`
	UpdatedAtMs  int64  `json:"updatedAtMs"`
}

// GetGlobalSyncStats queries aggregate counts from all sync tables.
func (s *Store) GetGlobalSyncStats(ctx context.Context, nowMs int64) (*GlobalSyncStats, error) {
	st := &GlobalSyncStats{}
	_ = s.db.QueryRowContext(ctx, `SELECT COUNT(*) FROM users`).Scan(&st.TotalUsers)
	_ = s.db.QueryRowContext(ctx, `SELECT COUNT(*) FROM user_state WHERE sync_seq > 0`).Scan(&st.SyncUsers)
	_ = s.db.QueryRowContext(ctx, `SELECT COUNT(*) FROM favorites WHERE deleted = 0`).Scan(&st.TotalFavorites)
	_ = s.db.QueryRowContext(ctx, `SELECT COUNT(*) FROM playlists WHERE deleted = 0`).Scan(&st.TotalPlaylists)
	_ = s.db.QueryRowContext(ctx, `SELECT COUNT(*) FROM playlist_songs WHERE deleted = 0`).Scan(&st.TotalPlaylistSongs)
	_ = s.db.QueryRowContext(ctx, `SELECT COUNT(*) FROM history WHERE deleted = 0`).Scan(&st.TotalHistory)
	_ = s.db.QueryRowContext(ctx, `SELECT COUNT(*) FROM downloads WHERE deleted = 0`).Scan(&st.TotalDownloads)
	_ = s.db.QueryRowContext(ctx, `SELECT COUNT(*) FROM user_scripts WHERE deleted = 0`).Scan(&st.TotalScripts)
	_ = s.db.QueryRowContext(ctx, `SELECT COUNT(*) FROM play_events`).Scan(&st.TotalPlayEvents)

	oneDayAgo := nowMs - 24*60*60*1000
	_ = s.db.QueryRowContext(ctx, `SELECT COUNT(*) FROM play_events WHERE listened_at_ms >= ?`, oneDayAgo).Scan(&st.RecentPlayEvents)

	return st, nil
}

// ListRecentGlobalEvents returns the newest play events across all users for monitoring.
func (s *Store) ListRecentGlobalEvents(ctx context.Context, limit int) ([]GlobalSyncEvent, error) {
	if limit <= 0 || limit > 100 {
		limit = 30
	}
	rows, err := s.db.QueryContext(ctx, `
		SELECT e.id, e.user_id, COALESCE(NULLIF(u.nickname, ''), u.username, '用户#' || e.user_id),
		       e.event_id, e.device_id, e.platform, e.song_id, e.title, e.artist,
		       e.listened_ms, e.listened_at_ms, e.updated_at_ms
		FROM play_events e
		LEFT JOIN users u ON u.id = e.user_id
		ORDER BY e.id DESC
		LIMIT ?
	`, limit)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	var events []GlobalSyncEvent
	for rows.Next() {
		var ev GlobalSyncEvent
		if err := rows.Scan(&ev.ID, &ev.UserID, &ev.Username, &ev.EventID, &ev.DeviceID, &ev.Platform,
			&ev.SongID, &ev.Title, &ev.Artist, &ev.ListenedMs, &ev.ListenedAtMs, &ev.UpdatedAtMs); err != nil {
			return nil, err
		}
		events = append(events, ev)
	}
	return events, rows.Err()
}
