package httpapi

import (
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/gin-gonic/gin"

	"melos-backend/internal/security"
	"melos-backend/internal/store"
)

// registerAdmin mounts the admin console routes (role=admin required).
func (s *Server) registerAdmin(r *gin.Engine) {
	admin := s.requireAdmin()
	r.GET("/api/admin/users", admin, s.handleAdminListUsers)
	r.GET("/api/admin/users/:id", admin, s.handleAdminGetUser)
	r.POST("/api/admin/users", admin, s.handleAdminCreateUser)
	r.PUT("/api/admin/users/:id", admin, s.handleAdminUpdateUser)
	r.DELETE("/api/admin/users/:id", admin, s.handleAdminDeleteUser)
	r.GET("/api/admin/sync/stats", admin, s.handleAdminSyncStats)
	s.registerAdminUserData(r, admin)
	s.registerVersionAdmin(r, admin)
}

// jsParseInt mimics JS parseInt: leading digits are parsed, invalid -> !ok.
func jsParseInt(raw string) (int, bool) {
	s := strings.TrimSpace(raw)
	if s == "" {
		return 0, false
	}
	i := 0
	if s[0] == '+' || s[0] == '-' {
		i = 1
	}
	j := i
	for j < len(s) && s[j] >= '0' && s[j] <= '9' {
		j++
	}
	if j == i {
		return 0, false
	}
	n, err := strconv.Atoi(s[:j])
	if err != nil {
		return 0, false
	}
	return n, true
}

func firstQuery(c *gin.Context, keys ...string) string {
	for _, k := range keys {
		if v := c.Query(k); v != "" {
			return v
		}
	}
	return ""
}

func (s *Server) handleAdminListUsers(c *gin.Context) {
	limit := 10
	if n, ok := jsParseInt(firstQuery(c, "limit", "pageSize")); ok {
		limit = n
	}
	limit = max(1, min(100, limit))

	page := 1
	if n, ok := jsParseInt(firstQuery(c, "page", "current")); ok {
		page = n
	}
	page = max(1, page)

	startRaw, hasStart := c.GetQuery("_start")
	endRaw, hasEnd := c.GetQuery("_end")
	if hasStart && hasEnd {
		start := 0
		if n, ok := jsParseInt(startRaw); ok {
			start = max(0, n)
		}
		end := 10
		if n, ok := jsParseInt(endRaw); ok {
			end = max(start, n)
		}
		limit = max(1, end-start)
		page = start/limit + 1
	}

	offset := (page - 1) * limit
	search := strings.TrimSpace(firstQuery(c, "search", "q"))
	role := strings.TrimSpace(c.Query("role"))

	ctx := c.Request.Context()
	total, err := s.store.CountUsers(ctx, search, role)
	if err != nil {
		s.fail500(c, err)
		return
	}
	items, err := s.store.ListUsers(ctx, offset, limit, search, role)
	if err != nil {
		s.fail500(c, err)
		return
	}
	mapped := make([]map[string]any, 0, len(items))
	for _, u := range items {
		mapped = append(mapped, store.PublicUser(u))
	}
	c.Header("x-total-count", strconv.FormatInt(total, 10))
	ok(c, gin.H{"items": mapped, "total": total, "page": page, "limit": limit})
}

func (s *Server) handleAdminGetUser(c *gin.Context) {
	id, _ := jsParseInt(c.Param("id"))
	user, err := s.store.FindUserByID(c.Request.Context(), int64(id))
	if err != nil {
		s.fail500(c, err)
		return
	}
	if user == nil {
		fail(c, http.StatusNotFound, "用户不存在")
		return
	}
	ok(c, store.PublicUser(user))
}

func (s *Server) handleAdminCreateUser(c *gin.Context) {
	body, okBody := decodeBody(c)
	if !okBody {
		return
	}
	name := strings.TrimSpace(asString(body["username"]))
	if !usernameRe.MatchString(name) {
		fail(c, http.StatusBadRequest, "用户名需 2-20 位（字母/数字/下划线/中文）")
		return
	}
	password, isStr := body["password"].(string)
	if !isStr || runeLen(password) < 5 || runeLen(password) > 64 {
		fail(c, http.StatusBadRequest, "密码需 5-64 位")
		return
	}
	if v, present := body["nickname"]; present && v != nil && runeLen(asString(v)) > nicknameMaxLen {
		fail(c, http.StatusBadRequest, "昵称最长 24 字")
		return
	}
	role := "user"
	if v, present := body["role"]; present {
		role = asString(v)
	}
	if role != "admin" && role != "user" {
		fail(c, http.StatusBadRequest, "角色必须是 admin 或 user")
		return
	}
	ctx := c.Request.Context()
	existing, err := s.store.FindUserByUsername(ctx, name)
	if err != nil {
		s.fail500(c, err)
		return
	}
	if existing != nil {
		fail(c, http.StatusConflict, "用户名已存在")
		return
	}
	salt := security.NewSalt()
	hash, err := security.HashPassword(password, salt)
	if err != nil {
		s.fail500(c, err)
		return
	}
	var nickname *string
	if isTruthy(body["nickname"]) {
		n := asString(body["nickname"])
		nickname = &n
	}
	user, err := s.store.CreateUser(ctx, name, hash, salt, nickname, role, "", time.Now().UnixMilli())
	if err != nil {
		s.fail500(c, err)
		return
	}
	ok(c, store.PublicUser(user))
}

func (s *Server) handleAdminUpdateUser(c *gin.Context) {
	body, okBody := decodeBody(c)
	if !okBody {
		return
	}
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
	me := currentUser(c)
	params := store.AdminUpdateParams{}

	if v, present := body["nickname"]; present {
		if v != nil && runeLen(asString(v)) > nicknameMaxLen {
			fail(c, http.StatusBadRequest, "昵称最长 24 字")
			return
		}
		n := ""
		if v != nil {
			n = asString(v)
		}
		params.Nickname = &n
	}
	if v, present := body["avatar"]; present {
		a := ""
		if v != nil {
			a = asString(v)
		}
		params.Avatar = &a
	}
	if v, present := body["role"]; present {
		role := asString(v)
		if role != "admin" && role != "user" {
			fail(c, http.StatusBadRequest, "角色必须是 admin 或 user")
			return
		}
		if int64(id) == me.ID && role != "admin" {
			adminCount, err := s.store.CountUsers(ctx, "", "admin")
			if err != nil {
				s.fail500(c, err)
				return
			}
			if adminCount <= 1 {
				fail(c, http.StatusBadRequest, "系统至少需要保留一个管理员账号")
				return
			}
		}
		params.Role = &role
	}
	if v, present := body["password"]; present {
		if pw, isStr := v.(string); !isStr || pw != "" {
			if !isStr || runeLen(pw) < 5 || runeLen(pw) > 64 {
				fail(c, http.StatusBadRequest, "密码需 5-64 位")
				return
			}
			salt := security.NewSalt()
			hash, err := security.HashPassword(pw, salt)
			if err != nil {
				s.fail500(c, err)
				return
			}
			params.PasswordHash = &hash
			params.Salt = &salt
			if int64(id) != me.ID {
				if err := s.store.RevokeAllSessions(ctx, int64(id), ""); err != nil {
					s.fail500(c, err)
					return
				}
			}
		}
	}

	updated, err := s.store.AdminUpdateUser(ctx, int64(id), params, time.Now().UnixMilli())
	if err != nil {
		s.fail500(c, err)
		return
	}
	ok(c, store.PublicUser(updated))
}

func (s *Server) handleAdminDeleteUser(c *gin.Context) {
	id, _ := jsParseInt(c.Param("id"))
	me := currentUser(c)
	if int64(id) == me.ID {
		fail(c, http.StatusBadRequest, "不能删除当前登录的账号")
		return
	}
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
	if user.Role == "admin" {
		adminCount, err := s.store.CountUsers(ctx, "", "admin")
		if err != nil {
			s.fail500(c, err)
			return
		}
		if adminCount <= 1 {
			fail(c, http.StatusBadRequest, "系统至少需要保留一个管理员账号")
			return
		}
	}
	if err := s.store.AdminDeleteUser(ctx, int64(id)); err != nil {
		s.fail500(c, err)
		return
	}
	okMsg(c, "用户已删除")
}

func (s *Server) handleAdminSyncStats(c *gin.Context) {
	ctx := c.Request.Context()
	now := time.Now().UnixMilli()
	stats, err := s.store.GetGlobalSyncStats(ctx, now)
	if err != nil {
		s.fail500(c, err)
		return
	}
	events, err := s.store.ListRecentGlobalEvents(ctx, 50)
	if err != nil {
		s.fail500(c, err)
		return
	}
	ok(c, gin.H{
		"stats":  stats,
		"events": events,
		"time":   now,
	})
}
