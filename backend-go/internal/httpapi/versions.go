package httpapi

import (
	"crypto/sha256"
	"encoding/hex"
	"errors"
	"fmt"
	"io"
	"net/http"
	"os"
	"path/filepath"
	"strconv"
	"strings"
	"time"

	"github.com/gin-gonic/gin"

	"melos-backend/internal/store"
)

// apkURLPrefix is the public download path stored in app_versions.apk_url.
const apkURLPrefix = "/api/app/download/"

// registerVersionPublic mounts the unauthenticated client-facing endpoints.
func (s *Server) registerVersionPublic(r *gin.Engine) {
	r.GET("/api/app/version/latest", s.handleAppLatestVersion)
	r.GET("/api/app/download/:file", s.handleAppDownload)
}

// registerVersionAdmin mounts the admin console endpoints for releases.
func (s *Server) registerVersionAdmin(r *gin.Engine, admin gin.HandlerFunc) {
	r.GET("/api/admin/versions", admin, s.handleAdminListVersions)
	r.POST("/api/admin/versions", admin, s.handleAdminCreateVersion)
	r.PATCH("/api/admin/versions/:id", admin, s.handleAdminUpdateVersion)
	r.DELETE("/api/admin/versions/:id", admin, s.handleAdminDeleteVersion)
}

func formFirst(c *gin.Context, keys ...string) string {
	for _, k := range keys {
		if v := strings.TrimSpace(c.PostForm(k)); v != "" {
			return v
		}
	}
	return ""
}

// parseBoolLoose accepts bool / 0|1 / "true"|"false" form values.
func parseBoolLoose(v any) (bool, bool) {
	switch t := v.(type) {
	case bool:
		return t, true
	case float64:
		return t != 0, true
	case string:
		switch strings.ToLower(strings.TrimSpace(t)) {
		case "1", "true", "yes", "on":
			return true, true
		case "0", "false", "no", "off", "":
			return false, true
		}
	}
	return false, false
}

func (s *Server) handleAdminListVersions(c *gin.Context) {
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

	ctx := c.Request.Context()
	total, err := s.store.CountVersions(ctx)
	if err != nil {
		s.fail500(c, err)
		return
	}
	items, err := s.store.ListVersions(ctx, (page-1)*limit, limit)
	if err != nil {
		s.fail500(c, err)
		return
	}
	mapped := make([]map[string]any, 0, len(items))
	for _, v := range items {
		mapped = append(mapped, v.Public())
	}
	c.Header("x-total-count", strconv.FormatInt(total, 10))
	ok(c, gin.H{"items": mapped, "total": total, "page": page, "limit": limit})
}

// handleAdminCreateVersion accepts multipart/form-data (an APK file plus
// metadata), stores the file locally and publishes the release row.
func (s *Server) handleAdminCreateVersion(c *gin.Context) {
	// Enforce the APK size limit before multipart parsing reads the body.
	c.Request.Body = http.MaxBytesReader(c.Writer, c.Request.Body, s.cfg.APKMaxBytes()+1<<20)

	fileHeader, err := c.FormFile("file")
	if err != nil {
		log.Printf("[melos-backend] 读取上传的 APK 失败: %v", err)
		if isBodyTooLarge(err) {
			fail(c, http.StatusRequestEntityTooLarge, fmt.Sprintf("APK 文件过大（上限 %dMB）", s.cfg.APKMaxMB))
			return
		}
		fail(c, http.StatusBadRequest, "请上传 APK 文件（multipart 字段名 file）")
		return
	}

	codeRaw := formFirst(c, "versionCode", "version_code")
	code, err := strconv.ParseInt(codeRaw, 10, 64)
	if err != nil || code <= 0 {
		fail(c, http.StatusBadRequest, "versionCode 必须为正整数")
		return
	}
	versionName := formFirst(c, "versionName", "version_name")
	if versionName == "" {
		fail(c, http.StatusBadRequest, "versionName 不能为空")
		return
	}
	changelog := strings.TrimSpace(formFirst(c, "changelog"))
	forceUpdate := false
	if raw := formFirst(c, "forceUpdate", "force_update"); raw != "" {
		b, ok := parseBoolLoose(raw)
		if !ok {
			fail(c, http.StatusBadRequest, "forceUpdate 必须为布尔值")
			return
		}
		forceUpdate = b
	}
	enabled := true
	if raw := formFirst(c, "enabled"); raw != "" {
		b, ok := parseBoolLoose(raw)
		if !ok {
			fail(c, http.StatusBadRequest, "enabled 必须为布尔值")
			return
		}
		enabled = b
	}

	ctx := c.Request.Context()
	if existing, err := s.store.FindVersionByCode(ctx, code); err != nil {
		s.fail500(c, err)
		return
	} else if existing != nil {
		fail(c, http.StatusConflict, "该 versionCode 已存在")
		return
	}

	if err := os.MkdirAll(s.cfg.APKDir, 0o755); err != nil {
		s.fail500(c, err)
		return
	}
	name := fmt.Sprintf("%d-%s.apk", code, sanitizeUsernameSegment(versionName))
	full := filepath.Join(s.cfg.APKDir, name)
	if _, statErr := os.Stat(full); statErr == nil {
		name = fmt.Sprintf("%d-%s-%d.apk", code, sanitizeUsernameSegment(versionName), time.Now().UnixNano())
		full = filepath.Join(s.cfg.APKDir, name)
	}

	src, err := fileHeader.Open()
	if err != nil {
		s.fail500(c, err)
		return
	}
	defer src.Close()

	dst, err := os.Create(full)
	if err != nil {
		s.fail500(c, err)
		return
	}
	hasher := sha256.New()
	size, copyErr := io.Copy(io.MultiWriter(dst, hasher), src)
	closeErr := dst.Close()
	if copyErr != nil {
		_ = os.Remove(full)
		if isBodyTooLarge(copyErr) {
			fail(c, http.StatusRequestEntityTooLarge, fmt.Sprintf("APK 文件过大（上限 %dMB）", s.cfg.APKMaxMB))
			return
		}
		s.fail500(c, copyErr)
		return
	}
	if closeErr != nil {
		_ = os.Remove(full)
		s.fail500(c, closeErr)
		return
	}

	hash := formFirst(c, "hashSha256", "hash_sha256")
	if hash == "" {
		hash = hex.EncodeToString(hasher.Sum(nil))
	}

	row, err := s.store.CreateVersion(ctx, store.CreateVersionParams{
		VersionCode: code,
		VersionName: versionName,
		ApkURL:      apkURLPrefix + name,
		FileSize:    size,
		HashSHA256:  hash,
		Changelog:   changelog,
		ForceUpdate: forceUpdate,
		Enabled:     enabled,
	})
	if err != nil {
		_ = os.Remove(full)
		if isUniqueViolation(err) {
			fail(c, http.StatusConflict, "该 versionCode 已存在")
			return
		}
		s.fail500(c, err)
		return
	}
	ok(c, row.Public())
}

// handleAdminUpdateVersion patches enabled / changelog / force_update.
func (s *Server) handleAdminUpdateVersion(c *gin.Context) {
	body, okBody := decodeBody(c)
	if !okBody {
		return
	}
	id, _ := jsParseInt(c.Param("id"))
	ctx := c.Request.Context()

	row, err := s.store.FindVersionByID(ctx, int64(id))
	if err != nil {
		s.fail500(c, err)
		return
	}
	if row == nil {
		fail(c, http.StatusNotFound, "版本不存在")
		return
	}

	params := store.UpdateVersionParams{}
	if v, present := body["enabled"]; present {
		b, okBool := parseBoolLoose(v)
		if !okBool {
			fail(c, http.StatusBadRequest, "enabled 必须为布尔值")
			return
		}
		params.Enabled = &b
	}
	if v, present := body["forceUpdate"]; present {
		b, okBool := parseBoolLoose(v)
		if !okBool {
			fail(c, http.StatusBadRequest, "forceUpdate 必须为布尔值")
			return
		}
		params.ForceUpdate = &b
	} else if v, present := body["force_update"]; present {
		b, okBool := parseBoolLoose(v)
		if !okBool {
			fail(c, http.StatusBadRequest, "forceUpdate 必须为布尔值")
			return
		}
		params.ForceUpdate = &b
	}
	if v, present := body["changelog"]; present {
		text := ""
		if v != nil {
			text = asString(v)
		}
		params.Changelog = &text
	}

	updated, err := s.store.UpdateVersion(ctx, int64(id), params)
	if err != nil {
		s.fail500(c, err)
		return
	}
	ok(c, updated.Public())
}

// handleAdminDeleteVersion removes the row and its local APK file.
func (s *Server) handleAdminDeleteVersion(c *gin.Context) {
	id, _ := jsParseInt(c.Param("id"))
	row, err := s.store.DeleteVersion(c.Request.Context(), int64(id))
	if err != nil {
		s.fail500(c, err)
		return
	}
	if row == nil {
		fail(c, http.StatusNotFound, "版本不存在")
		return
	}
	removeAPKFile(s.cfg.APKDir, row.ApkURL)
	okMsg(c, "版本已删除")
}

// handleAppLatestVersion returns the highest enabled release (public).
func (s *Server) handleAppLatestVersion(c *gin.Context) {
	row, err := s.store.LatestEnabledVersion(c.Request.Context())
	if err != nil {
		s.fail500(c, err)
		return
	}
	ok(c, row.Public())
}

// handleAppDownload serves a stored APK file (public).
func (s *Server) handleAppDownload(c *gin.Context) {
	name := c.Param("file")
	if name == "" || name != filepath.Base(name) || strings.ContainsAny(name, `/\`) || strings.Contains(name, "..") {
		fail(c, http.StatusBadRequest, "非法的文件名")
		return
	}
	full := filepath.Join(s.cfg.APKDir, name)
	info, err := os.Stat(full)
	if err != nil || info.IsDir() {
		fail(c, http.StatusNotFound, "文件不存在")
		return
	}
	c.Header("Content-Type", "application/vnd.android.package-archive")
	c.Header("Content-Disposition", fmt.Sprintf("attachment; filename=%q", name))
	c.File(full)
}

// removeAPKFile deletes the local file behind a stored apk_url (best effort).
func removeAPKFile(dir, apkURL string) {
	if !strings.HasPrefix(apkURL, apkURLPrefix) {
		return
	}
	name := strings.TrimPrefix(apkURL, apkURLPrefix)
	if name == "" || name != filepath.Base(name) {
		return
	}
	_ = os.Remove(filepath.Join(dir, name))
}

func isBodyTooLarge(err error) bool {
	var mbe *http.MaxBytesError
	if errors.As(err, &mbe) {
		return true
	}
	return err != nil && strings.Contains(strings.ToLower(err.Error()), "too large")
}

func isUniqueViolation(err error) bool {
	return err != nil && strings.Contains(strings.ToLower(err.Error()), "unique constraint failed")
}
