package httpapi

import (
	"context"
	"crypto/sha256"
	"encoding/hex"
	"encoding/json"
	"errors"
	"fmt"
	"io"
	"log"
	"net/http"
	"os"
	"path/filepath"
	"sort"
	"strconv"
	"strings"
	"time"

	"github.com/gin-gonic/gin"

	"melos-backend/internal/store"
)

// apkURLPrefix is the public download path stored in app_versions.apk_url.
const apkURLPrefix = "/api/app/download/"

func (s *Server) enrichVersionPublic(v *store.AppVersion) map[string]any {
	if v == nil {
		return nil
	}
	m := v.Public()
	apkFile := strings.TrimPrefix(v.ApkURL, apkURLPrefix)
	_, statErr := os.Stat(filepath.Join(s.cfg.APKDir, apkFile))
	hasLocal := statErr == nil
	m["has_local_file"] = hasLocal
	m["hasLocalFile"] = hasLocal
	return m
}

// registerVersionPublic mounts the unauthenticated client-facing endpoints.
func (s *Server) registerVersionPublic(r *gin.Engine) {
	r.GET("/api/app/version/latest", s.handleAppLatestVersion)
	r.HEAD("/api/app/version/latest", s.handleAppLatestVersion)
	r.GET("/api/app/download/:file", s.handleAppDownload)
	r.HEAD("/api/app/download/:file", s.handleAppDownload)
}

// registerVersionAdmin mounts the admin console endpoints for releases.
func (s *Server) registerVersionAdmin(r *gin.Engine, admin gin.HandlerFunc) {
	r.GET("/api/admin/versions", admin, s.handleAdminListVersions)
	r.POST("/api/admin/versions", s.requireAdminOrReleaseToken(), s.handleAdminCreateVersion)
	r.POST("/api/admin/versions/sync-github", s.requireAdminOrReleaseToken(), s.handleAdminSyncGitHub)
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
		mapped = append(mapped, s.enrichVersionPublic(v))
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

	// 自动清理服务器旧版本 APK，本地只保留当前最新版本
	pruneOldAPKs(s.cfg.APKDir, name)

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
	ok(c, s.enrichVersionPublic(row))
}

type gitHubAsset struct {
	ID                 int64  `json:"id"`
	Name               string `json:"name"`
	Size               int64  `json:"size"`
	BrowserDownloadURL string `json:"browser_download_url"`
	URL                string `json:"url"`
}

type gitHubRelease struct {
	TagName string        `json:"tag_name"`
	Name    string        `json:"name"`
	Body    string        `json:"body"`
	Assets  []gitHubAsset `json:"assets"`
}

type releaseUpdateJSON struct {
	VersionName string `json:"versionName"`
	VersionCode int64  `json:"versionCode"`
	ApkSize     int64  `json:"apkSize"`
	SHA256      string `json:"sha256"`
	MD5         string `json:"md5"`
	ForceUpdate bool   `json:"forceUpdate"`
	UpdateLog   string `json:"updateLog"`
	PublishTime string `json:"publishTime"`
}

// handleAdminSyncGitHub fetches the latest release from GitHub, downloads the APK,
// deletes any old APKs to ensure only the latest APK remains on disk, and records it in the database.
func (s *Server) handleAdminSyncGitHub(c *gin.Context) {
	repo := strings.TrimSpace(firstQuery(c, "repo"))
	tag := strings.TrimSpace(firstQuery(c, "tag"))
	if repo == "" {
		repo = s.cfg.GitHubRepo
	}
	if repo == "" {
		repo = "d1752472377/Melos"
	}

	apiURL := fmt.Sprintf("https://api.github.com/repos/%s/releases/latest", repo)
	if tag != "" {
		apiURL = fmt.Sprintf("https://api.github.com/repos/%s/releases/tags/%s", repo, tag)
	}

	ghToken := strings.TrimSpace(firstQuery(c, "github_token", "token"))
	if ghToken == "" {
		ghToken = s.cfg.GitHubToken
	}

	client := &http.Client{Timeout: 30 * time.Second}
	req, err := http.NewRequestWithContext(c.Request.Context(), "GET", apiURL, nil)
	if err != nil {
		s.fail500(c, err)
		return
	}
	req.Header.Set("User-Agent", "Melos-Backend")
	req.Header.Set("Accept", "application/vnd.github.v3+json")
	if ghToken != "" {
		req.Header.Set("Authorization", "Bearer "+ghToken)
	}

	resp, err := client.Do(req)
	if err != nil {
		fail(c, http.StatusBadGateway, fmt.Sprintf("请求 GitHub API 失败: %v", err))
		return
	}
	defer resp.Body.Close()

	if resp.StatusCode != http.StatusOK {
		fail(c, resp.StatusCode, fmt.Sprintf("GitHub Release 获取失败 (HTTP %d)，请确认仓库是否存在或公开", resp.StatusCode))
		return
	}

	var rel gitHubRelease
	if err := json.NewDecoder(resp.Body).Decode(&rel); err != nil {
		s.fail500(c, fmt.Errorf("解析 GitHub Release 失败: %w", err))
		return
	}

	// 查找 update.json 和 apk asset
	var updateAsset *gitHubAsset
	var apkAsset *gitHubAsset

	for i := range rel.Assets {
		a := &rel.Assets[i]
		lower := strings.ToLower(a.Name)
		if lower == "update.json" {
			updateAsset = a
		} else if strings.HasSuffix(lower, ".apk") {
			if apkAsset == nil || strings.Contains(lower, "latest") {
				apkAsset = a
			}
		}
	}

	if apkAsset == nil {
		fail(c, http.StatusNotFound, "该 GitHub Release 中未找到 .apk 安装包资产文件")
		return
	}

	var updateInfo releaseUpdateJSON
	hasUpdateJSON := false
	if updateAsset != nil {
		updateURL := updateAsset.BrowserDownloadURL
		isAssetAPI := false
		if ghToken != "" && updateAsset.URL != "" {
			updateURL = updateAsset.URL
			isAssetAPI = true
		} else if s.cfg.GitHubProxyURL != "" {
			updateURL = strings.TrimRight(s.cfg.GitHubProxyURL, "/") + "/" + updateURL
		}

		uReq, err := http.NewRequestWithContext(c.Request.Context(), "GET", updateURL, nil)
		if err == nil {
			uReq.Header.Set("User-Agent", "Melos-Backend")
			if isAssetAPI {
				uReq.Header.Set("Accept", "application/octet-stream")
			}
			if ghToken != "" {
				uReq.Header.Set("Authorization", "Bearer "+ghToken)
			}
			if uResp, err := client.Do(uReq); err == nil {
				if uResp.StatusCode == http.StatusOK {
					if err := json.NewDecoder(uResp.Body).Decode(&updateInfo); err == nil {
						hasUpdateJSON = true
					}
				}
				uResp.Body.Close()
			}
		}
	}

	var versionCode int64
	var versionName string
	var changelog string
	var forceUpdate bool
	var expectedHash string

	if hasUpdateJSON {
		versionCode = updateInfo.VersionCode
		versionName = updateInfo.VersionName
		changelog = updateInfo.UpdateLog
		if changelog == "" {
			changelog = rel.Body
		}
		forceUpdate = updateInfo.ForceUpdate
		expectedHash = updateInfo.SHA256
	} else {
		versionName = strings.TrimPrefix(rel.TagName, "v")
		changelog = rel.Body
		codeStr := strings.ReplaceAll(versionName, ".", "")
		if n, err := strconv.ParseInt(codeStr, 10, 64); err == nil {
			versionCode = n
		}
	}

	if versionCode <= 0 {
		fail(c, http.StatusBadRequest, "未能从 Release 或 update.json 中解析出合法的 versionCode")
		return
	}
	if versionName == "" {
		versionName = fmt.Sprintf("v%s", rel.TagName)
	}

	if err := os.MkdirAll(s.cfg.APKDir, 0o755); err != nil {
		s.fail500(c, err)
		return
	}

	name := fmt.Sprintf("%d-%s.apk", versionCode, sanitizeUsernameSegment(versionName))
	full := filepath.Join(s.cfg.APKDir, name)

	// 下载 APK (流式下载并计算 SHA256)
	downloadURL := apkAsset.BrowserDownloadURL
	isAssetAPI := false
	if ghToken != "" && apkAsset.URL != "" {
		downloadURL = apkAsset.URL
		isAssetAPI = true
	} else if s.cfg.GitHubProxyURL != "" {
		downloadURL = strings.TrimRight(s.cfg.GitHubProxyURL, "/") + "/" + downloadURL
	}

	dlClient := &http.Client{Timeout: 10 * time.Minute}
	dlReq, err := http.NewRequestWithContext(context.Background(), "GET", downloadURL, nil)
	if err != nil {
		s.fail500(c, err)
		return
	}
	dlReq.Header.Set("User-Agent", "Melos-Backend")
	if isAssetAPI {
		dlReq.Header.Set("Accept", "application/octet-stream")
	}
	if ghToken != "" {
		dlReq.Header.Set("Authorization", "Bearer "+ghToken)
	}

	dlResp, err := dlClient.Do(dlReq)
	if err != nil {
		fail(c, http.StatusBadGateway, fmt.Sprintf("下载 APK 失败: %v", err))
		return
	}
	defer dlResp.Body.Close()

	if dlResp.StatusCode != http.StatusOK {
		fail(c, dlResp.StatusCode, fmt.Sprintf("下载 APK 响应异常 (HTTP %d)", dlResp.StatusCode))
		return
	}

	tmpFile := full + ".tmp"
	dst, err := os.Create(tmpFile)
	if err != nil {
		s.fail500(c, err)
		return
	}

	hasher := sha256.New()
	size, copyErr := io.Copy(io.MultiWriter(dst, hasher), dlResp.Body)
	closeErr := dst.Close()
	if copyErr != nil || closeErr != nil {
		_ = os.Remove(tmpFile)
		s.fail500(c, fmt.Errorf("写入 APK 文件失败: %v", copyErr))
		return
	}

	computedHash := hex.EncodeToString(hasher.Sum(nil))
	if expectedHash != "" && !strings.EqualFold(expectedHash, computedHash) {
		_ = os.Remove(tmpFile)
		fail(c, http.StatusBadGateway, fmt.Sprintf("APK SHA-256 校验失败 (预期 %s，实际 %s)", expectedHash, computedHash))
		return
	}

	if err := os.Rename(tmpFile, full); err != nil {
		_ = os.Remove(tmpFile)
		s.fail500(c, err)
		return
	}

	// 自动清理服务器历史旧版本 APK，本地磁盘仅保留当前最新版！
	prunedCount := pruneOldAPKs(s.cfg.APKDir, name)

	ctx := c.Request.Context()
	existing, err := s.store.FindVersionByCode(ctx, versionCode)
	if err != nil {
		s.fail500(c, err)
		return
	}

	var row *store.AppVersion
	if existing == nil {
		row, err = s.store.CreateVersion(ctx, store.CreateVersionParams{
			VersionCode: versionCode,
			VersionName: versionName,
			ApkURL:      apkURLPrefix + name,
			FileSize:    size,
			HashSHA256:  computedHash,
			Changelog:   changelog,
			ForceUpdate: forceUpdate,
			Enabled:     true,
		})
	} else {
		enabled := true
		row, err = s.store.UpdateVersion(ctx, existing.ID, store.UpdateVersionParams{
			Changelog:   &changelog,
			ForceUpdate: &forceUpdate,
			Enabled:     &enabled,
		})
	}

	if err != nil {
		s.fail500(c, err)
		return
	}

	ok(c, gin.H{
		"version":        s.enrichVersionPublic(row),
		"prunedOldFiles": prunedCount,
		"message":        fmt.Sprintf("已成功从 GitHub Release 同步最新版本 %s (#%d)，本地旧安装包已清理", versionName, versionCode),
	})
}

// pruneOldAPKs keeps keepFileName plus up to 2 other newest .apk files,
// ensuring disk space is reclaimed while retaining recently published versions.
func pruneOldAPKs(dir, keepFileName string) int {
	entries, err := os.ReadDir(dir)
	if err != nil {
		return 0
	}
	type apkEntry struct {
		name    string
		modTime time.Time
	}
	var others []apkEntry
	for _, entry := range entries {
		if entry.IsDir() {
			continue
		}
		name := entry.Name()
		if strings.HasSuffix(strings.ToLower(name), ".apk") && name != keepFileName {
			info, err := entry.Info()
			if err == nil {
				others = append(others, apkEntry{name: name, modTime: info.ModTime()})
			}
		}
	}
	sort.Slice(others, func(i, j int) bool {
		return others[i].modTime.After(others[j].modTime)
	})
	const maxOthers = 2
	pruned := 0
	for i := maxOthers; i < len(others); i++ {
		if err := os.Remove(filepath.Join(dir, others[i].name)); err == nil {
			pruned++
		}
	}
	return pruned
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
	ok(c, s.enrichVersionPublic(updated))
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
	if row == nil {
		ok(c, nil)
		return
	}
	ok(c, s.enrichVersionPublic(row))
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
		fail(c, http.StatusNotFound, "该版本的安装包文件已从服务器归档移除或不存在，请下载最新版本")
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
