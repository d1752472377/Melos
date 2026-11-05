package store

import (
	"context"
	"encoding/json"
	"strings"
)

// Client log ingestion & admin queries.

// ClientLogItem is one log entry from the app's LogReporter batch.
type ClientLogItem struct {
	Level       string            `json:"level"`
	Tag         string            `json:"tag"`
	Message     string            `json:"message"`
	Stack       string            `json:"stack,omitempty"`
	Extra       map[string]string `json:"extra,omitempty"`
	TimeMs      int64             `json:"timeMs"`
	SessionID   string            `json:"sessionId"`
	Count       int64             `json:"count"`
	Thread      string            `json:"thread,omitempty"`
	Breadcrumbs []BreadcrumbItem  `json:"breadcrumbs,omitempty"`
	Context     map[string]string `json:"context,omitempty"`
}

// BreadcrumbItem is one step of the in-app action trail attached to errors.
type BreadcrumbItem struct {
	TimeMs  int64  `json:"timeMs"`
	Level   string `json:"level"`
	Tag     string `json:"tag"`
	Message string `json:"message"`
}

// LogBatchMeta carries the per-batch context (app/env info).
type LogBatchMeta struct {
	AppVersion  string            `json:"appVersion"`
	VersionCode int64             `json:"versionCode"`
	OS          string            `json:"os"`
	Device      string            `json:"device"`
	AppUID      int64             `json:"appUid"`
	Context     map[string]string `json:"context"`
}

// InsertClientLogs stores a batch of log entries. Rows are capped so a single
// malicious/buggy client cannot balloon the table.
func (s *Store) InsertClientLogs(ctx context.Context, meta LogBatchMeta, items []ClientLogItem, ip string, nowMs int64) (int, error) {
	if len(items) > 100 {
		items = items[:100]
	}
	tx, err := s.db.BeginTx(ctx, nil)
	if err != nil {
		return 0, err
	}
	defer func() { _ = tx.Rollback() }()

	accepted := 0
	for _, it := range items {
		level := normalizeLevel(it.Level)
		tag := truncate(it.Tag, 120)
		message := truncate(it.Message, 4000)
		stack := truncate(it.Stack, 12000)
		if message == "" {
			continue
		}
		if it.Count < 1 {
			it.Count = 1
		}
		if it.Count > 10000 {
			it.Count = 10000
		}
		extraJSON := ""
		if len(it.Extra) > 0 {
			if b, err := json.Marshal(it.Extra); err == nil {
				extraJSON = truncate(string(b), 2000)
			}
		}
		// 环境快照：条目自带的优先（崩溃时冻结的那份），否则用批次级的
		contextJSON := ""
		ctxMap := it.Context
		if len(ctxMap) == 0 {
			ctxMap = meta.Context
		}
		if len(ctxMap) > 0 {
			if b, err := json.Marshal(ctxMap); err == nil {
				contextJSON = truncate(string(b), 8000)
			}
		}
		breadcrumbJSON := ""
		if len(it.Breadcrumbs) > 0 {
			crumbs := it.Breadcrumbs
			if len(crumbs) > 50 {
				crumbs = crumbs[len(crumbs)-50:]
			}
			if b, err := json.Marshal(crumbs); err == nil {
				breadcrumbJSON = truncate(string(b), 12000)
			}
		}
		if _, err := tx.ExecContext(ctx,
			`INSERT INTO client_logs (user_id, session_id, level, tag, message, stack, extra_json, count, app_version, version_code, os, device, ip, thread, context_json, breadcrumbs_json, created_at)
			 VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`,
			meta.AppUID, truncate(it.SessionID, 64), level, tag, message, stack, extraJSON,
			it.Count, truncate(meta.AppVersion, 40), meta.VersionCode, truncate(meta.OS, 40), truncate(meta.Device, 120), truncate(ip, 64),
			truncate(it.Thread, 80), contextJSON, breadcrumbJSON,
			nowMs); err != nil {
			return accepted, err
		}
		accepted++
	}
	return accepted, tx.Commit()
}

func normalizeLevel(l string) string {
	switch strings.ToUpper(strings.TrimSpace(l)) {
	case "ERROR":
		return "ERROR"
	case "WARN", "WARNING":
		return "WARN"
	case "DEBUG":
		return "DEBUG"
	default:
		return "INFO"
	}
}

func truncate(s string, n int) string {
	if len(s) <= n {
		return s
	}
	// 尽量不切断 UTF-8 多字节字符
	cut := s[:n]
	for len(cut) > 0 && !utf8ValidLast(cut) {
		cut = cut[:len(cut)-1]
	}
	return cut
}

func utf8ValidLast(s string) bool {
	b := s[len(s)-1]
	if b < 0x80 {
		return true
	}
	// 粗校验：末字节是否是多字节序列的合法后续
	return b&0xC0 == 0x80
}

// AdminClientLog is one row returned to the admin console.
type AdminClientLog struct {
	ID          int64             `json:"id"`
	UserID      int64             `json:"userId"`
	Username    string            `json:"username"`
	SessionID   string            `json:"sessionId"`
	Level       string            `json:"level"`
	Tag         string            `json:"tag"`
	Message     string            `json:"message"`
	Stack       string            `json:"stack"`
	Extra       map[string]string `json:"extra"`
	Count       int64             `json:"count"`
	AppVersion  string            `json:"appVersion"`
	VersionCode int64             `json:"versionCode"`
	OS          string            `json:"os"`
	Device      string            `json:"device"`
	IP          string            `json:"ip"`
	Thread      string            `json:"thread"`
	Context     map[string]string `json:"context"`
	Breadcrumbs []BreadcrumbItem  `json:"breadcrumbs"`
	CreatedAt   int64             `json:"createdAt"`
}

// ListClientLogsFilter is the admin log query.
type ListClientLogsFilter struct {
	Level   string
	UserID  int64
	Search  string
	Offset  int
	Limit   int
}

// CountClientLogs returns the total rows matching the filter.
func (s *Store) CountClientLogs(ctx context.Context, f ListClientLogsFilter) (int64, error) {
	where, args := logFilterWhere(f)
	var total int64
	err := s.db.QueryRowContext(ctx, `SELECT COUNT(*) FROM client_logs l`+where, args...).Scan(&total)
	return total, err
}

// ListClientLogs returns one page of log rows, newest first.
func (s *Store) ListClientLogs(ctx context.Context, f ListClientLogsFilter) ([]AdminClientLog, error) {
	where, args := logFilterWhere(f)
	args = append(args, f.Limit, f.Offset)
	rows, err := s.db.QueryContext(ctx, `
		SELECT l.id, l.user_id, COALESCE(u.username, ''), l.session_id, l.level, l.tag,
		       l.message, l.stack, l.extra_json, l.count, l.app_version, l.version_code,
		       l.os, l.device, l.ip, l.thread, l.context_json, l.breadcrumbs_json, l.created_at
		FROM client_logs l
		LEFT JOIN users u ON u.id = l.user_id
		`+where+`
		ORDER BY l.id DESC LIMIT ? OFFSET ?`, args...)
	if err != nil {
		return nil, err
	}
	defer rows.Close()

	out := []AdminClientLog{}
	for rows.Next() {
		var r AdminClientLog
		var extraJSON, stack, ctxJSON, crumbJSON string
		if err := rows.Scan(&r.ID, &r.UserID, &r.Username, &r.SessionID, &r.Level, &r.Tag,
			&r.Message, &stack, &extraJSON, &r.Count, &r.AppVersion, &r.VersionCode,
			&r.OS, &r.Device, &r.IP, &r.Thread, &ctxJSON, &crumbJSON, &r.CreatedAt); err != nil {
			return nil, err
		}
		r.Stack = stack
		if extraJSON != "" {
			_ = json.Unmarshal([]byte(extraJSON), &r.Extra)
		}
		if ctxJSON != "" {
			_ = json.Unmarshal([]byte(ctxJSON), &r.Context)
		}
		if crumbJSON != "" {
			_ = json.Unmarshal([]byte(crumbJSON), &r.Breadcrumbs)
		}
		out = append(out, r)
	}
	return out, rows.Err()
}

func logFilterWhere(f ListClientLogsFilter) (string, []any) {
	clauses := []string{}
	args := []any{}
	if f.Level != "" {
		clauses = append(clauses, "l.level = ?")
		args = append(args, f.Level)
	}
	if f.UserID > 0 {
		clauses = append(clauses, "l.user_id = ?")
		args = append(args, f.UserID)
	}
	if f.Search != "" {
		clauses = append(clauses, "(l.tag LIKE ? OR l.message LIKE ? OR l.stack LIKE ? OR l.device LIKE ? OR l.app_version LIKE ?)")
		like := "%" + f.Search + "%"
		args = append(args, like, like, like, like, like)
	}
	if len(clauses) == 0 {
		return "", args
	}
	return " WHERE " + strings.Join(clauses, " AND "), args
}

// ClientLogStats is the summary header for the admin log page.
type ClientLogStats struct {
	Errors24h   int64 `json:"errors24h"`
	Warns24h    int64 `json:"warns24h"`
	Total24h    int64 `json:"total24h"`
	Devices24h  int64 `json:"devices24h"`
	Total       int64 `json:"total"`
}

// GetClientLogStats aggregates headline numbers for the admin console.
func (s *Store) GetClientLogStats(ctx context.Context, nowMs int64) (*ClientLogStats, error) {
	st := &ClientLogStats{}
	cutoff := nowMs - 24*60*60*1000
	_ = s.db.QueryRowContext(ctx, `SELECT COUNT(*) FROM client_logs WHERE level='ERROR' AND created_at >= ?`, cutoff).Scan(&st.Errors24h)
	_ = s.db.QueryRowContext(ctx, `SELECT COUNT(*) FROM client_logs WHERE level='WARN' AND created_at >= ?`, cutoff).Scan(&st.Warns24h)
	_ = s.db.QueryRowContext(ctx, `SELECT COUNT(*) FROM client_logs WHERE created_at >= ?`, cutoff).Scan(&st.Total24h)
	_ = s.db.QueryRowContext(ctx, `SELECT COUNT(DISTINCT COALESCE(NULLIF(device,''), ip)) FROM client_logs WHERE created_at >= ?`, cutoff).Scan(&st.Devices24h)
	_ = s.db.QueryRowContext(ctx, `SELECT COUNT(*) FROM client_logs`).Scan(&st.Total)
	return st, nil
}

// PruneOldClientLogs drops log rows older than the given number of days.
func (s *Store) PruneOldClientLogs(ctx context.Context, days int, nowMs int64) (int64, error) {
	if days <= 0 {
		days = 30
	}
	cutoff := nowMs - int64(days)*24*60*60*1000
	res, err := s.db.ExecContext(ctx, `DELETE FROM client_logs WHERE created_at < ?`, cutoff)
	if err != nil {
		return 0, err
	}
	n, _ := res.RowsAffected()
	return n, nil
}
