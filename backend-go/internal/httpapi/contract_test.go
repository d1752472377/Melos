package httpapi

import (
	"net/http"
	"testing"

	"github.com/gin-gonic/gin"
)

// Ported from backend/scripts/client-contract.mjs: field-level wire conformance
// so a renamed key cannot silently deserialise to null on the Android client.

func (e *testEnv) hasKeys(label string, obj map[string]any, keys ...string) {
	e.t.Helper()
	missing := []string{}
	for _, k := range keys {
		if _, present := obj[k]; !present {
			missing = append(missing, k)
		}
	}
	if len(missing) > 0 {
		e.t.Fatalf("%s 缺少字段 %v（实际键: %v）", label, missing, obj)
	}
}

func TestClientWireContract(t *testing.T) {
	e := newEnv(t)
	tokenA, _ := e.register("ca_user", "secret123", "ca@example.com")
	tokenB, _ := e.register("cb_user", "secret123", "cb@example.com")

	rec, health := e.request(http.MethodGet, "/api/health", "", nil)
	e.mustOK(rec, "health")
	e.hasKeys("HealthData", asMap(health["data"]), "ok", "time", "version")

	song := func(id, platform string) gin.H {
		return gin.H{"id": id, "title": "歌曲" + id, "artist": "歌手", "album": "专辑", "artwork": "", "platform": platform}
	}

	rec, pushBody := e.request(http.MethodPost, "/api/sync/push", tokenA, gin.H{
		"favorites": gin.H{"upsert": []any{song("f1", "netease"), song("f2", "netease")}},
		"history": gin.H{"upsert": []any{
			gin.H{"id": "h1", "title": "歌曲h1", "artist": "歌手", "album": "专辑", "artwork": "", "platform": "netease", "playedAtMs": 1700000000000},
		}},
		"playlists": gin.H{"upsert": []any{
			gin.H{"id": "local_1", "name": "我的通勤", "coverUrl": "", "kind": "mine"},
			gin.H{"id": "123456", "name": "收藏的网易歌单", "coverUrl": "https://cdn.example.com/c.jpg", "kind": "fav"},
		}},
		"playlistSongs": gin.H{"upsert": []any{
			gin.H{"playlistId": "local_1", "position": 0, "song": song("f1", "netease")},
			gin.H{"playlistId": "local_1", "position": 1, "song": song("f2", "netease")},
		}},
		"scripts": gin.H{"upsert": []any{
			gin.H{"id": "sc1", "name": "自写音源", "description": "说明", "code": "const a = 1;", "enabled": true},
		}},
		"downloads": gin.H{"upsert": []any{
			gin.H{"platform": "netease", "id": "f1", "deviceId": "dev-phone", "deviceName": "Pixel 8",
				"title": "歌曲f1", "artist": "歌手", "sizeBytes": 4096, "downloadedAtMs": 1700000000000},
		}},
		"playEvents": []any{
			gin.H{"eventId": "ev1", "platform": "netease", "id": "f1", "title": "歌曲f1", "artist": "歌手",
				"listenedMs": 30000, "durationMs": 200000, "listenedAtMs": 1700000000000},
		},
	})
	e.mustOK(rec, "contract push")
	pushData := asMap(pushBody["data"])
	e.hasKeys("SyncPushResult", pushData, "version", "acceptedEvents", "serverTime")
	e.want(asFloat(pushData["acceptedEvents"]) == 1, "acceptedEvents 1")

	rec, pullBody := e.request(http.MethodGet, "/api/sync/pull?since=0&eventSince=0", tokenA, nil)
	e.mustOK(rec, "contract pull")
	d := asMap(pullBody["data"])
	e.hasKeys("CloudSyncPullData", d, "version", "nextEventId", "serverTime",
		"favorites", "history", "playlists", "playlistSongs", "playEvents", "scripts", "downloads")

	favs := asArr(d["favorites"])
	hist := asArr(d["history"])
	pls := asArr(d["playlists"])
	plSongs := asArr(d["playlistSongs"])
	scripts := asArr(d["scripts"])
	downloads := asArr(d["downloads"])
	events := asArr(d["playEvents"])

	e.hasKeys("SyncFavoriteItem", asMap(favs[0]), "platform", "id", "song", "updatedAtMs", "deleted")
	e.hasKeys("SyncHistoryItem", asMap(hist[0]), "platform", "id", "song", "playedAtMs", "updatedAtMs", "deleted")
	e.hasKeys("SyncPlaylistItem", asMap(pls[0]), "id", "kind", "name", "coverUrl", "updatedAtMs", "deleted")
	e.hasKeys("SyncPlaylistSongItem", asMap(plSongs[0]), "playlistId", "position", "platform", "songId", "song", "updatedAtMs", "deleted")
	e.hasKeys("SyncScriptItem", asMap(scripts[0]), "id", "name", "description", "code", "enabled", "updatedAtMs", "deleted")
	e.hasKeys("SyncDownloadItem", asMap(downloads[0]), "platform", "id", "deviceId", "deviceName", "title", "artist",
		"sizeBytes", "downloadedAtMs", "updatedAtMs", "deleted")
	e.hasKeys("SyncPlayEventItem", asMap(events[0]), "eventId", "platform", "songId", "title", "artist",
		"listenedMs", "durationMs", "listenedAtMs", "updatedAtMs")

	// Values, not just key names.
	e.want(asStr(asMap(asMap(favs[0])["song"])["title"]) != "", "favourite song parses")
	e.want(len(favs) == 2, "two favourites")
	var mineKind, favKind string
	for _, p := range pls {
		m := asMap(p)
		if asStr(m["id"]) == "local_1" {
			mineKind = asStr(m["kind"])
		}
		if asStr(m["id"]) == "123456" {
			favKind = asStr(m["kind"])
		}
	}
	e.want(mineKind == "mine", "kind mine round-trips")
	e.want(favKind == "fav", "kind fav round-trips")
	e.want(len(plSongs) == 2, "two playlist songs")
	e.want(asStr(asMap(scripts[0])["code"]) == "const a = 1;", "script code round-trips")
	e.want(asBool(asMap(scripts[0])["enabled"]), "script enabled is bool")
	e.want(asStr(asMap(downloads[0])["deviceId"]) == "dev-phone", "download keeps deviceId")
	e.want(asFloat(asMap(downloads[0])["sizeBytes"]) == 4096, "download size intact")
	e.want(asFloat(asMap(events[0])["listenedMs"]) == 30000 && asStr(asMap(events[0])["songId"]) == "f1", "play event intact")

	// Tombstones must keep their locating keys.
	cursor := asFloat(d["version"])
	rec, _ = e.request(http.MethodPost, "/api/sync/push", tokenA, gin.H{
		"favorites": gin.H{"delete": []any{gin.H{"platform": "netease", "id": "f2"}}},
		"playlists": gin.H{"delete": []any{gin.H{"id": "123456", "kind": "fav"}}},
		"downloads": gin.H{"delete": []any{gin.H{"platform": "netease", "id": "f1", "deviceId": "dev-phone"}}},
		"scripts":   gin.H{"delete": []any{gin.H{"id": "sc1"}}},
	})
	e.mustOK(rec, "tombstone push")

	rec, deltaBody := e.request(http.MethodGet, "/api/sync/pull?since="+itoa(cursor)+"&eventSince=0", tokenA, nil)
	delta := asMap(deltaBody["data"])
	var favTomb, plTomb, dlTomb, scTomb map[string]any
	for _, x := range asArr(delta["favorites"]) {
		if asStr(asMap(x)["id"]) == "f2" {
			favTomb = asMap(x)
		}
	}
	for _, x := range asArr(delta["playlists"]) {
		if asStr(asMap(x)["id"]) == "123456" {
			plTomb = asMap(x)
		}
	}
	for _, x := range asArr(delta["downloads"]) {
		if asStr(asMap(x)["id"]) == "f1" {
			dlTomb = asMap(x)
		}
	}
	for _, x := range asArr(delta["scripts"]) {
		if asStr(asMap(x)["id"]) == "sc1" {
			scTomb = asMap(x)
		}
	}
	e.want(favTomb != nil && asBool(favTomb["deleted"]), "favourite tombstone")
	e.want(plTomb != nil && asStr(plTomb["kind"]) == "fav", "playlist tombstone keeps kind")
	e.want(dlTomb != nil && asStr(dlTomb["deviceId"]) == "dev-phone", "download tombstone keeps deviceId")
	e.want(scTomb != nil && asBool(scTomb["deleted"]), "script tombstone")
	e.want(len(asArr(delta["favorites"])) == 1, "delta only returns changes")
	e.want(asFloat(delta["version"]) > cursor, "delta version advances")

	rec, againBody := e.request(http.MethodGet, "/api/sync/pull?since="+itoa(asFloat(delta["version"]))+"&eventSince=0", tokenA, nil)
	again := asMap(againBody["data"])
	e.want(len(asArr(again["favorites"])) == 0 && len(asArr(again["playlists"])) == 0, "cursor does not repeat changes")

	rec, fullBody := e.request(http.MethodGet, "/api/sync/pull?since=0&eventSince=0", tokenA, nil)
	for _, x := range asArr(asMap(fullBody["data"])["favorites"]) {
		e.want(asStr(asMap(x)["id"]) != "f2", "full pull hides tombstones")
	}

	// Account isolation + anonymous access.
	rec, bBody := e.request(http.MethodGet, "/api/sync/pull?since=0&eventSince=0", tokenB, nil)
	bData := asMap(bBody["data"])
	e.want(len(asArr(bData["favorites"])) == 0 && len(asArr(bData["scripts"])) == 0 && len(asArr(bData["downloads"])) == 0, "account B sees nothing")

	rec, _ = e.request(http.MethodGet, "/api/sync/pull?since=0&eventSince=0", "", nil)
	e.want(rec.Code == 401, "anonymous pull rejected")
}
