package httpapi

import (
	"context"
	"fmt"
	"net/http"
	"testing"
	"time"

	"github.com/gin-gonic/gin"

	"melos-backend/internal/config"
)

// playEvent builds one play-event payload as the Android client sends it.
func playEvent(eventID, deviceID, songID string, listenedMs, listenedAtMs int64) gin.H {
	return gin.H{
		"eventId":      eventID,
		"deviceId":     deviceID,
		"platform":     "netease",
		"id":           songID,
		"title":        "歌曲" + songID,
		"artist":       "歌手",
		"listenedMs":   listenedMs,
		"listenedAtMs": listenedAtMs,
	}
}

func pushEvents(t *testing.T, e *testEnv, token string, events ...gin.H) {
	t.Helper()
	rec, body := e.request(http.MethodPost, "/api/sync/push", token, gin.H{"playEvents": events})
	if rec.Code != 200 {
		t.Fatalf("push play events: %d %s", rec.Code, rec.Body.String())
	}
	if accepted, ok := asMap(body["data"])["acceptedEvents"]; ok && asFloat(accepted) < 0 {
		t.Fatalf("bad acceptedEvents: %v", accepted)
	}
}

// pull returns the parsed pull payload for one device token.
func pull(t *testing.T, e *testEnv, token string, since, eventSince int64) map[string]any {
	t.Helper()
	rec, body := e.request(http.MethodGet,
		fmt.Sprintf("/api/sync/pull?since=%d&eventSince=%d", since, eventSince), token, nil)
	if rec.Code != 200 {
		t.Fatalf("pull: %d %s", rec.Code, rec.Body.String())
	}
	return asMap(body["data"])
}

// TestPlayEventsContinuedListeningReachesOtherDevices locks the cursor bug:
// merging a continued segment into an already-pulled row changed listened_ms
// but not id, so every other device (and the per-device admin view) silently
// missed the extra listening time.
func TestPlayEventsContinuedListeningReachesOtherDevices(t *testing.T) {
	e := newEnv(t)
	adminToken := adminLogin(t, e)

	deviceA, userData := e.register("two_phones", "secret123", "two_phones@example.com")
	uid := itoa(asFloat(asMap(userData["user"])["id"]))

	rec, loginBody := e.request(http.MethodPost, "/api/auth/login", "", gin.H{
		"username": "two_phones", "password": "secret123", "deviceName": "第二台",
	})
	e.mustOK(rec, "second device login")
	deviceB := asStr(asMap(loginBody["data"])["token"])

	now := time.Now().UnixMilli()
	start := now - 3*60*1000

	// 设备 A 听第一段
	pushEvents(t, e, deviceA, playEvent("seg-a1", "devA", "song1", 20000, start))

	first := pull(t, e, deviceB, 0, 0)
	events := asArr(first["playEvents"])
	e.want(len(events) == 1, "device B sees the first segment", itoa(float64(len(events))))
	version := asFloat(first["version"])
	nextEventID := asFloat(first["nextEventId"])
	e.want(nextEventID > 0, "nextEventId advanced")

	// 设备 A 继续听同一首（与上一段连续），再推一段
	pushEvents(t, e, deviceA, playEvent("seg-a2", "devA", "song1", 20000, start+20000))

	second := pull(t, e, deviceB, int64(version), int64(nextEventID))
	events = asArr(second["playEvents"])
	e.want(len(events) == 1, "device B receives the continued segment", itoa(float64(len(events))))
	if len(events) == 1 {
		ev := asMap(events[0])
		e.want(asStr(ev["eventId"]) == "seg-a2", "continued segment keeps its own id", asStr(ev["eventId"]))
		e.want(asFloat(ev["listenedMs"]) == 20000, "continued segment carries its own duration")
	}

	// 服务端总量 = 两段之和（两行都算进去，没有被"合并成一行"吞掉）
	rec, body := e.request(http.MethodGet, "/api/admin/users/"+uid+"/overview", adminToken, nil)
	e.mustOK(rec, "overview")
	stats := asMap(asMap(body["data"])["stats"])
	e.want(asFloat(stats["playEvents"]) == 2, "two raw events stored", rec.Body.String())
	e.want(asFloat(stats["listenedMs"]) == 40000, "listened time summed", rec.Body.String())
	e.want(asFloat(stats["playSessions"]) == 1, "still ONE play session", rec.Body.String())

	// 重试同一段：幂等，不再新增行、不再计时
	pushEvents(t, e, deviceA, playEvent("seg-a2", "devA", "song1", 20000, start+20000))
	rec, body = e.request(http.MethodGet, "/api/admin/users/"+uid+"/overview", adminToken, nil)
	stats = asMap(asMap(body["data"])["stats"])
	e.want(asFloat(stats["playEvents"]) == 2 && asFloat(stats["listenedMs"]) == 40000,
		"retry of a stored segment is ignored", rec.Body.String())

	// 隔了很久再听同一首：新的一次播放
	pushEvents(t, e, deviceA, playEvent("seg-a3", "devA", "song1", 20000, start+10*60*1000))
	rec, body = e.request(http.MethodGet, "/api/admin/users/"+uid+"/overview", adminToken, nil)
	stats = asMap(asMap(body["data"])["stats"])
	e.want(asFloat(stats["playSessions"]) == 2, "a long gap starts a new play session", rec.Body.String())
}

// TestPlaySessionNumbersAgreeAcrossAdminEndpoints: 概览、活跃统计、播放记录
// 三个接口必须用同一套"一次播放"口径。
func TestPlaySessionNumbersAgreeAcrossAdminEndpoints(t *testing.T) {
	e := newEnv(t)
	adminToken := adminLogin(t, e)
	userToken, userData := e.register("count_user", "secret123", "count@example.com")
	uid := itoa(asFloat(asMap(userData["user"])["id"]))

	now := time.Now().UnixMilli()
	start := now - 10*60*1000

	// 两段连续（= 1 次播放），中间隔 5 分钟（= 2 次播放），再一段连续（= 仍是 2 次）
	pushEvents(t, e, userToken,
		playEvent("c1", "devC", "songX", 20000, start),
		playEvent("c2", "devC", "songX", 20000, start+20000),
	)
	pushEvents(t, e, userToken, playEvent("c3", "devC", "songX", 20000, start+5*60*1000))

	// 另一台设备同时听同一首歌：必须单独算一次播放（口径含设备）
	pushEvents(t, e, userToken, playEvent("d1", "devD", "songX", 20000, start))

	rec, body := e.request(http.MethodGet, "/api/admin/users/"+uid+"/overview", adminToken, nil)
	e.mustOK(rec, "overview")
	stats := asMap(asMap(body["data"])["stats"])
	sessions := asFloat(stats["playSessions"])
	e.want(sessions == 3, "overview: 3 plays (2 on devC + 1 on devD)", rec.Body.String())
	// 同一批里的 c1/c2 合并成一行（那是同一批内的省行合并），c3 与 d1 各自成行：
	// 3 行原始事件 = 40000 + 20000 + 20000 ms。
	e.want(asFloat(stats["playEvents"]) == 3, "overview: 3 raw rows", rec.Body.String())
	e.want(asFloat(stats["listenedMs"]) == 80000, "overview: listening time summed", rec.Body.String())

	rec, body = e.request(http.MethodGet, "/api/admin/users/"+uid+"/play-events", adminToken, nil)
	e.mustOK(rec, "play-events")
	list := asArr(asMap(body["data"])["items"])
	e.want(float64(len(list)) == sessions, "session list count matches overview",
		itoa(float64(len(list))), itoa(sessions))

	var listed float64
	for _, it := range list {
		listed += asFloat(asMap(it)["listenedMs"])
	}
	e.want(listed == asFloat(stats["listenedMs"]), "session list time matches overview")

	rec, body = e.request(http.MethodGet, "/api/admin/users/"+uid+"/active-stats", adminToken, nil)
	e.mustOK(rec, "active-stats")
	days := asArr(asMap(body["data"])["days"])
	var plays, listened float64
	for _, d := range days {
		plays += asFloat(asMap(d)["events"])
		listened += asFloat(asMap(d)["listenedMs"])
	}
	e.want(plays == 3, "daily chart plays match overview", itoa(plays))
	e.want(listened == asFloat(stats["listenedMs"]), "daily chart time matches overview")
}

// TestPlaySessionDeviceName: 「设备」列要能显示下载记录里登记的设备名。
// play_events.device_id 是客户端设备 id，而 sessions.id 是服务端 UUID，
// 旧 SQL 关联这两列永远匹配不上。
func TestPlaySessionDeviceName(t *testing.T) {
	e := newEnv(t)
	adminToken := adminLogin(t, e)
	userToken, userData := e.register("name_user", "secret123", "name@example.com")
	uid := itoa(asFloat(asMap(userData["user"])["id"]))

	rec, _ := e.request(http.MethodPost, "/api/sync/push", userToken, gin.H{
		"downloads": gin.H{"upsert": []any{gin.H{
			"platform": "netease", "id": "song1", "deviceId": "devName",
			"deviceName": "Pixel 7", "title": "歌曲song1", "quality": "320k",
		}}},
	})
	e.mustOK(rec, "push download with device name")

	pushEvents(t, e, userToken, playEvent("n1", "devName", "song1", 20000, time.Now().UnixMilli()-60000))

	rec, body := e.request(http.MethodGet, "/api/admin/users/"+uid+"/play-events", adminToken, nil)
	e.mustOK(rec, "play-events")
	list := asArr(asMap(body["data"])["items"])
	e.want(len(list) == 1, "one session")
	if len(list) == 1 {
		e.want(asStr(asMap(list[0])["deviceName"]) == "Pixel 7", "device name resolved from downloads",
			asStr(asMap(list[0])["deviceName"]))
	}

	// raw=1 视图也要带上设备名
	rec, body = e.request(http.MethodGet, "/api/admin/users/"+uid+"/play-events?raw=1", adminToken, nil)
	e.mustOK(rec, "play-events raw")
	raw := asArr(asMap(body["data"])["items"])
	e.want(len(raw) == 1 && asStr(asMap(raw[0])["deviceName"]) == "Pixel 7", "raw view has device name")
}

// TestSendCodeDoesNotLockUserOutWhenMailFails: 发信失败时不能留下冷却记录，
// 否则用户没收到邮件却要等 60 秒才能重试。
func TestSendCodeDoesNotLockUserOutWhenMailFails(t *testing.T) {
	// 指向一个必然连不上的地址：连接被拒绝，测试不必等 8s 超时。
	e := newEnvWith(t, func(c *config.Config) { c.MailServiceURL = "http://127.0.0.1:1" })

	rec, _ := e.request(http.MethodPost, "/api/auth/send-code", "", gin.H{"email": "lockout@example.com"})
	e.want(rec.Code == http.StatusInternalServerError, "mail failure surfaces as 500", itoa(float64(rec.Code)))

	rec, _ = e.request(http.MethodPost, "/api/auth/send-code", "", gin.H{"email": "lockout@example.com"})
	e.want(rec.Code != http.StatusTooManyRequests, "retry is not blocked by a phantom cooldown",
		itoa(float64(rec.Code)), rec.Body.String())

	var rows int
	if err := e.st.DB().QueryRow(`SELECT COUNT(*) FROM email_codes WHERE email = ?`, "lockout@example.com").Scan(&rows); err != nil {
		t.Fatalf("count codes: %v", err)
	}
	e.want(rows == 0, "failed send leaves no code row", itoa(float64(rows)))
}

// TestStatsQueriesSurfaceDatabaseErrors: 统计类查询不能再吞错误返回全 0。
func TestStatsQueriesSurfaceDatabaseErrors(t *testing.T) {
	e := newEnv(t)
	if err := e.st.Close(); err != nil {
		t.Fatalf("close store: %v", err)
	}
	ctx := context.Background()
	now := time.Now().UnixMilli()
	if _, err := e.st.GetGlobalSyncStats(ctx, now); err == nil {
		t.Fatal("GetGlobalSyncStats should surface the error")
	}
	if _, err := e.st.GetClientLogStats(ctx, now); err == nil {
		t.Fatal("GetClientLogStats should surface the error")
	}
	if _, err := e.st.AdminUserOverview(ctx, 1); err == nil {
		t.Fatal("AdminUserOverview should surface the error")
	}
}
