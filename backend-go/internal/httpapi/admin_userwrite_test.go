package httpapi

import (
	"net/http"
	"testing"

	"github.com/gin-gonic/gin"
)

// adminLogin returns a token for the seeded admin account.
func adminLogin(t *testing.T, e *testEnv) string {
	t.Helper()
	rec, body := e.request(http.MethodPost, "/api/auth/login", "", gin.H{"username": "admin", "password": "12346"})
	e.mustOK(rec, "admin login")
	return asStr(asMap(body["data"])["token"])
}

// TestAdminCreateScriptWithoutID locks the fix for
// POST /api/admin/users/:id/scripts 400「脚本 id 不能为空」：
// 管理端「新增脚本」不带 id，服务端必须自己补一个可用的 id。
func TestAdminCreateScriptWithoutID(t *testing.T) {
	e := newEnv(t)
	adminToken := adminLogin(t, e)
	_, userData := e.register("script_user", "secret123", "script@example.com")
	uid := itoa(asFloat(asMap(userData["user"])["id"]))

	rec, body := e.request(http.MethodPost, "/api/admin/users/"+uid+"/scripts", adminToken, gin.H{
		"name": "墨澜聚合音源", "description": "测试用", "enabled": true, "code": "// ==UserScript==\n// @name 墨澜\n// ==/UserScript==\n",
	})
	e.mustOK(rec, "create script without id")
	created := asMap(body["data"])
	scriptID := asStr(created["id"])
	e.want(scriptID != "", "server generated a script id", rec.Body.String())
	e.want(asStr(created["name"]) == "墨澜聚合音源", "name echoed back")

	// 列表里能查到，说明真的入库了（否则管理端刷新后仍看不到脚本）
	rec, body = e.request(http.MethodGet, "/api/admin/users/"+uid+"/scripts", adminToken, nil)
	e.mustOK(rec, "list scripts")
	items := asArr(asMap(body["data"])["items"])
	e.want(len(items) == 1, "one script stored", rec.Body.String())
	e.want(asStr(asMap(items[0])["scriptId"]) == scriptID, "listed under generated id")
	e.want(asBool(asMap(items[0])["enabled"]), "enabled flag stored")

	// 省略 name 时用脚本头的 @name 兜底
	rec, body = e.request(http.MethodPost, "/api/admin/users/"+uid+"/scripts", adminToken, gin.H{"code": "// @name 自动取名\nx"})
	e.mustOK(rec, "create script with default name")
	e.want(asStr(asMap(body["data"])["name"]) == "自动取名", "name derived from @name")

	// 同一 id 走 PUT 更新
	rec, _ = e.request(http.MethodPut, "/api/admin/users/"+uid+"/scripts/"+scriptID, adminToken, gin.H{
		"name": "改名", "code": "// v2", "enabled": false,
	})
	e.mustOK(rec, "update script")
	rec, body = e.request(http.MethodGet, "/api/admin/users/"+uid+"/scripts", adminToken, nil)
	items = asArr(asMap(body["data"])["items"])
	e.want(len(items) == 2, "update did not duplicate rows", rec.Body.String())

	// 删除真实存在的脚本
	rec, _ = e.request(http.MethodDelete, "/api/admin/users/"+uid+"/scripts/"+scriptID, adminToken, nil)
	e.mustOK(rec, "delete script")
	rec, body = e.request(http.MethodGet, "/api/admin/users/"+uid+"/scripts", adminToken, nil)
	e.want(len(asArr(asMap(body["data"])["items"])) == 1, "tombstoned script hidden")
}

// TestAdminScriptValidation covers the id / code guards.
func TestAdminScriptValidation(t *testing.T) {
	e := newEnv(t)
	adminToken := adminLogin(t, e)
	_, userData := e.register("validate_user", "secret123", "validate@example.com")
	uid := itoa(asFloat(asMap(userData["user"])["id"]))

	cases := []struct {
		name    string
		payload gin.H
		want    int
		why     string
	}{
		{"slash id", gin.H{"id": "a/b", "code": "x"}, http.StatusBadRequest, "id with slash would break /scripts/:sid"},
		{"blank code", gin.H{"code": "   \n"}, http.StatusBadRequest, "empty script body"},
		{"missing code", gin.H{"name": "n"}, http.StatusBadRequest, "code is required"},
		{"too long id", gin.H{"id": string(make([]byte, 200)), "code": "x"}, http.StatusBadRequest, "id length cap"},
	}
	for _, c := range cases {
		rec, _ := e.request(http.MethodPost, "/api/admin/users/"+uid+"/scripts", adminToken, c.payload)
		e.want(rec.Code == c.want, c.name, "want", itoa(float64(c.want)), "got", itoa(float64(rec.Code)), rec.Body.String())
		_ = c.why
	}
}

// TestAdminDeleteMissingRows: 删除不存在的行必须回 404，而不是假成功。
func TestAdminDeleteMissingRows(t *testing.T) {
	e := newEnv(t)
	adminToken := adminLogin(t, e)
	_, userData := e.register("delete_user", "secret123", "delete@example.com")
	uid := itoa(asFloat(asMap(userData["user"])["id"]))

	// 先写入一次，确保 user_state 存在，便于断言 sync_seq 不被空操作推进
	rec, _ := e.request(http.MethodPost, "/api/admin/users/"+uid+"/scripts", adminToken, gin.H{"id": "real_script", "code": "// x"})
	e.mustOK(rec, "seed script")

	var seqBefore int64
	if err := e.st.DB().QueryRow("SELECT sync_seq FROM user_state WHERE user_id = " + uid).Scan(&seqBefore); err != nil {
		t.Fatalf("read sync_seq: %v", err)
	}

	targets := []string{
		"/api/admin/users/" + uid + "/scripts/__missing__",
		"/api/admin/users/" + uid + "/playlists/__missing__",
		"/api/admin/users/" + uid + "/favorites/netease/__missing__",
		"/api/admin/users/" + uid + "/history/netease/__missing__",
		"/api/admin/users/" + uid + "/downloads/netease/__missing__/device",
	}
	for _, url := range targets {
		rec, _ := e.request(http.MethodDelete, url, adminToken, nil)
		e.want(rec.Code == http.StatusNotFound, "delete missing -> 404", url, itoa(float64(rec.Code)), rec.Body.String())
	}

	// 空操作不该推进同步游标（否则用户设备会拉到一个空增量）
	var seqAfter int64
	if err := e.st.DB().QueryRow("SELECT sync_seq FROM user_state WHERE user_id = " + uid).Scan(&seqAfter); err != nil {
		t.Fatalf("read sync_seq: %v", err)
	}
	e.want(seqAfter == seqBefore, "failed deletes leave sync_seq untouched", itoa(float64(seqBefore)), itoa(float64(seqAfter)))

	// 真删一次仍然 200
	rec, _ = e.request(http.MethodDelete, "/api/admin/users/"+uid+"/scripts/real_script", adminToken, nil)
	e.mustOK(rec, "delete existing script")
}

// TestAdminPlaylistWrites: 新建歌单不带 id 也要能建；往不存在的歌单加歌必须 404。
func TestAdminPlaylistWrites(t *testing.T) {
	e := newEnv(t)
	adminToken := adminLogin(t, e)
	_, userData := e.register("pl_user", "secret123", "playlist@example.com")
	uid := itoa(asFloat(asMap(userData["user"])["id"]))

	rec, body := e.request(http.MethodPost, "/api/admin/users/"+uid+"/playlists", adminToken, gin.H{"name": "通勤歌单", "kind": "mine"})
	e.mustOK(rec, "create playlist without id")
	playlistID := asStr(asMap(body["data"])["id"])
	e.want(playlistID != "", "server generated a playlist id")

	rec, _ = e.request(http.MethodPost, "/api/admin/users/"+uid+"/playlists/"+playlistID+"/songs", adminToken, gin.H{
		"platform": "netease", "id": "s1", "song": song("s1"), "position": 0,
	})
	e.mustOK(rec, "add song to existing playlist")

	rec, _ = e.request(http.MethodPost, "/api/admin/users/"+uid+"/playlists/__missing__/songs", adminToken, gin.H{
		"platform": "netease", "id": "s1", "song": song("s1"),
	})
	e.want(rec.Code == http.StatusNotFound, "add song to missing playlist -> 404", itoa(float64(rec.Code)), rec.Body.String())

	rec, body = e.request(http.MethodGet, "/api/admin/users/"+uid+"/playlists", adminToken, nil)
	items := asArr(asMap(body["data"])["items"])
	e.want(len(items) == 1 && asFloat(asMap(items[0])["songCount"]) == 1, "playlist has exactly one song", rec.Body.String())

	// 重复添加同一首：upsert，不重复计数
	rec, _ = e.request(http.MethodPost, "/api/admin/users/"+uid+"/playlists/"+playlistID+"/songs", adminToken, gin.H{"platform": "netease", "id": "s1", "song": song("s1"), "position": 5})
	e.mustOK(rec, "re-add same song")
	rec, body = e.request(http.MethodGet, "/api/admin/users/"+uid+"/playlists/"+playlistID+"/songs", adminToken, nil)
	songs := asArr(asMap(body["data"])["items"])
	e.want(len(songs) == 1 && asFloat(asMap(songs[0])["position"]) == 5, "upsert keeps one row with new position", rec.Body.String())
}

// TestAdminUserDataRequiresExistingUser: 逐用户读接口对不存在的用户回 404，
// 与 /overview 保持一致（否则管理端看到空列表会以为用户数据被清空）。
func TestAdminUserDataRequiresExistingUser(t *testing.T) {
	e := newEnv(t)
	adminToken := adminLogin(t, e)

	for _, suffix := range []string{
		"favorites", "playlists", "history", "scripts", "downloads", "play-events", "overview", "active-stats",
	} {
		rec, _ := e.request(http.MethodGet, "/api/admin/users/999999/"+suffix, adminToken, nil)
		e.want(rec.Code == http.StatusNotFound, "missing user -> 404", suffix, itoa(float64(rec.Code)), rec.Body.String())
		rec, _ = e.request(http.MethodGet, "/api/admin/users/not-a-number/"+suffix, adminToken, nil)
		e.want(rec.Code == http.StatusNotFound, "bad :id -> 404", suffix, itoa(float64(rec.Code)), rec.Body.String())
	}
}
