package httpapi

import (
	"bytes"
	"context"
	"encoding/json"
	"io"
	"net/http"
	"net/http/httptest"
	"path/filepath"
	"strings"
	"testing"
	"time"

	"github.com/gin-gonic/gin"

	"melos-backend/internal/config"
	"melos-backend/internal/store"
)

// Ported from backend/scripts/smoke.mjs. Registration in the current Node
// backend requires an email + verification code, so the tests seed codes
// directly through the store (the mail transport is not exercised here).

type testEnv struct {
	t      *testing.T
	router *gin.Engine
	srv    *Server
	st     *store.Store
}

func newEnv(t *testing.T) *testEnv { return newEnvWith(t, nil) }

func newEnvWith(t *testing.T, mutate func(*config.Config)) *testEnv {
	t.Helper()
	gin.SetMode(gin.TestMode)
	dbPath := filepath.Join(t.TempDir(), "music.db")
	st, err := store.Open("file:" + filepath.ToSlash(dbPath))
	if err != nil {
		t.Fatalf("open store: %v", err)
	}
	t.Cleanup(func() { _ = st.Close() })
	if _, err := st.EnsureAdminUser(context.Background(), "admin", "12346"); err != nil {
		t.Fatalf("seed admin: %v", err)
	}
	cfg := &config.Config{
		Port: 8787, Host: "127.0.0.1", TrustProxy: false, RateLimitDisabled: true,
		SessionTTLDays: 90, MaxSessionsPerUser: 10,
		MailServiceURL: "https://mail.invalid", MailAdminAuth: "x",
		MailFromAddress: "melos@dovislab.com", MailFromName: "Melos 音乐",
		APKDir: filepath.Join(t.TempDir(), "apk"), APKMaxMB: 200,
	}
	if mutate != nil {
		mutate(cfg)
	}
	srv := NewServer(cfg, st)
	return &testEnv{t: t, router: srv.Router(), srv: srv, st: st}
}

func (e *testEnv) request(method, url, token string, payload any) (*httptest.ResponseRecorder, map[string]any) {
	e.t.Helper()
	var body io.Reader
	if payload != nil {
		b, err := json.Marshal(payload)
		if err != nil {
			e.t.Fatalf("marshal payload: %v", err)
		}
		body = bytes.NewReader(b)
	}
	req := httptest.NewRequest(method, url, body)
	req.Header.Set("Content-Type", "application/json")
	if token != "" {
		req.Header.Set("Authorization", "Bearer "+token)
	}
	rec := httptest.NewRecorder()
	e.router.ServeHTTP(rec, req)
	out := map[string]any{}
	if rec.Body.Len() > 0 {
		_ = json.Unmarshal(rec.Body.Bytes(), &out)
	}
	return rec, out
}

func (e *testEnv) seedCode(email, code, scene string) {
	e.t.Helper()
	now := time.Now().UnixMilli()
	email = strings.ToLower(strings.TrimSpace(email))
	if _, err := e.st.SaveEmailCode(context.Background(), email, code, scene, now+300000, now); err != nil {
		e.t.Fatalf("seed code: %v", err)
	}
}

func (e *testEnv) register(username, password, email string) (string, map[string]any) {
	e.t.Helper()
	e.seedCode(email, "123456", "register")
	rec, body := e.request(http.MethodPost, "/api/auth/register", "", gin.H{
		"username": username, "password": password, "email": email, "code": "123456", "deviceName": "test-device",
	})
	if rec.Code != 200 {
		e.t.Fatalf("register %s failed: %d %s", username, rec.Code, rec.Body.String())
	}
	return asStr(asMap(body["data"])["token"]), asMap(body["data"])
}

func (e *testEnv) mustOK(rec *httptest.ResponseRecorder, name string) {
	e.t.Helper()
	if rec.Code != 200 {
		e.t.Fatalf("%s: expected 200, got %d %s", name, rec.Code, rec.Body.String())
	}
}

func (e *testEnv) want(cond bool, name string, extra ...string) {
	e.t.Helper()
	if !cond {
		e.t.Fatalf("assertion failed: %s %v", name, extra)
	}
}

func asMap(v any) map[string]any { m, _ := v.(map[string]any); return m }
func asArr(v any) []any          { a, _ := v.([]any); return a }
func asStr(v any) string         { s, _ := v.(string); return s }
func asFloat(v any) float64      { f, _ := v.(float64); return f }
func asBool(v any) bool          { b, _ := v.(bool); return b }

func song(id string) gin.H {
	return gin.H{"id": id, "title": "歌曲" + id, "artist": "歌手", "album": "专辑", "artwork": "", "platform": "netease"}
}

func TestRootAndHealth(t *testing.T) {
	e := newEnv(t)

	rec, body := e.request(http.MethodGet, "/", "", nil)
	e.mustOK(rec, "GET /")
	e.want(asStr(body["name"]) == "melos-backend", "GET / name")
	e.want(asStr(body["version"]) == "2.0.0", "GET / version")
	e.want(len(asArr(body["endpoints"])) > 0, "GET / endpoints")

	rec, body = e.request(http.MethodGet, "/api/health", "", nil)
	e.mustOK(rec, "GET /api/health (no auth)")
	e.want(asFloat(body["code"]) == 200 && asStr(body["msg"]) == "ok", "health envelope")
	data := asMap(body["data"])
	e.want(asBool(data["ok"]) && asStr(data["version"]) == "2.0.0", "health data")
}

func TestRegisterLoginMe(t *testing.T) {
	e := newEnv(t)
	ctx := context.Background()

	// Missing email -> 400
	rec, _ := e.request(http.MethodPost, "/api/auth/register", "", gin.H{"username": "alice", "password": "secret123"})
	e.want(rec.Code == 400, "register without email returns 400", rec.Body.String())

	// Bad email -> 400
	e.seedCode("not-an-email", "123456", "register")
	rec, _ = e.request(http.MethodPost, "/api/auth/register", "", gin.H{"username": "alice", "password": "secret123", "email": "not-an-email", "code": "123456"})
	e.want(rec.Code == 400, "register invalid email returns 400")

	// Wrong code -> 400
	e.seedCode("alice@example.com", "111111", "register")
	rec, _ = e.request(http.MethodPost, "/api/auth/register", "", gin.H{"username": "alice", "password": "secret123", "email": "alice@example.com", "code": "999999"})
	e.want(rec.Code == 400, "register wrong code returns 400")

	tokenA, dataA := e.register("alice", "secret123", "Alice@Example.COM")
	userA := asMap(dataA["user"])
	e.want(asStr(userA["email"]) == "alice@example.com", "register normalises email")
	aliceID := int(asFloat(userA["id"]))

	// Duplicate username -> 409 (needs a fresh valid code/email)
	e.seedCode("dup@example.com", "123456", "register")
	rec, _ = e.request(http.MethodPost, "/api/auth/register", "", gin.H{"username": "alice", "password": "secret123", "email": "dup@example.com", "code": "123456"})
	e.want(rec.Code == 409, "duplicate username 409")

	// Email already bound -> 409
	e.seedCode("alice@example.com", "222222", "register")
	rec, _ = e.request(http.MethodPost, "/api/auth/register", "", gin.H{"username": "mallory", "password": "secret123", "email": "alice@example.com", "code": "222222"})
	e.want(rec.Code == 409, "email already bound 409")

	// Wrong password login -> 401
	rec, _ = e.request(http.MethodPost, "/api/auth/login", "", gin.H{"username": "alice", "password": "wrongpass"})
	e.want(rec.Code == 401, "wrong password 401")

	// Login by email works
	rec, loginBody := e.request(http.MethodPost, "/api/auth/login", "", gin.H{"username": "alice@example.com", "password": "secret123"})
	e.mustOK(rec, "login by email")
	tokenB := asStr(asMap(loginBody["data"])["token"])
	e.want(tokenB != "", "login returns token")

	// GET /me
	rec, meBody := e.request(http.MethodGet, "/api/me", tokenA, nil)
	e.mustOK(rec, "GET /me")
	me := asMap(meBody["data"])
	e.want(asStr(me["username"]) == "alice" && asStr(me["email"]) == "alice@example.com", "me fields")
	e.want(asFloat(me["createdAt"]) == asFloat(me["created_at"]) && asFloat(me["createdAt"]) > 0, "createdAt alias")

	// Alice has two sessions
	rec, sessBody := e.request(http.MethodGet, "/api/me/sessions", tokenA, nil)
	e.mustOK(rec, "list sessions")
	sessions := asArr(sessBody["data"])
	e.want(len(sessions) == 2, "two sessions", string(rec.Body.Bytes()))

	// Update nickname only; email unchanged
	rec, updBody := e.request(http.MethodPut, "/api/me", tokenA, gin.H{"nickname": "爱丽丝"})
	e.mustOK(rec, "PUT /me nickname")
	e.want(asStr(asMap(updBody["data"])["email"]) == "alice@example.com", "email unchanged")

	// Invalid email -> 400, long email -> 400
	rec, _ = e.request(http.MethodPut, "/api/me", tokenA, gin.H{"email": "not-an-email"})
	e.want(rec.Code == 400, "invalid email 400")
	rec, _ = e.request(http.MethodPut, "/api/me", tokenA, gin.H{"email": strings.Repeat("a", 118) + "@x.io"})
	e.want(rec.Code == 400, "over-long email 400")

	// Stealing another user's email -> 409
	tokenC, _ := e.register("carol", "secret999", "carol@example.com")
	rec, _ = e.request(http.MethodPut, "/api/me", tokenC, gin.H{"email": "alice@example.com"})
	e.want(rec.Code == 409, "email conflict 409")

	// Unbind with empty string, then another user may bind it
	rec, unbindBody := e.request(http.MethodPut, "/api/me", tokenA, gin.H{"email": ""})
	e.mustOK(rec, "unbind email")
	e.want(asStr(asMap(unbindBody["data"])["email"]) == "", "email unbound")

	rec, bindBody := e.request(http.MethodPut, "/api/me", tokenC, gin.H{"email": "Alice@Example.com"})
	e.mustOK(rec, "rebind email")
	e.want(asStr(asMap(bindBody["data"])["email"]) == "alice@example.com", "rebind normalised")

	// Idempotent re-submit of own email
	rec, _ = e.request(http.MethodPut, "/api/me", tokenC, gin.H{"email": "alice@example.com"})
	e.mustOK(rec, "idempotent self email")

	_ = ctx
	_ = aliceID
}

func TestEmailCodeLoginFlow(t *testing.T) {
	e := newEnv(t)

	rec, _ := e.request(http.MethodPost, "/api/auth/send-code", "", gin.H{})
	e.want(rec.Code == 400, "send-code without email 400")
	rec, _ = e.request(http.MethodPost, "/api/auth/send-code", "", gin.H{"email": "not-an-email"})
	e.want(rec.Code == 400, "send-code invalid email 400")

	e.seedCode("eva@example.com", "123456", "login")
	rec, _ = e.request(http.MethodPost, "/api/auth/email-login", "", gin.H{"email": "eva@example.com", "code": "000000"})
	e.want(rec.Code == 400, "wrong code 400")

	rec, body := e.request(http.MethodPost, "/api/auth/email-login", "", gin.H{"email": "eva@example.com", "code": "123456", "deviceName": "phone-eva"})
	e.mustOK(rec, "email login auto-register")
	token := asStr(asMap(body["data"])["token"])
	evaUser := asMap(asMap(body["data"])["user"])
	e.want(asStr(evaUser["email"]) == "eva@example.com", "auto-register email")
	evaID := int(asFloat(evaUser["id"]))

	// Replay -> 400
	rec, _ = e.request(http.MethodPost, "/api/auth/email-login", "", gin.H{"email": "eva@example.com", "code": "123456"})
	e.want(rec.Code == 400, "consumed code replay 400")

	// Second login keeps the same user id
	e.seedCode("eva@example.com", "654321", "login")
	rec, body = e.request(http.MethodPost, "/api/auth/email-login", "", gin.H{"email": "eva@example.com", "code": "654321"})
	e.mustOK(rec, "returning email login")
	e.want(int(asFloat(asMap(asMap(body["data"])["user"])["id"])) == evaID, "same user id")

	rec, meBody := e.request(http.MethodGet, "/api/me", token, nil)
	e.mustOK(rec, "me after email login")
	e.want(asStr(asMap(meBody["data"])["email"]) == "eva@example.com", "email login me")
}

func TestSyncCore(t *testing.T) {
	e := newEnv(t)
	token, _ := e.register("alice", "secret123", "alice@example.com")

	// push two favourites -> version 1
	rec, body := e.request(http.MethodPost, "/api/sync/push", token, gin.H{"favorites": gin.H{"upsert": []any{song("1"), song("2")}}})
	e.mustOK(rec, "push favourites")
	e.want(asFloat(asMap(body["data"])["version"]) == 1, "version 1")

	// full pull
	rec, body = e.request(http.MethodGet, "/api/sync/pull?since=0&eventSince=0", token, nil)
	e.mustOK(rec, "pull full")
	full := asMap(body["data"])
	e.want(len(asArr(full["favorites"])) == 2, "two favourites")

	// delete + play event -> version 2
	rec, body = e.request(http.MethodPost, "/api/sync/push", token, gin.H{
		"favorites":  gin.H{"delete": []any{gin.H{"platform": "netease", "id": "2"}}},
		"playEvents": []any{gin.H{"eventId": "evt-1", "platform": "netease", "id": "2", "title": "歌", "artist": "测试歌手", "listenedMs": 120000, "durationMs": 269000, "listenedAtMs": time.Now().UnixMilli()}},
	})
	e.mustOK(rec, "push delete+event")
	data := asMap(body["data"])
	e.want(asFloat(data["version"]) == 2 && asFloat(data["acceptedEvents"]) == 1, "version 2 + accepted event")

	// incremental pull shows tombstone with song=null
	rec, body = e.request(http.MethodGet, "/api/sync/pull?since=1&eventSince=0", token, nil)
	inc := asMap(body["data"])
	favs := asArr(inc["favorites"])
	e.want(len(favs) == 1, "one incremental favourite")
	fav0 := asMap(favs[0])
	e.want(asStr(fav0["id"]) == "2" && asBool(fav0["deleted"]) && fav0["song"] == nil, "tombstone has song null")
	e.want(len(asArr(inc["playEvents"])) == 1, "playEvents returned")

	// event idempotency
	rec, body = e.request(http.MethodPost, "/api/sync/push", token, gin.H{"playEvents": []any{gin.H{"eventId": "evt-1", "platform": "netease", "id": "2", "listenedMs": 5000}}})
	e.mustOK(rec, "dedupe push")
	e.want(asFloat(asMap(body["data"])["acceptedEvents"]) == 0, "duplicate event rejected")

	// LWW: resurrect then delete -> delete wins
	_, _ = e.request(http.MethodPost, "/api/sync/push", token, gin.H{"favorites": gin.H{"upsert": []any{song("2")}}})
	rec, body = e.request(http.MethodPost, "/api/sync/push", token, gin.H{"favorites": gin.H{"delete": []any{gin.H{"platform": "netease", "id": "2"}}}})
	version := asFloat(asMap(body["data"])["version"])
	rec, body = e.request(http.MethodGet, "/api/sync/pull?since="+itoa(version-1), token, nil)
	found := false
	for _, f := range asArr(asMap(body["data"])["favorites"]) {
		m := asMap(f)
		if asStr(m["id"]) == "2" && asBool(m["deleted"]) {
			found = true
		}
	}
	e.want(found, "later delete wins")

	// Playlist cascade tombstone + kind normalisation
	rec, body = e.request(http.MethodPost, "/api/sync/push", token, gin.H{
		"playlists":     gin.H{"upsert": []any{gin.H{"id": "pl_1", "name": "我的歌单"}}},
		"playlistSongs": gin.H{"upsert": []any{gin.H{"playlistId": "pl_1", "position": 0, "song": song("9")}}},
	})
	e.mustOK(rec, "push playlist+song")
	version = asFloat(asMap(body["data"])["version"])
	rec, body = e.request(http.MethodPost, "/api/sync/push", token, gin.H{"playlists": gin.H{"delete": []any{gin.H{"id": "pl_1"}}}})
	version = asFloat(asMap(body["data"])["version"])
	rec, body = e.request(http.MethodGet, "/api/sync/pull?since="+itoa(version-1), token, nil)
	pl := asMap(body["data"])
	for _, p := range asArr(pl["playlists"]) {
		m := asMap(p)
		if asStr(m["id"]) == "pl_1" {
			e.want(asBool(m["deleted"]), "playlist tombstone")
		}
	}
	allSongsDead := true
	for _, s := range asArr(pl["playlistSongs"]) {
		if !asBool(asMap(s)["deleted"]) {
			allSongsDead = false
		}
	}
	e.want(allSongsDead && len(asArr(pl["playlistSongs"])) > 0, "playlist songs tombstoned")

	// New device full pull hides tombstones
	rec, body = e.request(http.MethodGet, "/api/sync/pull?since=0&eventSince=0", token, nil)
	for _, p := range asArr(asMap(body["data"])["playlists"]) {
		e.want(!asBool(asMap(p)["deleted"]), "full pull excludes tombstones")
	}

	// kind: mine / fav / default / dirty
	rec, body = e.request(http.MethodPost, "/api/sync/push", token, gin.H{"playlists": gin.H{"upsert": []any{
		gin.H{"id": "local_mine_1", "name": "通勤", "kind": "mine"},
		gin.H{"id": "777", "name": "收藏的歌单", "kind": "fav"},
		gin.H{"id": "old_client", "name": "老客户端没传 kind"},
		gin.H{"id": "weird", "name": "拼错的 kind", "kind": "FAVOURITE"},
	}}})
	e.mustOK(rec, "push kinds")
	rec, body = e.request(http.MethodGet, "/api/sync/pull?since=0&eventSince=0", token, nil)
	byID := map[string]string{}
	for _, p := range asArr(asMap(body["data"])["playlists"]) {
		m := asMap(p)
		byID[asStr(m["id"])] = asStr(m["kind"])
	}
	e.want(byID["local_mine_1"] == "mine", "kind mine")
	e.want(byID["777"] == "fav", "kind fav")
	e.want(byID["old_client"] == "mine", "kind default mine")
	e.want(byID["weird"] == "mine", "kind dirty normalised")

	// delete fav playlist keeps kind
	rec, body = e.request(http.MethodPost, "/api/sync/push", token, gin.H{"playlists": gin.H{"delete": []any{gin.H{"id": "777", "kind": "fav"}}}})
	version = asFloat(asMap(body["data"])["version"])
	rec, body = e.request(http.MethodGet, "/api/sync/pull?since="+itoa(version-1), token, nil)
	for _, p := range asArr(asMap(body["data"])["playlists"]) {
		m := asMap(p)
		if asStr(m["id"]) == "777" {
			e.want(asBool(m["deleted"]) && asStr(m["kind"]) == "fav", "fav tombstone keeps kind")
		}
	}
}

func TestScriptsAndDownloads(t *testing.T) {
	e := newEnv(t)
	token, _ := e.register("alice", "secret123", "alice@example.com")

	// scripts: long code round-trip + enabled semantics
	longCode := strings.Repeat("// 音源脚本骨架\n", 2500) + "module.exports = { search: async (kw) => [] };"
	rec, _ := e.request(http.MethodPost, "/api/sync/push", token, gin.H{"scripts": gin.H{"upsert": []any{
		gin.H{"id": "sc_src", "name": "我的音源", "description": "测试脚本", "code": longCode, "enabled": true},
		gin.H{"id": "sc_off", "name": "停用脚本", "code": "x", "enabled": false},
		gin.H{"id": "sc_default", "name": "缺省启用", "code": "y"},
	}}})
	e.mustOK(rec, "push scripts")

	rec, body := e.request(http.MethodGet, "/api/sync/pull?since=0&eventSince=0", token, nil)
	scripts := map[string]map[string]any{}
	for _, s := range asArr(asMap(body["data"])["scripts"]) {
		m := asMap(s)
		scripts[asStr(m["id"])] = m
	}
	e.want(asStr(scripts["sc_src"]["code"]) == longCode, "script code round-trip")
	e.want(asBool(scripts["sc_src"]["enabled"]), "enabled true")
	e.want(!asBool(scripts["sc_off"]["enabled"]), "enabled false")
	e.want(asBool(scripts["sc_default"]["enabled"]), "enabled default")

	// oversize -> 413 naming the script
	rec, body = e.request(http.MethodPost, "/api/sync/push", token, gin.H{"scripts": gin.H{"upsert": []any{
		gin.H{"id": "sc_huge", "name": "巨型脚本", "code": strings.Repeat("x", 1024*1024+1)},
	}}})
	e.want(rec.Code == 413, "oversized script 413", rec.Body.String())
	e.want(strings.Contains(asStr(body["msg"]), "音源脚本过大") && strings.Contains(asStr(body["msg"]), "sc_huge"), "413 names script")

	// exactly 1MB is accepted
	rec, _ = e.request(http.MethodPost, "/api/sync/push", token, gin.H{"scripts": gin.H{"upsert": []any{
		gin.H{"id": "sc_edge", "name": "边界脚本", "code": strings.Repeat("x", 1024*1024)},
	}}})
	e.want(rec.Code == 200, "exactly 1MB accepted", rec.Body.String())

	// same id overwrite falls in place, cursor only returns once
	rec, body = e.request(http.MethodPost, "/api/sync/push", token, gin.H{"scripts": gin.H{"upsert": []any{
		gin.H{"id": "sc_src", "name": "改名后", "description": "", "code": "const q = 1;", "enabled": false},
	}}})
	v2 := asFloat(asMap(body["data"])["version"])
	rec, body = e.request(http.MethodGet, "/api/sync/pull?since="+itoa(v2-1), token, nil)
	delta := asArr(asMap(body["data"])["scripts"])
	e.want(len(delta) == 1 && asStr(asMap(delta[0])["id"]) == "sc_src" && asStr(asMap(delta[0])["code"]) == "const q = 1;", "script overwrite")

	// tombstone: incremental visible, full pull hidden, no payload
	rec, body = e.request(http.MethodPost, "/api/sync/push", token, gin.H{"scripts": gin.H{"delete": []any{gin.H{"id": "sc_src"}}}})
	v3 := asFloat(asMap(body["data"])["version"])
	rec, body = e.request(http.MethodGet, "/api/sync/pull?since="+itoa(v3-1), token, nil)
	for _, s := range asArr(asMap(body["data"])["scripts"]) {
		m := asMap(s)
		if asStr(m["id"]) == "sc_src" {
			e.want(asBool(m["deleted"]) && asStr(m["code"]) == "" && asStr(m["name"]) == "" && !asBool(m["enabled"]), "script tombstone payload masked")
		}
	}
	rec, body = e.request(http.MethodGet, "/api/sync/pull?since=0&eventSince=0", token, nil)
	for _, s := range asArr(asMap(body["data"])["scripts"]) {
		e.want(asStr(asMap(s)["id"]) != "sc_src", "full pull excludes deleted script")
	}

	// downloads: device dimension
	dl := func(deviceID, deviceName string, extra gin.H) gin.H {
		m := gin.H{"platform": "netease", "id": "d_song", "deviceId": deviceID, "deviceName": deviceName,
			"title": "下载的歌", "artist": "测试歌手", "album": "测试专辑", "artwork": "https://art/1.jpg",
			"quality": "320k", "sizeBytes": 8123456, "downloadedAtMs": 1700000000000}
		for k, v := range extra {
			m[k] = v
		}
		return m
	}
	rec, body = e.request(http.MethodPost, "/api/sync/push", token, gin.H{"downloads": gin.H{"upsert": []any{
		dl("dev-phone", "Pixel 8", nil), dl("dev-tablet", "小米平板", nil),
	}}})
	e.mustOK(rec, "push downloads")
	dlV1 := asFloat(asMap(body["data"])["version"])
	rec, body = e.request(http.MethodGet, "/api/sync/pull?since=0&eventSince=0", token, nil)
	rows := []map[string]any{}
	for _, d := range asArr(asMap(body["data"])["downloads"]) {
		rows = append(rows, asMap(d))
	}
	e.want(len(rows) == 2, "two device rows")
	for _, r := range rows {
		if asStr(r["deviceId"]) == "dev-phone" {
			e.want(asStr(r["title"]) == "下载的歌" && asFloat(r["sizeBytes"]) == 8123456 && asFloat(r["downloadedAtMs"]) == 1700000000000 && !asBool(r["deleted"]), "download fields")
		}
	}

	rec, body = e.request(http.MethodPost, "/api/sync/push", token, gin.H{"downloads": gin.H{"upsert": []any{dl("dev-phone", "Pixel 8", gin.H{"quality": "flac"})}}})
	dlV2 := asFloat(asMap(body["data"])["version"])
	e.want(dlV2 == dlV1+1, "device update bumps version")
	rec, body = e.request(http.MethodGet, "/api/sync/pull?since="+itoa(dlV1), token, nil)
	dlt := asArr(asMap(body["data"])["downloads"])
	e.want(len(dlt) == 1 && asStr(asMap(dlt[0])["deviceId"]) == "dev-phone" && asStr(asMap(dlt[0])["quality"]) == "flac", "only changed device in delta")

	rec, body = e.request(http.MethodPost, "/api/sync/push", token, gin.H{"downloads": gin.H{"delete": []any{gin.H{"platform": "netease", "id": "d_song", "deviceId": "dev-phone"}}}})
	dlV3 := asFloat(asMap(body["data"])["version"])
	rec, body = e.request(http.MethodGet, "/api/sync/pull?since=0&eventSince=0", token, nil)
	alive := []string{}
	for _, d := range asArr(asMap(body["data"])["downloads"]) {
		m := asMap(d)
		if asStr(m["id"]) == "d_song" && !asBool(m["deleted"]) {
			alive = append(alive, asStr(m["deviceId"]))
		}
	}
	e.want(len(alive) == 1 && alive[0] == "dev-tablet", "delete only affects its device")
	rec, body = e.request(http.MethodGet, "/api/sync/pull?since="+itoa(dlV3-1), token, nil)
	for _, d := range asArr(asMap(body["data"])["downloads"]) {
		m := asMap(d)
		if asStr(m["deviceId"]) == "dev-phone" {
			e.want(asBool(m["deleted"]) && asStr(m["platform"]) == "netease" && asStr(m["id"]) == "d_song", "download tombstone key complete")
		}
	}
}

func TestDirtyDataAndNormalisation(t *testing.T) {
	e := newEnv(t)
	token, _ := e.register("alice", "secret123", "alice@example.com")

	_, baseBody := e.request(http.MethodPost, "/api/sync/push", token, gin.H{})
	before := asFloat(asMap(baseBody["data"])["version"])
	rec, body := e.request(http.MethodPost, "/api/sync/push", token, gin.H{
		"scripts": gin.H{
			"upsert": []any{gin.H{"name": "没有 id 的脚本", "code": "bad"}, nil, gin.H{"id": "sc_good", "name": "合法脚本", "code": "good"}},
			"delete": []any{gin.H{}},
		},
		"downloads": gin.H{
			"upsert": []any{
				gin.H{"platform": "netease", "id": "no_device", "title": "缺 deviceId"},
				gin.H{"platform": "netease", "deviceId": "dev-x", "title": "缺歌曲 id"},
				gin.H{"platform": "netease", "id": "d_song", "deviceId": "dev-phone2", "deviceName": "第二台手机"},
			},
			"delete": []any{gin.H{"platform": "netease", "id": "d_song"}},
		},
	})
	e.mustOK(rec, "dirty push returns 200")
	_ = before

	rec, body = e.request(http.MethodGet, "/api/sync/pull?since="+itoa(before)+"&eventSince=0", token, nil)
	data := asMap(body["data"])
	ds := asArr(data["scripts"])
	e.want(len(ds) == 1 && asStr(asMap(ds[0])["id"]) == "sc_good", "invalid scripts skipped")
	dd := asArr(data["downloads"])
	e.want(len(dd) == 1 && asStr(asMap(dd[0])["deviceId"]) == "dev-phone2", "invalid downloads skipped")
	for _, s := range ds {
		e.want(!asBool(asMap(s)["deleted"]), "no orphan script tombstone")
	}

	// numeric / boolean normalisation
	rec, _ = e.request(http.MethodPost, "/api/sync/push", token, gin.H{
		"scripts":   gin.H{"upsert": []any{gin.H{"id": "sc_norm", "name": "归一化", "code": "", "enabled": "0"}}},
		"downloads": gin.H{"upsert": []any{gin.H{"platform": "netease", "id": "d_norm", "deviceId": "dev-n", "sizeBytes": -42, "downloadedAtMs": "abc", "quality": nil}}},
	})
	e.mustOK(rec, "normalisation push")
	rec, body = e.request(http.MethodGet, "/api/sync/pull?since="+itoa(before)+"&eventSince=0", token, nil)
	for _, s := range asArr(asMap(body["data"])["scripts"]) {
		m := asMap(s)
		if asStr(m["id"]) == "sc_norm" {
			e.want(!asBool(m["enabled"]), "enabled '0' -> false")
		}
	}
	for _, d := range asArr(asMap(body["data"])["downloads"]) {
		m := asMap(d)
		if asStr(m["id"]) == "d_norm" {
			e.want(asFloat(m["sizeBytes"]) == 0 && asFloat(m["downloadedAtMs"]) == 0 && asStr(m["quality"]) == "", "numeric normalisation")
		}
	}
}

func TestSessionsAndLogout(t *testing.T) {
	e := newEnv(t)
	token, _ := e.register("alice", "secret123", "alice@example.com")

	rec, _ := e.request(http.MethodPost, "/api/auth/logout", token, nil)
	e.mustOK(rec, "logout")
	rec, _ = e.request(http.MethodGet, "/api/me", token, nil)
	e.want(rec.Code == 401, "token invalid after logout")

	rec, body := e.request(http.MethodPost, "/api/auth/login", "", gin.H{"username": "alice", "password": "secret123", "deviceName": "tablet-b"})
	e.mustOK(rec, "login again")
	newToken := asStr(asMap(body["data"])["token"])

	rec, body = e.request(http.MethodGet, "/api/me/sessions", newToken, nil)
	e.mustOK(rec, "sessions")
	sessions := asArr(body["data"])
	e.want(len(sessions) == 1 && asBool(asMap(sessions[0])["current"]), "only current session")

	rec, _ = e.request(http.MethodGet, "/api/sync/pull?since=0", "", nil)
	e.want(rec.Code == 401, "pull without token 401")
}

func TestCrossUserIsolation(t *testing.T) {
	e := newEnv(t)
	tokenA, _ := e.register("alice", "secret123", "alice@example.com")
	tokenB, _ := e.register("carol", "secret999", "carol@example.com")

	rec, _ := e.request(http.MethodPost, "/api/sync/push", tokenA, gin.H{
		"scripts":   gin.H{"upsert": []any{gin.H{"id": "sc_default", "name": "A的脚本", "code": "a"}}},
		"downloads": gin.H{"upsert": []any{gin.H{"platform": "netease", "id": "x", "deviceId": "dev-a"}}},
	})
	e.mustOK(rec, "A push")

	rec, body := e.request(http.MethodGet, "/api/sync/pull?since=0&eventSince=0", tokenB, nil)
	data := asMap(body["data"])
	e.want(len(asArr(data["scripts"])) == 0 && len(asArr(data["downloads"])) == 0, "B sees nothing")

	rec, _ = e.request(http.MethodPost, "/api/sync/push", tokenB, gin.H{"scripts": gin.H{"upsert": []any{gin.H{"id": "sc_default", "name": "B的脚本", "code": "b"}}}})
	e.mustOK(rec, "B reuses script id")

	rec, body = e.request(http.MethodGet, "/api/sync/pull?since=0&eventSince=0", tokenA, nil)
	for _, s := range asArr(asMap(body["data"])["scripts"]) {
		if asStr(asMap(s)["id"]) == "sc_default" {
			e.want(asStr(asMap(s)["name"]) == "A的脚本", "A script untouched by B")
		}
	}
}

func TestAdminAndPassword(t *testing.T) {
	e := newEnv(t)

	rec, body := e.request(http.MethodPost, "/api/auth/login", "", gin.H{"username": "admin", "password": "12346"})
	e.mustOK(rec, "admin login")
	adminToken := asStr(asMap(body["data"])["token"])

	rec, body = e.request(http.MethodGet, "/api/me", adminToken, nil)
	e.mustOK(rec, "admin me")
	e.want(asStr(asMap(body["data"])["role"]) == "admin", "admin role")

	tokenUser, userData := e.register("alice", "secret123", "alice@example.com")
	aliceID := int(asFloat(asMap(userData["user"])["id"]))

	rec, _ = e.request(http.MethodGet, "/api/admin/users", tokenUser, nil)
	e.want(rec.Code == 403, "normal user forbidden")

	rec, body = e.request(http.MethodGet, "/api/admin/users", adminToken, nil)
	e.mustOK(rec, "admin list")
	listData := asMap(body["data"])
	e.want(asFloat(listData["total"]) >= 2, "admin list total")
	for _, u := range asArr(listData["items"]) {
		e.want(asMap(u)["email"] != nil, "list item has email field")
	}
	e.want(rec.Header().Get("x-total-count") != "", "x-total-count header")

	rec, body = e.request(http.MethodGet, "/api/admin/users/"+itoa(float64(aliceID)), adminToken, nil)
	e.mustOK(rec, "admin get user")
	e.want(asStr(asMap(body["data"])["email"]) == "alice@example.com", "admin sees email")

	rec, body = e.request(http.MethodPost, "/api/admin/users", adminToken, gin.H{"username": "bob_test", "password": "bobpassword", "nickname": "鲍勃", "role": "user"})
	e.mustOK(rec, "admin create user")
	bobID := int(asFloat(asMap(body["data"])["id"]))

	rec, body = e.request(http.MethodPut, "/api/admin/users/"+itoa(float64(bobID)), adminToken, gin.H{"nickname": "鲍勃改名", "role": "admin"})
	e.mustOK(rec, "admin update user")
	e.want(asStr(asMap(body["data"])["nickname"]) == "鲍勃改名" && asStr(asMap(body["data"])["role"]) == "admin", "admin update result")

	rec, body = e.request(http.MethodPost, "/api/auth/login", "", gin.H{"username": "bob_test", "password": "bobpassword"})
	e.mustOK(rec, "bob login")
	bobToken := asStr(asMap(body["data"])["token"])
	rec, _ = e.request(http.MethodPost, "/api/sync/push", bobToken, gin.H{
		"scripts":   gin.H{"upsert": []any{gin.H{"id": "bob_sc", "name": "B的脚本", "code": "z"}}},
		"downloads": gin.H{"upsert": []any{gin.H{"platform": "netease", "id": "bob_dl", "deviceId": "bob-dev", "title": "B的下载"}}},
	})
	e.mustOK(rec, "bob push")

	rec, _ = e.request(http.MethodDelete, "/api/admin/users/"+itoa(float64(bobID)), adminToken, nil)
	e.mustOK(rec, "admin delete user")

	var remaining int
	err := e.st.DB().QueryRow("SELECT (SELECT COUNT(*) FROM user_scripts WHERE user_id = ?) + (SELECT COUNT(*) FROM downloads WHERE user_id = ?)", bobID, bobID).Scan(&remaining)
	if err != nil {
		t.Fatalf("leftover query: %v", err)
	}
	e.want(remaining == 0, "cascade cleanup leaves no rows")

	adminID := 1
	rec, _ = e.request(http.MethodDelete, "/api/admin/users/"+itoa(float64(adminID)), adminToken, nil)
	e.want(rec.Code == 400, "cannot delete self")

	// change password
	rec, _ = e.request(http.MethodPut, "/api/me/password", adminToken, gin.H{"oldPassword": "wrong", "newPassword": "newpassword999"})
	e.want(rec.Code == 400, "wrong old password 400")
	rec, _ = e.request(http.MethodPut, "/api/me/password", adminToken, gin.H{"oldPassword": "12346", "newPassword": "newpassword999"})
	e.mustOK(rec, "change password")
	rec, _ = e.request(http.MethodPost, "/api/auth/login", "", gin.H{"username": "admin", "password": "12346"})
	e.want(rec.Code == 401, "old password rejected")
	rec, _ = e.request(http.MethodPost, "/api/auth/login", "", gin.H{"username": "admin", "password": "newpassword999"})
	e.mustOK(rec, "new password works")
}

func itoa(f float64) string {
	return strconvItoa(int(f))
}

func strconvItoa(i int) string {
	if i == 0 {
		return "0"
	}
	neg := i < 0
	if neg {
		i = -i
	}
	var buf [20]byte
	pos := len(buf)
	for i > 0 {
		pos--
		buf[pos] = byte('0' + i%10)
		i /= 10
	}
	if neg {
		pos--
		buf[pos] = '-'
	}
	return string(buf[pos:])
}
