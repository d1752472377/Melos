package httpapi

import (
	"net/http"
	"strconv"

	"github.com/gin-gonic/gin"

	"melos-backend/internal/store"
)

func i64toa(n int64) string { return strconv.FormatInt(n, 10) }

// registerAdminUserData mounts read-only admin endpoints for per-user data.
func (s *Server) registerAdminUserData(r *gin.Engine, admin gin.HandlerFunc) {
	r.GET("/api/admin/users/:id/overview", admin, s.handleAdminUserOverview)
	r.GET("/api/admin/users/:id/active-stats", admin, s.handleAdminUserActiveStats)
	r.GET("/api/admin/users/:id/favorites", admin, s.handleAdminUserFavorites)
	r.GET("/api/admin/users/:id/playlists", admin, s.handleAdminUserPlaylists)
	r.GET("/api/admin/users/:id/playlists/:pid/songs", admin, s.handleAdminUserPlaylistSongs)
	r.GET("/api/admin/users/:id/history", admin, s.handleAdminUserHistory)
	r.GET("/api/admin/users/:id/scripts", admin, s.handleAdminUserScripts)
	r.GET("/api/admin/users/:id/downloads", admin, s.handleAdminUserDownloads)
	r.GET("/api/admin/users/:id/play-events", admin, s.handleAdminUserPlayEvents)
}

// readPageParams parses limit/page and clamps them to sane bounds.
func readPageParams(c *gin.Context) (page, limit, offset int) {
	limit = 20
	if n, ok := jsParseInt(firstQuery(c, "limit", "pageSize")); ok {
		limit = n
	}
	limit = max(1, min(100, limit))
	page = 1
	if n, ok := jsParseInt(firstQuery(c, "page", "current")); ok {
		page = max(1, n)
	}
	offset = (page - 1) * limit
	return page, limit, offset
}

func (s *Server) handleAdminUserOverview(c *gin.Context) {
	id, _ := jsParseInt(c.Param("id"))
	ctx := c.Request.Context()
	user, err := s.store.FindUserByID(ctx, int64(id))
	if err != nil {
		s.fail500(c, err)
		return
	}
	if user == nil {
		fail(c, http.StatusNotFound, "用户不存在")
		return
	}
	stats, err := s.store.AdminUserOverview(ctx, int64(id))
	if err != nil {
		s.fail500(c, err)
		return
	}
	sessions, err := s.store.ListSessions(ctx, int64(id))
	if err != nil {
		s.fail500(c, err)
		return
	}
	sessionMaps := make([]map[string]any, 0, len(sessions))
	for _, si := range sessions {
		sessionMaps = append(sessionMaps, map[string]any{
			"id":           si.ID,
			"device_name":  si.DeviceName,
			"created_at":   si.CreatedAt,
			"last_seen_at": si.LastSeenAt,
			"expires_at":   si.ExpiresAt,
		})
	}
	ok(c, gin.H{"user": store.PublicUser(user), "stats": stats, "sessions": sessionMaps})
}

func (s *Server) handleAdminUserActiveStats(c *gin.Context) {
	id, _ := jsParseInt(c.Param("id"))
	ctx := c.Request.Context()
	user, err := s.store.FindUserByID(ctx, int64(id))
	if err != nil {
		s.fail500(c, err)
		return
	}
	if user == nil {
		fail(c, http.StatusNotFound, "用户不存在")
		return
	}
	days, err := s.store.AdminUserActiveDays(c.Request.Context(), int64(id))
	if err != nil {
		s.fail500(c, err)
		return
	}
	ok(c, gin.H{"days": days})
}

func (s *Server) handleAdminUserFavorites(c *gin.Context) {
	id, okUser := s.adminRequireUser(c)
	if !okUser {
		return
	}
	page, limit, offset := readPageParams(c)
	ctx := c.Request.Context()
	total, items, err := s.store.AdminUserFavorites(ctx, id, offset, limit)
	if err != nil {
		s.fail500(c, err)
		return
	}
	c.Header("x-total-count", i64toa(total))
	ok(c, gin.H{"items": items, "total": total, "page": page, "limit": limit})
}

func (s *Server) handleAdminUserPlaylists(c *gin.Context) {
	id, okUser := s.adminRequireUser(c)
	if !okUser {
		return
	}
	ctx := c.Request.Context()
	items, err := s.store.AdminUserPlaylists(ctx, id)
	if err != nil {
		s.fail500(c, err)
		return
	}
	ok(c, gin.H{"items": items, "total": len(items)})
}

func (s *Server) handleAdminUserPlaylistSongs(c *gin.Context) {
	id, okUser := s.adminRequireUser(c)
	if !okUser {
		return
	}
	pid := c.Param("pid")
	page, limit, offset := readPageParams(c)
	ctx := c.Request.Context()
	total, items, err := s.store.AdminUserPlaylistSongs(ctx, id, pid, offset, limit)
	if err != nil {
		s.fail500(c, err)
		return
	}
	c.Header("x-total-count", i64toa(total))
	ok(c, gin.H{"items": items, "total": total, "page": page, "limit": limit})
}

func (s *Server) handleAdminUserHistory(c *gin.Context) {
	id, okUser := s.adminRequireUser(c)
	if !okUser {
		return
	}
	page, limit, offset := readPageParams(c)
	ctx := c.Request.Context()
	total, items, err := s.store.AdminUserHistory(ctx, id, offset, limit)
	if err != nil {
		s.fail500(c, err)
		return
	}
	c.Header("x-total-count", i64toa(total))
	ok(c, gin.H{"items": items, "total": total, "page": page, "limit": limit})
}

func (s *Server) handleAdminUserScripts(c *gin.Context) {
	id, okUser := s.adminRequireUser(c)
	if !okUser {
		return
	}
	ctx := c.Request.Context()
	items, err := s.store.AdminUserScripts(ctx, id)
	if err != nil {
		s.fail500(c, err)
		return
	}
	ok(c, gin.H{"items": items, "total": len(items)})
}

func (s *Server) handleAdminUserDownloads(c *gin.Context) {
	id, okUser := s.adminRequireUser(c)
	if !okUser {
		return
	}
	page, limit, offset := readPageParams(c)
	ctx := c.Request.Context()
	total, items, err := s.store.AdminUserDownloads(ctx, id, offset, limit)
	if err != nil {
		s.fail500(c, err)
		return
	}
	c.Header("x-total-count", i64toa(total))
	ok(c, gin.H{"items": items, "total": total, "page": page, "limit": limit})
}

func (s *Server) handleAdminUserPlayEvents(c *gin.Context) {
	id, okUser := s.adminRequireUser(c)
	if !okUser {
		return
	}
	page, limit, offset := readPageParams(c)
	ctx := c.Request.Context()
	// 默认返回按"连续播放"合并后的会话（管理端看到的就是"一次播放"）；
	// raw=1 时才返回逐条原始心跳，供核对上报行为。
	if c.Query("raw") == "1" {
		total, items, err := s.store.AdminUserPlayEvents(ctx, id, offset, limit)
		if err != nil {
			s.fail500(c, err)
			return
		}
		c.Header("x-total-count", i64toa(total))
		ok(c, gin.H{"items": items, "total": total, "page": page, "limit": limit, "mode": "raw"})
		return
	}
	total, items, err := s.store.AdminUserPlaySessions(ctx, id, offset, limit)
	if err != nil {
		s.fail500(c, err)
		return
	}
	c.Header("x-total-count", i64toa(total))
	ok(c, gin.H{"items": items, "total": total, "page": page, "limit": limit, "mode": "session"})
}
