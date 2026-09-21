package security

import (
	"sync"
	"time"
)

// Login brute-force lockout: 5 failures per (username + client IP) lock the
// account for 15 minutes. In-memory with lazy sweeping and a size cap.
const (
	loginFailLimit   = 5
	loginLockMs      = 15 * 60 * 1000
	maxFailMapSize   = 5000
	failSweepEveryMs = 60 * 1000
)

type failEntry struct {
	count   int
	firstAt int64
}

// LoginLockout tracks failed login attempts keyed by username + IP.
type LoginLockout struct {
	mu          sync.Mutex
	fails       map[string]*failEntry
	lastSweepAt int64
}

// NewLoginLockout creates an empty lockout registry.
func NewLoginLockout() *LoginLockout {
	return &LoginLockout{fails: make(map[string]*failEntry)}
}

func failKey(username, ip string) string {
	if ip != "" {
		return username + "\x01" + ip
	}
	return username
}

func (l *LoginLockout) sweepLocked(now int64) {
	if len(l.fails) < maxFailMapSize && now-l.lastSweepAt < failSweepEveryMs {
		return
	}
	l.lastSweepAt = now
	for k, v := range l.fails {
		if now-v.firstAt >= loginLockMs {
			delete(l.fails, k)
		}
	}
	// Still over capacity (e.g. many random attackers): drop oldest entries.
	if len(l.fails) > maxFailMapSize {
		toDelete := len(l.fails) - maxFailMapSize
		deleted := 0
		for k := range l.fails {
			delete(l.fails, k)
			deleted++
			if deleted >= toDelete {
				break
			}
		}
	}
}

// IsLocked reports whether the username+IP pair is currently locked.
func (l *LoginLockout) IsLocked(username, ip string) bool {
	l.mu.Lock()
	defer l.mu.Unlock()
	key := failKey(username, ip)
	e, ok := l.fails[key]
	if !ok {
		return false
	}
	now := time.Now().UnixMilli()
	if now-e.firstAt >= loginLockMs {
		delete(l.fails, key)
		return false
	}
	return e.count >= loginFailLimit
}

// RecordFailure increments the failure counter.
func (l *LoginLockout) RecordFailure(username, ip string) {
	l.mu.Lock()
	defer l.mu.Unlock()
	now := time.Now().UnixMilli()
	l.sweepLocked(now)
	key := failKey(username, ip)
	e, ok := l.fails[key]
	if !ok {
		e = &failEntry{firstAt: now}
		l.fails[key] = e
	}
	e.count++
}

// Clear resets the failure counter after a successful login.
func (l *LoginLockout) Clear(username, ip string) {
	l.mu.Lock()
	defer l.mu.Unlock()
	delete(l.fails, failKey(username, ip))
}
