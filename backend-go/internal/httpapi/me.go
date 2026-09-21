package httpapi

import (
	"net/http"
	"time"

	"github.com/gin-gonic/gin"

	"melos-backend/internal/security"
	"melos-backend/internal/store"
)

// registerMe mounts the /api/me routes (profile, password, email, sessions).
func (s *Server) registerMe(r *gin.Engine) {
	auth := s.requireUser()
	r.GET("/api/me", auth, s.handleGetMe)
	r.PUT("/api/me", auth, s.handleUpdateMe)
	r.PUT("/api/me/password", auth, s.handleChangePassword)
	r.POST("/api/me/email/bind", auth, s.handleEmailBind)
	r.POST("/api/me/email/unbind", auth, s.handleEmailUnbind)
	r.GET("/api/me/sessions", auth, s.handleListSessions)
	r.DELETE("/api/me/sessions/:id", auth, s.handleRevokeSession)
}

func (s *Server) handleGetMe(c *gin.Context) {
	ok(c, store.PublicUser(currentUser(c)))
}

// handleUpdateMe applies partial profile updates. Email uses three-state
// semantics: absent = unchanged, "" = unbind, value = bind (registration only).
func (s *Server) handleUpdateMe(c *gin.Context) {
	body, okBody := decodeBody(c)
	if !okBody {
		return
	}
	user := currentUser(c)
	ctx := c.Request.Context()

	if v, present := body["nickname"]; present && v != nil {
		if runeLen(asString(v)) > nicknameMaxLen {
			fail(c, http.StatusBadRequest, "昵称最长 24 字")
			return
		}
	}

	var normalizedEmail *string
	if v, present := body["email"]; present {
		email, okEmail, errMsg := normalizeEmail(v)
		if !okEmail {
			fail(c, http.StatusBadRequest, errMsg)
			return
		}
		if email != "" {
			holder, err := s.store.FindUserByEmail(ctx, email)
			if err != nil {
				s.fail500(c, err)
				return
			}
			if holder != nil && holder.ID != user.ID {
				fail(c, http.StatusConflict, "该邮箱已被其它账号绑定")
				return
			}
		}
		normalizedEmail = &email
	}

	updated, err := s.store.UpdateUserProfile(ctx, user.ID,
		optionalString(body, "nickname"), optionalString(body, "avatar"), optionalString(body, "background"),
		normalizedEmail, time.Now().UnixMilli())
	if err != nil {
		if isEmailConflict(err) {
			fail(c, http.StatusConflict, "该邮箱已被其它账号绑定")
			return
		}
		s.fail500(c, err)
		return
	}

	data := store.PublicUser(updated)
	if _, present := body["email"]; present {
		if normalizedEmail != nil && *normalizedEmail != "" {
			okMsgData(c, "邮箱已登记（未做邮件验证：服务端暂无 SMTP 配置，无法发送验证码）", data)
		} else {
			okMsgData(c, "邮箱已解绑", data)
		}
		return
	}
	ok(c, data)
}

// handleChangePassword verifies the old password, rotates the hash and revokes
// every other device session.
func (s *Server) handleChangePassword(c *gin.Context) {
	body, okBody := decodeBody(c)
	if !okBody {
		return
	}
	oldPassword, oldIsStr := body["oldPassword"].(string)
	if !oldIsStr || oldPassword == "" {
		fail(c, http.StatusBadRequest, "请输入原密码")
		return
	}
	newPassword, newIsStr := body["newPassword"].(string)
	if !newIsStr || newPassword == "" || runeLen(newPassword) < 5 || runeLen(newPassword) > 64 {
		fail(c, http.StatusBadRequest, "新密码需 5-64 位")
		return
	}
	ctx := c.Request.Context()
	user := currentUser(c)

	fresh, err := s.store.FindUserByID(ctx, user.ID)
	if err != nil {
		s.fail500(c, err)
		return
	}
	if fresh == nil {
		fail(c, http.StatusNotFound, "用户不存在")
		return
	}
	if !security.VerifyPassword(oldPassword, fresh.Salt, fresh.PasswordHash) {
		fail(c, http.StatusBadRequest, "原密码错误")
		return
	}
	salt := security.NewSalt()
	hash, err := security.HashPassword(newPassword, salt)
	if err != nil {
		s.fail500(c, err)
		return
	}
	if err := s.store.UpdateUserPassword(ctx, user.ID, hash, salt, time.Now().UnixMilli()); err != nil {
		s.fail500(c, err)
		return
	}
	if sess := currentSession(c); sess != nil {
		if err := s.store.RevokeAllSessions(ctx, user.ID, sess.SessionID); err != nil {
			s.fail500(c, err)
			return
		}
	}
	okMsg(c, "密码修改成功")
}

// handleEmailBind verifies a code for the new address then binds it.
func (s *Server) handleEmailBind(c *gin.Context) {
	body, okBody := decodeBody(c)
	if !okBody {
		return
	}
	email, okEmail, errMsg := normalizeEmail(body["email"])
	if !okEmail || email == "" {
		if errMsg == "" {
			errMsg = "请提供有效的新邮箱地址"
		}
		fail(c, http.StatusBadRequest, errMsg)
		return
	}
	codeStr := trimSpace(asString(body["code"]))
	if !isNumericCode(codeStr) {
		fail(c, http.StatusBadRequest, "请输入 6 位数字验证码")
		return
	}
	ctx := c.Request.Context()
	now := time.Now().UnixMilli()

	record, err := s.store.FindLatestValidEmailCode(ctx, email, "bind", now)
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
	user := currentUser(c)
	holder, err := s.store.FindUserByEmail(ctx, email)
	if err != nil {
		s.fail500(c, err)
		return
	}
	if holder != nil && holder.ID != user.ID {
		fail(c, http.StatusConflict, "该邮箱已被其它账号绑定")
		return
	}
	if err := s.store.MarkEmailCodeUsed(ctx, record.ID); err != nil {
		s.fail500(c, err)
		return
	}
	updated, err := s.store.UpdateUserProfile(ctx, user.ID, nil, nil, nil, &email, now)
	if err != nil {
		s.fail500(c, err)
		return
	}
	okMsgData(c, "邮箱绑定成功", store.PublicUser(updated))
}

// handleEmailUnbind verifies a code for the current address then unbinds it.
func (s *Server) handleEmailUnbind(c *gin.Context) {
	body, okBody := decodeBody(c)
	if !okBody {
		return
	}
	user := currentUser(c)
	currentEmail := user.Email
	if currentEmail == "" {
		fail(c, http.StatusBadRequest, "当前账号未绑定邮箱，无需解绑")
		return
	}
	codeStr := trimSpace(asString(body["code"]))
	if !isNumericCode(codeStr) {
		fail(c, http.StatusBadRequest, "请输入 6 位数字验证码")
		return
	}
	ctx := c.Request.Context()
	now := time.Now().UnixMilli()

	record, err := s.store.FindLatestValidEmailCode(ctx, currentEmail, "unbind", now)
	if err != nil {
		s.fail500(c, err)
		return
	}
	if record == nil || record.Code != codeStr {
		record, err = s.store.FindLatestValidEmailCode(ctx, currentEmail, "login", now)
		if err != nil {
			s.fail500(c, err)
			return
		}
	}
	if record == nil || record.Code != codeStr {
		fail(c, http.StatusBadRequest, "验证码错误或已过期")
		return
	}
	if err := s.store.MarkEmailCodeUsed(ctx, record.ID); err != nil {
		s.fail500(c, err)
		return
	}
	empty := ""
	updated, err := s.store.UpdateUserProfile(ctx, user.ID, nil, nil, nil, &empty, now)
	if err != nil {
		s.fail500(c, err)
		return
	}
	okMsgData(c, "邮箱已成功解绑", store.PublicUser(updated))
}

func (s *Server) handleListSessions(c *gin.Context) {
	list, err := s.store.ListSessions(c.Request.Context(), currentUser(c).ID)
	if err != nil {
		s.fail500(c, err)
		return
	}
	sess := currentSession(c)
	items := make([]gin.H, 0, len(list))
	for _, si := range list {
		items = append(items, gin.H{
			"id":         si.ID,
			"deviceName": si.DeviceName,
			"createdAt":  si.CreatedAt,
			"lastSeenAt": si.LastSeenAt,
			"expiresAt":  si.ExpiresAt,
			"current":    sess != nil && si.ID == sess.SessionID,
		})
	}
	ok(c, items)
}

func (s *Server) handleRevokeSession(c *gin.Context) {
	if err := s.store.RevokeSessionByID(c.Request.Context(), currentUser(c).ID, c.Param("id")); err != nil {
		s.fail500(c, err)
		return
	}
	okMsg(c, "已移除设备")
}
