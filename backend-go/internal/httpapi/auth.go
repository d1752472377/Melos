package httpapi

import (
	"fmt"
	"log"
	"net/http"
	"strconv"
	"strings"
	"time"

	"github.com/gin-gonic/gin"

	"melos-backend/internal/security"
	"melos-backend/internal/store"
)

// registerAuth mounts /api/auth/* and the logout routes.
func (s *Server) registerAuth(r *gin.Engine) {
	r.POST("/api/auth/send-code", s.handleSendCode)
	r.POST("/api/auth/email-login", s.handleEmailLogin)
	r.POST("/api/auth/register", s.handleRegister)
	r.POST("/api/auth/login", s.handleLogin)
	r.POST("/api/auth/logout", s.requireUser(), s.handleLogout)
	r.POST("/api/auth/logout-all", s.requireUser(), s.handleLogoutAll)
}

// issueSession creates an opaque token + session for the user.
func (s *Server) issueSession(c *gin.Context, u *store.User, deviceName string) (string, int64, error) {
	token := security.GenerateToken()
	_, expiresAt, err := s.store.CreateSession(c.Request.Context(), u.ID, security.HashToken(token),
		deviceName, s.cfg.SessionTTLMillis(), s.cfg.MaxSessionsPerUser, time.Now().UnixMilli())
	if err != nil {
		return "", 0, err
	}
	return token, expiresAt, nil
}

func deviceNameOf(body map[string]any) string {
	if isTruthy(body["deviceName"]) {
		return asString(body["deviceName"])
	}
	return ""
}

// handleSendCode issues a random 6-digit code (60s cooldown, 5min validity).
func (s *Server) handleSendCode(c *gin.Context) {
	body, okBody := decodeBody(c)
	if !okBody {
		return
	}
	email, okEmail, errMsg := normalizeEmail(body["email"])
	if !okEmail || email == "" {
		if errMsg == "" {
			errMsg = "请提供有效的邮箱地址"
		}
		fail(c, http.StatusBadRequest, errMsg)
		return
	}
	scene := asString(body["scene"])
	if scene == "" {
		scene = "login"
	}
	now := time.Now().UnixMilli()

	lastSentAt, err := s.store.GetLatestCodeSentTime(c.Request.Context(), email, scene)
	if err != nil {
		s.fail500(c, err)
		return
	}
	const coolDownMs = 60 * 1000
	if lastSentAt > 0 && now-lastSentAt < coolDownMs {
		waitSeconds := (coolDownMs - (now - lastSentAt) + 999) / 1000
		fail(c, http.StatusTooManyRequests, fmt.Sprintf("发送太频繁，请稍后 %d 秒后再试", waitSeconds))
		return
	}

	code := fmt.Sprintf("%06d", randomInt(100000, 1000000))
	expiresAt := now + 5*60*1000

	// 先落库再发信：落库是为了让 60 秒冷却挡住并发狂刷（发信本身慢且不可回滚）。
	// 但发信失败必须把这行删掉，否则用户没收到邮件却要等 60 秒才能重试。
	codeID, err := s.store.SaveEmailCode(c.Request.Context(), email, code, scene, expiresAt, now)
	if err != nil {
		s.fail500(c, err)
		return
	}
	if err := s.mail.SendVerifyCode(c.Request.Context(), email, code, scene); err != nil {
		if delErr := s.store.DeleteEmailCode(c.Request.Context(), codeID); delErr != nil {
			log.Printf("[melos-backend] 回滚验证码失败 id=%d: %v", codeID, delErr)
		}
		s.fail500(c, fmt.Errorf("验证码发送失败：%s", err.Error()))
		return
	}
	okMsg(c, "验证码已发送，请注意查收邮件")
}

// handleEmailLogin verifies a code, then logs in or auto-registers the user.
func (s *Server) handleEmailLogin(c *gin.Context) {
	body, okBody := decodeBody(c)
	if !okBody {
		return
	}
	email, okEmail, errMsg := normalizeEmail(body["email"])
	if !okEmail || email == "" {
		if errMsg == "" {
			errMsg = "请提供有效的邮箱地址"
		}
		fail(c, http.StatusBadRequest, errMsg)
		return
	}
	codeStr := strings.TrimSpace(asString(body["code"]))
	if !isNumericCode(codeStr) {
		fail(c, http.StatusBadRequest, "请输入正确的验证码")
		return
	}
	ctx := c.Request.Context()
	now := time.Now().UnixMilli()

	record, err := s.store.FindLatestValidEmailCode(ctx, email, "login", now)
	if err != nil {
		s.fail500(c, err)
		return
	}
	if record == nil || record.Code != codeStr {
		fail(c, http.StatusBadRequest, "验证码错误或已过期")
		return
	}
	if err := s.store.MarkEmailCodeUsed(ctx, record.ID); err != nil {
		s.fail500(c, err)
		return
	}

	user, err := s.store.FindUserByEmail(ctx, email)
	if err != nil {
		s.fail500(c, err)
		return
	}
	if user == nil {
		user, err = s.autoRegisterByEmail(c, email, now)
		if err != nil {
			return
		}
	}

	token, expiresAt, err := s.issueSession(c, user, deviceNameOf(body))
	if err != nil {
		s.fail500(c, err)
		return
	}
	ok(c, gin.H{"token": token, "expiresAt": expiresAt, "user": store.PublicUser(user)})
}

// autoRegisterByEmail creates a user for an email that logged in for the first
// time, deriving a unique valid username from the local part.
func (s *Server) autoRegisterByEmail(c *gin.Context, email string, now int64) (*store.User, error) {
	ctx := c.Request.Context()
	baseName := sanitizeUsernameSegment(prefixBeforeAt(email))
	if runes := []rune(baseName); len(runes) > 12 {
		baseName = string(runes[:12])
	}
	if baseName == "" {
		baseName = "user"
	}
	candidate := baseName
	if len([]rune(candidate)) < 2 {
		candidate = "m_" + candidate
	}
	counter := 0
	for {
		existing, err := s.store.FindUserByUsername(ctx, candidate)
		if err != nil {
			s.fail500(c, err)
			return nil, err
		}
		if existing == nil {
			break
		}
		counter++
		runes := []rune(baseName)
		if len(runes) > 10 {
			runes = runes[:10]
		}
		candidate = string(runes) + "_" + randomBase36(4)
		if counter > 10 {
			candidate = "u_" + base36OfMillis(now)
			break
		}
	}

	salt := security.NewSalt()
	randomPassword := randomBase36(32)
	hash, err := security.HashPassword(randomPassword, salt)
	if err != nil {
		s.fail500(c, err)
		return nil, err
	}
	nickname := prefixBeforeAt(email)
	if nickname == "" {
		nickname = candidate
	}
	user, err := s.store.CreateUser(ctx, candidate, hash, salt, &nickname, "user", email, now)
	if err != nil {
		if isEmailConflict(err) {
			user, err = s.store.FindUserByEmail(ctx, email)
			if err != nil {
				s.fail500(c, err)
				return nil, err
			}
			return user, nil
		}
		s.fail500(c, err)
		return nil, err
	}
	return user, nil
}

// handleRegister creates a user (email + code required) and logs them in.
func (s *Server) handleRegister(c *gin.Context) {
	body, okBody := decodeBody(c)
	if !okBody {
		return
	}
	name := strings.TrimSpace(asString(body["username"]))
	if msg := validateRegister(name, body["password"], body["nickname"]); msg != "" {
		fail(c, http.StatusBadRequest, msg)
		return
	}
	email, okEmail, errMsg := normalizeEmail(body["email"])
	if !okEmail || email == "" {
		if errMsg == "" {
			errMsg = "注册必须提供有效的邮箱地址"
		}
		fail(c, http.StatusBadRequest, errMsg)
		return
	}
	codeStr := strings.TrimSpace(asString(body["code"]))
	if !isNumericCode(codeStr) {
		fail(c, http.StatusBadRequest, "请输入 6 位数字验证码")
		return
	}
	ctx := c.Request.Context()
	now := time.Now().UnixMilli()

	record, err := s.store.FindLatestValidEmailCode(ctx, email, "register", now)
	if err != nil {
		s.fail500(c, err)
		return
	}
	if record == nil || record.Code != codeStr {
		record, err = s.store.FindLatestValidEmailCode(ctx, email, "login", now)
		if err != nil {
			s.fail500(c, err)
			return
		}
	}
	if record == nil || record.Code != codeStr {
		fail(c, http.StatusBadRequest, "验证码错误或已过期")
		return
	}

	holder, err := s.store.FindUserByEmail(ctx, email)
	if err != nil {
		s.fail500(c, err)
		return
	}
	if holder != nil {
		fail(c, http.StatusConflict, "该邮箱已被其它账号绑定")
		return
	}
	if existing, err := s.store.FindUserByUsername(ctx, name); err != nil {
		s.fail500(c, err)
		return
	} else if existing != nil {
		fail(c, http.StatusConflict, "用户名已存在")
		return
	}
	if err := s.store.MarkEmailCodeUsed(ctx, record.ID); err != nil {
		s.fail500(c, err)
		return
	}

	salt := security.NewSalt()
	hash, err := security.HashPassword(asString(body["password"]), salt)
	if err != nil {
		s.fail500(c, err)
		return
	}
	var nickname *string
	if isTruthy(body["nickname"]) {
		n := asString(body["nickname"])
		nickname = &n
	}
	user, err := s.store.CreateUser(ctx, name, hash, salt, nickname, "user", email, now)
	if err != nil {
		if isEmailConflict(err) {
			fail(c, http.StatusConflict, "该邮箱已被其它账号绑定")
			return
		}
		s.fail500(c, err)
		return
	}
	token, expiresAt, err := s.issueSession(c, user, deviceNameOf(body))
	if err != nil {
		s.fail500(c, err)
		return
	}
	ok(c, gin.H{"token": token, "expiresAt": expiresAt, "user": store.PublicUser(user)})
}

// handleLogin authenticates by username or email with brute-force lockout.
func (s *Server) handleLogin(c *gin.Context) {
	body, okBody := decodeBody(c)
	if !okBody {
		return
	}
	name := strings.TrimSpace(asString(body["username"]))
	password, isStr := body["password"].(string)
	if name == "" || !isStr || password == "" {
		fail(c, http.StatusBadRequest, "需要 username / password")
		return
	}
	ctx := c.Request.Context()
	ip := c.ClientIP()

	if s.lockout.IsLocked(name, ip) {
		fail(c, http.StatusTooManyRequests, "失败次数过多，请 15 分钟后再试")
		return
	}
	user, err := s.store.FindUserByUsername(ctx, name)
	if err != nil {
		s.fail500(c, err)
		return
	}
	if user == nil {
		user, err = s.store.FindUserByEmail(ctx, strings.ToLower(name))
		if err != nil {
			s.fail500(c, err)
			return
		}
	}
	if user == nil {
		s.lockout.RecordFailure(name, ip)
		fail(c, http.StatusUnauthorized, "用户名或密码错误")
		return
	}
	if !security.VerifyPassword(password, user.Salt, user.PasswordHash) {
		s.lockout.RecordFailure(name, ip)
		fail(c, http.StatusUnauthorized, "用户名或密码错误")
		return
	}
	s.lockout.Clear(name, ip)

	token, expiresAt, err := s.issueSession(c, user, deviceNameOf(body))
	if err != nil {
		s.fail500(c, err)
		return
	}
	ok(c, gin.H{"token": token, "expiresAt": expiresAt, "user": store.PublicUser(user)})
}

func (s *Server) handleLogout(c *gin.Context) {
	if err := s.store.RevokeSessionByTokenHash(c.Request.Context(), currentTokenHash(c)); err != nil {
		s.fail500(c, err)
		return
	}
	okMsg(c, "已退出登录")
}

func (s *Server) handleLogoutAll(c *gin.Context) {
	if err := s.store.RevokeAllSessions(c.Request.Context(), currentUser(c).ID, ""); err != nil {
		s.fail500(c, err)
		return
	}
	okMsg(c, "已退出所有设备")
}

// isNumericCode mirrors /^\d{4,8}$/.
func isNumericCode(s string) bool {
	if len(s) < 4 || len(s) > 8 {
		return false
	}
	for _, r := range s {
		if r < '0' || r > '9' {
			return false
		}
	}
	return true
}

func prefixBeforeAt(email string) string {
	if i := strings.IndexByte(email, '@'); i >= 0 {
		return email[:i]
	}
	return email
}

// base36OfMillis mirrors Date.now().toString(36).slice(-6).
func base36OfMillis(ms int64) string {
	s := strconv.FormatInt(ms, 36)
	if len(s) > 6 {
		return s[len(s)-6:]
	}
	return s
}
