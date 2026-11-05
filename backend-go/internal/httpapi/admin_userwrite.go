package httpapi

import (
	"context"
	"crypto/tls"
	"encoding/base64"
	"encoding/json"
	"fmt"
	"io"
	"net/http"
	"strings"
	"time"

	"github.com/gin-gonic/gin"
)

// registerAdminUserWrite mounts the admin's write endpoints for user data.
//
// 全部走 store 的 adminWriteTx：写入同时推进 user_state.sync_seq，
// 用户设备下次 pull 就能拿到改动（语义等同"管理员这台设备做了一次同步"）。
func (s *Server) registerAdminUserWrite(r *gin.Engine, admin gin.HandlerFunc) {
	// 音源脚本
	r.POST("/api/admin/users/:id/scripts", admin, s.handleAdminSaveScript)
	r.PUT("/api/admin/users/:id/scripts/:sid", admin, s.handleAdminSaveScript)
	r.DELETE("/api/admin/users/:id/scripts/:sid", admin, s.handleAdminDeleteScript)
	// 脚本从链接导入（服务端拉取，绕开浏览器跨域）
	r.POST("/api/admin/users/:id/scripts/import-url", admin, s.handleAdminImportScriptURL)
	// 收藏
	r.POST("/api/admin/users/:id/favorites", admin, s.handleAdminAddFavorite)
	r.DELETE("/api/admin/users/:id/favorites/:platform/:songId", admin, s.handleAdminDeleteFavorite)
	// 歌单
	r.POST("/api/admin/users/:id/playlists", admin, s.handleAdminSavePlaylist)
	r.PUT("/api/admin/users/:id/playlists/:pid", admin, s.handleAdminSavePlaylist)
	r.DELETE("/api/admin/users/:id/playlists/:pid", admin, s.handleAdminDeletePlaylist)
	r.POST("/api/admin/users/:id/playlists/:pid/songs", admin, s.handleAdminAddPlaylistSong)
	r.DELETE("/api/admin/users/:id/playlists/:pid/songs/:platform/:songId", admin, s.handleAdminDeletePlaylistSong)
	// 历史 / 下载
	r.DELETE("/api/admin/users/:id/history", admin, s.handleAdminClearHistory)
	r.DELETE("/api/admin/users/:id/history/:platform/:songId", admin, s.handleAdminDeleteHistory)
	r.DELETE("/api/admin/users/:id/downloads/:platform/:songId/:deviceId", admin, s.handleAdminDeleteDownload)
}

// adminRequireUser checks the target user exists and returns its id.
func (s *Server) adminRequireUser(c *gin.Context) (int64, bool) {
	id, _ := jsParseInt(c.Param("id"))
	user, err := s.store.FindUserByID(c.Request.Context(), int64(id))
	if err != nil {
		s.fail500(c, err)
		return 0, false
	}
	if user == nil {
		fail(c, http.StatusNotFound, "用户不存在")
		return 0, false
	}
	return user.ID, true
}

// ==================== 脚本 ====================

func (s *Server) handleAdminSaveScript(c *gin.Context) {
	userID, okUser := s.adminRequireUser(c)
	if !okUser {
		return
	}
	body, okBody := decodeBody(c)
	if !okBody {
		return
	}
	scriptID := strings.TrimSpace(c.Param("sid"))
	if scriptID == "" {
		scriptID = strings.TrimSpace(asString(body["id"]))
	}
	if scriptID == "" {
		fail(c, http.StatusBadRequest, "脚本 id 不能为空")
		return
	}
	code, isStr := body["code"].(string)
	if !isStr {
		fail(c, http.StatusBadRequest, "缺少脚本源码 code")
		return
	}
	if len(code) > 2*1024*1024 {
		fail(c, http.StatusRequestEntityTooLarge, "脚本过大（上限 2MB）")
		return
	}
	name := strings.TrimSpace(asString(body["name"]))
	if name == "" {
		name = scriptID
	}
	description := asString(body["description"])
	enabled := true
	if v, present := body["enabled"]; present {
		enabled = truthyAny(v)
	}

	if err := s.store.AdminSaveScript(c.Request.Context(), userID, scriptID, name, description, code, enabled, time.Now().UnixMilli()); err != nil {
		s.fail500(c, err)
		return
	}
	okMsg(c, "脚本已保存")
}

func (s *Server) handleAdminDeleteScript(c *gin.Context) {
	userID, okUser := s.adminRequireUser(c)
	if !okUser {
		return
	}
	scriptID := strings.TrimSpace(c.Param("sid"))
	if scriptID == "" {
		fail(c, http.StatusBadRequest, "脚本 id 不能为空")
		return
	}
	if err := s.store.AdminDeleteScript(c.Request.Context(), userID, scriptID, time.Now().UnixMilli()); err != nil {
		s.fail500(c, err)
		return
	}
	okMsg(c, "脚本已删除")
}

// handleAdminImportScriptURL pulls a script from a URL server-side.
//
// 为什么由服务端拉：浏览器直连会撞上目标站点的 CORS，且脚本常见托管在
// GitHub raw / gist，前端 fetch 拿不到正文。服务端拉还能顺手做大小与超时限制。
func (s *Server) handleAdminImportScriptURL(c *gin.Context) {
	userID, okUser := s.adminRequireUser(c)
	if !okUser {
		return
	}
	body, okBody := decodeBody(c)
	if !okBody {
		return
	}
	rawURL := strings.TrimSpace(asString(body["url"]))
	if rawURL == "" {
		fail(c, http.StatusBadRequest, "请填写脚本链接")
		return
	}
	if !strings.HasPrefix(rawURL, "http://") && !strings.HasPrefix(rawURL, "https://") {
		fail(c, http.StatusBadRequest, "链接必须以 http:// 或 https:// 开头")
		return
	}

	const maxBytes = 2 * 1024 * 1024
	// 强制 HTTP/1.1：这台服务器到 raw.githubusercontent.com 的 HTTP/2 连接
	// 会被中途 RST（curl 走 1.1 却能通），Go 默认协商 h2 就会"连接被重置"。
	transport := &http.Transport{
		Proxy:              http.ProxyFromEnvironment,
		ForceAttemptHTTP2:  false,
		TLSNextProto:       map[string]func(string, *tls.Conn) http.RoundTripper{},
		TLSHandshakeTimeout: 15 * time.Second,
	}
	client := &http.Client{Timeout: 20 * time.Second, Transport: transport}

	var lastErr error
	var code string
	var usedSource string
	for _, candidate := range scriptSourceCandidates(rawURL) {
		body, err := fetchScript(c.Request.Context(), client, candidate, maxBytes)
		if err != nil {
			lastErr = err
			continue
		}
		if strings.TrimSpace(body) == "" {
			lastErr = fmt.Errorf("%s 内容为空", candidate)
			continue
		}
		code = body
		usedSource = candidate
		break
	}
	if code == "" {
		msg := "拉取失败"
		if lastErr != nil {
			msg += "：" + lastErr.Error()
		}
		fail(c, http.StatusBadGateway, msg)
		return
	}

	name := strings.TrimSpace(asString(body["name"]))
	if name == "" {
		name = scriptNameFromCode(code)
	}
	if name == "" {
		name = urlBaseName(rawURL)
	}
	scriptID := strings.TrimSpace(asString(body["id"]))
	if scriptID == "" {
		scriptID = "admin_" + fmt.Sprintf("%d", time.Now().UnixNano()/1e6)
	}
	description := asString(body["description"])
	enabled := true
	if v, present := body["enabled"]; present {
		enabled = truthyAny(v)
	}

	// dryRun：只把源码交回管理端预览，不入库。
	// 管理员必须先看一眼内容（避免把 GitHub 的 404 页面当成脚本写进用户设备）。
	if truthyAny(body["dryRun"]) {
		ok(c, gin.H{
			"id":     scriptID,
			"name":   name,
			"code":   code,
			"bytes":  len(code),
			"source": usedSource,
		})
		return
	}

	if err := s.store.AdminSaveScript(c.Request.Context(), userID, scriptID, name, description, code, enabled, time.Now().UnixMilli()); err != nil {
		s.fail500(c, err)
		return
	}
	okMsgData(c, "已从链接导入", gin.H{
		"id":     scriptID,
		"name":   name,
		"bytes":  len(code),
		"source": usedSource,
	})
}

// scriptSourceCandidates returns the URLs to try, in order.
//
// GitHub raw 在国内网络下常被重置/限速，因此同一个脚本准备三条路径：
// 原链接 → jsDelivr CDN 镜像 → GitHub contents API（base64）。
// 只要有一条通，管理员就不必自己找镜像。
func scriptSourceCandidates(raw string) []string {
	out := []string{raw}
	owner, repo, branch, path, ok := parseGitHubRaw(raw)
	if ok {
		out = append(out, fmt.Sprintf("https://cdn.jsdelivr.net/gh/%s/%s@%s/%s", owner, repo, branch, path))
		out = append(out, fmt.Sprintf("https://api.github.com/repos/%s/%s/contents/%s?ref=%s", owner, repo, path, branch))
	}
	return out
}

// parseGitHubRaw splits raw.githubusercontent.com/{owner}/{repo}/{branch}/{path}.
func parseGitHubRaw(raw string) (owner, repo, branch, path string, ok bool) {
	const prefix = "https://raw.githubusercontent.com/"
	if !strings.HasPrefix(raw, prefix) {
		return "", "", "", "", false
	}
	rest := strings.TrimPrefix(raw, prefix)
	if i := strings.IndexAny(rest, "?#"); i >= 0 {
		rest = rest[:i]
	}
	parts := strings.SplitN(rest, "/", 4)
	if len(parts) < 4 {
		return "", "", "", "", false
	}
	return parts[0], parts[1], parts[2], parts[3], true
}

// fetchScript downloads one candidate URL and returns its text body.
// GitHub's contents API returns JSON with base64 content, handled here.
func fetchScript(ctx context.Context, client *http.Client, url string, maxBytes int64) (string, error) {
	req, err := http.NewRequestWithContext(ctx, http.MethodGet, url, nil)
	if err != nil {
		return "", err
	}
	req.Header.Set("User-Agent", "Melos-Admin/1.0")
	req.Header.Set("Accept", "*/*")
	resp, err := client.Do(req)
	if err != nil {
		return "", err
	}
	defer resp.Body.Close()
	if resp.StatusCode != http.StatusOK {
		return "", fmt.Errorf("HTTP %d", resp.StatusCode)
	}
	raw, err := io.ReadAll(io.LimitReader(resp.Body, maxBytes+1))
	if err != nil {
		return "", err
	}
	if int64(len(raw)) > maxBytes {
		return "", fmt.Errorf("内容过大（上限 %d KB）", maxBytes/1024)
	}
	// contents API：{"content":"<base64>","encoding":"base64",...}
	if strings.Contains(url, "api.github.com/repos/") {
		var payload struct {
			Content  string `json:"content"`
			Encoding string `json:"encoding"`
		}
		if err := json.Unmarshal(raw, &payload); err == nil && payload.Content != "" {
			decoded, derr := base64.StdEncoding.DecodeString(strings.ReplaceAll(payload.Content, "\n", ""))
			if derr == nil {
				return string(decoded), nil
			}
		}
	}
	return string(raw), nil
}

// scriptNameFromCode reads the "@name" tag from a userscript-style header.
func scriptNameFromCode(code string) string {
	head := code
	if len(head) > 2000 {
		head = head[:2000]
	}
	for _, line := range strings.Split(head, "\n") {
		l := strings.TrimSpace(strings.TrimPrefix(strings.TrimSpace(line), "*"))
		l = strings.TrimSpace(strings.TrimPrefix(l, "//"))
		if strings.HasPrefix(l, "@name") && !strings.HasPrefix(l, "@name:") {
			v := strings.TrimSpace(strings.TrimPrefix(l, "@name"))
			if v != "" {
				return truncateStr(v, 80)
			}
		}
	}
	return ""
}

func urlBaseName(raw string) string {
	u := raw
	if i := strings.IndexAny(u, "?#"); i >= 0 {
		u = u[:i]
	}
	u = strings.TrimRight(u, "/")
	if i := strings.LastIndex(u, "/"); i >= 0 {
		u = u[i+1:]
	}
	if u == "" {
		return "导入的脚本"
	}
	return truncateStr(u, 80)
}

// ==================== 收藏 ====================

func (s *Server) handleAdminAddFavorite(c *gin.Context) {
	userID, okUser := s.adminRequireUser(c)
	if !okUser {
		return
	}
	body, okBody := decodeBody(c)
	if !okBody {
		return
	}
	platform := strings.TrimSpace(asString(body["platform"]))
	songID := strings.TrimSpace(asString(body["id"]))
	if platform == "" || songID == "" {
		fail(c, http.StatusBadRequest, "platform 与歌曲 id 不能为空")
		return
	}
	songJSON := "{}"
	if song, ok := body["song"]; ok && song != nil {
		if b, err := json.Marshal(song); err == nil {
			songJSON = string(b)
		}
	} else {
		// 没给完整歌曲对象时，至少构造一个最小可用结构（客户端能显示歌名）
		if b, err := json.Marshal(map[string]any{
			"id":       songID,
			"platform": platform,
			"title":    asString(body["title"]),
			"artist":   asString(body["artist"]),
		}); err == nil {
			songJSON = string(b)
		}
	}
	if err := s.store.AdminAddFavorite(c.Request.Context(), userID, platform, songID, songJSON, time.Now().UnixMilli()); err != nil {
		s.fail500(c, err)
		return
	}
	okMsg(c, "已加入收藏")
}

func (s *Server) handleAdminDeleteFavorite(c *gin.Context) {
	userID, okUser := s.adminRequireUser(c)
	if !okUser {
		return
	}
	if err := s.store.AdminDeleteFavorite(c.Request.Context(), userID,
		strings.TrimSpace(c.Param("platform")), strings.TrimSpace(c.Param("songId")), time.Now().UnixMilli()); err != nil {
		s.fail500(c, err)
		return
	}
	okMsg(c, "已取消收藏")
}

// ==================== 歌单 ====================

func (s *Server) handleAdminSavePlaylist(c *gin.Context) {
	userID, okUser := s.adminRequireUser(c)
	if !okUser {
		return
	}
	body, okBody := decodeBody(c)
	if !okBody {
		return
	}
	playlistID := strings.TrimSpace(c.Param("pid"))
	if playlistID == "" {
		playlistID = strings.TrimSpace(asString(body["id"]))
	}
	if playlistID == "" {
		playlistID = fmt.Sprintf("local_admin_%d", time.Now().UnixNano()/1e6)
	}
	name := strings.TrimSpace(asString(body["name"]))
	if name == "" {
		fail(c, http.StatusBadRequest, "歌单名不能为空")
		return
	}
	kind := asString(body["kind"])
	cover := asString(body["coverUrl"])

	if err := s.store.AdminUpsertPlaylist(c.Request.Context(), userID, playlistID, kind, name, cover, time.Now().UnixMilli()); err != nil {
		s.fail500(c, err)
		return
	}
	okMsgData(c, "歌单已保存", gin.H{"id": playlistID})
}

func (s *Server) handleAdminDeletePlaylist(c *gin.Context) {
	userID, okUser := s.adminRequireUser(c)
	if !okUser {
		return
	}
	if err := s.store.AdminDeletePlaylist(c.Request.Context(), userID,
		strings.TrimSpace(c.Param("pid")), time.Now().UnixMilli()); err != nil {
		s.fail500(c, err)
		return
	}
	okMsg(c, "歌单已删除")
}

func (s *Server) handleAdminAddPlaylistSong(c *gin.Context) {
	userID, okUser := s.adminRequireUser(c)
	if !okUser {
		return
	}
	body, okBody := decodeBody(c)
	if !okBody {
		return
	}
	playlistID := strings.TrimSpace(c.Param("pid"))
	platform := strings.TrimSpace(asString(body["platform"]))
	songID := strings.TrimSpace(asString(body["id"]))
	if playlistID == "" || platform == "" || songID == "" {
		fail(c, http.StatusBadRequest, "歌单 id / platform / 歌曲 id 都不能为空")
		return
	}
	songJSON := "{}"
	if song, ok := body["song"]; ok && song != nil {
		if b, err := json.Marshal(song); err == nil {
			songJSON = string(b)
		}
	}
	position := int64(0)
	if v, ok := body["position"]; ok {
		position = numAsI64(v)
	}
	if err := s.store.AdminUpsertPlaylistSong(c.Request.Context(), userID, playlistID, platform, songID, songJSON, position, time.Now().UnixMilli()); err != nil {
		s.fail500(c, err)
		return
	}
	okMsg(c, "已加入歌单")
}

func (s *Server) handleAdminDeletePlaylistSong(c *gin.Context) {
	userID, okUser := s.adminRequireUser(c)
	if !okUser {
		return
	}
	if err := s.store.AdminDeletePlaylistSong(c.Request.Context(), userID,
		strings.TrimSpace(c.Param("pid")), strings.TrimSpace(c.Param("platform")),
		strings.TrimSpace(c.Param("songId")), time.Now().UnixMilli()); err != nil {
		s.fail500(c, err)
		return
	}
	okMsg(c, "已从歌单移除")
}

// ==================== 历史 / 下载 ====================

func (s *Server) handleAdminDeleteHistory(c *gin.Context) {
	userID, okUser := s.adminRequireUser(c)
	if !okUser {
		return
	}
	if err := s.store.AdminDeleteHistory(c.Request.Context(), userID,
		strings.TrimSpace(c.Param("platform")), strings.TrimSpace(c.Param("songId")), time.Now().UnixMilli()); err != nil {
		s.fail500(c, err)
		return
	}
	okMsg(c, "已删除该条历史")
}

func (s *Server) handleAdminClearHistory(c *gin.Context) {
	userID, okUser := s.adminRequireUser(c)
	if !okUser {
		return
	}
	n, err := s.store.AdminClearHistory(c.Request.Context(), userID, time.Now().UnixMilli())
	if err != nil {
		s.fail500(c, err)
		return
	}
	okMsgData(c, "已清空听歌历史", gin.H{"deleted": n})
}

func (s *Server) handleAdminDeleteDownload(c *gin.Context) {
	userID, okUser := s.adminRequireUser(c)
	if !okUser {
		return
	}
	if err := s.store.AdminDeleteDownload(c.Request.Context(), userID,
		strings.TrimSpace(c.Param("platform")), strings.TrimSpace(c.Param("songId")),
		strings.TrimSpace(c.Param("deviceId")), time.Now().UnixMilli()); err != nil {
		s.fail500(c, err)
		return
	}
	okMsg(c, "已删除下载记录")
}

// truthyAny mirrors JS truthiness for the few shapes we accept from JSON.
func truthyAny(v any) bool {
	switch t := v.(type) {
	case nil:
		return false
	case bool:
		return t
	case float64:
		return t != 0
	case string:
		return t != "" && t != "0" && t != "false"
	default:
		return true
	}
}
