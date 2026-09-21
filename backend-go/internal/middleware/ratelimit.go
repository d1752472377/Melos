// Package middleware provides Gin middleware shared across routes.
package middleware

import (
	"net/http"
	"strconv"
	"strings"
	"sync"
	"time"

	"github.com/gin-gonic/gin"
)

// RateLimiter is a fixed-window, per-IP limiter (mirrors lib/rateLimit.js).
// Keys are isolated per bucket so sync traffic cannot exhaust the auth budget.
type RateLimiter struct {
	name        string
	limit       int
	windowMs    int64
	mu          sync.Mutex
	counts      map[string]int
	lastSweepAt int64
}

// NewRateLimiter creates a limiter for the given bucket.
func NewRateLimiter(name string, limit int, windowMs int64) *RateLimiter {
	return &RateLimiter{name: name, limit: limit, windowMs: windowMs, counts: make(map[string]int)}
}

// sweepLocked drops counters from stale windows. Called with r.mu held.
func (r *RateLimiter) sweepLocked(now int64) {
	if len(r.counts) < 2000 && now-r.lastSweepAt < 30_000 {
		return
	}
	r.lastSweepAt = now
	currentSlot := now / r.windowMs
	for k := range r.counts {
		idx := strings.LastIndexByte(k, '\x01')
		if idx < 0 {
			delete(r.counts, k)
			continue
		}
		slot, err := strconv.ParseInt(k[idx+1:], 10, 64)
		if err != nil || slot < currentSlot-1 {
			delete(r.counts, k)
		}
	}
}

// Middleware enforces the limit and replies 429 when exceeded.
func (r *RateLimiter) Middleware() gin.HandlerFunc {
	return func(c *gin.Context) {
		now := time.Now().UnixMilli()
		slot := now / r.windowMs
		key := r.name + "\x01" + c.ClientIP() + "\x01" + strconv.FormatInt(slot, 10)

		r.mu.Lock()
		r.counts[key]++
		count := r.counts[key]
		r.sweepLocked(now)
		r.mu.Unlock()

		if count > r.limit {
			c.AbortWithStatusJSON(http.StatusTooManyRequests, gin.H{"code": 429, "msg": "请求过于频繁，请稍后再试"})
			return
		}
		c.Next()
	}
}
