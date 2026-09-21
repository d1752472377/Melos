package httpapi

import (
	"fmt"
	"math"
	"net/http"
	"strconv"
	"time"

	"github.com/gin-gonic/gin"
)

// maxScriptBytes is the per-script source limit (1MB); the client applies a
// smaller 512KB self-limit, this is only the server-side backstop.
const maxScriptBytes = 1024 * 1024

// registerSync mounts /api/sync/pull and /api/sync/push.
func (s *Server) registerSync(r *gin.Engine) {
	auth := s.requireUser()
	r.GET("/api/sync/pull", auth, s.handleSyncPull)
	r.POST("/api/sync/push", auth, s.handleSyncPush)
}

func queryInt64(raw string) int64 {
	if raw == "" {
		return 0
	}
	f, err := strconv.ParseFloat(raw, 64)
	if err != nil || math.IsNaN(f) {
		return 0
	}
	if f < 0 {
		return 0
	}
	return int64(f)
}

func (s *Server) handleSyncPull(c *gin.Context) {
	since := queryInt64(c.Query("since"))
	eventSince := queryInt64(c.Query("eventSince"))
	res, err := s.store.PullSync(c.Request.Context(), currentUser(c).ID, since, eventSince)
	if err != nil {
		s.fail500(c, err)
		return
	}
	ok(c, res)
}

func (s *Server) handleSyncPush(c *gin.Context) {
	body, okBody := decodeBody(c)
	if !okBody {
		return
	}
	if tooBig := findOversizedScript(body); tooBig != "" {
		fail(c, http.StatusRequestEntityTooLarge,
			fmt.Sprintf("单个音源脚本过大（上限 %dKB）：%s", maxScriptBytes/1024, tooBig))
		return
	}
	version, acceptedEvents, err := s.store.ApplySync(c.Request.Context(), currentUser(c).ID, body, time.Now().UnixMilli())
	if err != nil {
		s.fail500(c, err)
		return
	}
	ok(c, gin.H{"version": version, "acceptedEvents": acceptedEvents, "serverTime": time.Now().UnixMilli()})
}

// findOversizedScript returns the id of the first script whose code exceeds the
// 1MB limit, or "" when all scripts are acceptable.
func findOversizedScript(body map[string]any) string {
	coll, ok := body["scripts"].(map[string]any)
	if !ok {
		return ""
	}
	upserts, ok := coll["upsert"].([]any)
	if !ok {
		return ""
	}
	for _, item := range upserts {
		m, ok := item.(map[string]any)
		if !ok {
			continue
		}
		code, ok := m["code"].(string)
		if !ok {
			continue
		}
		if len(code) > maxScriptBytes {
			if isTruthy(m["id"]) {
				return asString(m["id"])
			}
			return "(未命名)"
		}
	}
	return ""
}
