package httpapi

import (
	"bytes"
	"encoding/json"
	"mime/multipart"
	"net/http"
	"net/http/httptest"
	"strings"
	"testing"

	"github.com/gin-gonic/gin"

	"melos-backend/internal/config"
)

func (e *testEnv) adminToken() string {
	e.t.Helper()
	rec, body := e.request(http.MethodPost, "/api/auth/login", "", gin.H{"username": "admin", "password": "12346"})
	if rec.Code != 200 {
		e.t.Fatalf("admin login failed: %d %s", rec.Code, rec.Body.String())
	}
	return asStr(asMap(body["data"])["token"])
}

func (e *testEnv) uploadVersion(token string, fields map[string]any, filename string, content []byte) (*httptest.ResponseRecorder, map[string]any) {
	e.t.Helper()
	var buf bytes.Buffer
	w := multipart.NewWriter(&buf)
	for k, v := range fields {
		if err := w.WriteField(k, asString(v)); err != nil {
			e.t.Fatalf("write field: %v", err)
		}
	}
	if filename != "" {
		fw, err := w.CreateFormFile("file", filename)
		if err != nil {
			e.t.Fatalf("create form file: %v", err)
		}
		if _, err := fw.Write(content); err != nil {
			e.t.Fatalf("write file: %v", err)
		}
	}
	if err := w.Close(); err != nil {
		e.t.Fatalf("close multipart: %v", err)
	}

	req := httptest.NewRequest(http.MethodPost, "/api/admin/versions", &buf)
	req.Header.Set("Content-Type", w.FormDataContentType())
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

func TestVersionManagement(t *testing.T) {
	e := newEnv(t)
	adminToken := e.adminToken()

	// Normal users cannot manage versions.
	userToken, _ := e.register("alice", "secret123", "alice@example.com")
	rec, _ := e.uploadVersion(userToken, gin.H{"versionCode": 1, "versionName": "1.0.0"}, "app.apk", []byte("apkdata"))
	e.want(rec.Code == 403, "non-admin forbidden", rec.Body.String())

	// Missing file / bad versionCode -> 400.
	rec, _ = e.uploadVersion(adminToken, gin.H{"versionCode": 1, "versionName": "1.0.0"}, "", nil)
	e.want(rec.Code == 400, "missing file 400", rec.Body.String())
	rec, _ = e.uploadVersion(adminToken, gin.H{"versionCode": 0, "versionName": "1.0.0"}, "app.apk", []byte("x"))
	e.want(rec.Code == 400, "invalid versionCode 400")
	rec, _ = e.uploadVersion(adminToken, gin.H{"versionCode": 1}, "app.apk", []byte("x"))
	e.want(rec.Code == 400, "missing versionName 400")

	// Publish v100.
	payload := []byte("APK-BYTES-100")
	rec, body := e.uploadVersion(adminToken, gin.H{
		"versionCode": 100, "versionName": "1.0.0", "changelog": "首个版本", "forceUpdate": "true",
	}, "melos.apk", payload)
	e.mustOK(rec, "publish version")
	v := asMap(body["data"])
	e.want(asFloat(v["version_code"]) == 100 && asStr(v["version_name"]) == "1.0.0", "version fields")
	e.want(strings.HasPrefix(asStr(v["apk_url"]), apkURLPrefix), "apk_url prefix", asStr(v["apk_url"]))
	e.want(asFloat(v["file_size"]) == float64(len(payload)), "file_size")
	e.want(asBool(v["force_update"]) && asBool(v["enabled"]), "flags")
	e.want(len(asStr(v["hash_sha256"])) == 64, "sha256 computed")
	e.want(asStr(v["created_at"]) != "", "created_at filled by DB default")

	// Duplicate version_code -> 409.
	rec, _ = e.uploadVersion(adminToken, gin.H{"versionCode": 100, "versionName": "1.0.1"}, "m.apk", []byte("x"))
	e.want(rec.Code == 409, "duplicate versionCode 409")

	// Public latest (no auth) and download (no auth).
	rec, body = e.request(http.MethodGet, "/api/app/version/latest", "", nil)
	e.mustOK(rec, "public latest")
	e.want(asFloat(asMap(body["data"])["version_code"]) == 100, "latest version code")

	fileName := strings.TrimPrefix(asStr(v["apk_url"]), apkURLPrefix)
	rec, _ = e.request(http.MethodGet, "/api/app/download/"+fileName, "", nil)
	e.want(rec.Code == 200, "download 200", rec.Body.String())
	e.want(bytes.Equal(rec.Body.Bytes(), payload), "download bytes match")

	// Publish a higher version, then disable it: latest must fall back.
	rec, body = e.uploadVersion(adminToken, gin.H{"versionCode": 200, "versionName": "2.0.0"}, "m2.apk", []byte("APK-200"))
	e.mustOK(rec, "publish v200")
	id200 := int(asFloat(asMap(body["data"])["id"]))
	rec, body = e.request(http.MethodGet, "/api/app/version/latest", "", nil)
	e.want(asFloat(asMap(body["data"])["version_code"]) == 200, "latest is 200")

	rec, body = e.request(http.MethodPatch, "/api/admin/versions/"+itoa(float64(id200)), adminToken,
		gin.H{"enabled": false, "changelog": "已禁用", "forceUpdate": false})
	e.mustOK(rec, "patch version")
	d := asMap(body["data"])
	e.want(!asBool(d["enabled"]) && asStr(d["changelog"]) == "已禁用" && !asBool(d["force_update"]), "patch applied")

	rec, body = e.request(http.MethodGet, "/api/app/version/latest", "", nil)
	e.want(asFloat(asMap(body["data"])["version_code"]) == 100, "latest falls back when disabled")

	// Admin list: pagination, version_code DESC, total header.
	rec, body = e.request(http.MethodGet, "/api/admin/versions?page=1&limit=10", adminToken, nil)
	e.mustOK(rec, "admin list")
	ld := asMap(body["data"])
	e.want(asFloat(ld["total"]) == 2, "list total")
	items := asArr(ld["items"])
	e.want(len(items) == 2 && asFloat(asMap(items[0])["version_code"]) == 200, "list sorted desc")
	e.want(rec.Header().Get("x-total-count") == "2", "x-total-count header")

	// Delete v100: row and local file both disappear.
	rec, _ = e.request(http.MethodDelete, "/api/admin/versions/"+itoa(asFloat(v["id"])), adminToken, nil)
	e.mustOK(rec, "delete version")
	rec, _ = e.request(http.MethodGet, "/api/app/download/"+fileName, "", nil)
	e.want(rec.Code == 404, "file removed after delete")
	rec, _ = e.request(http.MethodDelete, "/api/admin/versions/"+itoa(asFloat(v["id"])), adminToken, nil)
	e.want(rec.Code == 404, "delete missing version 404")

	// Unauthenticated admin access is rejected.
	rec, _ = e.request(http.MethodGet, "/api/admin/versions", "", nil)
	e.want(rec.Code == 401, "admin list without token 401")
}

func TestVersionUploadSizeLimit(t *testing.T) {
	e := newEnvWith(t, func(cfg *config.Config) { cfg.APKMaxMB = 1 })
	adminToken := e.adminToken()

	big := bytes.Repeat([]byte("x"), 2*1024*1024)
	rec, _ := e.uploadVersion(adminToken, gin.H{"versionCode": 1, "versionName": "1.0.0"}, "big.apk", big)
	e.want(rec.Code == 413, "oversized apk 413", rec.Body.String())
}
