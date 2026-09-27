// Package httpapi implements the Gin HTTP layer: routing, middleware, auth and
// the exact {code,msg,data} envelope used by the Android client and admin UI.
package httpapi

import (
	"fmt"
	"log"
	"net/http"
	"os"
	"runtime/debug"
	"strings"
	"time"

	"github.com/gin-gonic/gin"

	"melos-backend/internal/config"
	"melos-backend/internal/mail"
	"melos-backend/internal/middleware"
	"melos-backend/internal/security"
	"melos-backend/internal/store"
)

// Version is reported by GET / and /api/health.
const Version = "2.0.0"

const (
	ctxUserKey      = "melos.user"
	ctxSessionKey   = "melos.session"
	ctxTokenHashKey = "melos.tokenHash"
)

// Server bundles the dependencies shared by all handlers.
type Server struct {
	cfg     *config.Config
	store   *store.Store
	mail    *mail.Client
	lockout *security.LoginLockout
}

// NewServer builds a Server.
func NewServer(cfg *config.Config, st *store.Store) *Server {
	return &Server{
		cfg:     cfg,
		store:   st,
		mail:    mail.New(cfg),
		lockout: security.NewLoginLockout(),
	}
}

// Router assembles the Gin engine (mirrors buildApp() in app.js).
func (s *Server) Router() *gin.Engine {
	gin.SetMode(gin.ReleaseMode)
	r := gin.New()
	r.Use(gin.Logger(), s.recoverer())

	if s.cfg.TrustProxy {
		_ = r.SetTrustedProxies([]string{"0.0.0.0/0", "::/0"})
	} else {
		_ = r.SetTrustedProxies(nil)
	}

	r.Use(cors())

	if !s.cfg.RateLimitDisabled {
		authMW := middleware.NewRateLimiter("auth", 10, 60_000).Middleware()
		apiMW := middleware.NewRateLimiter("api", 120, 60_000).Middleware()
		r.Use(func(c *gin.Context) {
			switch {
			case strings.HasPrefix(c.Request.URL.Path, "/api/auth/"):
				authMW(c)
			case strings.HasPrefix(c.Request.URL.Path, "/api/"):
				apiMW(c)
			default:
				c.Next()
			}
		})
	}

	r.GET("/", func(c *gin.Context) {
		c.PureJSON(http.StatusOK, gin.H{
			"name":    "melos-backend",
			"version": Version,
			"endpoints": []string{
				"/api/health",
				"/api/auth/register", "/api/auth/login", "/api/auth/logout", "/api/auth/logout-all",
				"/api/me", "/api/me/password", "/api/me/sessions",
				"/api/sync/pull", "/api/sync/push",
				"/api/admin/users", "/api/admin/versions",
				"/api/app/version/latest",
			},
		})
	})

	r.GET("/api/health", func(c *gin.Context) {
		c.PureJSON(http.StatusOK, gin.H{
			"code": 200,
			"msg":  "ok",
			"data": gin.H{"ok": true, "time": time.Now().UnixMilli(), "version": Version},
		})
	})

	s.registerAuth(r)
	s.registerMe(r)
	s.registerSync(r)
	s.registerAdmin(r)
	s.registerVersionPublic(r)

	r.NoRoute(func(c *gin.Context) {
		fail(c, http.StatusNotFound, "接口不存在")
	})

	return r
}

func cors() gin.HandlerFunc {
	return func(c *gin.Context) {
		h := c.Writer.Header()
		h.Set("Access-Control-Allow-Origin", "*")
		h.Set("Access-Control-Allow-Methods", "GET,POST,PUT,PATCH,DELETE,OPTIONS")
		h.Set("Access-Control-Allow-Headers", "Content-Type, Authorization")
		h.Set("Access-Control-Expose-Headers", "x-total-count")
		h.Set("Access-Control-Max-Age", "86400")
		if c.Request.Method == http.MethodOptions {
			c.AbortWithStatus(http.StatusNoContent)
			return
		}
		c.Next()
	}
}

func (s *Server) recoverer() gin.HandlerFunc {
	return func(c *gin.Context) {
		defer func() {
			if rec := recover(); rec != nil {
				log.Printf("[melos-backend] panic: %v\n%s", rec, debug.Stack())
				fail(c, http.StatusInternalServerError, s.internalMsg(fmt.Errorf("%v", rec)))
				c.Abort()
			}
		}()
		c.Next()
	}
}

// internalMsg mirrors app.js: 5xx messages are hidden in production.
func (s *Server) internalMsg(err error) string {
	if os.Getenv("NODE_ENV") == "production" {
		return "服务器内部错误"
	}
	if err == nil {
		return "服务器内部错误"
	}
	return err.Error()
}

func (s *Server) fail500(c *gin.Context, err error) {
	log.Printf("[melos-backend] error: %v", err)
	fail(c, http.StatusInternalServerError, s.internalMsg(err))
}

func currentUser(c *gin.Context) *store.User {
	if v, ok := c.Get(ctxUserKey); ok {
		if u, ok := v.(*store.User); ok {
			return u
		}
	}
	return nil
}

func currentSession(c *gin.Context) *store.SessionUser {
	if v, ok := c.Get(ctxSessionKey); ok {
		if su, ok := v.(*store.SessionUser); ok {
			return su
		}
	}
	return nil
}

func currentTokenHash(c *gin.Context) string {
	v, _ := c.Get(ctxTokenHashKey)
	str, _ := v.(string)
	return str
}

// authenticate validates the bearer token and injects the user/session into the
// context. It writes the error response and returns false on failure.
func (s *Server) authenticate(c *gin.Context) bool {
	header := c.GetHeader("Authorization")
	var token string
	if strings.HasPrefix(header, "Bearer ") {
		token = strings.TrimSpace(header[len("Bearer "):])
	} else {
		token = strings.TrimSpace(header)
	}
	if token == "" {
		fail(c, http.StatusUnauthorized, "未登录")
		return false
	}
	tokenHash := security.HashToken(token)
	su, err := s.store.FindSessionByTokenHash(c.Request.Context(), tokenHash)
	if err != nil {
		s.fail500(c, err)
		return false
	}
	if su == nil || su.RevokedAt.Valid {
		fail(c, http.StatusUnauthorized, "登录已失效，请重新登录")
		return false
	}
	now := time.Now().UnixMilli()
	if su.ExpiresAt <= now {
		_ = s.store.RevokeSessionByTokenHash(c.Request.Context(), tokenHash)
		fail(c, http.StatusUnauthorized, "登录已过期，请重新登录")
		return false
	}
	if now-su.LastSeenAt >= 5*60*1000 {
		_ = s.store.TouchSession(c.Request.Context(), su.SessionID, now)
	}
	c.Set(ctxUserKey, &su.User)
	c.Set(ctxSessionKey, su)
	c.Set(ctxTokenHashKey, tokenHash)
	return true
}

func (s *Server) requireUser() gin.HandlerFunc {
	return func(c *gin.Context) {
		if !s.authenticate(c) {
			c.Abort()
			return
		}
		c.Next()
	}
}

func (s *Server) requireAdmin() gin.HandlerFunc {
	return func(c *gin.Context) {
		if !s.authenticate(c) {
			c.Abort()
			return
		}
		if u := currentUser(c); u == nil || u.Role != "admin" {
			fail(c, http.StatusForbidden, "无权限访问：仅管理员可用")
			c.Abort()
			return
		}
		c.Next()
	}
}

func (s *Server) requireAdminOrReleaseToken() gin.HandlerFunc {
	return func(c *gin.Context) {
		token := strings.TrimSpace(c.GetHeader("X-Release-Token"))
		if token == "" {
			if auth := strings.TrimSpace(c.GetHeader("Authorization")); strings.HasPrefix(auth, "Bearer ") {
				cand := strings.TrimSpace(strings.TrimPrefix(auth, "Bearer "))
				if s.cfg.ReleaseToken != "" && cand == s.cfg.ReleaseToken {
					token = cand
				}
			}
		}
		if s.cfg.ReleaseToken != "" && token != "" && token == s.cfg.ReleaseToken {
			c.Next()
			return
		}

		if !s.authenticate(c) {
			c.Abort()
			return
		}
		if u := currentUser(c); u == nil || u.Role != "admin" {
			fail(c, http.StatusForbidden, "无权限访问：仅管理员可用")
			c.Abort()
			return
		}
		c.Next()
	}
}
