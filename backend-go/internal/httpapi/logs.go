package httpapi

import (
	"strconv"
	"strings"
	"time"

	"github.com/gin-gonic/gin"

	"melos-backend/internal/store"
)

// registerLogs mounts the client log ingestion + admin query routes.
func (s *Server) registerLogs(r *gin.Engine) {
	// 批量上报：不强制登录（崩溃/登录失败时的日志最有价值），
	// 但挂全局 api 限流，且单请求体 ≤ 4MB（decodeBody 内建）。
	r.POST("/api/logs/batch", s.handleLogBatch)

	admin := s.requireAdmin()
	r.GET("/api/admin/logs", admin, s.handleAdminListLogs)
	r.GET("/api/admin/logs/stats", admin, s.handleAdminLogStats)
	r.PATCH("/api/admin/logs/:id", admin, s.handleAdminSetLogResolved)
	r.DELETE("/api/admin/logs", admin, s.handleAdminPruneLogs)
}

// handleLogBatch receives a client log batch (LogReporter).
func (s *Server) handleLogBatch(c *gin.Context) {
	// 尽力识别用户：带合法 token 就记 uid，没有也收（匿名设备日志对排障同样关键）。
	var uid int64
	if s.authenticateQuiet(c) {
		if u := currentUser(c); u != nil {
			uid = u.ID
		}
	}

	body, okBody := decodeBody(c)
	if !okBody {
		return
	}
	rawLogs, _ := body["logs"].([]any)
	if len(rawLogs) == 0 {
		ok(c, gin.H{"accepted": 0}) // 空批次：200，客户端清空队列即可
		return
	}
	if len(rawLogs) > 100 {
		rawLogs = rawLogs[:100]
	}

	meta := store.LogBatchMeta{
		AppVersion:  asString(body["appVersion"]),
		VersionCode: numAsI64(body["versionCode"]),
		OS:          asString(body["os"]),
		Device:      asString(body["device"]),
		AppUID:      uid,
		Context:     asStringMap(body["context"]),
	}

	items := make([]store.ClientLogItem, 0, len(rawLogs))
	for _, raw := range rawLogs {
		m, ok := raw.(map[string]any)
		if !ok {
			continue
		}
		it := store.ClientLogItem{
			Level:     asString(m["level"]),
			Tag:       asString(m["tag"]),
			Message:   asString(m["message"]),
			Stack:     asString(m["stack"]),
			TimeMs:    numAsI64(m["timeMs"]),
			SessionID: asString(m["sessionId"]),
			Count:     numAsI64(m["count"]),
			Thread:    asString(m["thread"]),
			Extra:     asStringMap(m["extra"]),
			Context:   asStringMap(m["context"]),
		}
		if arr, ok := m["breadcrumbs"].([]any); ok {
			for _, c := range arr {
				cm, ok := c.(map[string]any)
				if !ok {
					continue
				}
				it.Breadcrumbs = append(it.Breadcrumbs, store.BreadcrumbItem{
					TimeMs:  numAsI64(cm["timeMs"]),
					Level:   truncateStr(asString(cm["level"]), 20),
					Tag:     truncateStr(asString(cm["tag"]), 60),
					Message: truncateStr(asString(cm["message"]), 300),
				})
			}
		}
		items = append(items, it)
	}

	now := time.Now().UnixMilli()
	accepted, err := s.store.InsertClientLogs(c.Request.Context(), meta, items, c.ClientIP(), now)
	if err != nil {
		s.fail500(c, err)
		return
	}

	// debugUntil 预留为全局配置（将来可按设备精准下发）。0 = 关闭。
	ok(c, gin.H{"accepted": accepted, "debugUntil": 0})
}

// handleAdminListLogs returns a filtered page of client logs.
func (s *Server) handleAdminListLogs(c *gin.Context) {
	page, limit, offset := readPageParams(c)
	level := strings.ToUpper(strings.TrimSpace(c.Query("level")))
	switch level {
	case "WARNING":
		level = "WARN"
	case "ALL":
		level = ""
	}
	f := store.ListClientLogsFilter{
		Level:  level,
		Search: strings.TrimSpace(c.Query("search")),
		Limit:  limit,
		Offset: offset,
	}
	if n, ok := jsParseInt(c.Query("userId")); ok && n > 0 {
		f.UserID = int64(n)
	}
	if raw := strings.TrimSpace(c.Query("resolved")); raw != "" {
		if v, err := strconv.ParseBool(raw); err == nil {
			f.Resolved = &v
		}
	}

	ctx := c.Request.Context()
	total, err := s.store.CountClientLogs(ctx, f)
	if err != nil {
		s.fail500(c, err)
		return
	}
	items, err := s.store.ListClientLogs(ctx, f)
	if err != nil {
		s.fail500(c, err)
		return
	}
	c.Header("x-total-count", strconv.FormatInt(total, 10))
	ok(c, gin.H{"items": items, "total": total, "page": page, "limit": limit})
}

// handleAdminLogStats returns the headline numbers for the log page.
func (s *Server) handleAdminLogStats(c *gin.Context) {
	st, err := s.store.GetClientLogStats(c.Request.Context(), time.Now().UnixMilli())
	if err != nil {
		s.fail500(c, err)
		return
	}
	ok(c, st)
}

// handleAdminSetLogResolved marks one log as handled/not handled.
func (s *Server) handleAdminSetLogResolved(c *gin.Context) {
	id, err := strconv.ParseInt(c.Param("id"), 10, 64)
	if err != nil || id <= 0 {
		fail(c, 400, "非法的日志 id")
		return
	}
	body, okBody := decodeBody(c)
	if !okBody {
		return
	}
	raw, exists := body["resolved"]
	if !exists {
		fail(c, 400, "缺少 resolved 字段")
		return
	}
	resolved, _ := raw.(bool)
	found, err := s.store.SetClientLogResolved(c.Request.Context(), id, resolved, time.Now().UnixMilli())
	if err != nil {
		s.fail500(c, err)
		return
	}
	if !found {
		fail(c, 404, "日志不存在")
		return
	}
	ok(c, gin.H{"id": id, "resolved": resolved})
}

// handleAdminPruneLogs deletes log rows older than ?days (default 30).
func (s *Server) handleAdminPruneLogs(c *gin.Context) {
	days := 30
	if n, ok := jsParseInt(c.Query("days")); ok && n > 0 && n <= 365 {
		days = n
	}
	n, err := s.store.PruneOldClientLogs(c.Request.Context(), days, time.Now().UnixMilli())
	if err != nil {
		s.fail500(c, err)
		return
	}
	okMsgData(c, "已清理", gin.H{"deleted": n})
}

func truncateStr(s string, n int) string {
	if len(s) <= n {
		return s
	}
	return s[:n]
}

// numAsI64 coerces a decoded JSON number (float64) to int64, floor at 0.
func numAsI64(v any) int64 {
	f, ok := v.(float64)
	if !ok || f != f || f < 0 {
		return 0
	}
	return int64(f)
}

// asStringMap normalises a decoded JSON object into map[string]string,
// truncating keys/values so a hostile client cannot blow up a row.
func asStringMap(v any) map[string]string {
	m, ok := v.(map[string]any)
	if !ok || len(m) == 0 {
		return nil
	}
	out := make(map[string]string, len(m))
	for k, val := range m {
		if k == "" {
			continue
		}
		out[truncateStr(k, 60)] = truncateStr(asString(val), 300)
	}
	if len(out) == 0 {
		return nil
	}
	return out
}
