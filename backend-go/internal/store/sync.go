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

	// ===== 播放事件：解析后统一在事务内做"会话合并" =====
	//
	// 为什么不在解析阶段就查库定夺：同一批里可能带同一首歌的多段
	// （新客户端每 20 秒一段），若在事务外逐条查库，后一段看不到前一段刚做的合并，
	// 于是同一次连续播放被拆成多行。放到事务里顺序处理，前一段的结果对后一段可见。
	type pendingPlayEvent struct {
		eventID    string
		deviceID   string
		platform   string
		songID     string
		title      string
		artist     string
		listenedMs int64
		durationMs int64
		listenedAt int64
	}
	var playEvents []pendingPlayEvent
	if raw, ok := body["playEvents"].([]any); ok {
		for _, item := range raw {
			e, ok := item.(map[string]any)
			if !ok {
				continue
			}
			listened := int64(math.Round(math.Max(0, math.Min(jsNumber(e["listenedMs"]), 24*60*60*1000))))
			if listened <= 0 {
				continue // 没听就没事件，避免用 0 秒记录撑起"播放次数"
			}
			listenedAt := nowMs
			if truthy(e["listenedAtMs"]) {
				listenedAt = nonNeg(e["listenedAtMs"])
			}
			platform := jsString(e["platform"])
			songID := jsString(e["id"])
			eventID := jsString(e["eventId"])
			if eventID == "" {
				eventID = platform + ":" + songID + ":" + strconv.FormatInt(listenedAt, 10)
			}
			playEvents = append(playEvents, pendingPlayEvent{
				eventID:    eventID,
				deviceID:   jsString(e["deviceId"]),
				platform:   platform,
				songID:     songID,
				title:      jsString(e["title"]),
				artist:     jsString(e["artist"]),
				listenedMs: listened,
				durationMs: nonNeg(e["durationMs"]),
				listenedAt: listenedAt,
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
	for _, ev := range playEvents {
		ok, err := applyPlayEvent(ctx, tx, userID, ev.eventID, ev.deviceID, ev.platform, ev.songID,
			ev.title, ev.artist, ev.listenedMs, ev.durationMs, ev.listenedAt, nowMs)
		if err != nil {
			return 0, 0, err
		}
		if ok {
			acceptedEvents++
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

// ==================== play events: session merging ====================

// playSessionGapMs is how much silence still counts as "the same continuous
// listen". A new segment that starts within this window after the previous
// segment ended is merged into the same row (same play session).
//
// Why 15s: the current client reports a segment every 20s (contiguous, so the
// gap is ~0 plus jitter), while older builds sent a 1-2s heartbeat. Real gaps
// (user paused, switched song and came back) are minutes long, so 15s separates
// "continuous" from "a new play" cleanly without needing client cooperation.
const playSessionGapMs = 15_000

// mergedEventIDsCap bounds the idempotency key list stored per session row.
const mergedEventIDsCap = 600

// applyPlayEvent stores one play event, merging it into the previous session of
// the same (user, song, device) when playback was continuous.
//
// Returns whether the event was newly accepted (false = duplicate/ignored).
//
// Merging keeps the session's original start time and event id, and appends the
// merged event ids into merged_ids so that a client retry of an already-merged
// segment is recognised and does not double-count the listening time.
func applyPlayEvent(ctx context.Context, tx *sql.Tx, userID int64, eventID, deviceID, platform, songID,
	title, artist string, listenedMs, durationMs, listenedAt, nowMs int64) (bool, error) {

	// 1) Exact retry of an event we already stored as its own row.
	var exists int
	err := tx.QueryRowContext(ctx,
		`SELECT 1 FROM play_events WHERE user_id = ? AND event_id = ? LIMIT 1`, userID, eventID).Scan(&exists)
	if err == nil {
		return false, nil
	}
	if err != sql.ErrNoRows {
		return false, err
	}

	// 2) Most recent session of this song on this device.
	var (
		rowID       int64
		rowStart    int64
		rowListened int64
		rowMerged   string
	)
	err = tx.QueryRowContext(ctx, `
		SELECT id, listened_at_ms, listened_ms, merged_ids FROM play_events
		WHERE user_id = ? AND platform = ? AND song_id = ? AND device_id = ?
		ORDER BY listened_at_ms DESC, id DESC LIMIT 1`,
		userID, platform, songID, deviceID).Scan(&rowID, &rowStart, &rowListened, &rowMerged)
	switch {
	case err == nil:
		// 2a) Retry of a segment that was already merged into this row.
		if mergedIDsContain(rowMerged, eventID) {
			return false, nil
		}
		prevEnd := rowStart + rowListened
		continuous := listenedAt >= rowStart && listenedAt <= prevEnd+playSessionGapMs
		if continuous {
			_, err = tx.ExecContext(ctx, `
				UPDATE play_events
				SET listened_ms = ?, duration_ms = MAX(duration_ms, ?), updated_at_ms = ?, merged_ids = ?
				WHERE id = ?`,
				rowListened+listenedMs, durationMs, nowMs, mergedIDsAppend(rowMerged, eventID), rowID)
			return true, err
		}
	case err != sql.ErrNoRows:
		return false, err
	}

	// 3) Not continuous (or first ever): a new play session.
	_, err = tx.ExecContext(ctx, `
		INSERT INTO play_events (user_id, event_id, device_id, platform, song_id, title, artist,
		                         listened_ms, duration_ms, listened_at_ms, updated_at_ms, merged_ids)
		VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, '')
		ON CONFLICT (user_id, event_id) DO NOTHING`,
		userID, eventID, deviceID, platform, songID, title, artist,
		listenedMs, durationMs, listenedAt, nowMs)
	return true, err
}

// mergedIDsContain reports whether id is present in the "|"-separated list.
func mergedIDsContain(list, id string) bool {
	if list == "" || id == "" {
		return false
	}
	for _, part := range strings.Split(list, "|") {
		if part == id {
			return true
		}
	}
	return false
}

// mergedIDsAppend appends id to the "|"-separated list, dropping the oldest
// entries once the cap is reached (idempotency degrades gracefully instead of
// letting the column grow without bound).
func mergedIDsAppend(list, id string) string {
	if list == "" {
		return id
	}
	if len(list)+len(id)+1 > mergedEventIDsCap {
		// 超长时只保留最近的一半，新的追加在后面
		parts := strings.Split(list, "|")
		if len(parts) > 1 {
			list = strings.Join(parts[len(parts)/2:], "|")
		}
	}
	return list + "|" + id
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
