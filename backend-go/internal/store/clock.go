package store

import "time"

// nowMs returns the current time as Unix milliseconds (server receive time).
func nowMs() int64 { return time.Now().UnixMilli() }
